# 本地词库导入与讯飞接入评估

2026-10-25 核查了[讯飞输入法官网](https://srf.xunfei.cn/)及[讯飞开放平台](https://www.xfyun.cn/site/)。未找到向第三方输入法公开提供完整拼音词库或个人学习词库实时查询的、可验证的 API 文档。官网提供定制方案，这不等于可直接接入的免费词库 API。未来如果取得正式 SDK、接口文档和相应使用许可，需另行评估其候选格式、性能和联网行为。

1.3.2 实现的是**显式词表的本地迁移**。没有读取讯飞私有数据库、反向提取其安装包词库、请求讯飞服务或修改其网络策略。没有迁移讯飞模型、学习频率、云联想或账号同步。本次没有取得真实讯飞导出文件，因此不宣称已迁移用户的讯飞词库。

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

文本和 ZIP 容器最多 2 MiB，ZIP 解压后的文本也最多 2 MiB。合并后的独立词库最多 5000 条。不支持其他语言、任意 ZIP 附件、二进制词库或加密备份。文件有坏行时整体拒绝并显示行号，不导入其中的部分内容。

## 讯飞明文词表先转换

讯飞不同版本的导出格式可能不同。只有已经能读出的中文词语文本可以用 `local_vocab.py --format ifly-text` 转换，每行第一个字段作为中文词语，其余字段不解释。不支持把 `.dat`、`.dic` 等未知二进制文件改后缀后转换。导出若带有拼音，应整理为 TSV，使用 `--format tsv` 保留明确读音。

```powershell
python -m pip install -r requirements.txt
python local_vocab.py "C:\Private\words.txt" "C:\Private\words.zip" --format ifly-text
# 若确认原文件是带 BOM 的 UTF-16 明文，再添加 --encoding utf-16；GB18030 使用 --encoding gb18030。
```

核对同目录的 `words.review.json`，尤其是多音字、姓名和跳过行。自动生成的拼音可能错误，不能当作讯飞原有读音或排序。将 ZIP 放到手机，再从新增入口导入。私人词表、转换 ZIP 和 review 文件不应上传 GitHub。

## 存储与清除

导入词库写入键盘 UID 私有的 `no_backup/local-dictionary/words.tsv`，不使用系统词典、存储权限或持久文件授权。使用 AtomicFile 完成整次写入，成功后一次切换内存索引。导入、去重与清除不修改 `local-rime/user` 的学习数据库。显式导入词条与系统词典一样可在隐私输入中匹配，隐私模式继续禁止 Rime 选词学习。

「清除导入的词库」需确认，只清除新增入口的词条，不影响 Rime 学习数据、系统词典或字号等设置。同签名覆盖升级保留词库；卸载或清除应用数据会删除它。

主机测试：用 JDK 17 编译 `DictionaryFile.java` 和 `tests/DictionaryFileTest.java`，运行 `local.gboard.enhanced.DictionaryFileTest`。覆盖 TSV、ZIP、去重、读音规范化、编码错误、坏行、路径与解压边界、5000 条上限、合并溢出和清除后空标记。
