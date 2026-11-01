# 智能琴谱 · PianoScoreFollower

Android 钢琴练习应用，使用 Google 官方 Jetpack Compose Material 3 控件。

**当前版本：1.0.0** · [下载安装包](https://github.com/Jimmy-xuzimo/PianoScoreFollower/releases/tag/v1.0.0) · [项目介绍](https://jimmy-xuzimo.github.io/PianoScoreFollower/) · [使用说明](docs/USER_GUIDE.md)

## 演奏与曲库

- 导入 MIDI、MusicXML / 压缩 MusicXML，保存原文件、练习位置与多曲目曲库。
- 演奏页按需选曲，不再默认打开《你离开的事实》；该曲仍保留在曲库内置曲目中。
- 独立深色演奏模式：深色背景、白色琴谱、清晰的文字与按钮，可切换浅色。悬浮控制条可隐藏。
- 五线谱／简谱切换。简谱上下两行、大括号连接，不写“左右手”；保留和弦、八度点、时值、休止、反复和必要的变化标记。
- 麦克风或 USB MIDI 跟谱，点按起点；支持逐行跟随、分页、手动浏览后回到跟随位置。
- 音高与节奏评分：按应弹音符等权，显示错音、漏音、多弹及明细。麦克风评分是估计。
- USB MIDI 录制、回放、保存、重命名、删除，导出原始 MIDI 与重新量化的钢琴 MusicXML。
- PDF／照片保留排序、保存及定时滚动；没有图片识谱功能。
- 校音、示范播放、主题和壁纸取色。示范音源按需加载，不阻塞跟谱。

## 跟谱实现与实测边界

麦克风使用 Google Magenta 实时单向 Onsets and Frames 模型，由 LiteRT 在本机执行。
完整音高与起音通过 Parangonar SLT_OLTW 的 Kotlin 适配对齐，结合速度估计和乐谱约束预测。
预测只平滑光标，不生成已弹音符、不替用户得分，也不单独确认翻页。
重复乐句或失配时可能需要更多上下文。

因果推理缓存送入实际后处理与跟谱管线：开发组 **97.04%**、9 曲固定回归组 **97.64%**、
另一 5 曲回归组 **97.72%** 的有效演奏时间定位在正确拍前后半拍内。
这些数据已用于诊断，**不是新盲测或实体麦克风准确率**。
重弹恢复 **16/20 在 3 秒内**，P95 约 3.67 秒，尚未达到原定恢复门槛。
正式发布不表示所有算法目标或实体硬件验收已通过。

详见 [测试报告](docs/TEST_REPORT.md)、[架构](docs/ARCHITECTURE.md)、[复现命令](docs/TESTING.md)。
历史测试保留在 [docs/history](docs/history)，不能与当前引擎结果混用。

## 安装与构建

Android 10+，本地存储与麦克风权限按需申请。模型随包离线提供，APK 较大。
1.0 使用独立正式签名；旧预览或旧版签名不匹配时不能覆盖安装，先导出自己的曲谱、录制再更换安装。
切勿直接卸载含未导出数据的旧版本。

JDK 17、Android SDK Platform 36、Build Tools 36.0.0：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
node --test tools/graph.test.cjs tools/numbered.test.cjs tools/navigation.test.cjs
```

Release 签名从本机忽略的 `keystore.properties` 读取；没有签名配置时构建的是未签名 APK。
Debug 使用独立包名，仅 Debug 包包含音频／MIDI 测试注入入口。

## 开源与隐私

原创代码采用 [BSD 3-Clause](LICENSE)。完整项目、模型、字体、音源来源及各自许可证见
[第三方署名](THIRD_PARTY_NOTICES.md)，应用“设置 → 关于”可离线阅读协议并打开 GitHub 与介绍网页。
音乐作品与测试数据不适用软件 BSD 协议。外部测试录音不随源码或 APK 分发。
数据在设备本地处理；录制保存琴键与踏板事件，不录制麦克风音频。见 [隐私政策](privacy-policy.html)。

独立 [网页版](https://github.com/Jimmy-xuzimo/PianoScoreFollower-Web) 单独维护，本次 Android 重构没有自动同步其算法。
