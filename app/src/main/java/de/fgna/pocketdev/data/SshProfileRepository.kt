package de.fgna.pocketdev.data

import android.content.Context
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ssh.SshProfile

data class StoredSshProfile(
    val profile: SshProfile,
    val hasSecret: Boolean,
)

class SshProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pocketdev-profile", Context.MODE_PRIVATE)
    private val secretStore = SecureSecretStore(context)

    fun load(): StoredSshProfile? {
        val host = prefs.getString("host", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val fingerprint = prefs.getString("hostKeySha256", null) ?: return null
        val authMode = runCatching {
            AuthMode.valueOf(prefs.getString("authMode", AuthMode.PASSWORD.name)!!)
        }.getOrDefault(AuthMode.PASSWORD)

        return StoredSshProfile(
            profile = SshProfile(
                host = host,
                port = prefs.getInt("port", 22),
                username = username,
                hostKeySha256 = fingerprint,
                authMode = authMode,
            ),
            hasSecret = !secretStore.get(SECRET_KEY).isNullOrEmpty(),
        )
    }

    fun save(profile: SshProfile, secret: String?) {
        prefs.edit()
            .putString("host", profile.host)
            .putInt("port", profile.port)
            .putString("username", profile.username)
            .putString("hostKeySha256", profile.hostKeySha256)
            .putString("authMode", profile.authMode.name)
            .apply()

        if (!secret.isNullOrEmpty()) {
            secretStore.put(SECRET_KEY, secret)
        }
    }

    fun loadSecret(): String? = secretStore.get(SECRET_KEY)

    fun clear() {
        prefs.edit().clear().apply()
        secretStore.remove(SECRET_KEY)
    }

    private companion object {
        const val SECRET_KEY = "ssh-auth-secret"
    }
}
