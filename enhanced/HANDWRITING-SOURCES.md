# Offline handwriting dependencies

Zinnia recognition sources are from Taku Kudo's upstream, commit
`581faa8f6f15e4a7b21964be3a5ec36265c80e5b`:
https://github.com/taku910/zinnia/tree/581faa8f6f15e4a7b21964be3a5ec36265c80e5b/zinnia
The source files needed for recognition are included under `native/third_party/zinnia`,
unchanged, under the BSD license reproduced in `ZINNIA-LICENSE`.

The Simplified Chinese model is Tegaki 0.3, under LGPL 2.1, reproduced in
`TEGAKI-LICENSE`. The original model and its complete editable XML source are in
the upstream release archive:
https://github.com/tegaki/tegaki/releases/download/v0.3/tegaki-zinnia-simplified-chinese-0.3.zip
The unchanged model is 26,834,816 bytes, SHA-256
`e16153d1ff267cd479aea260d6f71a3edda8b4ba06db2d121513adda65a4449e`.

The Android keyboard bundles the model; recognition needs no network or model
download. Stroke coordinates are kept in memory and discarded on selection,
cancellation, or editor change. The engine recognizes one character at a time;
stroke order and writing style affect candidate ranking. No claim of matching
Gboard's or iFly's handwriting accuracy is made.
