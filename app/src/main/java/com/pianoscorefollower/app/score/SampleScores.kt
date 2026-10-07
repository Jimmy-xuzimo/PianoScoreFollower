package com.pianoscorefollower.app.score

/**
 * Built-in scores shipped in `assets/samples` so the renderer and follower can be
 * exercised without going through the system file picker.
 */
data class SampleScore(
    val assetPath: String,
    val title: String,
    val subtitle: String,
)

val BuiltInSamples: List<SampleScore> = listOf(
    SampleScore(
        assetPath = "samples/the_truth_that_you_leave.mid",
        title = "The Truth That You Leave（你离开的事实）",
        subtitle = "Pianoboy 高至豪 · 116 小节 · 双手完整曲目",
    ),
    SampleScore(
        assetPath = "samples/c_major_study.musicxml",
        title = "C 大调练习曲（32 小节）",
        subtitle = "MusicXML · 多页排版与自动翻页测试",
    ),
    SampleScore(
        assetPath = "samples/c_major_scale.mid",
        title = "C 大调音阶",
        subtitle = "单声部 · 基础跟谱测试",
    ),
    SampleScore(
        assetPath = "samples/minuet_in_c.mid",
        title = "C 大调小步舞曲",
        subtitle = "双手声部 · 分页测试",
    ),
    SampleScore(
        assetPath = "samples/chords_study.mid",
        title = "和弦练习",
        subtitle = "密集和弦 · 复音识别测试",
    ),
    SampleScore(
        assetPath = "samples/twinkle_piano.musicxml",
        title = "小星星（MusicXML）",
        subtitle = "MusicXML 原生导入 · 双手谱表",
    ),
)
