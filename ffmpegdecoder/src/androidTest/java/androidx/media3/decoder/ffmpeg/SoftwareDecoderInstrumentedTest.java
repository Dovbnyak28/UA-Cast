package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.*;

import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.util.Base64;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Separate library-test package; no UA-Cast app data or providers are accessed. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public class SoftwareDecoderInstrumentedTest {
  @Test public void independentNativeLibraryPreservesRequiredSoftwareCapabilities() {
    assertTrue("Independent FFmpeg JNI must load", FfmpegLibrary.isAvailable());
    assertTrue(FfmpegLibrary.getVersion(), FfmpegLibrary.getVersion().startsWith("Lavc60."));
    boolean relinkProbe = "true".equals(InstrumentationRegistry.getArguments().getString("uacast.relinkProbe"));
    assertEquals("LGPL version 2.1 or later" + (relinkProbe ? " (UA-Cast relink probe)" : ""),
        FfmpegLibrary.getNativeLicense());
    for (String mime : new String[] {MimeTypes.AUDIO_MPEG_L2, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS, MimeTypes.AUDIO_FLAC,
        MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_MPEG2, MimeTypes.VIDEO_VP8, MimeTypes.VIDEO_VP9}) {
      assertTrue("Missing software capability: " + mime, FfmpegLibrary.supportsFormat(mime));
    }
    assertFalse(FfmpegLibrary.supportsFormat("video/not-a-codec"));
  }

  @Test public void h264ReorderingAndEosDrainLoseNoFrames() throws Exception { verifyVideo("h264-bframes", false); }
  @Test public void mpeg2ReorderingAndEosDrainLoseNoFrames() throws Exception { verifyVideo("mpeg2-bframes", false); }
  @Test public void vp8ActuallyDecodesInSoftware() throws Exception { verifyVideo("vp8", false); }
  @Test public void vp9ActuallyDecodesInSoftware() throws Exception { verifyVideo("vp9", false); }
  @Test public void softwareYuvFrameCanRenderToAndroidSurface() throws Exception { verifyVideo("h264-bframes", true); }
  @Test public void mp2ActuallyProducesAllPcmSamples() throws Exception { verifyAudio("mp2"); }
  @Test public void ac3ActuallyProducesAllPcmSamples() throws Exception { verifyAudio("ac3"); }
  @Test public void eac3ActuallyProducesAllPcmSamples() throws Exception { verifyAudio("eac3"); }
  @Test public void dtsActuallyProducesAllPcmSamples() throws Exception { verifyAudio("dts"); }

  @Test public void rapidFlushAndReleaseDoNotLeakWorkers() throws Exception {
    JSONObject fixture = fixture("h264-bframes");
    int before = decoderThreads();
    for (int attempt = 0; attempt < 30; attempt++) {
      ExperimentalFfmpegVideoDecoder decoder = new ExperimentalFfmpegVideoDecoder(4, 4, 65536, 4, format(fixture));
      try {
        DecoderInputBuffer input = decoder.dequeueInputBuffer();
        fill(input, fixture.getJSONArray("packets").getJSONObject(0));
        decoder.queueInputBuffer(input);
        decoder.flush();
        decoder.setOutputStartTimeUs(0);
      } finally { decoder.release(); }
    }
    assertEquals("No decoder worker survives release", before, decoderThreads());
  }

  @Test public void failedInitializationDoesNotLeakAudioWorker() throws Exception {
    int before = decoderThreads();
    Format unsupported = new Format.Builder().setSampleMimeType("audio/not-a-codec").build();
    for (int index = 0; index < 30; index++) {
      try {
        new FfmpegAudioDecoder(unsupported, 4, 4, 65536, false);
        fail("Unsupported decoder must fail construction");
      } catch (RuntimeException | FfmpegDecoderException expected) { /* The process and worker must survive safely. */ }
    }
    assertEquals("Failed construction must stop SimpleDecoder's worker", before, decoderThreads());
  }

  private static int decoderThreads() {
    int count = 0;
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (thread.isAlive() && (thread.getName().equals("UA-Cast:FfmpegVideo") ||
          thread.getName().equals("ExoPlayer:SimpleDecoder"))) count++;
    }
    return count;
  }

  private void verifyVideo(String name, boolean render) throws Exception {
    JSONObject fixture = fixture(name);
    JSONArray packets = fixture.getJSONArray("packets");
    ExperimentalFfmpegVideoDecoder decoder = new ExperimentalFfmpegVideoDecoder(4, 4, 65536, 4, format(fixture));
    SurfaceTexture texture = null;
    Surface surface = null;
    try {
      decoder.setOutputMode(C.VIDEO_OUTPUT_MODE_SURFACE_YUV);
      if (render) { texture = new SurfaceTexture(false); surface = new Surface(texture); }
      int packet = 0, frames = 0;
      boolean queuedEos = false, ended = false;
      long previousTimeUs = Long.MIN_VALUE;
      long deadline = SystemClock.elapsedRealtime() + 15000;
      while (!ended && SystemClock.elapsedRealtime() < deadline) {
        if (!queuedEos) {
          DecoderInputBuffer input = decoder.dequeueInputBuffer();
          if (input != null) {
            if (packet == packets.length()) { input.addFlag(C.BUFFER_FLAG_END_OF_STREAM); queuedEos = true; }
            else { fill(input, packets.getJSONObject(packet++)); }
            decoder.queueInputBuffer(input);
          }
        }
        VideoDecoderOutputBuffer output = decoder.dequeueOutputBuffer();
        if (output == null) { SystemClock.sleep(1); continue; }
        try {
          if (output.isEndOfStream()) ended = true;
          else {
            assertEquals(fixture.getInt("width"), output.width);
            assertEquals(fixture.getInt("height"), output.height);
            assertTrue("Reordered frame PTS must be display ordered", output.timeUs >= previousTimeUs);
            previousTimeUs = output.timeUs;
            assertNotNull(output.data);
            if (render && frames == 0) decoder.renderToSurface(output, surface);
            frames++;
          }
        } finally { output.release(); }
      }
      assertTrue("Decoder must reach EOS, not remain buffering", ended);
      assertEquals("Every delayed/reordered frame must be emitted before EOS", fixture.getInt("expectedFrames"), frames);
    } finally {
      decoder.release();
      if (surface != null) surface.release();
      if (texture != null) texture.release();
    }
  }

  private void verifyAudio(String name) throws Exception {
    JSONObject fixture = fixture(name);
    JSONArray packets = fixture.getJSONArray("packets");
    FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(format(fixture), 4, 4, 65536, false);
    try {
      int packet = 0;
      long bytes = 0;
      boolean queuedEos = false, ended = false;
      long deadline = SystemClock.elapsedRealtime() + 15000;
      while (!ended && SystemClock.elapsedRealtime() < deadline) {
        if (!queuedEos) {
          DecoderInputBuffer input = decoder.dequeueInputBuffer();
          if (input != null) {
            if (packet == packets.length()) { input.addFlag(C.BUFFER_FLAG_END_OF_STREAM); queuedEos = true; }
            else fill(input, packets.getJSONObject(packet++));
            decoder.queueInputBuffer(input);
          }
        }
        SimpleDecoderOutputBuffer output = decoder.dequeueOutputBuffer();
        if (output == null) { SystemClock.sleep(1); continue; }
        try {
          if (output.isEndOfStream()) ended = true;
          else { assertNotNull(output.data); bytes += output.data.remaining(); }
        } finally { output.release(); }
      }
      assertTrue("Audio decoder must reach EOS", ended);
      assertEquals(fixture.getLong("expectedSamples") * fixture.getInt("channels") * 2, bytes);
      assertEquals(fixture.getInt("sampleRate"), decoder.getSampleRate());
      assertEquals(fixture.getInt("channels"), decoder.getChannelCount());
    } finally { decoder.release(); }
  }

  private static void fill(DecoderInputBuffer input, JSONObject packet) throws Exception {
    byte[] encoded = Base64.decode(packet.getString("data"), Base64.DEFAULT);
    input.ensureSpaceForWrite(encoded.length);
    input.data.put(encoded);
    input.timeUs = packet.getLong("timeUs");
    input.flip();
  }

  private static Format format(JSONObject fixture) throws Exception {
    byte[] extra = Base64.decode(fixture.getString("extraData"), Base64.DEFAULT);
    return new Format.Builder().setSampleMimeType(fixture.getString("mime"))
        .setWidth(fixture.getInt("width")).setHeight(fixture.getInt("height"))
        .setSampleRate(fixture.getInt("sampleRate")).setChannelCount(fixture.getInt("channels"))
        .setInitializationData(extra.length == 0 ? Collections.emptyList() : Collections.singletonList(extra)).build();
  }

  private static JSONObject fixture(String name) throws Exception {
    try (java.io.InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
        .getAssets().open("fixtures/" + name + ".json")) {
      java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
      byte[] block = new byte[8192];
      int length;
      while ((length = input.read(block)) != -1) data.write(block, 0, length);
      return new JSONObject(new String(data.toByteArray(), StandardCharsets.UTF_8));
    }
  }
}
