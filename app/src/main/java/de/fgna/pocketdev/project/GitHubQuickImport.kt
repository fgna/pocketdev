package de.fgna.pocketdev.project

object GitHubQuickImport {
    fun parse(input: String): String {
        val value = input.trim().removeSuffix("/")
        val repo = when {
            value.startsWith("https://github.com/") -> value.removePrefix("https://github.com/")
            else -> value
        }.removeSuffix(".git")
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) &&
            repo.split("/").none { it == "." || it == ".." }) {
            "Enter owner/name or https://github.com/owner/name"
        }
        return repo
    }

    fun command(repository: String): String {
        val repo = parse(repository)
        val name = repo.substringAfter('/')
        val ssh = "git@github.com:$repo.git"
        val https = "https://github.com/$repo"
        return """
            target="$HOME/Projects/$name"
            if [ -e "$target" ] || [ -L "$target" ]; then
              if [ ! -d "$target/.git" ] && [ ! -f "$target/.git" ]; then
                printf 'POCKETDEV_IMPORT_CONFLICT\n'; exit 20
              fi
              origin=$(git -C "$target" remote get-url origin 2>/dev/null) || exit 21
              case "$origin" in
                '$ssh'|'$https'|'$https.git'|'git://github.com/$repo.git'|'ssh://git@github.com/$repo.git') ;;
                *) printf 'POCKETDEV_IMPORT_CONFLICT\n'; exit 20 ;;
              esac
            else
              mkdir -p "$HOME/Projects" || exit 22
              git clone '$ssh' "$target" || exit 23
            fi
            printf 'POCKETDEV_IMPORT_OK:%s\n' "$target"
        """.trimIndent()
    }
}
