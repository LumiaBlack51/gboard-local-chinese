# 隐私设计

中文和语音均使用安装包内的数据，在本机处理。两个正式 APK 不请求 INTERNET，应用备份关闭；Rime 学习数据在应用私有 `no_backup/local-rime/user`。

编辑框带 `IME_FLAG_NO_PERSONALIZED_LEARNING`，或为密码类型时，中文使用 `local_pinyin_private`，其 `enable_user_dict` 为 false。单独设置 Rime 的 incognito 选项不足以阻止学习，因此使用独立方案。测试确认该模式不改变普通学习词库。手工配置的 Android 系统词典与普通学习词库不同，其备份受手机系统设置管理。

语音通过指定 `local.gboard.voice/.OfflineRecognitionService` 使用本地 Vosk；不调用系统默认识别服务。服务核验调用 UID 的签名，两个应用需要同一密钥签名。音频不保存，不写用户语音记录。结束、取消、键盘隐藏和编辑框变化均清理或丢弃会话结果。

调试版的合成音频入口仅接受拥有 DUMP 权限的调用，并限制到自建测试编辑框。正式版关闭 debuggable，不注册此入口；语音 APK 不包含合成测试音频。仓库包含诊断源码，以便复核，但没有手机截图、ADB 日志、设备序列号、词库数据库、密钥或访问令牌。

原 Gboard 为闭源软件，保留其前端和英文逻辑。这里的无网络权限检查和新增代码的本地处理验证，并不等于对全部原 Gboard 代码完成独立隐私审计。
