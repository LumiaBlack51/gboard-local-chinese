# 构建说明

宿主脚本针对 Windows、Python 3.11+、JDK 17、Android SDK platform 36 / Build Tools 36.1.0、NDK 28.2.13676358。SDK、Java、apktool 路径由 `build_config.py` 的环境变量读取，不含原工作站路径。主键盘适配只支持这里的 Gboard 18.4.1；升级底包不能直接套用反射或 smali 锚点。

## 外部输入

仓库没有原 Gboard APK、反编译代码、第三方 JAR、运行库或大型模型。准备以下本地文件，并核对 `dependency-manifest.json` 与字典 `source-hashes.json`：

1. 从自己设备取得 Gboard 18.4.1.985164140-release-arm64-v8a 的四个 APK，放在 `gboard-18.4.1/`；保留 `base.apk` 及三个 split 的文件名。用 apktool 2.12.1 解码 base 到 `gboard-18.4.1/smali/`，其中应包含 `smali/pbb.smali` 和 `smali_classes2/nho.smali`。
2. `tools/apktool_2.12.1.jar`、`tools/dexlib2-2.5.2.jar`、`tools/guava-27.1-android.jar`，从各上游发布页或 Maven Central 取得。
3. 官方 Fcitx5 Android 与 Rime 插件 ARM64 包 `0.1.3-0-g048f581c`，文件名见依赖清单，放在项目根。基础包提供 Fcitx5 / C++ 库，Rime 插件提供 librime。
4. 原雾凇归档放在 `enhanced/rime-ice.zip`；执行 `python enhanced/prepare_data.py` 提取词典与运行库，再执行 `python enhanced/build_word_index.py` 生成 GBI3 索引。确切词典原文 SHA-256 见 `enhanced/rime-data/source-hashes.json`。
5. RIME-LMDG LTS 的 `wanxiang-lts-zh-hans.gram` 放在 `enhanced/rime-data/`，其大小与哈希见依赖清单。
6. 取得官方 `sherpa-onnx-v1.13.8-android.tar.bz2`。取 ARM64 与 x86_64 的 `libonnxruntime.so`、`libsherpa-onnx-c-api.so` 到对应 `enhanced/native/lib/<ABI>/`。取得官方 `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile.tar.bz2`，将量化 encoder、未量化 decoder、量化 joiner 及 tokens 放到 `enhanced/voice-data/`，文件名依次为 `encoder.int8.onnx`、`decoder.onnx`、`joiner.int8.onnx`、`tokens.txt`。

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
python enhanced/build_updater.py
python enhanced/release_metadata.py
```

输出是 `enhanced/final/` 的四个键盘 APK、`enhanced/voice-helper/final/voice-helper.apk` 和 `enhanced/updater/out/updater.apk`。检查 apksigner verify、zipalign 16 KiB 页对齐、键盘和语音无 INTERNET / 无备份 / 无调试标志，及原 `libintegrated_shared_object.so` 内容哈希一致。

## 独立验证

测试源码位于 `enhanced/testsrc`、`enhanced/enginecheck`、`enhanced/voicecheck` 与 `probe`。`python enhanced/build_enhanced.py --test` 添加 AdapterCheckActivity，再运行 `python enhanced/make_debug.py` 输出同版本号、debuggable 的四个 AVD APK 到 `enhanced/build/test-apks`。正式版必须重新运行不带 `--test` 的构建命令，避免保留测试 Activity。其他构建入口为相应 `build_*.py` 或 `probe/build.ps1`。

1.1.1 的语音交互测试使用 `build_voicecheck.py` 生成的独立测试 APK；它将当前源码的 VoiceHook 放进 `HookCheckService` 测试输入法，在 `HookCheckActivity` 的合成编辑框中运行。仅在 AVD 选中测试输入法、启动此 Activity；它触发一次模拟麦克风命令，不再点击开始或确认，随后检查文字直接填入及面板关闭。结束后恢复原输入法，运行 `tests/run_avd_regression.py --serial emulator-XXXX` 汇总候选、语音、自动结束和录音生命周期回归。不得在个人手机或聊天应用运行这个测试输入法。

语音调试测试需要 `enhanced/speech-fixture.wav`：16 kHz 单声道 PCM16，合成测试句“今天下午召开会议请注意保护隐私”；混说测试 `enhanced/speech-mixed.wav` 来自上述官方模型归档的 `test_wavs/1.wav`。`build_voice_helper.py --release` 不打包这些测试录音，也不开放测试音频入口。

正式语音服务的包可见性和签名校验会拒绝独立测试客户端。使用 `python enhanced/build_releasetest.py` 构建同签名 instrumentation，安装到 AVD 后执行 `tests/run_release_voice_checks.py --serial emulator-XXXX`，在键盘 UID 下验证实际录音及正式版测试音频禁用。测试 APK 与目标键盘必须同签名；它不在正式发行中。

语音回调遵循 [Android 15 RecognitionService 的会话与归因约定](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android15-release/core/java/android/speech/RecognitionService.java)：对应 Callback 处理停止/取消，返回识别结果前关闭麦克风。正式包回归包含停止、取消及取消后再次开始，验证连续会话不会互相干扰。

更新回归在旧版 AVD 中通过管理应用点击“检查更新”，下载公开 GitHub 正式资产，依次确认安卓的三个组件更新和首次 Play Protect 扫描。`tests/verify_installed_update.py` 核对安装来源、版本、所有安装 APK 的哈希与键盘首次安装时间；`tests/verify_update_ui.py` 检查管理入口、成功提示和重复检查状态。`tests/verify_github_release.py` 比较全部九个上传文件的服务端哈希及大小。结果见 `tests/results/avd-installed-update-v1.1.json`。

真机诊断仅在自建测试编辑框运行，禁止对聊天应用发送全局测试按键。不要为了测试启用讯飞联网。本次 AVD 中原版和增强版 Gboard 的 ARM 翻译运行均出现上游 native crash，因此将原生候选控件放在调试 Activity 中验证，并使用独立语音测试 APK；不把那个故障当成完整键盘界面通过验证。

v1.0.0 曾在 Android 16 真机验证；v1.1.0 的新增功能在 AVD 验证，没有重新进行真机测试。仓库把宿主路径和签名设置改成环境配置，未从空环境重新完成全部底包及模型下载构建；不宣称逐字节可复现。
