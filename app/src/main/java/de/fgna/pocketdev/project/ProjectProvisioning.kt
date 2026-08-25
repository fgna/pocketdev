package de.fgna.pocketdev.project

object ProjectProvisioning {
    const val EXISTS_MARKER = "POCKETDEV_PROJECT_EXISTS"
    const val MISSING_MARKER = "POCKETDEV_PROJECT_MISSING"
    const val CREATED_MARKER = "POCKETDEV_PROJECT_CREATED"

    fun checkCommand(project: ProjectConfig): String {
        val path = validatedPath(project)
        return "if [ -d ${ProjectCommandBuilder.shellQuote(path)} ]; then printf '$EXISTS_MARKER\\n'; else printf '$MISSING_MARKER\\n'; fi"
    }

    fun createCommand(project: ProjectConfig): String {
        val path = validatedPath(project)
        val quotedPath = ProjectCommandBuilder.shellQuote(path)
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" }
        val repo = project.githubRepository.trim()
        val create = if (repo.isBlank()) {
            "mkdir -p $quotedPath"
        } else {
            require(Regex("^[^/\\s]+/[^/\\s]+$").matches(repo)) {
                "GitHub repository must use owner/name format."
            }
            val sshUrl = "git@github.com:$repo.git"
            "mkdir -p ${ProjectCommandBuilder.shellQuote(parent)} && git clone ${ProjectCommandBuilder.shellQuote(sshUrl)} $quotedPath"
        }
        return "$create && test -d $quotedPath && printf '$CREATED_MARKER %s\\n' $quotedPath"
    }

    private fun validatedPath(project: ProjectConfig): String {
        val path = project.remotePath.trim()
        require(path.startsWith("/")) { "Project path must be absolute." }
        require(path != "/") { "Project path cannot be the filesystem root." }
        return path
    }
}
