# Running Codex from PocketDev (remote, screen-off safe)

PocketDev is an SSH control surface; Codex runs **on the Linux development host**, not on the Android phone. A long-running SSH command may lose its streaming display when Android suspends the app or the connection changes. PocketDev's foreground protection improves continuity but is not a guarantee against app termination, SSH loss, or host sleep.

Use **tmux** on the host for long Codex sessions. The tmux server owns the terminal independently of PocketDev, so disconnecting the phone does not normally terminate the Codex process. This is a host-side workflow, **not** a built-in PocketDev Codex integration.

## Prerequisites

- A working PocketDev SSH connection to a trusted Linux host.
- Codex CLI installed and authenticated **on that host**; verify `command -v codex` and `codex --version`. Authentication is interactive when initially configured; never paste secrets into scripts or logs.
- `tmux` installed (`sudo apt-get update && sudo apt-get install -y tmux` on Ubuntu).
- A Git worktree for the project, an appropriate branch, and permission to write a local run directory.
- The host must remain powered on and awake. A host reboot, shutdown, or exhausted disk is not solved by tmux.

## Proven native engineering-run route (Automated Engineering, 2026-10-12)

**Use this route for the R2 engineering agent, not a bare `codex exec`.** PocketDev only opens the SSH session. The host-side project launcher manages a genuine Codex MCP agent, runtime qualification, native transactions, pinned original-R2 checkpoint, isolated state and independent SQLite readback. This workflow successfully executed both separate 3S and 4S development runs on the owner's Linux host; **engineering review was still required**.

The observed host had `$HOME/.local/bin/codex` (Codex CLI 0.162.1), `/usr/bin/tmux` 3.4, and no `codex` on the default interactive `PATH`. Use the absolute Codex path, or set `PATH` explicitly. Do not reinstall Codex merely because `command -v codex` is empty. Verify `test -x "$HOME/.local/bin/codex"` and `"$HOME/.local/bin/codex" --version`.

The following is the **observed 4S pattern**. It depends on the named source branch, qualification scripts, sealed checkpoint and checksum being present and valid **on this specific host**. The hash is an identity assertion for that historical checkpoint, not a reusable hash for a newly generated checkpoint. Do not guess or substitute another hash. This starts a *new* independent run; it does **not** continue or alter the original run.

```bash
set -euo pipefail

REPO="$HOME/projects/automated-engineering"
RUN="$HOME/.local/state/drone-4s-$(date -u +%Y%m%dT%H%M%SZ)"
SESSION="drone4s-$(date +%s)"
CODEX="$HOME/.local/bin/codex"
CHECKPOINT="$HOME/.local/state/r2-sealed-checkpoint-20261011.json"
SEALED_RUN="$HOME/.local/state/rehearsal7-context-20261011T154053Z/platform"
EXPECTED_SHA256="9d06f70691ca96b3825ccde7b6391b62356491c42b30adfc6c2aa91489582992"

test -x "$CODEX"
test -f "$CHECKPOINT"
test -d "$SEALED_RUN"
printf '%s  %s\n' "$EXPECTED_SHA256" "$CHECKPOINT" | sha256sum -c -
git -C "$REPO" fetch origin
mkdir -p "$RUN"
git -C "$REPO" worktree add --detach "$RUN/worktree" \
  origin/feat/314-native-review-request

# Keep the 4S-specific agent assignment in a separate, auditable run prompt.
# The original executed assignment is in the run's modified
# docs/planning/rehearsal7-codex-agent-run.md. Do not silently reuse it
# for a different goal or overwrite any prior run.
cat >> "$RUN/worktree/docs/planning/rehearsal7-codex-agent-run.md" <<'PROMPT'

ADDITIONAL ENGINEERING ASSIGNMENT — 4S INDEPENDENT DEVELOPMENT
Develop the 4S quadcopter using the original sealed R2 checkpoint.
Preserve all 15 unchanged requirements. Do not import the completed 3S run.
Research exact motor, propeller, ESC and battery alternatives and traceable
manufacturer/supplier data. Compute bounded mass, thrust, energy, endurance
and costs where inputs permit. Submit changes only through native MCP
proposals, validation and policy. Freeze accepted baselines.
Keep EVIDENCE_PENDING / DEFER and selection_authorized=false if evidence
does not support a formal decision. Do not claim flightworthiness or release.
PROMPT

cat > "$RUN/start.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
RUN="$(cd "$(dirname "$0")" && pwd)"
cd "$RUN/worktree"
export PATH="$HOME/.local/bin:/usr/local/bin:/usr/bin:/bin:$PATH"
export PYTHONDONTWRITEBYTECODE=1
trap 'echo "$?" > "$RUN/exit-code.txt"' EXIT
python -u scripts/qualify_platform_r2_review.py "$RUN/qualification.json"
python -u scripts/run_rehearsal7_codex.py \
  --run "$RUN/platform" \
  --qualification "$RUN/qualification.json" \
  --sealed-run "$HOME/.local/state/rehearsal7-context-20261011T154053Z/platform" \
  --checkpoint "$HOME/.local/state/r2-sealed-checkpoint-20261011.json" \
  --checkpoint-sha256 9d06f70691ca96b3825ccde7b6391b62356491c42b30adfc6c2aa91489582992 \
  --codex "$HOME/.local/bin/codex" \
  --host-seconds 3300 --max-seconds 3600
SH

chmod 700 "$RUN/start.sh"
tmux new-session -d -s "$SESSION" \
  "bash '$RUN/start.sh' > '$RUN/run.log' 2>&1"
printf '%s\n' "$RUN" > "$HOME/.local/state/drone-4s-latest.txt"
echo "Started: $SESSION; run: $RUN"
```

To inspect the result **without touching the engineering database**:

```bash
RUN="$(cat "$HOME/.local/state/drone-4s-latest.txt")"
echo "=== TMUX ==="
tmux list-sessions 2>/dev/null || true
echo "=== EXIT ==="
cat "$RUN/exit-code.txt" 2>/dev/null || echo "Still running or interrupted"
echo "=== PLATFORM RESULT ==="
python3 -m json.tool "$RUN/platform/rehearsal7-result.json" 2>/dev/null || true
echo "=== LOG ==="
tail -n 45 "$RUN/run.log" 2>/dev/null || true
echo "=== SOURCE STATUS ==="
git -C "$RUN/worktree" status --short
```

**Observed owner-host outcomes:** 3S and 4S runs exited with code 0, platform qualification PASS, independent readback PASS, 24 accepted objects and 13 baselines each. Both ended at `AGENT_EXITED_REVIEW_REQUIRED`: DEV-ARCH SATISFIED, DEV-TRADE ELIGIBLE with `formal-trades-complete` unmet, DEV-SEL BLOCKED. These are not successful architecture selection or physical validation. The 3S and 4S runs must remain independent; a later formal trade must explicitly reconcile their separately frozen baselines and object identities.

### Why direct Codex invocation failed on this host

A separate `codex exec --sandbox workspace-write --json -` run returned process exit 0 but **performed no edits or tests**. The tool sandbox reported `bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted`. On that host, `unshare --user --map-root-user true` also failed to write `/proc/self/uid_map`; `/proc/sys/kernel/apparmor_restrict_unprivileged_userns` was `1`. This supports an AppArmor / user-namespace restriction, not a defect in the engineering method.

The verified engineering route above uses the **project's native MCP runner**. It is not evidence that all direct CLI sandboxes are functional. Do not globally disable AppArmor, casually choose `danger-full-access`, or treat `exit-code=0` as completed engineering. For ordinary non-platform Codex tasks, diagnose the sandbox separately and retain least privilege.

---

## Prepare a durable, reviewable task

On the host, switch to the **existing** project branch deliberately. Check `git status --short` first. Do not reset or clean away ongoing work. Put the Codex instruction in a file, rather than embedding a long prompt in a mobile text field.

The following example assumes `~/Projects/my-project` exists. Replace it with the actual host path; choose a new run ID for each execution. This example uses the safer CLI sandbox default (`workspace-write`) and does not grant unlimited filesystem access.

```bash
cd ~/Projects/my-project || exit 1
git status --short --branch
command -v codex || exit 1
command -v tmux || exit 1

RUN_ID="codex-$(date -u +%Y%m%dT%H%M%SZ)"
RUN_DIR="$HOME/.local/state/pocketdev-codex/$RUN_ID"
mkdir -p "$RUN_DIR" || exit 1

cat > "$RUN_DIR/prompt.txt" <<'PROMPT'
Read AGENTS.md and the current project documentation.
Continue from existing work; do not restart or overwrite previous revisions.
Complete the requested engineering/development task, run relevant checks,
and summarize files changed, checks run, limitations and next actions.
Do not claim tests or approvals that were not actually performed.
PROMPT

printf '%s\n' "$PWD" > "$RUN_DIR/worktree.txt"
printf '%s\n' "$RUN_ID" > "$HOME/.local/state/pocketdev-codex/latest-run-id.txt"
printf '%s\n' "$RUN_DIR"
```

For projects with historical evidence, preserve a dated copy or Git checkpoint of unfinished new work **before** running a continuation. Keep sensitive data and large generated artifacts out of Git; do not use `git add -A` indiscriminately.

## Launch under tmux

Paste this as one block **after** the preparation above, in the same PocketDev command session. If the earlier session has disconnected, first set `RUN_DIR` to the saved printed directory.

```bash
cd ~/Projects/my-project || exit 1
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_DIR="$HOME/.local/state/pocketdev-codex/$(cat "$HOME/.local/state/pocketdev-codex/latest-run-id.txt")"
SESSION="codex-$RUN_ID"
test -s "$RUN_DIR/prompt.txt" || exit 1
test ! -e "$RUN_DIR/exit-code.txt" || { echo "Run already ended"; exit 1; }
tmux has-session -t "$SESSION" 2>/dev/null && { echo "Session already exists"; exit 1; }

cat > "$RUN_DIR/start.sh" <<'SH'
#!/usr/bin/env bash
set -o pipefail
cd "$(cat "$(dirname "$0")/worktree.txt")" || exit 1
export PATH="$HOME/.local/bin:$PATH"
timeout -k 15s 2700s codex exec --sandbox workspace-write --json - \
  < "$(dirname "$0")/prompt.txt" \
  > "$(dirname "$0")/run.jsonl" 2>&1
rc=$?
printf '%s\n' "$rc" > "$(dirname "$0")/exit-code.txt"
exit "$rc"
SH
chmod 700 "$RUN_DIR/start.sh"
tmux new-session -d -s "$SESSION" "$RUN_DIR/start.sh"
printf '%s\n' "$SESSION" > "$RUN_DIR/session.txt"
echo "Started $SESSION; output: $RUN_DIR/run.jsonl"
```

Disconnect PocketDev or lock the phone after `tmux list-sessions` confirms the session. The session may disappear automatically when the command ends; that alone is not an error.

**Security:** `--sandbox danger-full-access` gives Codex very broad local access. Avoid it unless the machine is an isolated, trusted test environment and that access is specifically necessary. A prompt saying “do not modify” is not an access-control boundary. Verify the current Codex CLI's sandbox options with `codex exec --help`; policy/approval requirements remain applicable.

## Inspect progress after reconnecting

```bash
RUN_DIR="$HOME/.local/state/pocketdev-codex/$(cat "$HOME/.local/state/pocketdev-codex/latest-run-id.txt")"
echo "=== session ==="
tmux list-sessions 2>/dev/null || true
echo "=== exit code ==="
cat "$RUN_DIR/exit-code.txt" 2>/dev/null || echo "No exit code yet"
echo "=== recent events ==="
tail -c 3000 "$RUN_DIR/run.jsonl" 2>/dev/null || true
echo
echo "=== Git ==="
cd "$(cat "$RUN_DIR/worktree.txt")" || exit 1
git status --short
git log -3 --oneline
```

With `--json`, `run.jsonl` holds machine-readable event lines plus any diagnostic output. Search for `"type":"turn.completed"` and the final agent message; inspect the process exit code and actual repository artifacts. Code `0` means the CLI process exited successfully, **not** that the engineering outcome is verified or committed. Code `124` typically means `timeout` stopped it. Missing exit-code files can indicate a still-running process or abnormal termination; check `tmux list-sessions` and the latest log modification time.

For a live terminal, run `tmux attach -t SESSION_NAME`. Detach with **Ctrl+B**, then **D**. Do not press Ctrl+C unless you intend to interrupt the running command. Attaching is optional when output goes to the log file.

## Interrupted runs and continuity

1. Inspect `git status --short` and all new output directories; source files may be uncommitted even if the CLI stopped.
2. Back up new/untracked outputs outside the worktree (for example using `tar -czf`); capture the SHA-256 and preserve the old revision.
3. Use a new run directory and task file that explicitly names the existing work, incomplete calculations and next steps. **Do not** rerun an old prompt blindly or overwrite its log.
4. Validate generated artifacts and tests, then commit only reviewed paths. Push to a draft PR when suitable. Check GitHub to distinguish **local progress** from published work.
5. If a deadline was reached, continue from files/commits, not by altering old evidence or pretending the previous run finished.

Avoid parallel Codex writers in the same worktree; give independent work its own worktree/branch. Keep API credentials, SSH keys, `~/.codex` auth files and other secrets out of reports and run archives.

## Practical limitations

- tmux survives SSH disconnects, **not** host power loss or reboot by itself.
- PocketDev's command streaming and its Stop button are distinct from a detached tmux job; do not assume stopping the mobile command stops the detached host job.
- `timeout` limits the individual CLI invocation; a new continuation does not turn an incomplete result into a completed one.
- Host sleep settings, disk space, Git credentials and external API availability can still interrupt work.
- Documenting a successful Codex process is not proof of correct code, correct engineering, passed product gates, or human approval.

For an example of the engineering workflow, see the exploratory R2 work in [fgna/automated-engineering PR #310](https://github.com/fgna/automated-engineering/pull/310); its formal validation boundaries are separate from these host execution instructions.
