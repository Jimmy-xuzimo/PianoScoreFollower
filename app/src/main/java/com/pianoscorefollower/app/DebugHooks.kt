package com.pianoscorefollower.app

/** Registered only by debug builds. Release APKs have no exported test entry. */
object DebugHooks {
    var command: ((String, String) -> Unit)? = null
    var scoreCommand: ((String, String) -> Unit)? = null
}
