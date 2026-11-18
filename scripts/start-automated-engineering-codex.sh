#!/usr/bin/env bash
# Stable PocketDev entrypoint for an Automated Engineering Codex sprint.
set -euo pipefail
SOURCE="${AE_REPO:-$HOME/projects/automated-engineering}"
CODEX="${CODEX_BIN:-$HOME/.local/bin/codex}"
BASE="${AE_RUN_ROOT:-$HOME/.local/state/pocketdev-codex}"
TASK="${AE_TASK_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docs/prompts/automated-engineering-e2e.txt}"
git -C "$SOURCE" rev-parse --git-dir >/dev/null 2>&1 || { echo "Missing repo: $SOURCE" >&2; exit 1; }
test -x "$CODEX" || { echo "Missing Codex: $CODEX" >&2; exit 1; }
test -s "$TASK" || { echo "Missing task: $TASK" >&2; exit 1; }
command -v tmux >/dev/null
git -C "$SOURCE" fetch origin main
ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
SESSION="ae-codex-$ID"
RUN="$BASE/$SESSION"
mkdir -p "$RUN"
git -C "$SOURCE" worktree add -b "work/ae-codex-$ID" "$RUN/worktree" origin/main
cp "$TASK" "$RUN/prompt.txt"
git -C "$RUN/worktree" rev-parse HEAD > "$RUN/source-commit.txt"
printf '%s\n' "$CODEX" > "$RUN/codex-path.txt"
cat > "$RUN/start.sh" <<'SH'
#!/usr/bin/env bash
set -uo pipefail
RUN="$(cd "$(dirname "$0")" && pwd)"
cd "$RUN/worktree" || exit 1
export PATH="$HOME/.local/bin:$PATH"
CODEX="$(cat "$RUN/codex-path.txt")"
trap 'rc=$?; printf "%s\n" "$rc" > "$RUN/exit-code.txt"' EXIT
# FULL ACCESS: no Codex sandbox or per-command approvals. Trusted host only.
"$CODEX" exec --dangerously-bypass-approvals-and-sandbox --json - < "$RUN/prompt.txt" > "$RUN/run.jsonl" 2>&1
SH
chmod 700 "$RUN/start.sh"
tmux new-session -d -s "$SESSION" bash "$RUN/start.sh"
printf '%s\n' "$RUN" > "$BASE/ae-latest.txt"
echo "STARTED $SESSION"
echo "RUN $RUN"
echo "COMMIT $(cat "$RUN/source-commit.txt")"
