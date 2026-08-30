package de.fgna.pocketdev.transfer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fgna.pocketdev.data.SshProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PhoneTransferFile(
    val name: String,
    val sizeBytes: Long,
    val directory: Boolean,
    val uri: String,
)

data class FileTransferState(
    val serverPath: String = SshFileTransferClient.DEFAULT_SERVER_PATH,
    val resolvedServerPath: String? = null,
    val phoneTreeUri: String? = null,
    val serverFiles: List<RemoteTransferFile> = emptyList(),
    val phoneFiles: List<PhoneTransferFile> = emptyList(),
    val selectedServerFiles: Set<String> = emptySet(),
    val selectedPhoneFiles: Set<String> = emptySet(),
    val busy: Boolean = false,
    val progress: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class FileTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepository = SshProfileRepository(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = SshFileTransferClient()

    private val _state = MutableStateFlow(
        FileTransferState(
            serverPath = prefs.getString(KEY_SERVER_PATH, SshFileTransferClient.DEFAULT_SERVER_PATH)
                ?: SshFileTransferClient.DEFAULT_SERVER_PATH,
            phoneTreeUri = prefs.getString(KEY_PHONE_TREE, null),
        ),
    )
    val state: StateFlow<FileTransferState> = _state.asStateFlow()

    fun setServerPath(value: String) {
        _state.update { it.copy(serverPath = value, message = null, error = null) }
    }

    fun saveServerPath() {
        val value = _state.value.serverPath.trim().ifBlank { SshFileTransferClient.DEFAULT_SERVER_PATH }
        prefs.edit().putString(KEY_SERVER_PATH, value).apply()
        _state.update { it.copy(serverPath = value, message = "Server transfer directory saved.", error = null) }
        refresh()
    }

    fun setPhoneTree(uri: Uri) {
        val root = usablePhoneRoot(uri)
        if (root == null) {
            reportPhoneFolderError("PocketDev cannot read and write this folder. Please choose another folder and confirm 'Use this folder'.")
            return
        }
        prefs.edit().putString(KEY_PHONE_TREE, uri.toString()).apply()
        _state.update {
            it.copy(
                phoneTreeUri = uri.toString(),
                selectedPhoneFiles = emptySet(),
                message = "Phone transfer directory selected.",
                error = null,
            )
        }
        refreshPhoneFiles()
    }

    fun reportPhoneFolderError(message: String) {
        _state.update { it.copy(error = message, message = null) }
    }

    fun toggleServerFile(name: String) {
        _state.update { state ->
            val selected = state.selectedServerFiles.toMutableSet()
            if (!selected.add(name)) selected.remove(name)
            state.copy(selectedServerFiles = selected)
        }
    }

    fun togglePhoneFile(uri: String) {
        _state.update { state ->
            val selected = state.selectedPhoneFiles.toMutableSet()
            if (!selected.add(uri)) selected.remove(uri)
            state.copy(selectedPhoneFiles = selected)
        }
    }

    fun deleteServerFile(name: String) {
        val file = _state.value.serverFiles.firstOrNull { it.name == name && !it.directory } ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, progress = "Deleting ${file.name}…", error = null, message = null) }
        viewModelScope.launch {
            runCatching {
                val (profile, secret) = profileAndSecret()
                client.deleteFile(profile, secret, _state.value.serverPath, file.name)
            }.onSuccess { resolvedPath ->
                _state.update {
                    it.copy(
                        busy = false,
                        progress = null,
                        resolvedServerPath = resolvedPath,
                        selectedServerFiles = it.selectedServerFiles - file.name,
                        message = "Deleted ${file.name}.",
                    )
                }
                refresh()
            }.onFailure(::finishWithError)
        }
    }

    fun deletePhoneFile(uri: String) {
        val file = _state.value.phoneFiles.firstOrNull { it.uri == uri && !it.directory } ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, progress = "Deleting ${file.name}…", error = null, message = null) }
        viewModelScope.launch {
            runCatching {
                val root = phoneRoot() ?: error("The saved phone transfer folder is no longer accessible. Choose it again.")
                val target = root.listFiles().firstOrNull { it.uri.toString() == file.uri && !it.isDirectory }
                    ?: error("${file.name} is no longer in the phone transfer directory.")
                require(target.delete()) { "Could not delete ${file.name}." }
            }.onSuccess {
                _state.update {
                    it.copy(
                        busy = false,
                        progress = null,
                        selectedPhoneFiles = it.selectedPhoneFiles - file.uri,
                        message = "Deleted ${file.name}.",
                    )
                }
                refresh()
            }.onFailure(::finishWithError)
        }
    }

    fun refresh() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, progress = "Refreshing…", error = null) }
        viewModelScope.launch {
            runCatching {
                val (profile, secret) = profileAndSecret()
                val listing = client.list(profile, secret, _state.value.serverPath)
                val phoneFiles = readPhoneFiles()
                listing to phoneFiles
            }.onSuccess { (listing, phoneFiles) ->
                _state.update {
                    it.copy(
                        busy = false,
                        progress = null,
                        resolvedServerPath = listing.absolutePath,
                        serverFiles = listing.files,
                        phoneFiles = phoneFiles,
                        selectedServerFiles = it.selectedServerFiles.intersect(listing.files.filterNot { file -> file.directory }.map { file -> file.name }.toSet()),
                        selectedPhoneFiles = it.selectedPhoneFiles.intersect(phoneFiles.filterNot { file -> file.directory }.map { file -> file.uri }.toSet()),
                        error = null,
                    )
                }
            }.onFailure(::finishWithError)
        }
    }

    fun refreshPhoneFiles() {
        runCatching { readPhoneFiles() }
            .onSuccess { files -> _state.update { it.copy(phoneFiles = files, error = null) } }
            .onFailure { error -> _state.update { it.copy(error = error.message ?: error::class.java.simpleName) } }
    }

    fun uploadSelected() {
        val selected = _state.value.phoneFiles.filter { !it.directory && it.uri in _state.value.selectedPhoneFiles }
        if (selected.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true, progress = "Preparing ${selected.size} file(s)…", error = null, message = null) }
        viewModelScope.launch {
            val transferId = TransferActivityRegistry.begin()
            try {
                runCatching {
                    val app = getApplication<Application>()
                    val (profile, secret) = profileAndSecret()
                    val staging = File(app.cacheDir, "transfer/upload").apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    val localFiles = selected.map { item ->
                        val name = item.name.trim()
                        require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name) {
                            "Unsafe transfer filename: ${item.name}"
                        }
                        val target = File(staging, name)
                        app.contentResolver.openInputStream(Uri.parse(item.uri)).use { input ->
                            requireNotNull(input) { "Could not open ${item.name}." }
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target
                    }
                    client.upload(profile, secret, _state.value.serverPath, localFiles) { current, total, name ->
                        _state.update { it.copy(progress = "Uploading $current/$total · $name") }
                    }
                }.onSuccess { resolvedPath ->
                    _state.update {
                        it.copy(
                            busy = false,
                            progress = null,
                            resolvedServerPath = resolvedPath,
                            selectedPhoneFiles = emptySet(),
                            message = "Upload completed.",
                        )
                    }
                    refresh()
                }.onFailure(::finishWithError)
            } finally {
                TransferActivityRegistry.end(transferId)
            }
        }
    }

    fun downloadSelected() {
        val selected = _state.value.serverFiles.filter { !it.directory && it.name in _state.value.selectedServerFiles }
        if (selected.isEmpty() || _state.value.busy) return
        val phoneRoot = phoneRoot() ?: run {
            _state.update { it.copy(error = "The saved phone transfer folder is no longer accessible. Choose it again.") }
            return
        }
        _state.update { it.copy(busy = true, progress = "Downloading ${selected.size} file(s)…", error = null, message = null) }
        viewModelScope.launch {
            val transferId = TransferActivityRegistry.begin()
            try {
                runCatching {
                    val app = getApplication<Application>()
                    val (profile, secret) = profileAndSecret()
                    val staging = File(app.cacheDir, "transfer/download")
                    val files = client.download(
                        profile,
                        secret,
                        _state.value.serverPath,
                        selected.map { it.name },
                        staging,
                    ) { current, total, name ->
                        _state.update { it.copy(progress = "Downloading $current/$total · $name") }
                    }
                    files.forEachIndexed { index, file ->
                        _state.update { it.copy(progress = "Saving ${index + 1}/${files.size} · ${file.name}") }
                        phoneRoot.findFile(file.name)?.delete()
                        val target = phoneRoot.createFile("application/octet-stream", file.name)
                            ?: error("Could not create ${file.name} in the phone transfer directory.")
                        app.contentResolver.openOutputStream(target.uri, "w").use { output ->
                            requireNotNull(output) { "Could not write ${file.name}." }
                            file.inputStream().use { input -> input.copyTo(output) }
                        }
                    }
                }.onSuccess {
                    _state.update {
                        it.copy(
                            busy = false,
                            progress = null,
                            selectedServerFiles = emptySet(),
                            message = "Download completed.",
                        )
                    }
                    refresh()
                }.onFailure(::finishWithError)
            } finally {
                TransferActivityRegistry.end(transferId)
            }
        }
    }

    private fun profileAndSecret() = run {
        val stored = profileRepository.load() ?: error("Configure the SSH server first.")
        require(stored.profile.hostKeySha256.isNotBlank()) { "Trust the SSH server first." }
        val secret = profileRepository.loadSecret()?.takeIf { it.isNotBlank() }
            ?: error("No SSH authentication secret is stored.")
        stored.profile to secret
    }

    private fun phoneRoot(): DocumentFile? {
        val uriString = _state.value.phoneTreeUri ?: return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return usablePhoneRoot(uri)
    }

    private fun usablePhoneRoot(uri: Uri): DocumentFile? {
        val root = runCatching { DocumentFile.fromTreeUri(getApplication(), uri) }.getOrNull() ?: return null
        if (!root.isDirectory || !root.canRead() || !root.canWrite()) return null
        return root
    }

    private fun readPhoneFiles(): List<PhoneTransferFile> {
        val uriString = _state.value.phoneTreeUri ?: return emptyList()
        val root = phoneRoot() ?: error("The saved phone transfer folder is no longer accessible. Choose it again.")
        return root.listFiles()
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                PhoneTransferFile(
                    name = name,
                    sizeBytes = file.length(),
                    directory = file.isDirectory,
                    uri = file.uri.toString(),
                )
            }
            .sortedWith(compareBy<PhoneTransferFile> { !it.directory }.thenBy { it.name.lowercase() })
    }

    private fun finishWithError(error: Throwable) {
        _state.update {
            it.copy(
                busy = false,
                progress = null,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "pocketdev-transfer"
        const val KEY_SERVER_PATH = "serverPath"
        const val KEY_PHONE_TREE = "phoneTreeUri"
    }
}
