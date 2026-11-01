# 开源项目与资源署名

智能琴谱原创代码：Copyright (c) 2026 Jimmy-xuzimo，BSD 3-Clause，见 [LICENSE](LICENSE)。
这个协议不覆盖第三方代码、训练模型、字体、音源和音乐作品。

| 项目 | 用途 | 协议与来源 |
| --- | --- | --- |
| Google Jetpack Compose Material 3 / AndroidX | Android 界面、生命周期、WebView | Apache 2.0 · https://github.com/androidx/androidx |
| Kotlin / kotlinx.coroutines | 语言与异步调度 | Apache 2.0 · https://github.com/JetBrains/kotlin · https://github.com/Kotlin/kotlinx.coroutines |
| Google LiteRT 1.4.2 | 本地钢琴模型推理 | Apache 2.0 · https://github.com/google-ai-edge/LiteRT |
| Magenta Onsets and Frames | 实时单向钢琴音符模型 | Apache 2.0 · https://github.com/magenta/magenta |
| Parangonar SLT_OLTW | 音高、速度与音符序列在线对齐的 Kotlin 适配 | Apache 2.0 · https://github.com/sildater/parangonar |
| alphaTab 1.8.4 | MusicXML / MIDI 排版、播放时间映射、合成 | MPL 2.0 · https://github.com/CoderLine/alphaTab |
| TinySoundFont / SFZero | alphaTab 内含合成组件 | MIT · https://github.com/schellingb/TinySoundFont · https://github.com/stevefolta/SFZero |
| Haxe Standard Library / SharpZipLib | alphaTab 内含 XML / 压缩组件 | MIT · https://github.com/HaxeFoundation/haxe · https://github.com/icsharpcode/SharpZipLib |
| NVorbis / libvorbis | alphaTab 内含音频解码组件 | MIT / BSD 3-Clause · https://github.com/NVorbis/NVorbis · https://github.com/xiph/vorbis |
| Bravura | 音乐符号字体 | SIL OFL 1.1 · https://github.com/steinbergmedia/bravura |
| FreePats Upright Piano KW (small) | 示范播放钢琴音源 piano.sf2 | CC0 1.0 · https://freepats.zenvoid.org/Piano/acoustic-grand-piano.html |

alphaTab 原始文件及其内嵌组件的许可证、版权声明保留在 JS 文件头中；MPL 完整文本、
字体 OFL、音源 CC0、Apache 2.0 和项目 BSD 完整文本随 APK 打包，
可在“设置 → 关于”离线阅读。源码仓库提供应用改动及对应 alphaTab 文件。

FreePats Upright Piano KW 采样由 Gonzalo 与 Roberto 于 2017 年录制，
由 Roberto 编辑处理。感谢 Inma Martínez de Miguel 与 Claudia Marín Martínez。
本项目未使用旧版未被播放器引用的 SONiVOX 音源。

Magenta 模型的官方地址、哈希与流式适配说明见 third_party/magenta/；
Parangonar 的固定提交、移植文件和修改说明见 third_party/parangonar/。
这些上游项目不代表对本应用的认可或效果保证。

## 仅用于测试或研究

Matchmaker 的 Arzt / LSE 适配仅位于测试源集，Apache 2.0，见 third_party/matchmaker/。
Matchmaker benchmark 的 ASAP、Batik、Vienna 外部数据仅在本机研究使用，
不放入仓库或 APK，其许可见原数据仓库：https://github.com/pymatchmaker/matchmaker-benchmark-data 。
用户提供的视频和录音不随应用或源代码分发。

## 内置曲谱

《The Truth That You Leave／你离开的事实》为 Pianoboy 高至豪的音乐作品。
曲库保留旧版随附的 MIDI 示例，但不作为默认打开曲目。
作品与曲谱版权归各自权利人；本项目 BSD 开源协议不授予这些音乐作品的权利。
其余内置练习同样应区分音乐作品与应用软件的协议。

项目：https://github.com/Jimmy-xuzimo/PianoScoreFollower
介绍：https://jimmy-xuzimo.github.io/PianoScoreFollower/
