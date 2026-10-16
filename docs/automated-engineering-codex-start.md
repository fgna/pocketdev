# Standardstart: Automated Engineering über PocketDev + tmux

Der dauerhaft identische Einstiegspunkt wird aus dem **PocketDev-Repository** auf dem Linux-Host ausgeführt:

```bash
cd "$HOME/projects/PocketDev"
git pull --ff-only
bash scripts/start-automated-engineering-codex.sh
```

Das startet einen **äußeren Codex-Operator**, der anhand von `docs/prompts/automated-engineering-e2e.txt` den echten nativen E2E-Launcher ermitteln und ausführen soll. Die fachlichen Arbeitsanweisungen für den inneren Codex-MCP-Engineering-Agenten stammen aus der **autoritativen nativen Engineering-Umgebung**, siehe [Agent- und Instruktionsvertrag](automated-engineering-agent-contract.md). Der tmux-Wrapper ist **nicht** selbst der native Engineering-E2E-Launcher.

Voraussetzungen: Host-Zugang über PocketDev SSH; tmux; ausführbares `~/.local/bin/codex`; Git-Repos unter `~/projects/PocketDev` und `~/projects/automated-engineering`. Mit `AE_REPO`, `CODEX_BIN`, `AE_RUN_ROOT`, `AE_TASK_FILE` lassen sich Pfade anpassen, ohne das Skript zu ändern.

**ACHTUNG: Full Access.** Die Option `--dangerously-bypass-approvals-and-sandbox` schaltet Codex-Sandbox und Einzelgenehmigungen aus. Ausschließlich auf ausdrücklich vertrauenswürdigen/isolierten Hosts starten. Prompt-Anweisungen sind keine technische Zugriffsgrenze. Der unabhängige Engineering-Human-Gate bleibt erforderlich.

Das Skript fetched `origin/main`, legt einen frischen isolierten Git-Worktree und Run-Ordner an, archiviert Prompt + Quell-Commit, startet den Codex-Prozess innerhalb von tmux und speichert JSONL und Exit-Code. Alte Runs bleiben unverändert.

**Status nach einer neuen PocketDev-Verbindung:**

```bash
RUN="$(cat "$HOME/.local/state/pocketdev-codex/ae-latest.txt")"
tmux list-sessions 2>/dev/null || true
cat "$RUN/exit-code.txt" 2>/dev/null || echo "Noch aktiv oder unerwartet unterbrochen"
tail -n 20 "$RUN/run.jsonl"
git -C "$RUN/worktree" status --short
```

Wichtig: Host muss eingeschaltet bleiben; tmux schützt nur gegen SSH-/Telefon-Unterbrechungen. Codex-Exitcode 0 beweist keinen erfolgreichen Engineering-Run. Für Engineering-GO/NO_GO das native Readback/Package prüfen.

Dieser Standard ersetzt manuell kopierte, datierte Beispiel-Shellblöcke. Zum Ändern der Aufgabe die versionierte Prompt-Datei reviewen, nicht den Startbefehl.
