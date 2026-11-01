package com.pianoscorefollower.app.score

import android.util.AtomicFile
import java.io.File

internal fun atomicWrite(file: File, data: ByteArray) {
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file)
    val out = atomic.startWrite()
    try { out.write(data); atomic.finishWrite(out) }
    catch (e: Exception) { atomic.failWrite(out); throw e }
}
