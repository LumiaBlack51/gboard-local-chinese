# 第三方来源与许可范围

本仓库的 APK 是个人设备上的非官方改版，含 Google Gboard 18.4.1.985164140-release-arm64-v8a；它不是由本项目整体重新许可的开源软件。仓库保持私有，原 Gboard 的反编译代码与原 APK 没有加入 Git 历史。

| 部件 | 来源与记录 |
| --- | --- |
| Rime 1.16.1 | [librime](https://github.com/rime/librime)，附 `enhanced/LIBRIME-LICENSE` |
| Fcitx5 Android 运行库 | [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)，原 ARM64 包 `0.1.3-0-g048f581c`，外部输入，保留上游许可 |
| 雾凇词库 | [rime-ice](https://github.com/iDvel/rime-ice)，GPL-3.0，附 `enhanced/rime-data/LICENSE`，字典来源哈希见同目录 `source-hashes.json` |
| 万象语法数据 | [RIME-LMDG](https://github.com/amzxyz/RIME-LMDG)，LTS 的 `wanxiang-lts-zh-hans.gram`，附 `enhanced/RIME-LMDG-LICENSE` |
| Vosk Android 0.3.75 | [vosk-api](https://github.com/alphacep/vosk-api)，附 `enhanced/VOSK-LICENSE` |
| 普通话模型 | [Vosk models](https://alphacephei.com/vosk/models)，`vosk-model-small-cn-0.22` |

大型第三方数据与运行库作为外部构建输入，没有提交到 Git。确切本地输入哈希在 `dependency-manifest.json`。仅新增适配代码的上传不授予原 Gboard 或其他第三方组件的再许可权；本项目也未为新增代码另行指定整体开源许可证。
