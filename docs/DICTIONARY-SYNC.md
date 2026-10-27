# 手机与电脑词库共享（1.3.5）

手机默认插电、离电均由 Android JobScheduler 每小时安排一次联网同步；可在「检查更新 → 词库共享设置」关闭「允许电池供电时同步」，改为仅插电。系统省电、网络不可用时可能延后；键盘使用中仍交换独立词条文件，仅延后刷新 Rime 学习快照，不是精确闹钟。

电脑端由 systemd 用户 timer 每小时触发 oneshot，ConditionACPower=true；离电时不启动 Python，运行中拔电也会结束本次进程。不更改电源管理、输入法切换和前台窗口。

部分 ColorOS/OPlus 手机还需要在系统设置中搜索「自启动」，允许「Gboard增强更新」。这是允许系统启动定时任务，不会增加常驻服务。若未允许，系统可能直接拒绝任务并移除调度；允许后在共享设置重新保存配对配置可恢复。

## 不常驻的实现

- 手机：无 AlarmManager、无前台服务、无循环重试、无保活通知。系统持有周期任务；独立 `local.gboard.updater:dictionary_sync` 进程仅在执行时启动，结束或系统停止任务后退出。开启键盘或组成拼音时只延后 Rime 学习快照刷新，独立词条文件仍可同步，不修改当前输入。只有可选的仅插电模式会在拔电时停止；默认可用电池同步。
- 电脑：timer 属于已存在的 systemd 用户管理器，脚本每次只执行一轮，无后台守护进程。执行最长 60 秒，网络读写超时 15 秒。供电监测线程仅在这一轮联网期间存在。
- 服务器：systemd socket 按连接启动 Python，空闲 10 秒退出。已有 HTTPS 网关仍由它所属的服务管理。

## 共享范围和合并规则

交换 UTF-8 TSV 三列：`中文词语<Tab>无声调完整拼音（紧凑编码）<Tab>词频`。最多 50000 条、16 MiB。支持中文词语，不共享英文私有模型、五笔编码、密码、音频、输入连接、逐键记录和整段历史。

手机导出本地导入词库，以及 Rime 官方文本快照中计数为正的已学习词。只通过 Rime 自身 API 生成快照，不直接读取或覆盖运行中的 LevelDB。首次安装而引擎尚未启动时，仅导出已有词条，不为同步预载模型。

电脑导出 `rime_ice` 的 Rime 文本快照和 `custom_phrase.txt` 中符合中文全拼格式的词条；非中文快捷短语留在本机。中文输入不活跃时通过 Fcitx Save 刷新快照；使用中文时读取最近快照，新的学习可能在下一轮才共享。云端词条写入独立的 `cloud-phrases.tsv`，由 `lua_translator@*cloud_phrase` 读取。现有学习数据库与自定义短语不被覆盖。

服务端按「词语、拼音」去重，词频取最大值；同一请求重复提交不会增加词频。每次返回完整并集，断线后的下次同步可重试。无删除传播：清除本机导入词库不会清除服务器和其他设备，下一次同步可能恢复词条。需要永久清除时先停用所有客户端，再由服务器管理员清理；本版本不把各引擎的遗忘操作映射成全局删除。

## 候选排序与桌面接入

共享的是可跨引擎转换的词条及词频，并非整套模型或候选列表；两端不保证每一个候选位置相同。相同文字已存在原生候选时保留其原生身份和选词编号，避免静态同步词覆盖它后阻断学习。所有单字母输入优先展示原生首屏，新共享词仍可在其后出现。完整拼音的新词正常进入候选；电脑读取文件头指纹进行热更新，无需每小时重新部署整个词典。

桌面需 Python 3、PyYAML、Fcitx5/Rime 及 librime-lua，使用 `rime_ice`。在安装 one-shot 同步脚本和 timer 后执行：

```sh
python3 sync/configure_rime.py --rime ~/.local/share/fcitx5/rime --dynamic-ranking
```

工具安装 `cloud_phrase.lua` 和 `cloud_native_filter.lua`，保留已有键位与学习数据；如发现同一补丁位置已有其他组件则停止并要求人工合并。原 YAML 备份为 `.before-cloud-setup`。`--dynamic-ranking` 仅在未设置用户置顶规则时关闭上游默认固定置顶，并从 `custom_phrase.txt` 移除三条上游样例 `呣/m`、`呒/m`、`噷/hm`，原文件备份为 `.before-candidate-repair`；不删除基础词典中的这些字。已有自定义置顶规则保留。随后按本机 Rime 的部署方式重新编译方案，并让 Fcitx 重新加载 Rime 配置；不要在正在组成拼音时强制重启输入法。

手机的相同修正包含在主键盘 1.3.5 中，保留原生候选相对顺序与编号；隐私模式不加入个人导入/同步词。同步与键盘候选生成分别验证，接口收到词条成功不等于前台候选已消费该词条。

## API 与新设备

`POST /v1/sync`，请求头：

```
Authorization: Bearer <每台设备独立的令牌>
X-Phrase-Protocol: 1
Content-Type: text/tab-separated-values; charset=utf-8
```

请求、响应均为上述 TSV，可含 `#` 注释；空词库可只包含注释。服务器对整个请求验证后，在事务内合并；格式错误或超过上限不修改词库。凭据仅以 SHA-256 摘要保存在服务端。接口版本为路径中的 `v1`，新设备只需实现相同协议与自身词库转换，不依赖 Android 或桌面内部数据库格式。

管理员新增设备，在服务器私有终端运行（令牌只会输出一次）：

```
runuser -u phrase-sync -- python3 /opt/phrase-sync/server.py \
  --database /var/lib/phrase-sync/words.sqlite3 --add-device DEVICE_NAME
```

每个客户端使用私有配对 JSON，例子中的值需替换：

```json
{
  "endpoint": "https://SERVER:8443/v1/sync",
  "systemTrust": true,
  "certificateSha256": "服务端自签名证书的64位小写十六进制指纹",
  "token": "本设备的令牌"
}
```

`systemTrust=true` 可省略 `certificateSha256`，使用系统 CA 与正常主机名验证，适合已有受信 HTTPS 网关及自动续期证书。`false` 使用配对证书指纹，并继续核验主机名和证书有效期。关闭跨站重定向，不把令牌转发到其他地址。手机在共享设置粘贴 JSON 即可配对，安装包和仓库没有个人凭据。

桌面私有配置位于 `~/.config/gboard-phrase-sync/config.json`，手机配对备份私有保存在 `~/.config/gboard-phrase-sync/devices/phone-plq110.json`（权限 600，不进入仓库），配对的内部服务公钥证书存为同目录 `server.crt`。脚本安装到 `~/.local/lib/gboard-phrase-sync/`，单元模板位于仓库 `sync/`。停用桌面同步：

```
systemctl --user disable --now gboard-phrase-sync.timer
```

撤销单个设备（保留其他设备与既有词条）：

```
runuser -u phrase-sync -- python3 /opt/phrase-sync/server.py \
  --database /var/lib/phrase-sync/words.sqlite3 --revoke-device DEVICE_NAME
```

本次原计划部署至 64.56.71.36，因其连接不稳定，最终复用 45.76.170.31:8443 已有受信 HTTPS 网关，仅添加 `/v1/sync` 路由；内部独立服务监听 Docker 网关地址的 8444 端口，入口仅允许对应容器网段。词库数据库和设备令牌不进入文件传输应用。网关此路由关闭访问日志，服务不记录词条、请求体或认证头。部署时须按实际网桥地址调整 socket 与反向代理，并保留原网关配置备份。


## 迁移到下一台服务器

安装包不写死服务器 IP 或设备令牌。先使用 `sync/backup.py --database /var/lib/phrase-sync/words.sqlite3 --output /私有目录/words.sqlite3` 生成一致的数据库备份，包含词条和设备认证摘要，不含设备明文令牌。它使用 SQLite 在线备份 API，并验证完整性。

在新服务器部署 `sync/server.py`、`common.py`、systemd socket/service 和 HTTPS 路由，将备份恢复为 `/var/lib/phrase-sync/words.sqlite3`，权限限于服务账户。保留数据库后，设备令牌可继续使用；也可逐台重新签发并撤销旧令牌。

确认新 HTTPS 地址有正常有效的系统受信证书，再将各客户端配对 JSON 的 `endpoint` 改为新地址；`systemTrust=true` 不依赖内部自签名证书和其指纹。手机在词库共享设置重新保存 JSON，电脑修改私有 `config.json` 即可，无需重建 APK 或更换词库格式。先验证一轮双向交换，再停用旧服务器 socket，防止客户端长期连接不同副本。使用自签名直连的设备还须更新配对证书及其指纹。
