package de.fgna.pocketdev.ssh

object GitSshAgent {
    private const val ENSURE_AGENT = "__POCKETDEV_ENSURE_AGENT__"

    private fun shell(template: String): String = template.replace('@', '$')

    private val ensureAgent = shell(
        """
        mkdir -p "@HOME/.cache/pocketdev"
        POCKETDEV_AGENT_SOCK="@HOME/.cache/pocketdev/ssh-agent.sock"
        SSH_AUTH_SOCK="@POCKETDEV_AGENT_SOCK" ssh-add -l >/dev/null 2>&1
        POCKETDEV_AGENT_STATUS=@?
        if [ "@POCKETDEV_AGENT_STATUS" -eq 2 ]; then
          rm -f "@POCKETDEV_AGENT_SOCK"
          ssh-agent -a "@POCKETDEV_AGENT_SOCK" >/dev/null
        fi
        export SSH_AUTH_SOCK="@POCKETDEV_AGENT_SOCK"
        """.trimIndent(),
    )

    fun wrap(command: String): String = "$ensureAgent\n$command"

    fun statusCommand(): String = shell(
        """
        __POCKETDEV_ENSURE_AGENT__
        if ssh-add -l >/dev/null 2>&1; then
          printf 'POCKETDEV_GIT_KEY_READY\n'
        else
          POCKETDEV_KEY_STATUS=@?
          if [ "@POCKETDEV_KEY_STATUS" -eq 1 ]; then
            printf 'POCKETDEV_GIT_KEY_LOCKED\n'
          else
            printf 'POCKETDEV_GIT_KEY_ERROR\n'
          fi
        fi
        """.trimIndent(),
    ).replace(ENSURE_AGENT, ensureAgent)

    fun unlockCommand(): String = shell(
        """
        __POCKETDEV_ENSURE_AGENT__
        POCKETDEV_KEY=""
        for candidate in "@HOME/.ssh/id_ed25519" "@HOME/.ssh/id_rsa" "@HOME/.ssh/id_ecdsa"; do
          if [ -f "@candidate" ]; then
            POCKETDEV_KEY="@candidate"
            break
          fi
        done
        if [ -z "@POCKETDEV_KEY" ]; then
          printf 'PocketDev: no SSH private key found in ~/.ssh\n' >&2
          exit 20
        fi

        IFS= read -r POCKETDEV_KEY_PASSPHRASE
        POCKETDEV_ASKPASS="@(mktemp)"
        trap 'rm -f "@POCKETDEV_ASKPASS"; unset POCKETDEV_KEY_PASSPHRASE' EXIT HUP INT TERM
        cat >"@POCKETDEV_ASKPASS" <<'POCKETDEV_ASKPASS_EOF'
#!/bin/sh
printf '%s\n' "@POCKETDEV_GIT_KEY_PASSPHRASE"
POCKETDEV_ASKPASS_EOF
        chmod 700 "@POCKETDEV_ASKPASS"
        DISPLAY=:0 SSH_ASKPASS_REQUIRE=force SSH_ASKPASS="@POCKETDEV_ASKPASS" \
          POCKETDEV_GIT_KEY_PASSPHRASE="@POCKETDEV_KEY_PASSPHRASE" \
          ssh-add "@POCKETDEV_KEY" </dev/null
        POCKETDEV_ADD_STATUS=@?
        unset POCKETDEV_KEY_PASSPHRASE
        if [ "@POCKETDEV_ADD_STATUS" -eq 0 ]; then
          printf 'POCKETDEV_GIT_KEY_READY\n'
        fi
        exit "@POCKETDEV_ADD_STATUS"
        """.trimIndent(),
    ).replace(ENSURE_AGENT, ensureAgent)
}
