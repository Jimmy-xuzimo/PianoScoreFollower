package com.pianoscorefollower.app.midi

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.HandlerThread
import com.pianoscorefollower.app.performance.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MidiEndpoint(val id: Int, val port: Int, val name: String, val identifier: String = name)
data class MidiInputState(val endpoints: List<MidiEndpoint> = emptyList(), val connected: MidiEndpoint? = null,
    val pressed: List<Int> = emptyList(), val message: String? = null)

class MidiInput(context: Context) : PerformanceInput {
    private val preferences = context.getSharedPreferences("midi_input",Context.MODE_PRIVATE)
    private val manager = context.getSystemService(MidiManager::class.java)
    private val thread = HandlerThread("piano-midi").apply { start() }
    private val handler = Handler(thread.looper)
    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var connectionVersion = 0
    private var openingId: Int? = null
    private var selected: Pair<Int,Int>? = null
    private val keyboard = MidiKeyboard()
    private var pending = false
    private val attacks = FloatArray(128)
    private var pendingAt = 0L
    private var scheduled = false
    private val _state = MutableStateFlow(MidiInputState())
    val state = _state.asStateFlow()
    override var listener: ((PerformanceObservation) -> Unit)? = null
    var messageListener: ((MidiMessage) -> Unit)? = null
    var disconnected: (() -> Unit)? = null
    private val decoder = MidiStreamDecoder { bytes, time ->
        messageListener?.invoke(MidiMessage(time, bytes.copyOf()))
        if (keyboard.accept(bytes)) {
            if (!pending) pendingAt = time / 1000
            pending = true
            attacks[bytes[1].toInt() and 127] = 1f
        }
        _state.value = _state.value.copy(pressed = keyboard.pressed())
        if (!scheduled) {
            scheduled = true
            handler.postDelayed({
                scheduled = false
                val values = keyboard.pitches()
                for (n in values.indices) values[n] = maxOf(values[n], attacks[n])
                val newKeys=if(pending) attacks.copyOf() else null
                attacks.fill(0f)
                listener?.invoke(PerformanceObservation(if (pending) pendingAt else System.nanoTime()/1_000_000,
                    values, pending, values.any { it > 0 }, true, attacks=newKeys))
                pending = false
            }, 35)
        }
    }
    private val receiver = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val owned = data.copyOfRange(offset, offset+count)
            val time = (if (timestamp > 0) timestamp else System.nanoTime()) / 1000
            handler.post { decoder.accept(owned, timeUs = time) }
        }
    }
    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) = refresh()
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (_state.value.connected?.id == info.id || openingId == info.id) { disconnect(); disconnected?.invoke() }
            refresh()
        }
    }
    init {
        @Suppress("DEPRECATION")
        manager?.registerDeviceCallback(callback, handler)
        handler.post { refresh() }
    }
    @Suppress("DEPRECATION")
    private fun refresh() {
        val endpoints = manager?.devices.orEmpty().filter { it.type == MidiDeviceInfo.TYPE_USB || it.type == MidiDeviceInfo.TYPE_VIRTUAL }
            .flatMap { d -> d.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.map { p ->
                MidiEndpoint(d.id, p.portNumber, (d.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI 键盘") +
                    if (p.name.isNullOrBlank()) "" else " · ${p.name}",
                    listOf(d.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER),
                        d.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT), d.properties.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER),
                        d.properties.getString(MidiDeviceInfo.PROPERTY_NAME),p.portNumber.toString()).joinToString("|"))
            } }
        _state.value = _state.value.copy(endpoints = endpoints)
        if (openingId != null) return
        if (device == null && endpoints.size == 1) connect(endpoints.first())
        else if (device == null) endpoints.firstOrNull { selected == (it.id to it.port) || it.identifier == preferences.getString("preferred",null) }?.let(::connect)
    }
    fun select(endpoint: MidiEndpoint) { handler.post { connect(endpoint) } }
    @Suppress("DEPRECATION")
    private fun connect(endpoint: MidiEndpoint) {
        disconnect()
        selected = endpoint.id to endpoint.port
        preferences.edit().putString("preferred",endpoint.identifier).apply()
        val version = connectionVersion
        val info = manager?.devices?.firstOrNull { it.id == endpoint.id } ?: return
        openingId = endpoint.id
        manager.openDevice(info, { opened ->
            if (version != connectionVersion) { opened?.close(); return@openDevice }
            openingId = null
            if (opened == null) { _state.value = _state.value.copy(message = "MIDI 设备连接失败"); return@openDevice }
            val output = opened.openOutputPort(endpoint.port)
            if (output == null) { opened.close(); _state.value = _state.value.copy(message = "MIDI 端口被占用"); return@openDevice }
            device = opened; port = output; output.connect(receiver)
            _state.value = _state.value.copy(connected = endpoint, message = null)
        }, handler)
    }
    private fun disconnect() {
        connectionVersion++; openingId = null; port?.close(); device?.close(); port = null; device = null
        decoder.reset(); keyboard.clear(); pending = false; attacks.fill(0f)
        _state.value = _state.value.copy(connected = null, pressed = emptyList())
    }
    fun simulateDisconnect() {
        check(com.pianoscorefollower.app.BuildConfig.DEBUG)
        handler.post { disconnect();disconnected?.invoke() }
    }
    fun inject(bytes: ByteArray) {
        check(com.pianoscorefollower.app.BuildConfig.DEBUG)
        handler.post {
            _state.value = _state.value.copy(connected = MidiEndpoint(-1,0,"测试 MIDI"))
            decoder.accept(bytes, timeUs = System.nanoTime()/1000)
        }
    }
    override fun close() {
        manager?.unregisterDeviceCallback(callback)
        handler.post { disconnect(); thread.quitSafely() }
    }
}
