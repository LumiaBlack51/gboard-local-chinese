## 1.3.8 延迟与 Emoji 回归

`python3 tests/benchmark_rime.py --baseline-ref v1.3.7` 在不同子进程使用同一套部署词典与个人数据副本，按键后枚举前五候选，排除第一轮热身；不向桌面发送按键，不修改活跃数据库。`python3 -m unittest discover -s tests -p 'test_compact_tsv.py'` 直接执行实际 Lua 模块，检查大快照、字母前缀碰撞与未知编码。

`python3 tests/run_startup_checks.py --serial emulator-XXXX` 在独立 UID 的临时 APK 中，以 20,001 条合成共享记录与 20,000 条历史记录比较主线程首次查询、后台预载与预载后首次查询，验证重复预载复用快照且不把远端次数回传。只接受隔离 AVD，退出自动卸载，不需要手机。此项不测真实电池能耗。JDK 可单独编译 `EmojiSuggestions.java` 与 `tests/EmojiSuggestionsTest.java`，传入 `enhanced/rime-data/emoji.txt` 检查完整别名表。

同签名 `PhoneCandidateInstrumentation` 在已安装正式键盘的隔离 AVD 中还检查 `ok` 的普通／隐私候选及实际 Emoji 选择提交；沿用候选原生编号与学习回归。正式打包必须包含 GBI3 补全索引和 Emoji 别名，`tests/verify_release.py` 核验这些资产和字典来源哈希。构建缓存与临时 AVD可在保存测试报告及发布文件后删除，保留可重复运行的测试源码。

正式传输链检查：在隔离 AVD 安装正式键盘、更新器及同签名 `tests/build_sync_checks.py` 生成的 synccheck 后，执行 `python3 -B tests/run_installed_learning_checks.py --serial emulator-XXXX`。它使用临时自签名 HTTPS 服务、合成词条与临时配对，验证正式更新器解析学习记录、签名保护 provider 写入成功和重复交换不回传总计数。只接受 AVD；会替换测试更新器的配对，不可用于个人手机。测试后删除测试 AVD 和临时包。

1.3.7 增加真实学习的双向集成检查：`python3 -B tests/run_learning_checks.py --serial emulator-XXXX`。需要本机 Android SDK/JDK 和已启动的隔离 AVD。测试用独立 UID APK 实际写入 Android AtomicFile，通过临时 HTTP 服务与隔离的真实 librime 交换学习记录，验证原生候选重新排序、原生编号保留、历史本机次数接续、重启保存、较新的本地/远端选择，以及往返重试无计数反馈。APK 无网络权限和 Activity，测试完成自动卸载；不会打开个人词库或使用生产令牌。输出 `tests/results/shared-learning-integration-v1.3.7.json` 记录源码哈希与测试范围。

服务端单元检查：`python3 -B -m unittest discover -s tests -p 'test_*learning.py'` 和 `python3 -B -m unittest discover -s tests -p 'test_phrase_sync.py'`。桌面脚本的 1.3.7 报告单独保存，保留历史 1.3.6 真机结果；不要将 AVD 结果标为真机结果。

1.3.6 新增 `python3 tests/verify_cloud_learning.py`：在隔离的真实 Rime 引擎注入最大词频的 `凪/zhi`、`徳/de`，检查全部共享单字编码与字母、原生选词学习、新共享词转为原生用户词，以及后续选择、同步文件刷新和引擎重启后排序保持。手机新增 SelectionLearning，普通选择写本地偏好，隐私模式禁用；PhoneCandidateInstrumentation 检查所有编码的原生首屏、共享词学习及再次选择原生词后取消共享词前移。

1.3.6 的候选合成检查可在独立测试 AVD 运行，或在已明确选择、仅有本项目 probe 编辑框处于前台的手机运行。手机须先清空未提交拼音、隐藏键盘，并确认词库同步任务未在执行。检查只向适配器传入合成候选，不向输入连接发送文字；临时替换的内存词表和本地偏好在 finally 中恢复，随后重新打开正式键盘。不得在个人应用编辑框活跃时运行。

桌面消费者回归还运行 `python3 tests/verify_desktop_candidates.py` 和 `python3 tests/verify_desktop_cloud_rime.py`。前者覆盖单字母、简拼、全拼、多音字、ü、大小写、数字标点，并实际选词验证学习；后者验证已同步文件及同一会话热更新。隔离目录不打开正在使用的个人学习数据库，也不向前台发送按键。详见对应测试脚本的参数说明。

1.3.5 重新构建键盘及更新器，语音与校对复用已有正式 APK。先执行 `python tests/test_phrase_sync.py`，再构建主键盘、make_release、build_updater、release_metadata 和 verify_release。`tests/build_sync_checks.py` 生成临时同签名检查包：候选栏回归和 PhoneCandidateInstrumentation 候选排序/原生编号检查只在独立模拟器运行；手机检查仅针对更新器，无 Activity 或输入事件，不启动手机键盘 instrumentation。

# 构建说明

1.3.4 复用 1.3.1 的语音、校对与更新器正式 APK，只重新构建主键盘。运行 `enhanced/build_enhanced.py`（不带 `--test`）、`enhanced/make_release.py`、`enhanced/release_metadata.py` 和 `tests/verify_release.py`。`release_version.py` 中未变化组件的 versionCode 保持原值，清单继续核验全部七个安装文件。

词库解析测试：JDK 17 编译 `enhanced/src/local/gboard/enhanced/DictionaryFile.java` 与 `tests/DictionaryFileTest.java` 到临时目录，再运行 `local.gboard.enhanced.DictionaryFileTest`。安装 requirements 后运行 `python tests/test_local_vocab.py` 验证显式拼音、UTF-16 明文词表转换与错误二进制输入。只使用合成词条，不发布个人导出文件。

宿主脚本针对 Windows、Python 3.11+、JDK 17、Android SDK platform 36 / Build Tools 36.1.0、NDK 28.2.13676358。SDK、Java、apktool 路径由 `build_config.py` 的环境变量读取，不含原工作站路径。主键盘适配只支持这里的 Gboard 18.4.1；升级底包不能直接套用反射或 smali 锚点。

## 外部输入

仓库没有原 Gboard APK、反编译代码、第三方 JAR、运行库或大型模型。准备以下本地文件，并核对 `dependency-manifest.json` 与字典 `source-hashes.json`：

1. 从自己设备取得 Gboard 18.4.1.985164140-release-arm64-v8a 的四个 APK，放在 `gboard-18.4.1/`；保留 `base.apk` 及三个 split 的文件名。用 apktool 2.12.1 解码 base 到 `gboard-18.4.1/smali/`，其中应包含 `smali/pbb.smali` 和 `smali_classes2/nho.smali`。
2. `tools/apktool_2.12.1.jar`、`tools/dexlib2-2.5.2.jar`、`tools/guava-27.1-android.jar`，从各上游发布页或 Maven Central 取得。
3. 官方 Fcitx5 Android 与 Rime 插件 ARM64 包 `0.1.3-0-g048f581c`，文件名见依赖清单，放在项目根。基础包提供 Fcitx5 / C++ 库，Rime 插件提供 librime。
4. 原雾凇归档放在 `enhanced/rime-ice.zip`；执行 `python enhanced/prepare_data.py` 提取词典与运行库，再执行 `python enhanced/build_word_index.py` 生成 GBI3 索引。确切词典原文 SHA-256 见 `enhanced/rime-data/source-hashes.json`。
5. RIME-LMDG LTS 的 `wanxiang-lts-zh-hans.gram` 放在 `enhanced/rime-data/`，其大小与哈希见依赖清单。
6. 取得官方 `sherpa-onnx-v1.13.8-android.tar.bz2`。取 ARM64 与 x86_64 的 `libonnxruntime.so`、`libsherpa-onnx-c-api.so` 到对应 `enhanced/native/lib/<ABI>/`。取得官方 `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile.tar.bz2`，将量化 encoder、未量化 decoder、量化 joiner 及 tokens 放到 `enhanced/voice-data/`，文件名依次为 `encoder.int8.onnx`、`decoder.onnx`、`joiner.int8.onnx`、`tokens.txt`。
7. 从 [Tegaki 0.3 简体中文归档](https://github.com/tegaki/tegaki/releases/download/v0.3/tegaki-zinnia-simplified-chinese-0.3.zip) 提取模型、元数据及 COPYING 到 `enhanced/handwriting-data/`，模型名 `handwriting-zh_CN.model`。哈希和完整可编辑 XML 来源见 [手写来源说明](../enhanced/HANDWRITING-SOURCES.md)。Zinnia 识别源码已按固定提交加入仓库。

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
python enhanced/build_handwriting.py
python enhanced/build_baseline.py
python enhanced/build_enhanced.py
python enhanced/make_release.py
python enhanced/build_voice_helper.py --release
python enhanced/build_updater.py
python enhanced/release_metadata.py
```

输出是 `enhanced/final/` 的四个键盘 APK、`enhanced/voice-helper/final/voice-helper.apk` 和 `enhanced/updater/out/updater.apk`。检查 apksigner verify、zipalign 16 KiB 页对齐、键盘和语音无 INTERNET / 无备份 / 无调试标志，及原 `libintegrated_shared_object.so` 内容哈希一致。

## 独立验证

数字、电话及日期时间输入框由输入法内部按普通文本键盘初始化，并自动点进可切换的 `?123` 数字符号页；点击 `返回`／`ABC` 返回字母。同一编辑框 `restartInput` 不重复强制点进数字页。数字密码转换为文本密码模式并设置禁止个性化学习，继续禁用麦克风；原应用的密码遮挡和字符过滤仍生效。Shift 单击会触发原生双击大写锁定，再点一次 Shift 退出。管理页可加入或移除顶部「收起」按钮，长按也可移除。1.3.3 识别原生 `AccessPointsBar`，将原生语音按钮移至设置位置，收起固定在语音右侧的最右端；被替换的原生操作加入功能菜单。箭头直接按控件中心绘制。按钮放在外层 FrameLayout，保持原生子控件类型及测量，并在原生触摸分发之前转发完整手势。原生语音保留其原有事件，命中测试按变换矩阵跟随新位置。

`tests/build_keyboard_modes_check.py` 构建临时同签名 instrumentation，在已选设备的独立 probe 隐私编辑框中验证正式键盘。覆盖电话、普通数字、带符号小数、数字密码、日期、时间及日期时间的数字上屏、ABC 返回、反复切换及连接重启；另检查数字密码模式和禁止学习标志、普通中文输入。测试包不进入发行 APK，测试完成后移除。

`tests/build_toolbar_check.py` 构建临时同签名 instrumentation，不进入发行 APK。仅在明确选择的设备、自建 probe 隐私编辑框中运行 `local.gboard.toolbarcheck/.ToolbarInstrumentation`：`-e mode snapshot` 在设备私有目录保存设置和导入词库指纹，覆盖安装后 `-e mode retention` 比较并删除；`-e mode verify` 检查原生工具栏、收起在语音右侧的最右端及垂直中心、原生语音命中位置、设置位置、单手入口、触摸区域、实际收起、重新打开、功能菜单设置入口和拼音候选切换。指纹及私人词条不输出。测试完成卸载检查包；不在个人聊天应用输入测试文字。

调试包包含 `KeyboardModesCheckActivity`，在 AVD 中用合成键盘验证数字页可输入 `+`、可切回字母、连续大写及退出，并核对工具栏识别和设置持久化。启动后从应用私有文件 `files/keyboard-modes-report.json` 读取结果。完整 Gboard 18.4.1 的 ARM 原生库在现有 x86 AVD 的 ARM 翻译环境会崩溃，因此这些检查不能代替 ARM64 真机上的键盘界面验收。

测试源码位于 `enhanced/testsrc`、`enhanced/enginecheck`、`enhanced/voicecheck` 与 `probe`。`python enhanced/build_enhanced.py --test` 添加 AdapterCheckActivity、GestureCheckActivity 与 FontSizeCheckActivity，再运行 `python enhanced/make_debug.py` 输出同版本号、debuggable 的四个 AVD APK 到 `enhanced/build/test-apks`。正式版必须重新运行不带 `--test` 的构建命令，避免保留测试 Activity。其他构建入口为相应 `build_*.py` 或 `probe/build.ps1`。

1.1.1 的语音交互测试使用 `build_voicecheck.py` 生成的独立测试 APK；它将当前源码的 VoiceHook 放进 `HookCheckService` 测试输入法，在 `HookCheckActivity` 的合成编辑框中运行。仅在 AVD 选中测试输入法、启动此 Activity；它触发一次模拟麦克风命令，不再点击开始或确认，随后检查文字直接填入及面板关闭。结束后恢复原输入法，运行 `tests/run_avd_regression.py --serial emulator-XXXX` 汇总候选、语音、自动结束和录音生命周期回归。不得在个人手机或聊天应用运行这个测试输入法。

语音调试测试需要 `enhanced/speech-fixture.wav`：16 kHz 单声道 PCM16，合成测试句“今天下午召开会议请注意保护隐私”；混说测试 `enhanced/speech-mixed.wav` 来自上述官方模型归档的 `test_wavs/1.wav`。`build_voice_helper.py --release` 不打包这些测试录音，也不开放测试音频入口。

正式语音服务的包可见性和签名校验会拒绝独立测试客户端。使用 `python enhanced/build_releasetest.py` 构建同签名 instrumentation，安装到 AVD 后执行 `tests/run_release_voice_checks.py --serial emulator-XXXX`，在键盘 UID 下验证实际录音及正式版测试音频禁用。测试 APK 与目标键盘必须同签名；它不在正式发行中。

语音回调遵循 [Android 15 RecognitionService 的会话与归因约定](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android15-release/core/java/android/speech/RecognitionService.java)：对应 Callback 处理停止/取消，返回识别结果前关闭麦克风。正式包回归包含停止、取消及取消后再次开始，验证连续会话不会互相干扰。

1.2.4 的 `tests/run_input_fixes.py --serial emulator-XXXX` 在已安装调试键盘、voicecheck 和调试语音服务的 AVD 中验证 C/V/B/N 下缘、字母与空格间隙、空格上沿、正常空格确认及原有光标/手写操作；语音使用合成句验证面板不抢焦点、输入连接刷新后的填入，以及模拟首次提交失败后保留结果和点击重试。语音轮次只替换测试编辑框的任务，保留测试 IME 进程，避免系统异步切回默认输入法。`--voice-only` 可复用本版本已通过的手势报告。测试结果带有 VoiceHook 与 KeyboardGestures 源码哈希。

更新回归在旧版 AVD 中通过管理应用点击“检查更新”，下载公开 GitHub 正式资产，依次确认安卓的三个组件更新和首次 Play Protect 扫描。`tests/verify_installed_update.py` 核对安装来源、版本、所有安装 APK 的哈希与键盘首次安装时间；`tests/verify_update_ui.py` 检查管理入口、成功提示和重复检查状态。`tests/verify_github_release.py` 比较全部九个上传文件的服务端哈希及大小。1.2.0 以同签名 RetentionInstrumentation 在更新前后比较本地 Rime 用户文件与设置，并验证旧更新器升级。1.2.2 还通过新版更新器重新下载全部公开 APK，实际完成同版本修复；以 `--before-last-update-time` 检查安装更新时间发生变化，保存独立修复报告；`verify_update_ui.py --repair` 验证修复后的成功提示和再次检查状态。两次安装均保留 18 个本地用户文件和设置。全部 instrumentation 仅用于 AVD，不随正式包发布。

`tests/run_updater_result_checks.py` 使用同签名 UpdaterResultInstrumentation，在实际更新器 UID 发送合成系统拒绝与取消状态给私有 InstallReceiver，并检查打开的页面即时刷新。测试会恢复此前的结果记录，不安装 APK；实际系统安装验证仍由另外的更新回归覆盖。独立测试 IME 的 LensCheckActivity 用合成拼音检查 ComposingLens 在输入法窗口中的显示与输入连接保留。

真机诊断仅在自建测试编辑框运行，禁止对聊天应用发送全局测试按键。不要为了测试启用讯飞联网。本次 AVD 中原版和增强版 Gboard 的 ARM 翻译运行均出现上游 native crash，因此将原生候选控件放在调试 Activity 中验证，并使用独立语音测试 APK；不把那个故障当成完整键盘界面通过验证。

1.2.5 的 `tests/run_phone_touch_checks.py --adb PATH --serial PHONE` 使用正式输入法和 `local.gboard.probe` 的临时隐私编辑框。坐标来自此次 1272×2800 真机截图，其他设备须先观察布局再调整；发送按键前检查当前编辑器和前台应用均为 probe。覆盖 C/V/B/N 五种纵向位置、空格中部确认与连续输入，结果包含源码哈希。另保存空间光标插字、手写选词和候选字号 150% 截图检查结果；候选检查是目视证据，不声称覆盖所有词、主题和尺寸。没有在真机安装调试键盘、测试输入法或 instrumentation，测试后恢复此前的字号设置。

v1.0.0 曾在 Android 16 真机验证；v1.1.0 的新增功能在 AVD 验证，没有重新进行真机测试。仓库把宿主路径和签名设置改成环境配置，未从空环境重新完成全部底包及模型下载构建；不宣称逐字节可复现。

## 1.2.6 录音与连写验证

正式语音使用 [RecognizerIntent.EXTRA_AUDIO_SOURCE](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE) 匿名文件描述符，由 IME UID 采集 16 kHz 单声道 PCM16。后台服务只识别传入音频，避免手机把辅助服务麦克风静音。管道结束后读取至 EOF；取消关闭两端。旧客户端直接录音入口仍保留，以兼容更新前的客户端。

`tests/run_voice_pipe.py --adb PATH --serial emulator-XXXX` 验证跨进程合成 PCM、奇数字节块、EOF、载入模型时提前停止，以及端点处理。测试客户端需要 `build_voicecheck.py` 打包的合成 fixture。`tests/run_input_fixes.py` 增加连写与采样回归，包括淡出像素变化、旧笔迹与新字分离、底排原始坐标、起笔曲线以及超过 512 点仍保留末端。

手机只安装正式键盘/服务及临时 probe。probe 可带 `speech_test=true` 和 `speech_delay_ms=6000` 在延迟后播放合成测试句，用设备麦克风验证真实声学链路；测试通过报警音频用途和 30% 播放音量避免已连接蓝牙与扬声器媒体音量为零影响，未修改系统音量或蓝牙配置。不用于准确率评测，结束后移除 probe 和测试截图。检查录音系统状态时只保存本次测试的静音标记，不发布系统音频历史。

## 1.3.0 中英校对

先运行 `python enhanced/download_proofreading.py` 验证并下载 `proofread-inputs.json` 的固定模型和 Maven AAR；安装构建依赖 torch、transformers、onnx、onnxruntime、sentencepiece、protobuf。执行 `python enhanced/prepare_proofreading.py --sources artifacts/proofreading --output enhanced/proofread-data --work artifacts/proofreading/onnx-work` 导出量化模型，再 `python enhanced/build_proofread.py --release` 生成 `enhanced/proofread/final/proofread.apk`。模型导出必须比较浮点和量化输出。许可证和上游模型卡随 APK 提供。版本常量位于 `release_version.py`，组件 manifest 也需同步。

`tests/run_proofreading.py --adb PATH --serial PHONE --backend auto` 只向临时同签名 probe 传入其源码内的固定合成文本；在准备完成的正式校对组件上测试。`cpu`/`npu` 可对比耗时。GPU 对这张图未通过全图执行检查，正式包不附带未使用的 GPU 库。`tests/VoiceTextTest.java` 覆盖大小写和保护规则；AVD 的 HookCheckActivity 带 `--ez undo true` 验证真实 IME 窗口中的撤销及光标/选区保护。

更新清单保留原三个 `components`，校对位于 `optionalComponents`，兼容旧更新器。新版更新器统一验证并安装七个 APK；旧版忽略扩展，升级更新器后再检查一次。

## 1.3.1 离线自动标点

执行 `python enhanced/download_punctuation.py` 下载并核验固定量化模型；`python enhanced/build_native.py` 构建 Sherpa-ONNX 标点 C API 的 JNI 包装，再执行 `python enhanced/build_voice_helper.py --release`。模型放在独立语音包的 `assets/local-punctuation`，不依赖可选校对。

用 JDK 17 编译运行 `tests/VoicePunctuationTest.java` 和 `tests/VoiceTextTest.java`。`python tests/build_punctuation_check.py` 构建临时同签名 instrumentation，目标为正式语音包；在明确选择的设备安装后执行 `adb -s SERIAL shell am instrument -w local.gboard.punctuationcheck/.PunctuationInstrumentation`。测试仅使用源码内的固定合成文本，不读取用户语音、词库或输入历史。测试包不在 Release 中，完成后卸载。真机键盘测试使用临时 probe 编辑框与合成音频声学回放。
