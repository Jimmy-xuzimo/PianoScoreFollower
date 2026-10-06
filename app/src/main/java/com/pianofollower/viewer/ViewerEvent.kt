package com.pianofollower.viewer

import org.json.JSONObject

sealed interface ViewerEvent {
    data object ViewerReady : ViewerEvent

    data class ScoreLoaded(
        val title: String,
        val artist: String,
        val measureCount: Int,
        val trackNames: List<String>,
    ) : ViewerEvent

    data class ScoreStructure(
        val ticksPerQuarter: Int,
        val bars: List<StructureBar>,
    ) : ViewerEvent

    data class RenderFinished(
        val pageCount: Int,
        val systemCount: Int,
        val measureCount: Int,
    ) : ViewerEvent

    data class PositionChanged(
        val measure: Int,
        val page: Int,
        val pageCount: Int,
        val isLastMeasureOfPage: Boolean,
    ) : ViewerEvent

    /** Page the user is actually looking at, i.e. the current scroll position. */
    data class PageChanged(
        val page: Int,
        val pageCount: Int,
    ) : ViewerEvent

    /**
     * Player lifecycle as reported by alphaTab. [state] is one of `ready`,
     * `soundfontLoaded`, `soundfontFailed`, `playing` or `paused`.
     */
    data class PlayerState(val state: String) : ViewerEvent

    data class Failure(val message: String) : ViewerEvent

    companion object {
        fun parse(raw: String): ViewerEvent? {
            return try {
                val json = JSONObject(raw)
                when (json.optString("type")) {
                    "viewerReady" -> ViewerReady

                    "scoreLoaded" -> ScoreLoaded(
                        title = json.optString("title"),
                        artist = json.optString("artist"),
                        measureCount = json.optInt("masterBars"),
                        trackNames = json.optJSONArray("tracks")?.let { array ->
                            (0 until array.length()).mapNotNull { index ->
                                array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                            }
                        } ?: emptyList(),
                    )

                    "scoreStructure" -> parseStructure(json)

                    "renderFinished" -> RenderFinished(
                        pageCount = json.optInt("pageCount"),
                        systemCount = json.optInt("systemCount"),
                        measureCount = json.optInt("measureCount"),
                    )

                    "positionChanged" -> PositionChanged(
                        measure = json.optInt("measure"),
                        page = json.optInt("page"),
                        pageCount = json.optInt("pageCount"),
                        isLastMeasureOfPage = json.optBoolean("isLastMeasureOfPage"),
                    )

                    "pageChanged" -> PageChanged(
                        page = json.optInt("page"),
                        pageCount = json.optInt("pageCount"),
                    )

                    "playerState" -> PlayerState(json.optString("state"))

                    "error" -> Failure(json.optString("message", "未知错误"))

                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parseStructure(json: JSONObject): ScoreStructure {
            val barsArray = json.optJSONArray("bars")
            val bars = ArrayList<StructureBar>(barsArray?.length() ?: 0)
            if (barsArray != null) {
                for (i in 0 until barsArray.length()) {
                    val barJson = barsArray.optJSONObject(i) ?: continue
                    val eventsArray = barJson.optJSONArray("events")
                    val events = ArrayList<StructureEvent>(eventsArray?.length() ?: 0)
                    if (eventsArray != null) {
                        for (j in 0 until eventsArray.length()) {
                            val eventJson = eventsArray.optJSONObject(j) ?: continue
                            val notesArray = eventJson.optJSONArray("notes") ?: continue
                            val notes = IntArray(notesArray.length()) { index ->
                                notesArray.optInt(index)
                            }
                            events.add(
                                StructureEvent(
                                    tick = eventJson.optLong("tick"),
                                    duration = eventJson.optInt("duration"),
                                    notes = notes,
                                )
                            )
                        }
                    }
                    bars.add(
                        StructureBar(
                            index = barJson.optInt("index"),
                            tempo = barJson.optDouble("tempo", 100.0).toFloat(),
                            events = events,
                        )
                    )
                }
            }
            return ScoreStructure(
                ticksPerQuarter = json.optInt("ticksPerQuarter", 960).coerceAtLeast(1),
                bars = bars,
            )
        }
    }
}

data class StructureBar(
    val index: Int,
    val tempo: Float,
    val events: List<StructureEvent>,
)

data class StructureEvent(
    val tick: Long,
    val duration: Int,
    val notes: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StructureEvent) return false
        return tick == other.tick && duration == other.duration && notes.contentEquals(other.notes)
    }

    override fun hashCode(): Int {
        var result = tick.hashCode()
        result = 31 * result + duration
        result = 31 * result + notes.contentHashCode()
        return result
    }
}
