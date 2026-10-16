# Codex-Auftragsmodell für Automated Engineering

## Ziel
PocketDev startet einen **äußeren Codex-Operator**. Dieser betreibt und verbessert eine vorhandene, autoritative Automated-Engineering-Umgebung. Ein **innerer nativer Codex-MCP-Engineering-Agent** führt deren aktuelle Engineering-Aufträge aus. Beide Ebenen dürfen nicht verwechselt werden.

```text
PocketDev SSH → tmux → Codex-Operator (versionierter Prompt)
                               |
                               v
                      nativer R2 Launcher / Host
                               |
            kontrollierter Projektzustand + Methode + Gates
                               |
                               v
                Codex-MCP-Engineering-Agent
                               |
                native Transaktionen / Evidenz / Review
```

## Woher stammen die Anweisungen?
Die fachlichen Anforderungen, nächsten Aktivitäten, Method-Gates und Freigabegrenzen stammen aus dem **jeweils aktuellen nativen Engineering-Projektzustand** und dessen Controlled Inputs. `AGENTS.md` beschreibt technische Repository-Konventionen. GitHub [Tracker #51](https://github.com/fgna/project-tracker/issues/51) und [Sprint #56](https://github.com/fgna/project-tracker/issues/56) steuern die Produkt- und Fehlerbehebungsarbeit. Die [versionierte Startinstruktion](prompts/automated-engineering-e2e.txt) gibt dem äußeren Operator lediglich die Ausführungs- und Nachweispflichten.

Wenn das konkrete Projekt, eine zulässige nächste Aufgabe oder die autorisierte Run-Konfiguration nicht ermittelbar ist, darf Codex sie nicht erfinden. Es berichtet den Blocker.

## Änderungen und Wiederverwendung
Der Aufruf bleibt immer `bash scripts/start-automated-engineering-codex.sh` (im PocketDev-Repo). Der Prompt liegt separat unter `docs/prompts/automated-engineering-e2e.txt`, sodass Updates geprüft werden können, ohne tmux-Skript/Command zu verändern. Die letzte an einen Run übergebene Prompt-Version und das Quellcommit werden im Run-Verzeichnis gesichert.

## Kontrollgrenzen
Der äußere Codex läuft auf ausdrücklichen Wunsch mit Full Access auf einem vertrauenswürdigen Host. Dadurch erhält der innere Engineering-Agent **keine** zusätzliche fachliche Genehmigungs- oder Änderungsautorität. Human Gate, Revisionsbindung, Method-Gates und NO_GO bleiben wirksam. Ein echter HOLD ist ein korrektes Ergebnis, aber keine Produktfreigabe.

## Prüfpunkte
Vor einem nativen Run: gültiger gepinnter Launcher, frischer isolierter State, qualifizierte Runtime, sicherer Stop/Archivierung. Danach: genaue native Aufgabenquelle, Events/Fehlercodes, akzeptierte Objekte/Baselines, Method-Readback, Terminalisierung, SHA-geprüftes Paket und – wenn möglich – Website-Eintrag. Änderungen an historischen Daten sind verboten.
