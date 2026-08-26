# Git SSH key agent

PocketDev uses a persistent user `ssh-agent` socket on the development server at:

`~/.cache/pocketdev/ssh-agent.sock`

Normal PocketDev commands export that socket automatically. If the agent has no identity loaded, the Workspace shows the Git key as locked and offers an Unlock action.

The unlock dialog sends the passphrase over the already authenticated SSH connection as command stdin. The passphrase is not persisted by PocketDev and is not embedded in the remote command line. On the server, `ssh-add` receives it through a temporary `SSH_ASKPASS` helper; the helper is removed immediately after the attempt.

The first existing key is selected from `~/.ssh/id_ed25519`, `~/.ssh/id_rsa`, and `~/.ssh/id_ecdsa`. A future enhancement may make the key path configurable if real user testing needs it.
