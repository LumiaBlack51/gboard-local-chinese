# 构建说明

宿主脚本针对 Windows、Python 3.11+、JDK 17、Android SDK platform 36 / Build Tools 36.1.0、NDK 28.2.13676358。SDK、Java、apktool 路径由 `build_config.py` 的环境变量读取，不含原工作站路径。主键盘适配只支持这里的 Gboard 18.4.1；升级底包不能直接套用反射或 smali 锚点。

## 外部输入

仓库没有原 Gboard APK、反编译代码、第三方 JAR、运行库或大型模型。准备以下本地文件，并核对 `dependency-manifest.json` 与字典 `source-hashes.json`：

1. 从自己设备取得 Gboard 18.4.1.985164140-release-arm64-v8a 的四个 APK，放在 `gboard-18.4.1/`；保留 `base.apk` 及三个 split 的文件名。用 apktool 2.12.1 解码 base 到 `gboard-18.4.1/smali/`，其中应包含 `smali/pbb.smali` 和 `smali_classes2/nho.smali`。
2. `tools/apktool_2.12.1.jar`、`tools/dexlib2-2.5.2.jar`、`tools/guava-27.1-android.jar`，从各上游发布页或 Maven Central 取得。
3. 官方 Fcitx5 Android 与 Rime 插件 ARM64 包 `0.1.3-0-g048f581c`，文件名见依赖清单，放在项目根。基础包提供 Fcitx5 / C++ 库，Rime 插件提供 librime。
4. 原雾凇归档放在 `enhanced/rime-ice.zip`；执行 `python enhanced/prepare_data.py` 提取词典与运行库，再执行 `python enhanced/build_word_index.py` 生成 GBI3 索引。确切词典原文 SHA-256 见 `enhanced/rime-data/source-hashes.json`。
5. RIME-LMDG LTS 的 `wanxiang-lts-zh-hans.gram` 放在 `enhanced/rime-data/`，其大小与哈希见依赖清单。
6. Vosk Android 0.3.75 AAR 放在 `enhanced/vosk-android-0.3.75.aar`，取其中 `jni/arm64-v8a/libvosk.so` 放到 `enhanced/native/lib/arm64-v8a/`。解压 `vosk-model-small-cn-0.22` 内容到 `enhanced/voice-data/`，该目录直接包含 `am`、`conf`、`graph`、`ivector`。

获取自己设备 APK 的示例：

```powershell
adb -s YOUR_SERIAL shell pm path com.google.android.inputmethod.latin
# 对上述每个实际返回路径执行 adb pull，使用对应文件名。
java -jar tools/apktool_2.12.1.jar d -f gboard-18.4.1/base.apk -o gboard-18.4.1/smali
```

## 签名与正式版

键盘和语音 APK 必须用同一用户持有的 PKCS12 密钥签名。私钥没有上传，因此从源码重建的 APK 默认不能覆盖已发布 APK；可切回原版后另行安装自己的构建。不要把私钥或密码提交到仓库。

设置 `JAVA_HOME`、`ANDROID_SDK_ROOT`、`GBOARD_KEYSTORE`、`GBOARD_KEYSTORE_PASS`；`GBOARD_KEY_ALIAS` 默认 `probe`。可用 JDK keytool 创建自己的密钥；密码通过环境变量传入。构建从该密钥导出公钥证书并计算允许的 SHA-256，保留签名校验而非完全禁用校验。

```powershell
$env:JAVA_HOME='C:\Path\To\jdk-17'
$env:ANDROID_SDK_ROOT='C:\Path\To\Android\Sdk'
$env:GBOARD_KEYSTORE='C:\Private\signing.p12'
# 在本机设置 GBOARD_KEYSTORE_PASS，不写进源码、命令日志或仓库。
python enhanced/build_native.py
python enhanced/build_baseline.py
python enhanced/build_enhanced.py
python enhanced/make_release.py
python enhanced/build_voice_helper.py --release
```

输出是 `enhanced/final/` 的四个签名键盘 APK 与 `enhanced/voice-helper/final/voice-helper.apk`。检查 apksigner verify、zipalign 16 KiB 页对齐、两个 APK 无 INTERNET / 无备份 / 无调试标志，及原 `libintegrated_shared_object.so` 内容哈希一致。

## 独立验证

测试源码位于 `enhanced/enginecheck`、`enhanced/voicecheck` 与 `probe`，构建入口为相应 `build_*.py` 或 `probe/build.ps1`。语音调试测试还需要 `enhanced/speech-fixture.wav`：16 kHz 单声道 PCM16，公开测试句“今天下午召开会议请注意保护隐私”。可在本机生成；未上传任何真人录音。

真机诊断仅在自建测试编辑框运行，禁止对聊天应用发送全局测试按键。正式版不注册合成音频诊断入口。不要为了测试启用讯飞联网。本次 AVD 中原版和增强版 Gboard 的 ARM 翻译运行均出现上游 native crash，因此 AVD 使用独立引擎与语音测试 APK，不把那个故障当成增强版界面通过验证。

Release 安装包来自已完成真机测试的本地构建。本仓库把宿主路径和签名设置改成环境配置，未从空环境重新完成全部底包及模型下载构建；不宣称逐字节可复现。
