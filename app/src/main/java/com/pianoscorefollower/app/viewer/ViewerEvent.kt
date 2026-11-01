package com.pianoscorefollower.app.viewer

import org.json.JSONObject

sealed interface ViewerEvent {
    data class PlaybackPosition(val tick: Long) : ViewerEvent
    data class SeekRequested(val tick: Long) : ViewerEvent
    data object UserBrowsing : ViewerEvent
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
        val selectedTracks: List<Int> = emptyList(),
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
                    "playbackPosition" -> PlaybackPosition(json.optLong("tick"))
                    "seekRequested" -> SeekRequested(json.optLong("tick"))
                    "userBrowsing" -> UserBrowsing

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
                                    sounding = eventJson.optJSONArray("sounding")?.let { a -> IntArray(a.length()) { a.optInt(it) } } ?: notes,
                                    notationTick = eventJson.optLong("notationTick", eventJson.optLong("tick")),
                                    tempo = eventJson.optDouble("tempo", barJson.optDouble("tempo",120.0)).toFloat(),
                                    voices = eventJson.optJSONArray("voices")?.let { array ->
                                        (0 until array.length()).map { k -> val v=array.getJSONObject(k)
                                            com.pianoscorefollower.app.follower.ScoreVoiceNote(v.getInt("pitch"),v.getInt("track"),
                                                v.getInt("staff"),v.getInt("voice"),v.optBoolean("tied"),v.getLong("start"),v.getLong("end"))
                                        }
                                    }.orEmpty(),
                                )
                            )
                        }
                    }
                    bars.add(
                        StructureBar(
                            index = barJson.optInt("index"),
                            tempo = barJson.optDouble("tempo", 100.0).toFloat(),
                            events = events,
                        occurrence = barJson.optInt("occurrence"),
                        start = barJson.optLong("start"),
                        end = barJson.optLong("end"),
                        )
                    )
                }
            }
            return ScoreStructure(
                ticksPerQuarter = json.optInt("ticksPerQuarter", 960).coerceAtLeast(1),
                bars = bars,
                selectedTracks = json.optJSONArray("selectedTracks")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty(),
            )
        }
    }
}

data class StructureBar(
    val index: Int,
    val tempo: Float,
    val events: List<StructureEvent>,
    val occurrence: Int = 0,
    val start: Long = 0,
    val end: Long = 0,
)

data class StructureEvent(
    val tick: Long,
    val duration: Int,
    val notes: IntArray,
    val sounding: IntArray = notes,
    val notationTick: Long = tick,
    val tempo: Float = 120f,
    val voices: List<com.pianoscorefollower.app.follower.ScoreVoiceNote> = emptyList(),
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
