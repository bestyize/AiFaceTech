package com.aifacemate.face.network

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*
import kotlinx.coroutines.*

class StreamingHttpTest {
    @Test fun readsEventsBeforeEofAndCancelsAWaitingSocket() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val release = CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().bufferedReader()
                while (input.readLine()?.isNotEmpty() == true) { }
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                // Split a Chinese UTF-8 character across writes, and keep the response open.
                "data: 你好\r\n\r\n".toByteArray(Charsets.UTF_8).forEach { output.write(it.toInt()); output.flush() }
                release.await(8, TimeUnit.SECONDS)
            }
        }
        val first = CompletableDeferred<String>()
        val request = launch(Dispatchers.IO) {
            NetworkClient().postEventStream("http://127.0.0.1:${server.localPort}", emptyMap(), "") {
                first.complete(it); true
            }
        }
        var cancelledPromptly = false
        try {
            assertEquals("你好", withTimeout(5000) { first.await() })
            cancelledPromptly = withTimeoutOrNull(2000) { request.cancelAndJoin(); true } == true
        } finally {
            release.countDown(); server.close(); worker.join(2000)
            request.cancelAndJoin()
        }
        assertTrue(cancelledPromptly, "Stopping speech must close a pending HTTP read promptly")
    }
}
