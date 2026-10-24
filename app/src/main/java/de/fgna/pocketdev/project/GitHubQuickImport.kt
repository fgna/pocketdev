package de.fgna.pocketdev.project

object GitHubQuickImport {
    fun parse(input: String): String {
        val value = input.trim().removeSuffix("/")
        val name = when {
            value.startsWith("https://github.com/") -> value.removePrefix("https://github.com/")
            value.startsWith("http://github.com/") -> value.removePrefix("http://github.com/")
            else -> value
        }.removeSuffix(".git")
        require(name.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) &&
            name.split("/").none { it == "." || it == ".." }) {
            "Enter a GitHub repository as owner/name or https://github.com/owner/name"
        }
        return name
    }

    fun command(repository: String): String {
        val repo = parse(repository)
        val name = repo.substringAfter('/')
        val ssh = "git@github.com:$repo.git"
        val https = "https://github.com/$repo"
        val target = "\$HOME/Projects/\$name"
        // All interpolated repository segments pass the strict GitHub identifier validator.
        return """
            target="$target"
            if [ -e "\$target" ] || [ -L "\$target" ]; then
              if [ ! -d "\$target/.git" ] && [ ! -f "\$target/.git" ]; then
                printf 'POCKETDEV_IMPORT_CONFLICT\\n'; exit 20
              fi
              origin=\$(git -C "\$target" remote get-url origin 2>/dev/null) || exit 21
              case "\$origin" in
                '$ssh'|'$https'|'$https.git'|'git://github.com/$repo.git'|'ssh://git@github.com/$repo.git') ;;
                *) printf 'POCKETDEV_IMPORT_CONFLICT\\n'; exit 20 ;;
              esac
            else
              mkdir -p "\$HOME/Projects" || exit 22
              git clone '$ssh' "\$target" || exit 23
            fi
            printf 'POCKETDEV_IMPORT_OK:%s\\n' "\$target"
        """.trimIndent()
    }
}
