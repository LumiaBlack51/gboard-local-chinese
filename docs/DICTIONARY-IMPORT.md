# 本地词库导入与讯飞接入评估

1.3.2 支持通过讯飞自带的导出入口迁移本地用户词。已验证的讯飞版本为 15.0.9；不需要云端接口或账号同步。Android 会隔离应用私有目录，因此采用用户明确导出的文件，不直接读取讯飞的私有学习数据库。

1.3.2 实现的是**显式词表的本地迁移**。没有读取讯飞私有数据库、反向提取其安装包词库、请求讯飞服务或修改其网络策略。没有迁移讯飞模型、学习频率、云联想或账号同步。个人词库只在机主本地迁移，原始内容不随源码或 Release 分发。

已在讯飞 15.0.9 的「设置 → 词库设置 → 用户词 → 导出」验证了离线导出流程，可同时得到 `user_dict.txt`、`user_dict_bin.dic` 和 `userAss.bin`。明文为 UTF-8，正文格式是「词条 类型」；类型 1 表示联系人，0 表示其他，**不是词频**。明文不含原始拼音。二进制学习备份与联想数据目前不能直接由 Rime 读取。

转换时可加 `--word-index enhanced/rime-data/word-index-v3.bin`，优先从随包公开词典匹配整个词的读音，选最高词频的读音；匹配不到才用 pypinyin。它们都不是从讯飞恢复出的原始读音，多音字仍可能有差异。review 中 `generated: false` 表示明确输入／本地词典读音，`generated: true` 表示自动生成。可按此字段筛选后再导入。英文、符号和无法得到拼音的词保留在原备份中，中文转换不会迁移原生英文学习模型。

个人导出、二进制备份、转换词库及 review 必须保存在仓库忽略的 `private/` 或其他私人目录，不能上传 GitHub。公开仓库只包含转换器、导入功能及合成测试。

## 排除重复，再查询整词和词组读音

匹配已有词典的词，本身不会增加词汇覆盖；导入后仅作为个人词优先显示。`word-index-v3.bin` 是补全索引，不等于完整基础词库，缺少索引记录也不能直接认定为新增词。

`tools/resolve_vocab.py` 会读取 `rime_ice.dict.yaml` 实际启用的词表，排除已存在的整词，再查询离线词语拼音数据；整词未命中时，用较长的已收录词组和单字读音组合。只有得到唯一的默认全拼时才输出，存在多个全拼的词留在报告中，不自动选第一个。结果来自词典，不宣称恢复了讯飞的原始输入编码；人名和特殊语境仍应结合实际使用核对。

本轮词典来源为 [phrase-pinyin-data](https://github.com/mozillazg/phrase-pinyin-data/tree/005814e5c481a5cc648db963db668db8f7c32199) 的 `large_pinyin.txt`，包含其说明列出的汉典、CC-CEDICT 等词语数据，MIT 许可证。整份词典下载到本机后查询，不把个人词条发给在线查询服务，也不把该数据加入手机 APK。

```powershell
python tools/resolve_vocab.py --review private/words.review.json --rime-data enhanced/rime-data --phrase-data C:\LocalDictionaries\large_pinyin.txt --output private/new-words.zip --report private/reading-review.json
```

报告区分基础词库已有词、可补充的新词和读音冲突词；输出 ZIP 只包含可补充的新词。测试使用公开合成词，个人词表和逐词核对结果不进入测试仓库。

## 手机上使用

打开「Gboard 中文本地增强」→ 滚动到「本地词库」→「导入本地词库」，通过系统文件选择器选择文件，核对条数后确认导入。取消预览不写入，导入合并去重，已有同词同拼音保留较高词频。返回键盘，输入完整拼音即可在候选前部选用词条。这里只增加完整拼音词条，不把它们加入 Rime 组句模型或简拼索引。

支持 UTF-8（可带 BOM）的 TSV，每行 `中文词语<Tab>无声调拼音`，可增加第三列整数词频（1～1000000）。中文长度 1～32 字，拼音最多 128 个字母；支持空格、撇号、`ü`、`u:` 转 `v`。例如（分隔符为实际 Tab）：

```text
离线词库	li xian ci ku	200
```

也支持只包含 `dictionary.txt` 的 Gboard 词库 ZIP：

```text
# Gboard Dictionary version:1
lixianciku	离线词库	zh-CN
```

文本和 ZIP 容器最多 16 MiB，ZIP 解压后的文本也最多 16 MiB。合并后的独立词库最多 50000 条。不支持其他语言、任意 ZIP 附件、二进制词库或加密备份。文件有坏行时整体拒绝并显示行号，不导入其中的部分内容。

## 讯飞明文词表先转换

讯飞不同版本的导出格式可能不同。只有已经能读出的中文词语文本可以用 `local_vocab.py --format ifly-text` 转换，每行第一个字段作为中文词语，其余字段不解释。不支持把 `.dat`、`.dic` 等未知二进制文件改后缀后转换。导出若带有拼音，应整理为 TSV，使用 `--format tsv` 保留明确读音。

```powershell
python -m pip install -r requirements.txt
python local_vocab.py "C:\Private\words.txt" "C:\Private\words.zip" --format ifly-text --word-index enhanced/rime-data/word-index-v3.bin
# 若确认原文件是带 BOM 的 UTF-16 明文，再添加 --encoding utf-16；GB18030 使用 --encoding gb18030。
```

核对同目录的 `words.review.json`，尤其是多音字、姓名和跳过行。自动生成的拼音可能错误，不能当作讯飞原有读音或排序。将 ZIP 放到手机，再从新增入口导入。私人词表、转换 ZIP 和 review 文件不应上传 GitHub。

## 存储与清除

导入词库写入键盘 UID 私有的 `no_backup/local-dictionary/words.tsv`，不使用系统词典、存储权限或持久文件授权。使用 AtomicFile 完成整次写入，成功后一次切换内存索引。导入、去重与清除不修改 `local-rime/user` 的学习数据库。显式导入词条与系统词典一样可在隐私输入中匹配，隐私模式继续禁止 Rime 选词学习。

「清除导入的词库」需确认，只清除新增入口的词条，不影响 Rime 学习数据、系统词典或字号等设置。同签名覆盖升级保留词库；卸载或清除应用数据会删除它。

主机测试：用 JDK 17 编译 `DictionaryFile.java` 和 `tests/DictionaryFileTest.java`，运行 `local.gboard.enhanced.DictionaryFileTest`。覆盖 TSV、ZIP、去重、读音规范化、编码错误、坏行、路径与解压边界、50000 条上限、合并溢出和清除后空标记。
