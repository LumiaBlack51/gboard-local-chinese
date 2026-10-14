# Offline voice punctuation

The voice helper bundles the int8 CT-Transformer Chinese/English model distributed
by [Sherpa-ONNX](https://k2-fsa.github.io/sherpa/onnx/punctuation/pretrained_models.html).
It is converted from Alibaba's
[punc_ct-transformer_zh-cn-common-vocab272727-pytorch](https://modelscope.cn/models/iic/punc_ct-transformer_zh-cn-common-vocab272727-pytorch/summary).
The upstream model card declares Apache License 2.0; the license is bundled here.
Sherpa-ONNX's own license is included separately.

- Model: `sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8`
- File: `model.int8.onnx`, 75,519,198 bytes (72.02 MiB)
- Model SHA-256: `65a3fb9f5ad7bfb96bf69e0dc4481df97f6ee60513c1d94ce981ba6effd524b1`
- Archive SHA-256: `c0d5aa5f8eeb686032345e180bedf39319dc2e0556781c6264bcadba8328a6e1`
- Labels: no punctuation, comma, period, question mark, enumeration comma.

`download_punctuation.py` pins and verifies both hashes. No model is downloaded by
the Android app. The CPU model runs after ASR, before optional proofreading, with
two inference threads. Speech warmup also prepares the punctuation model without
recording audio. The voice APK has no Internet permission.

`VoicePunctuation` accepts only inserted punctuation. Original characters, case,
numbers, identifiers and word spacing are retained. Predictions that drop words
or change text are discarded. Existing punctuation is retained; URLs, email
addresses and numeric expressions with decimal/time separators bypass inference.
If punctuation initialization fails, recognition still returns its original text;
the keyboard retains its sentence-ending fallback. Exclamation marks, colons,
semicolons and quotation marks are not predicted by this model.

Fixed synthetic samples verify functionality, not overall punctuation accuracy.
