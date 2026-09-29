package com.aifacemate.face.tools

import com.aifacemate.face.chat.*
import com.aifacemate.face.network.NetworkClient
import com.aifacemate.face.voice.streamSpokenReply
import com.aifacemate.face.voice.tts.client.*
import com.aifacemate.face.voice.tts.model.*
import java.io.File
import java.util.Properties
import kotlinx.coroutines.*

/** Manual measurement only. Never prints credentials, model answers or PCM; does not open audio devices. */
fun main(args: Array<String>) = runBlocking {
    val text = "你好，这是流式语音测试。第一句话可以先说，后面的内容继续合成。"
    if (PiperTts.prepareRuntime() && PiperTts.installed()) {
        val start = System.nanoTime()
        var firstMs = -1L
        var chunks = 0
        var samples = 0L
        var rate = 0
        PiperTts.stream(text) {
            if (firstMs < 0) firstMs = (System.nanoTime() - start) / 1_000_000
            chunks++; samples += it.pcm16.size / 2; rate = it.sampleRate
        }
        println("Piper: firstPCM=${firstMs}ms complete=${(System.nanoTime() - start) / 1_000_000}ms chunks=$chunks audioMs=${samples * 1000 / rate}")
        val callback = CompletableDeferred<Unit>()
        val work = launch(Dispatchers.Default) { PiperTts.stream(text.repeat(20)) { callback.complete(Unit); awaitCancellation() } }
        withTimeout(30_000) { callback.await() }
        val stop = System.nanoTime()
        withTimeout(5000) { work.cancelAndJoin() }
        println("Piper: cancellation=${(System.nanoTime() - stop) / 1_000_000}ms after first PCM callback")
    } else println("SKIP Piper: no installed local model/runtime")

    if (args.getOrNull(1)?.toBoolean() != true) return@runBlocking
    val config = Properties().apply { File(args[0]).inputStream().use(::load) }
    val key = config.getProperty("aifacemate.dev.mimoApiKey").orEmpty()
    val base = config.getProperty("aifacemate.dev.mimoBaseUrl", "https://api.xiaomimimo.com/v1")
    if (key.isBlank()) { println("SKIP MiMo: no development key"); return@runBlocking }
    val client = TtsClient(NetworkClient())
    val start = System.nanoTime()
    var firstMs = -1L
    var chunks = 0
    withTimeout(90_000) {
        client.stream(TtsRequest(apiKey = key, baseUrl = base, text = text, voice = "Chloe")) {
            if (firstMs < 0) firstMs = (System.nanoTime() - start) / 1_000_000
            chunks++
        }
    }
    println("MiMo: firstPCM=${firstMs}ms complete=${(System.nanoTime() - start) / 1_000_000}ms chunks=$chunks")
    val brainKey = config.getProperty("aifacemate.dev.apiKey").orEmpty()
    val brainBase = config.getProperty("aifacemate.dev.baseUrl").orEmpty()
    val model = config.getProperty("aifacemate.dev.model").orEmpty()
    if (brainKey.isBlank() || brainBase.isBlank() || model.isBlank()) { println("SKIP brain: no development configuration"); return@runBlocking }
    val pipelineStart = System.nanoTime()
    var firstTextMs = -1L
    var firstAudioMs = -1L
    var count = 0
    val result = withTimeout(120_000) {
        streamSpokenReply<SpeechAudioChunk>(
            generate = { delta ->
                val response = ChatClient().stream(ChatConfig(brainBase, brainKey, model), listOf(ChatMessage("user", "请用两句简短的中文介绍你能帮助我做什么，不要使用列表。")), delta)
                check(response is ChatResult.Success) { "Brain streaming request failed" }
            },
            onText = { if (firstTextMs < 0) firstTextMs = (System.nanoTime() - pipelineStart) / 1_000_000 },
            synthesize = { sentence, emit -> client.stream(TtsRequest(apiKey = key, baseUrl = base, text = sentence, voice = "Chloe"), emit) },
            play = { if (firstAudioMs < 0) firstAudioMs = (System.nanoTime() - pipelineStart) / 1_000_000; count++ },
        )
    }
    check(result.speechFailure == null) { "Speech streaming request failed" }
    println("Brain+MiMo: firstText=${firstTextMs}ms firstPCM=${firstAudioMs}ms complete=${(System.nanoTime() - pipelineStart) / 1_000_000}ms chunks=$count chars=${result.text.length}")
}
