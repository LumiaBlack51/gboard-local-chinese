# Windows / 微软拼音迁移与词库同步

Windows 客户端使用小狼毫（Weasel）和雾凇拼音 `rime_ice`，复用 [1.3.7 同步协议](DICTIONARY-SYNC.md)。它与 Linux 客户端共用文本快照和学习计数转换，不访问运行中的 LevelDB。每台 Windows 电脑须单独注册设备令牌，接入现有权威服务器，不能把旧服务器副本当成新的独立词库。

可从 [v1.3.8 Release](https://github.com/LumiaBlack51/gboard-local-chinese/releases/tag/v1.3.8) 下载 `windows-phrase-sync-1.3.8.zip` 及同名 `.sha256` 校验文件。解压后在包根目录运行下文命令。该补充包只含同步源码、配置工具和说明；Python、PyYAML、小狼毫及雾凇拼音需单独安装，不含个人词库、配对令牌、私钥或第三方模型。

## 微软拼音词条

先将 `%APPDATA%\Microsoft\InputMethod\Chs\ChsPinyinUDL.dat` 复制到私有备份目录，再转换该副本：

```powershell
python sync\microsoft_pinyin.py C:\私有备份\ChsPinyinUDL.dat "$env:USERPROFILE\.config\gboard-phrase-sync\microsoft-phrases.tsv"
```

工具只支持已识别的 `55AA8881 02006000 55AA55AA` 自学习词库，校验记录长度、纯中文词语及拼音索引，保留词条原有读音。输出去重的中文全拼 TSV 和数量报告；不输出词语到终端。不把未知二进制字段推断成真实选择次数，导入权重统一为 100。不提取 `ChsPinyinIH.dat` 输入历史，也不上传二进制词库或备份。格式不符时停止，截断或不合规记录在报告中单独计数。

格式参考：[imewlconverter 的微软拼音导入器](https://github.com/studyzy/imewlconverter/blob/master/src/ImeWlConverter.Formats/Win10MsSelfStudy/Win10MsPinyinSelfStudyImporter.cs)。微软原词库保持不变；本适配器不向微软私有数据库回写共享内容。

## 小狼毫与共享候选

需要 Python 3、配置阶段使用的 PyYAML、包含 librime-lua 的小狼毫，以及已部署的雾凇拼音。Windows 0.17.4 / librime 1.13.1 已做实际引擎验证。

在实际 Rime 用户目录配置共享组件，再按小狼毫的部署方式编译方案。首次迁移应在切换到新输入法前完成；平时同步不触发重新部署、切换输入法或修改前台窗口。

```powershell
python sync\configure_rime.py --rime C:\实际Rime用户目录 --dynamic-ranking
```

`cloud-phrases.tsv` 和 `cloud-learning.tsv` 用文件头指纹热更新。选择次数只来自本机安装的官方 Rime 文本快照及 `selection-learning` 聚合文件；收到的服务器总数不会作为本机次数回传。小狼毫在普通提交时保存聚合，不记录逐键或逐次历史。Windows 文件替换保留 `.new` / `.old` 聚合用于失败恢复，不需要启动子进程。隐私模式不读取共享候选、共享排序计数或新增本机计数。

共享快照在 Lua 中保留为文本字符串，只解析当前拼音对应的行，本机逐编码缓存最多保留 128 项。这样避免将几万条词和学习记录展开成常驻 Lua 对象，在旧版 librime-lua 的按键间垃圾回收中反复扫描；不改变词库文件、协议、选词次数、排序规则或 Backspace 的逐字删除行为。

## 私有配置与计划任务

将私有配对 `config.json`、可选的 `server.crt` 及转换得到的 `microsoft-phrases.tsv` 放在 `%USERPROFILE%\.config\gboard-phrase-sync\`。只允许当前用户、SYSTEM 和管理员访问此目录。令牌和个人词条不进入 Git。

```powershell
.\sync\install_windows.ps1 -Rime C:\实际Rime用户目录 -SetDefault
```

`-SetDefault` 可选，用于将小狼毫设为默认中文输入法。安装器解析 Rime 的实际文件系统路径，并通过一次性、普通用户计划任务设置原生 HKCU 的 `RimeUserDir`。这样从使用 AppData 重定向的封装应用执行安装时，系统任务和输入法仍能访问同一目录；不会复制或覆盖原生学习数据库。

`GboardPhraseSync` 在用户已登录时，每小时及登录时触发。仅插电启动，拔电停止；不唤醒电脑，单次最多 60 秒，禁止并行重复执行。使用 `pythonw.exe`，无控制台窗口或常驻同步进程。联网超时 15 秒，不重定向令牌、不使用代理，仍验证 TLS 主机名和证书。`status.json` 仅记录成功状态、数量、时间和错误类型。

手动验证及查看状态：

```powershell
Start-ScheduledTask -TaskName GboardPhraseSync
Get-ScheduledTaskInfo -TaskName GboardPhraseSync
Get-Content "$env:USERPROFILE\.config\gboard-phrase-sync\status.json"
```

如仅需停用同步：

```powershell
Disable-ScheduledTask -TaskName GboardPhraseSync
```

微软拼音仍可从系统输入法列表选择。要恢复其为默认输入法：

```powershell
Set-WinDefaultInputMethodOverride -InputTip '0804:{81D4E9C9-1D3B-41BC-9E6C-4B40BF79E35E}{FA550B04-5AD7-411F-A5AC-CA038EC515D7}'
```

共享协议不传播删除；停用任务或切回微软拼音不会删除服务器上的词条。

## 验证

`tests/test_windows_sync.py` 用合成数据验证格式边界、隐私过滤、供电限制、互斥及学习计数不会回流。`python -X utf8 tests/verify_windows_rime.py` 使用已安装的小狼毫 DLL 和临时用户目录，验证导入词候选、共享排序、重复选词持久化及隐私模式，不向真实用户数据库提交测试词。

`python -B -X utf8 tests/verify_windows_latency.py` 在临时目录生成 33000 条合成词和 17000 条学习记录，测量输入、退格后生成首屏候选，以及空格、数字键和候选点击 API 到取得提交文本的耗时。分别统计首次选词与重复选词，默认要求各组 p95 小于 33 毫秒。`--commit-rounds 60` 做 720 次选词，`--without-shared` 可诊断共享组件的额外成本。结果不包含 Windows 按键重复等待、跨进程通信或界面绘制，因此不是完整前台延迟或微软输入法对比测试。

## 浏览器输入延迟排查

2026-10-31 的 Windows 隔离引擎测试中，720 次选词的重复提交 p95 为 2.57 毫秒，最大 3.97 毫秒，未复现明显上屏卡顿；不能据此排除网页前台延迟。原有测试只测输入和退格，现在补上选词提交，结果见 `tests/results/windows-commit-before-2026-10-31.json`。

上游 [Weasel #1413](https://github.com/rime/weasel/issues/1413) 和 [PR #1869](https://github.com/rime/weasel/pull/1869) 记录了浏览器长期运行后候选窗重复显示、定位带来的延迟。当前仍不能把这些报告直接当成本机根因或修复证明。候选窗阴影也增加绘制成本，可先使用当前仓库源码中的工具应用保守的减负配置（旧版补充包不含此工具）：

```powershell
python -B -X utf8 sync\configure_weasel.py --rime C:\实际Rime用户目录
```

然后重新部署小狼毫。默认调用只将 `style/layout/shadow_radius` 设为 `0`，保留字体、配色、行内拼音、候选规则和学习数据；第一次修改前备份为 `weasel.custom.yaml.before-low-latency`。要恢复阴影，将备份中的该项值合并回当前配置并重新部署。此配置减少阴影绘制开销，但不会修复上游重复 `ShowWindow` / `SetWindowPos` 的代码，也不保证消除浏览器长时间运行后的卡顿。未自动安装尚未合并的第三方分支。

本轮未取得实际网页文本框的前后计时。若引擎测试正常而某个浏览器窗口仍迟缓，可以把受影响标签移到新窗口后比较，保留原页面内容；不要据此删除浏览器用户数据或 Rime 用户词库。

### 单个应用的预编辑对照

如果延迟集中在一个应用，可以只对该进程切换预编辑方式，比较富文本编辑器与 Rime 的交互。比如本机 ChatGPT / Dot 的实际进程名为 `ChatGPT.exe`：

```powershell
python -B -X utf8 sync\configure_weasel.py --rime C:\实际Rime用户目录 --external-preedit-for ChatGPT.exe
```

重新部署后，点击输入框外再点回来。此时未选定的拼音应显示在候选窗内；其他进程仍使用原来的行内拼音。第一次应用专用修改前另存 `weasel.custom.yaml.before-app-preedit`，工具只接受进程文件名，不接受可执行文件路径。按进程覆盖 `inline_preedit` 是 [Weasel 官方支持的配置](https://github.com/rime/weasel/wiki/Weasel-%E5%AE%9A%E5%88%B6%E5%8C%96#应用设置)，但这项对照不是已证实的通用性能修复；若没有改善或出现漏字、重复字、选词异常，应恢复该进程的行内预编辑：

```powershell
python -B -X utf8 sync\configure_weasel.py --rime C:\实际Rime用户目录 --inline-preedit-for ChatGPT.exe
```

再重新部署即可。两条命令均保留已关闭的阴影和现有学习数据。评价效果应在实际应用中连续输入、退格，并分别用空格和数字键选词；仅检查生成配置或运行隔离引擎测试不能证明前台延迟已经降低。
