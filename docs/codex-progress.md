# Codex JSONL progress — manual acceptance

PocketDev displays additional, best-effort progress only for commands containing `codex exec --json`. Ordinary SSH commands retain their existing command screen and Stop/Input behavior.

1. Open a project tab, run `codex exec --json 'Explain the repository layout'`, and check that the status updates as JSONL events arrive.
2. During a quiet period, the elapsed timer continues. The last-output age reflects the last received bytes, with a warning that silence is not proof of a stall.
3. Check that raw JSONL and stderr remain visible in command output. Copy the displayed remote log paths and inspect the files under `~/.local/state/pocketdev/jobs/` on the server; private log files should have restrictive permissions.
4. Start two tabs of the same project, run a separate Codex job in each, and confirm their actions, timers, Stop buttons and log paths stay independent.
5. Interrupt one job with Stop/Ctrl-C. Verify the other job continues and the interrupted tab shows the actual exit/failure result.
6. Switch away from PocketDev and back. Check that live output and retained log path remain associated with the same tab. After process termination, previously running commands should be reported as interrupted, with log paths still available for inspection.
7. Repeat with a normal command and sudo to confirm generic SSH and automatic prompting are unaffected.

This feature does not support reconnecting to a detached remote job, auto-approving requests or diagnosing a stall with certainty. Commands must be explicitly started with `--json`. There is no guarantee that every Codex version emits all event types.
