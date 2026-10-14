# Offline bilingual proofreading

This component uses two task-specific models, not a chat LLM. It has no network or microphone permission. Only the current recognition result enters the same-signature service; user text is never saved. The keyboard still works without this optional component.

## Pinned inputs

- English: [visheratin/t5-efficient-tiny-grammar-correction](https://huggingface.co/visheratin/t5-efficient-tiny-grammar-correction), revision `a98f126664317cf2e68d33234c7072fdd6b289f3`. The model card declares MIT. About 15.5 million parameters. The original card is included in the APK. Source weights are loaded locally with the Transformers safe weights loader; no remote model code is executed.
- Chinese: [shibing624/macbert4csc-base-chinese](https://huggingface.co/shibing624/macbert4csc-base-chinese), revision `615e6e09ef9a69ec487bc7c641ec3a311e2c11b9`. Apache-2.0, model card and license included. Approximately 102 million parameters; single-character correction with bidirectional context.
- Runtime: Microsoft `com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0`, MIT, [source](https://github.com/microsoft/onnxruntime/tree/v1.29.0). The AAR manifest, Internet permissions and telemetry initializer are deliberately not merged; runtime telemetry is disabled. The separate ASR APK keeps its original Sherpa runtime.
- Accelerator: `com.qualcomm.qti:qnn-runtime:2.42.0` from Maven Central. Qualcomm's LICENSE.pdf and NOTICE.txt are included unchanged. Redistributed object code is incorporated into this application; not published as a standalone SDK. Only the v79 HTP stack for Snapdragon 8 Elite is packaged. Other devices can fall back to CPU. See [QNN execution provider documentation](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html).
- Homophone filter: single-character readings extracted from the already bundled Rime Ice `cn_dicts/8105.dict.yaml`. GPL-3.0; the complete source dictionary, its attribution to Wiktionary and the original license are included in the APK. This is a filter, not a table of hand-coded corrections.

Exact input URLs, sizes and SHA-256 values are in `proofread-inputs.json`; exported model hashes are in the root dependency manifest. The model data and runtime binaries are external build inputs, not Git objects.

## Optimization and bounds

English encoder/decoder: int8 weight quantization, shared small vocabulary, greedy bounded decoding and two CPU threads. Chinese: static 128-token overlapping windows, uint16 activations / uint8 weights, fused layer normalization, QNN NPU graph caching and balanced HTP mode. The exported BERT attention mask uses -100 instead of float-min before calibration; otherwise a padding outlier destroys quantization precision. The mask still excludes padded keys from softmax. Compare quantized output to the original before publishing.

Models load lazily. The service has one inference worker, cancels superseded requests and releases sessions after unbind or 30 seconds idle. The IME waits at most 2.5 seconds and falls back to the original; service decoding has a 12-second deadline. First-run preparation creates model/compiler caches without processing user text. Text longer than 2048 characters is left unchanged by the service; an English span above 128 input tokens is also preserved rather than truncated.

Only high-confidence, same-reading Han substitutions are accepted. Chinese numerals, negations, Latin spans and common name/title patterns are protected. English changes have confidence and edit-distance gates; numbers, codes, negations and known acronyms are protected. These heuristics cannot prove meaning equivalence. Ambiguous semantic errors may remain, including some `phone` / `want` confusions; no special-case substitution is used. The keyboard offers original-text insertion while waiting and a brief exact-suffix-guarded undo after a modification.

GPU/NPU initialization errors fall back to CPU and never discard recognized text. The quantized Chinese graph could not run fully on Adreno in testing, so the unused GPU library is omitted from the release. Accelerator speed measurements are latency measurements, not measured battery savings. General Qwen models were evaluated but excluded because of size and unreliable rewriting.

The keyboard supplies a final sentence terminator independently of the optional model. It recognizes common question endings, preserves existing punctuation and leaves bare codes/URLs unchanged. Since 1.3.1, the voice helper first runs a separate offline punctuation model before optional proofreading; see [punctuation sources](PUNCTUATION-SOURCES.md). The keyboard sentence terminator remains a fallback.
