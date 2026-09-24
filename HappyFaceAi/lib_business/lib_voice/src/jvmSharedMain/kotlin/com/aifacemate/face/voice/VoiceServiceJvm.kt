package com.aifacemate.face.voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.*
actual fun createVoiceService(): VoiceService = DesktopVoiceService()
private class DesktopVoiceService : VoiceService {
 private var recording: TargetDataLine? = null; private var playback: SourceDataLine? = null; @Volatile private var stopRequested = false
 override suspend fun record(maxDurationMs: Long): VoiceCaptureResult = withContext(Dispatchers.IO) {
  stopRequested=false; val format=AudioFormat(16_000f,16,1,true,false); val line=runCatching { AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java,format)) as TargetDataLine }.getOrElse { return@withContext VoiceCaptureResult.Failure("麦克风不可用") }; recording=line; val out=ByteArrayOutputStream(); val end=System.currentTimeMillis()+maxDurationMs
  try { line.open(format); line.start(); val buf=ByteArray(2048); while(System.currentTimeMillis()<end && !stopRequested){val n=line.read(buf,0,buf.size);if(n>0)out.write(buf,0,n)}; VoiceCaptureResult.Success(VoiceClip(wav(out.toByteArray()))) } catch(e:Exception){VoiceCaptureResult.Failure("录音失败：${e.message}")} finally {line.stop();line.close();recording=null}
 }
 override suspend fun play(clip: VoiceClip): VoicePlaybackResult = withContext(Dispatchers.IO) { try { val stream=AudioSystem.getAudioInputStream(ByteArrayInputStream(clip.bytes)); val line=AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java,stream.format)) as SourceDataLine; playback=line; line.open(stream.format);line.start();val buf=ByteArray(4096);while(true){val n=stream.read(buf);if(n<0)break;line.write(buf,0,n)};line.drain();line.stop();line.close();VoicePlaybackResult.Complete }catch(e:Exception){VoicePlaybackResult.Failure("播放失败：${e.message}")} }
 override fun stop(){stopRequested=true;recording?.stop();playback?.stop()}
 private fun wav(pcm:ByteArray):ByteArray{val o=ByteArrayOutputStream();fun le(v:Int){o.write(v and 255);o.write(v shr 8 and 255);o.write(v shr 16 and 255);o.write(v shr 24 and 255)};o.write("RIFF".toByteArray());le(36+pcm.size);o.write("WAVEfmt ".toByteArray());le(16);o.write(byteArrayOf(1,0,1,0));le(16000);le(32000);o.write(byteArrayOf(2,0,16,0));o.write("data".toByteArray());le(pcm.size);o.write(pcm);return o.toByteArray()}
}
