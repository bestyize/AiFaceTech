package com.aifacemate.face.voice.realtime.internal

import com.aifacemate.face.voice.realtime.RealtimeAudio
import com.aifacemate.face.voice.realtime.RealtimeEvent
import com.aifacemate.face.voice.realtime.RealtimeSession

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun openSocket(url: String, headers: Map<String, String>): Pair<HttpClient, WebSocketSession> {
    val client = HttpClient { install(WebSockets) }
    try {
        val socket = withTimeout(15_000) {
            client.webSocketSession {
                url(url)
                headers.forEach { (name, value) -> header(name, value) }
            }
        }
        return client to socket
    } catch (error: Throwable) {
        client.close()
        throw error
    }
}

internal abstract class SocketSession(
    private val client: HttpClient,
    private val socket: WebSocketSession,
) : RealtimeSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val output = Channel<RealtimeEvent>(64)
    private val sendLock = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private var stopped = false
    override val events: Flow<RealtimeEvent> = output.receiveAsFlow()

    protected suspend fun send(frame: Frame) {
        sendLock.lock()
        try { socket.send(frame) } finally { sendLock.unlock() }
    }
    protected suspend fun emit(event: RealtimeEvent) { output.send(event) }
    protected suspend fun markReady() {
        if (ready.complete(Unit)) emit(RealtimeEvent.Ready)
    }

    protected suspend fun fail(message: String) {
        ready.completeExceptionally(IllegalStateException(message))
        emit(RealtimeEvent.Failure(message))
    }

    protected abstract suspend fun open()
    protected abstract suspend fun receive(frame: Frame)
    protected abstract fun audioFrame(bytes: ByteArray): Frame
    protected open suspend fun cancelOutput() = Unit
    protected open suspend fun finish() = Unit

    internal fun start() {
        scope.launch {
            try {
                open()
                for (frame in socket.incoming) receive(frame)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                fail(error.message ?: "Realtime connection failed")
            } finally {
                ready.completeExceptionally(IllegalStateException("Realtime connection closed"))
                output.trySend(RealtimeEvent.Closed)
                output.close()
                client.close()
            }
        }
    }

    internal suspend fun awaitReady() { withTimeout(15_000) { ready.await() } }

    override suspend fun sendAudio(audio: RealtimeAudio) {
        require(audio.bytes.isNotEmpty() && audio.bytes.size % 2 == 0) { "Expected nonempty PCM16 samples" }
        ready.await()
        check(!stopped) { "Session is closed" }
        send(audioFrame(audio.bytes))
    }

    override suspend fun interrupt() {
        if (stopped) return
        emit(RealtimeEvent.InterruptPlayback)
        if (ready.isCompleted && !ready.isCancelled) cancelOutput()
    }

    override suspend fun close() {
        if (stopped) return
        stopped = true
        try {
            if (ready.isCompleted && !ready.isCancelled) withTimeoutOrNull(2_000) { finish() }
        } finally {
            socket.close(CloseReason(CloseReason.Codes.NORMAL, "Finished"))
            client.close()
        }
    }
}
