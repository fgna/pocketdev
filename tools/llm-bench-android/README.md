# LLM Bench Android

Small standalone Android app for comparing local OpenAI-compatible LLMs on the same phone under repeatable conditions.

It intentionally lives under `tools/` and is not part of the PocketDev product app or Sprint 1 scope.

## What it measures

For every model and prompt:

- time to first token (TTFT), measured from request start to the first streamed content delta;
- total request duration;
- output token count when the API reports it;
- otherwise a clearly marked tokenizer-independent token estimate;
- generation tokens/second, measured after the first token;
- full model response;
- errors and HTTP failures.

The built-in suite has 20 German prompts covering:

- Weltwissen;
- Deutsch / explanation quality;
- reasoning;
- TaskOS-like prioritisation and reminder questions;
- longer advisory answers.

## Running against a phone-local server

1. Start the local LLM runtime on the Android device with an OpenAI-compatible `/v1/chat/completions` endpoint.
2. Open LLM Bench.
3. Enter the base URL, for example `http://127.0.0.1:8080`.
4. Enter one or more model IDs separated by commas.
5. Start the benchmark.
6. Export the resulting CSV through the Android share sheet.

For a fair comparison, keep runtime, quantisation settings, context size and phone power/thermal conditions unchanged while changing only the model.

## Recommended first comparison for TaskOS

Use the exact model IDs exposed by the local runtime for:

1. the current Gemma model as baseline;
2. Qwen3 1.7B Q4;
3. Qwen3.5 2B Q4.

If the runtime cannot keep several models available at once, benchmark one model at a time and export each run. The prompt IDs are stable, so the CSV files can still be compared directly.

## Build

Open `tools/llm-bench-android` as a project in Android Studio and build the `app` debug variant.

The project is deliberately isolated and does not yet carry its own Gradle wrapper binary. A wrapper can be generated from an installed Gradle with:

```bash
gradle wrapper --gradle-version 8.9
./gradlew test assembleDebug
```

## Interpretation notes

TTFT is the most important responsiveness metric for interactive TaskOS use. Tokens/second matters more for longer answers.

When `token_count_source` is `estimated`, the result is suitable for relative comparison but should not be treated as an exact tokenizer count. The estimate uses approximately four output characters per token. If a runtime sends OpenAI-compatible completion usage metadata, the app uses that count instead.

The benchmark does not currently rate answer quality automatically. The exported response text is retained so a later blind A/B scoring screen can compare correctness, depth, clarity and usefulness without revealing the model first.

## Next useful additions

- blind A/B/C quality scoring;
- battery percentage and thermal status before/after a run;
- process/RAM sampling where Android permits meaningful measurement;
- JSON export alongside CSV;
- cold-run vs warm-run separation;
- direct runtime adapters for llama.cpp / LiteRT if API-level tests show a need.
