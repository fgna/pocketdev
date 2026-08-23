# LLM Bench Android

Standalone Android app for benchmarking local `.litertlm` models directly on the phone with the same LiteRT-LM runtime path used by `fgna/my-taskOS`.

It intentionally lives under `tools/` and is not part of the PocketDev product app or Sprint 1 scope.

## Runtime

- `com.google.ai.edge.litertlm:litertlm-android:0.11.0`
- model import through the Android document picker
- copied into app-private `noBackupFilesDir`
- GPU preferred, CPU fallback on initialization or inference failure
- process-resident engine reused across prompts
- first prompt is therefore a cold run; later prompts are warm runs

No Base URL, HTTP server, API key, or network permission is required.

## What it measures

For each prompt:

- time to first output (TTFT), including model initialization on a cold run;
- total wall-clock duration;
- approximate output token count;
- approximate generation tokens/second;
- full response text;
- backend and cold/warm state in the model label;
- errors.

LiteRT-LM 0.11 does not expose OpenAI-style completion usage metadata through this integration, so token counts are currently a tokenizer-independent estimate of roughly four output characters per token. Timing values are measured directly.

The built-in suite contains 20 German prompts covering world knowledge, German explanation quality, reasoning, TaskOS-like questions, and longer advisory answers.

## Running

1. Open LLM Bench on the Android device.
2. Tap `.litertlm importieren` and choose the model file from local storage.
3. Optionally edit the model label used in the results.
4. Start the benchmark.
5. Export the CSV through the Android share sheet.
6. Import the next `.litertlm` model and repeat under similar battery and thermal conditions.

For the TaskOS decision, start with the current Gemma model as baseline and then run compatible Qwen LiteRT-LM conversions under the same conditions.

## Build

The project uses the same Android/Kotlin generation and LiteRT-LM dependency as the current TaskOS local-PA implementation. If the wrapper was copied from `my-taskOS/android`, build with:

```bash
./gradlew test
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Interpretation

TTFT is the most important responsiveness metric for interactive TaskOS use. Tokens/second becomes more important for longer answers. Compare cold and warm results separately, because loading a multi-GB model can dominate the first request.

The benchmark currently evaluates one imported model per run. This is deliberate: very large models should not be kept resident simultaneously on the phone just to perform an A/B comparison. Stable prompt IDs make separate CSV exports comparable.

## Next useful additions

- blind A/B/C quality scoring across imported result files;
- battery and thermal state before/after a run;
- RAM sampling where Android exposes meaningful values;
- JSON export alongside CSV;
- exact tokenizer counts if exposed by the LiteRT-LM API in a future runtime version.
