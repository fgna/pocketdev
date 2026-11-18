package de.fgna.pocketdev.project

/**
 * Project-owned APK path. Empty means discovery may be used only if unambiguous.
 * Absolute paths, shell metacharacters, traversal and non-APK files are never accepted.
 */
object ApkArtifactPath {
    fun validateOptional(value: String) {
        if (value.isBlank()) return
        require(value == value.trim() && value.endsWith(".apk")) { "Use a relative APK path ending in .apk." }
        require(!value.startsWith("/") && !value.contains('\\') && !value.contains('\u0000') &&
            !value.contains('\n') && !value.contains('\r')) { "APK path must be relative to the project." }
        require(value.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
            "APK path must not contain traversal or empty segments."
        }
    }

    fun select(configured: String, candidates: List<String>): String {
        validateOptional(configured)
        if (configured.isNotBlank()) return configured
        val available = candidates.map { it.removePrefix("./") }.filter { it.isNotBlank() }.distinct().sorted()
        require(available.isNotEmpty()) { "No APK found under build/outputs/apk. Run Build first or configure an APK path." }
        require(available.size == 1) {
            "Multiple APKs found: ${available.take(6).joinToString(", ")}. " +
                "Set APK path in Edit project to select one explicitly."
        }
        validateOptional(available.single())
        return available.single()
    }
}
