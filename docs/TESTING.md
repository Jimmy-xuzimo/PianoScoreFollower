# 可重复测试

## 构建与确定性检查

JDK 17、SDK Platform 36、Build Tools 36.0.0：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
node --test tools/graph.test.cjs tools/numbered.test.cjs tools/navigation.test.cjs
python tools/test_annotation_truth.py
python tools/check-midi-roundtrip.py
```

Release 无签名配置时为未签名产物。模拟器测试安装 Debug APK。

单元测试覆盖连续同音、长音、静音、八度、错音、起点选择、反复次数、全曲恢复、页面移动门槛、MIDI 分包 / 运行状态 / 踏板、长录制、转换后的声部与速度变化。Node 检查实际 alphaTab 模型与 Android 使用的同一导出器。

## 公开音频评估

使用 Python 环境安装 `numpy scipy soundfile mido`，源码目录外保存数据：

```powershell
python tools/prepare-benchmark.py --data ../benchmark-data --split development --seconds 0
python tools/prepare-benchmark.py --data ../benchmark-data --split heldout --seconds 0

$env:PIANO_BENCHMARK_MANIFEST = (Resolve-Path ../benchmark-data/development-full-manifest.txt).Path
.\gradlew.bat :app:testDebugUnitTest --tests '*AudioBenchmarkTest'
python tools/report-benchmark.py ../benchmark-data/development-full-manifest.txt

$env:PIANO_BENCHMARK_MANIFEST = (Resolve-Path ../benchmark-data/heldout-full-manifest.txt).Path
.\gradlew.bat :app:testDebugUnitTest --tests '*AudioBenchmarkTest'
python tools/report-benchmark.py ../benchmark-data/heldout-full-manifest.txt
Remove-Item Env:PIANO_BENCHMARK_MANIFEST
```

测试任务将清单路径纳入输入，切换清单会重新运行；未设置清单时跳过音频语料测试。手动修改语料但路径不变时，运行 `--rerun-tasks`。

固定数据源版本为 `7ea5babf039a0c009050646c90ea2bfe925653f6`。每组按曲谱文件区分开发与保留部分，各取最前两个演奏；少于两个时取现有文件。当前共 9 个开发录音、9 个保留录音，使用完整文件，另加 3 秒静音预热。默认 `--seconds 60` 可运行较短的快速回归，`--seconds 0` 为本轮完整评估。按作品 / 乐章分离，不按同一乐章的不同演奏混分。

音频解码为 44.1 kHz 单声道，按 1024 样本顺序送入生产用 `AudioAnalyzer` 和 `OnlineAligner`。不得离线预对齐输入或读取未来音频来定位；标注只在计算误差时使用。MusicXML 经同一 `graph.js` 生成目标。

`.match` 标注优先通过原谱音符 ID 和反复访问次数映射到实际播放 tick；缺少原始 ID 时使用小节标签与分数节拍。处理从 0 / 1 开始的小节编号差异；测试脚本包含反复后标注小节已展开的回归案例。汇总包含不确定与失配帧，不会只统计高置信度帧。半拍指标按四分音符计量，以首尾有效标注锚点之间的演奏时间统计；不包含录音前后无标注的空白。报告给出完整文件回归结果，不等同于公开排行榜的成绩。

保留集首次评估后曾进行通用算法修复并复测，因此后续结果应理解为固定回归集结果。要声称新的独立泛化成绩，需要另取完全未见曲目。

数据在源码目录之外，应用和源码包不带录音。请保留下载的 `LICENSE.md`、`LICENSING.md` 和来源信息；各原数据集采用独立的非商业许可。`report-benchmark.py <清单> --require-target` 在总体小于 95% 时返回失败，并输出包含核心源码 SHA-256 的汇总 JSON。

## MuMu 与 ADB

```powershell
adb connect 127.0.0.1:<模拟器端口>
adb install -r app/build/outputs/apk/debug/app-debug.apk
python tools/mumu-smoke.py --adb <adb.exe完整路径> --serial 127.0.0.1:<端口> dump
```

`mumu-smoke.py` 可按可访问文字点击、截图，或向 Debug 接收器发送测试命令。接收器只存在于 Debug 清单，要求系统 DUMP 权限。部分 MuMu Android 15 的 uiautomator 在完成导出后退出崩溃；脚本读取本次实际生成的唯一文件，不复用旧界面快照。

手动检查清单：

- 导入 MIDI、XML、压缩 MXL、PDF 和图片；损坏文件应反馈错误。
- MIDI 模式连续演奏跨页，核对音乐位置、网页光标和显示页。
- 手动翻页后不被拉回，点击恢复跟随；横竖屏保持音乐位置。
- 播放与跟谱互斥；音源按需加载、失败后重试。
- 拒绝 / 允许麦克风权限；切后台正确停止活动。
- MIDI 注入后录制、回放、导出、重命名与删除；重启恢复数据。
- PDF 页序、暂停、滚动时长与索引恢复。
- 迁移后原 `score/current.bin` / `current.meta` 仍在。

实体手机收音、实际 USB 热插拔和电钢琴兼容性必须单独测试，不能由上述步骤替代。

## 构造真实音频恢复

完成完整开发集准备后：

```powershell
python tools/prepare-recovery.py --data ../benchmark-data
$env:PIANO_BENCHMARK_MANIFEST = (Resolve-Path ../benchmark-data/recovery-manifest.txt).Path
.\gradlew.bat :app:testDebugUnitTest --tests '*AudioBenchmarkTest'
python tools/report-recovery.py ../benchmark-data/recovery-manifest.txt --require-target
Remove-Item Env:PIANO_BENCHMARK_MANIFEST
```

20 个用例仅取开发录音，固定随机种子；先播放，再停顿一秒并跳转，部分用例叠加 20 dB SNR 白噪声。要求连续约 0.3 秒误差 ≤ 半拍且置信度 ≥ 0.5；在 3 秒内达成的比例应至少 95%，否则命令返回失败。


## 本地用户音频（没有真值标签）

准备目录放在源码之外。下列 `$videoPath`、`$kotlinJar`、`$caseRoot` 为调用方选择的本机路径；不把本机 JDK / SDK 路径写进项目配置。`$kotlinJar` 指 Gradle 缓存中的 kotlin-stdlib 1.9.24 JAR。JAVA_HOME 为 JDK17，ffmpeg / node 已在 PATH。

```powershell
.\gradlew.bat :app:compileDebugKotlin
New-Item -ItemType Directory -Force -Path "$caseRoot/tools"
$taskClassPath="app/build/tmp/kotlin-classes/debug;$kotlinJar"
& "$env:JAVA_HOME/bin/javac" -encoding UTF-8 -d "$caseRoot/tools" -cp $taskClassPath tools/ConvertScore.java
& "$env:JAVA_HOME/bin/java" -cp "$caseRoot/tools;$taskClassPath" ConvertScore app/src/main/assets/samples/the_truth_that_you_leave.mid "$caseRoot/score.musicxml"
node tools/export-graph.cjs "$caseRoot/score.musicxml" "$caseRoot/graph.tsv"
ffmpeg -y -i "$videoPath" -vn -af adelay=3000:all=1 -ac 1 -ar 44100 -f f32le "$caseRoot/audio.f32"
[IO.File]::WriteAllText("$caseRoot/manifest.txt",$caseRoot)
$env:PIANO_BENCHMARK_MANIFEST="$caseRoot/manifest.txt"
.\gradlew.bat :app:testDebugUnitTest --tests '*AudioBenchmarkTest'
python tools/report-user-audio.py "$caseRoot" --video "$videoPath" --output "$caseRoot/summary.json"
Remove-Item Env:PIANO_BENCHMARK_MANIFEST
```

用户音频没有 truth.tsv，不报告位置准确率。`report-user-audio.py` 只输出位置回退、停留、估计位置和可选 Android 页面采样诊断。`report-continuity.py` 对正常录音比较 tick 变化；这种变化包含合法节奏校正，不能作为错误翻页次数。

## 1.0 神经模型管线与预测回归

`tools/probe-magenta.py <model.tflite> <audio.f32> <notes.tsv> --seconds <duration> --resampler linear --export-frames`
逐块执行已发布的模型。音频为 44.1 kHz 单声道 f32；程序按实时顺序转成 16 kHz。
NPF2 缓存保留模型帧标签与可用时刻，不包含真值。

设置 `PIANO_NEURAL_MANIFEST` 为案例目录清单，运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*NeuralPipelineReplayTest'
python tools/report-benchmark.py <manifest.txt> --trace neural-trace.tsv
python tools/report-recovery.py <recovery-manifest.txt> --trace neural-trace.tsv
```

每个案例需 graph.tsv、audio.f32、magenta-linear.notes.frames.bin；评估器额外读取 truth.tsv。
NPF1 旧缓存的标签减去 64 ms 后读取；NPF2 不作时间修改。
原始模型输出的 Android 数值一致性用 `tools/compare-model-frames.py` 检查。

网页检查需安装 Playwright 并提供本机 Chrome：
`PSF_PLAYWRIGHT` 指向可解析的 playwright 模块路径，运行
`node tools/verify-website.cjs http://localhost:8765 <截图目录>`。
该检查覆盖中英、深浅与三种视口；网页无需打包。

正式签名密钥和口令仅在本机忽略文件中保存，不能提交或打包源码。
1.0 首次签名与旧版本不匹配时，不要通过卸载来跳过数据备份。
