# 第三方来源与许可范围

本仓库的 APK 是非官方改版，含 Google Gboard 18.4.1.985164140-release-arm64-v8a；它不是由本项目整体重新许可的开源软件。仓库公开，原 Gboard 的反编译代码与原 APK 没有加入 Git 历史。

| 部件 | 来源与记录 |
| --- | --- |
| Rime 1.16.1 | [librime](https://github.com/rime/librime)，附 `enhanced/LIBRIME-LICENSE` |
| Fcitx5 Android 运行库 | [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)，原 ARM64 包 `0.1.3-0-g048f581c`，外部输入，保留上游许可 |
| 雾凇词库 | [rime-ice](https://github.com/iDvel/rime-ice)，GPL-3.0，附 `enhanced/rime-data/LICENSE`，字典来源哈希见同目录 `source-hashes.json` |
| 万象语法数据 | [RIME-LMDG](https://github.com/amzxyz/RIME-LMDG)，LTS 的 `wanxiang-lts-zh-hans.gram`，附 `enhanced/RIME-LMDG-LICENSE` |
| Sherpa-ONNX 1.13.8 | [官方 Android 运行库](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8)，附 `enhanced/SHERPA-LICENSE`，含 ONNX Runtime |
| 普通话/英语模型 | [官方双语模型](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html)，`streaming-zipformer-bilingual-zh-en-2023-02-20-mobile`，Apache-2.0，附上游模型说明 |

1.0.0 使用 Vosk Android 0.3.75 和 `vosk-model-small-cn-0.22`，旧版本对应来源记录保留在 Git 历史中。

大型第三方数据与运行库作为外部构建输入，没有提交到 Git。确切本地输入哈希在 `dependency-manifest.json`。仅新增适配代码的上传不授予原 Gboard 或其他第三方组件的再许可权；本项目也未为新增代码另行指定整体开源许可证。
