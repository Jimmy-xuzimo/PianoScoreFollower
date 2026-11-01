# 1.0 架构与数据流

Compose Material 3 → 曲库 / 演奏 / 录制 / 校音 / 设置。
演奏页有独立可持久化的深浅模式，网页乐谱始终白纸黑字。

```text
MIDI / MusicXML → ScoreLibrary → alphaTab / graph.js → ScoreGraph
AudioRecord → StreamingResampler → Magenta / LiteRT → 音高与起音 ┐
Android MidiManager → MIDI 分包、力度、踏板解析 ───────────────┤
                         SymbolicScoreFollower / SLT_OLTW ←───┘
                              ↓                ↓
                       FollowPosition       AssessmentEngine
                              ↓
                  确认位置、置信度、浏览状态 → 光标与翻页
```

`tick` 为包含反复的播放位置，`notationTick`、`measure` 为谱面位置，`occurrence` 为反复次数。
图谱从 alphaTab tickCache / MidiTickLookup 获取真实播放路径；默认选钢琴双谱表。

`PerformanceObservation` 区分起音与踏板保持的发声音符，时间采用单调时钟。
音频采集、模型分析、UI 分别调度；有界队列溢出会停止并反馈。
模型 16 kHz 输入，每 128 ms 处理一块，只使用已到达 PCM；帧标签时间和结果可用时间分开。

`TempoPitchTracker` 移植 Parangonar SLT_OLTW，保留上游数值运算的对照测试。
`NoteStartLocator` 并行保留多个起点；重复片段不明确时等待上下文。
异常停顿后保留本地路径并启动重新定位，确认后交接已有 DP 状态。
`ScoreTempoPrediction` 结合对齐速度与近期确认音符间隔的中位数，抑制和弦与离群间隔干扰。
光标最多向下一未确认事件之后半拍外推，翻页仍使用确认事件；静音冻结、低置信度停翻。
`anticipatedNotes` 是未来两个谱面事件的期望音，不是演奏观测，不能进入评分或录制。

原始录制独立保存；量化只影响派生 MusicXML。原始文件、练习位置和图片页序在私有目录中保存。
旧数据迁移保留恢复副本，旧版丢失的原始 MIDI 不伪装恢复。
网页桥携带乐谱与布局版本，旧会话回调、旧模型结果和过期布局不会覆盖新位置。

声音跟谱不等待音源：示范播放才加载 piano.sf2。校音继续使用独立轻量音高分析。
麦克风跟谱、校音、示范播放互斥。Debug 注入使用正式输入的同一核心管线，Release 不注册测试接收器。

第三方实现、固定版本与许可证见根目录 THIRD_PARTY_NOTICES.md 和 third_party/。
