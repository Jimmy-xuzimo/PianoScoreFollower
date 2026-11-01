# 跟谱引擎重建记录（2026-11-01，尚未验收）

本记录优先于旧版文档对麦克风跟谱算法的描述。当前源码已替换旧算法，
但 preview.5 尚未作为验收完成版本交付。实体平板麦克风表现未验证。

## 现有实现

- 原生 LiteRT 执行 Google Magenta 已发布的实时单向 Onsets and Frames 模型。
  权重来源、SHA256、许可见 `third_party/magenta/`。
- 16 kHz 流式重采样，128 ms 一次推理，仅使用当时已有的 PCM。
  模型帧标签时间与结果可用时间分开，纠正了额外添加的 64 ms 偏移。
- MIDI 与模型检测的完整音高起音共用 Parangonar SLT_OLTW 的 Kotlin 适配。
  不是旧版前方几个音级挑选，也不依赖示范音源解码。
- 对齐位置有惯性和有限速度外推；谱面小节与反复次数由确认事件提供。
- 异常停顿后并行寻找新的音符序列；正常继续演奏保留原路径。
  确认新路径后移交其 DP 状态，避免和弦重放改变对齐历史。
- 未知起点若存在相同重复乐句，继续等待上下文，不凭一个音跳转。

## 当前验证与限制

使用 Matchmaker 数据的完整曲目、因果模型输出缓存，逐帧送入实际
`AudioAnalyzer` 神经模型后处理和 `FollowerEngine`；不向算法提供标注。
这与完整 Android 推理、真实麦克风声学测试是不同层次。

最近一轮开发集定位在正确拍前后半拍内的有效时长占比为 96.97%；
9 曲固定回归集为 97.48%；另一 5 曲回归组为 97.67%。这些组已用于诊断，不能再称盲测集。

20 个带停顿、回跳及部分背景噪声的重弹案例全部最终恢复，
16/20 在 3 秒内恢复；恢复时间 P95 为 3.674 秒。**未达到 95% 在 3 秒内恢复的门槛。**
这些重弹案例也已经用于诊断，需要另加未见案例检验泛化。

本轮单元测试 95 项，0 失败、4 项依赖外部条件的测试跳过；
其中含旧算法基线测试，不能把测试总数当作新算法精度证据。
Debug/Release 构建与 lint 已通过最近一轮检查；lint 为 0 错误、13 警告。
Release 构建产物的生成不代表签名交付或实体设备验收通过。
用户视频没有逐拍人工标注，不能据此宣称用户曲目的定位准确率。

## 用户录音的 Android 完整运行

MuMu Android 15 安装当前 Debug 包，从曲首注入用户视频解码的音频，
使用实际 LiteRT、AudioAnalyzer 和 FollowerEngine，顺序运行 231.410 秒
（含测试前置静音）。不是将离线位置结果直接交给应用。

- 首次光标推进：输入时间 5.387 秒；包含前置静音，非人工标注的识别延迟。
- 光标向后移动次数：0；最后位置 444955 tick。没有逐拍标注，不能断言末位置正确。
- 推理平均 62.76 ms / 128 ms 块，最慢 179.62 ms；最大回放墙钟滞后约 260 ms。
- 7223 帧与离线同输入模型输出对齐，起音阈值判断差异 0；
  音高概率最大差 0.000861、平均差 0.000000265。
- 这证明应用内全长推理可实时运行及输出基本一致，不能证明手机拾音、
  音符转录准确率、逐拍定位准确率或实体 USB 已通过。

复核模型输出：`tools/compare-model-frames.py <Android.bin> <reference.bin>`。

## 复现

1. `tools/probe-magenta.py --resampler linear --export-frames` 顺序推理真实音频。
2. `PIANO_NEURAL_MANIFEST` 指向案例目录清单，运行
   `:app:testDebugUnitTest --tests '*NeuralPipelineReplayTest'`。
3. `tools/report-benchmark.py <manifest> --trace neural-trace.tsv` 统计定位；
   `tools/report-recovery.py <manifest> --trace neural-trace.tsv` 统计重弹恢复。

缓存 NPF1 使用旧的中心时间，回放读取时减 64 ms；NPF2 直接记录帧标签时间。
二者均含独立的结果可用时间。缓存不包含任何测试标注。

## 调研来源

- https://github.com/magenta/magenta/tree/main/magenta/models/onsets_frames_transcription/realtime
- https://github.com/sildater/parangonar
- https://github.com/pymatchmaker/matchmaker
- https://github.com/huispaty/rtt
- https://github.com/charlesneimog/OpenScofo

当前组合是本地测试中可运行且效果较好的方案；没有证据称其为所有设备、
所有曲目上的最好算法。RTT 的当前检查点在用户片段开头表现较弱；
OpenScofo 的旧版本适配尚未充分验证，不据此对其算法排名。
