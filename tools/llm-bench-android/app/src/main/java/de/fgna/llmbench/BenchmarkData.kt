package de.fgna.llmbench

internal data class BenchmarkPrompt(
    val id: String,
    val category: String,
    val text: String,
)

internal enum class TokenCountSource { API, ESTIMATED }

internal data class BenchmarkResult(
    val model: String,
    val prompt: BenchmarkPrompt,
    val ttftMs: Long,
    val totalMs: Long,
    val outputTokens: Int,
    val tokensPerSecond: Double,
    val tokenCountSource: TokenCountSource,
    val output: String,
    val error: String?,
)

internal object BenchmarkSuite {
    val defaultPrompts = listOf(
        BenchmarkPrompt("wk01", "Weltwissen", "Wer war Otto von Bismarck und warum war er historisch wichtig? Antworte in etwa fünf Absätzen."),
        BenchmarkPrompt("wk02", "Weltwissen", "Warum scheiterte die Weimarer Republik? Nenne politische, wirtschaftliche und gesellschaftliche Faktoren."),
        BenchmarkPrompt("wk03", "Weltwissen", "Erkläre die wichtigsten Ursachen und Folgen des Dreißigjährigen Kriegs."),
        BenchmarkPrompt("wk04", "Weltwissen", "Was ist der Unterschied zwischen TCP und UDP? Gib konkrete Anwendungsbeispiele."),
        BenchmarkPrompt("de01", "Deutsch", "Erkläre einem Jugendlichen verständlich, warum Schlaf für Lernen und Gedächtnis wichtig ist."),
        BenchmarkPrompt("de02", "Deutsch", "Fasse den folgenden Gedanken präzise in drei unterschiedlichen Formulierungen zusammen: Gute Planung soll Entscheidungen erleichtern, nicht zusätzliche Arbeit erzeugen."),
        BenchmarkPrompt("de03", "Deutsch", "Schreibe eine differenzierte kurze Erklärung, was mit Opportunitätskosten gemeint ist."),
        BenchmarkPrompt("rs01", "Reasoning", "Ein Zug fährt 120 km mit 80 km/h und danach 180 km mit 120 km/h. Wie hoch ist die Durchschnittsgeschwindigkeit über die gesamte Strecke? Erkläre kurz."),
        BenchmarkPrompt("rs02", "Reasoning", "Anna ist älter als Ben. Ben ist älter als Carla. David ist jünger als Carla. Ordne alle vier von alt nach jung."),
        BenchmarkPrompt("rs03", "Reasoning", "Du hast 45 Minuten, drei Aufgaben dauern 10, 20 und 30 Minuten. Die 20-Minuten-Aufgabe ist heute fällig, die 30-Minuten-Aufgabe morgen. Welche Reihenfolge ist sinnvoll und warum?"),
        BenchmarkPrompt("rs04", "Reasoning", "Ein Passwort besteht aus vier Ziffern. Die erste ist doppelt so groß wie die zweite, die dritte ist 3, die vierte ist die Summe der ersten beiden. Finde eine gültige Kombination, bei der alle Stellen Ziffern bleiben."),
        BenchmarkPrompt("to01", "TaskOS", "Ich habe heute wenig Energie und 30 Minuten Zeit. Welche Art von Aufgabe sollte ich jetzt bevorzugen und warum?"),
        BenchmarkPrompt("to02", "TaskOS", "Ich muss morgen um 14 Uhr zum Zahnarzt und brauche 25 Minuten Anfahrt. Welche sinnvollen Erinnerungen würdest du setzen?"),
        BenchmarkPrompt("to03", "TaskOS", "Aus diesen Aufgaben: Steuerunterlagen 60 Min hoch, Müll rausbringen 5 Min niedrig, Rechnung heute zahlen 10 Min niedrig. Ich habe 20 Minuten. Was zuerst?"),
        BenchmarkPrompt("to04", "TaskOS", "Formuliere aus 'Geburtstagsgeschenk für Paul besorgen, spätestens nächsten Freitag' einen klaren nächsten Schritt mit sinnvoller Vorbereitung."),
        BenchmarkPrompt("br01", "Beratung", "Ich möchte einen gebrauchten Mac als lokalen AI-Server für mehrere Jahre nutzen. Welche Kriterien sind wichtiger als reine Benchmarkwerte?"),
        BenchmarkPrompt("br02", "Beratung", "Vergleiche für ein privates Softwareprojekt SQLite und PostgreSQL. Wann ist SQLite die bessere Wahl?"),
        BenchmarkPrompt("br03", "Beratung", "Wie würdest du entscheiden, ob eine kleine App eine eigene Codebasis oder nur ein Modul in einem bestehenden Projekt sein sollte?"),
        BenchmarkPrompt("br04", "Beratung", "Nenne Risiken eines vollständig lokalen persönlichen Assistenten auf dem Smartphone und sinnvolle Gegenmaßnahmen."),
        BenchmarkPrompt("br05", "Beratung", "Welche Eigenschaften machen eine Antwort eines persönlichen Assistenten wirklich nützlich statt nur korrekt?"),
    )
}

internal object BenchmarkCsv {
    fun export(results: List<BenchmarkResult>): String = buildString {
        appendLine("model,prompt_id,category,prompt,ttft_ms,total_ms,output_tokens,tokens_per_second,token_count_source,error,output")
        results.forEach { r ->
            appendLine(
                listOf(
                    r.model,
                    r.prompt.id,
                    r.prompt.category,
                    r.prompt.text,
                    r.ttftMs,
                    r.totalMs,
                    r.outputTokens,
                    "%.2f".format(java.util.Locale.US, r.tokensPerSecond),
                    r.tokenCountSource.name.lowercase(),
                    r.error.orEmpty(),
                    r.output,
                ).joinToString(",") { csvCell(it.toString()) },
            )
        }
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\n' || it == '\r' || it == '\"' }) "\"$escaped\"" else escaped
    }
}
