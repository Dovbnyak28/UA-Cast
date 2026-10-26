package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Handler;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses actual Media3 extraction/events/surface rendering with software video forced. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public class Media3SoftwarePlayerInstrumentedTest {
  @Test public void defaultMedia3FactoryDiscoversEachSoftwareExtensionExactlyOnce() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    DefaultRenderersFactory factory = new DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
    Renderer[] renderers = factory.createRenderers(new Handler(android.os.Looper.getMainLooper()),
        new VideoRendererEventListener() {}, new androidx.media3.exoplayer.audio.AudioRendererEventListener() {},
        cues -> {}, metadata -> {});
    int video = 0, audio = 0;
    for (Renderer renderer : renderers) {
      if (renderer instanceof ExperimentalFfmpegVideoRenderer) video++;
      if (renderer instanceof FfmpegAudioRenderer) audio++;
    }
    assertEquals("No duplicate software video renderer", 1, video);
    assertEquals("No missing/duplicate software audio renderer", 1, audio);
  }
  @Test public void actualMedia3PlayerExtractsRendersAndEndsWithSoftwareH264() throws Exception { play(false); }
  @Test public void thirtyRapidReplacementsEndOnLatestItemAndReleaseSoftwareWorker() throws Exception { play(true); }

  private void play(boolean replace) throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File clip = new File(context.getCacheDir(), "h264-bframes.mp4");
    try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets()
        .open("fixtures/h264-bframes.mp4"); FileOutputStream output = new FileOutputStream(clip)) {
      byte[] block = new byte[8192];
      int size;
      while ((size = input.read(block)) != -1) output.write(block, 0, size);
    }
    AtomicReference<ExoPlayer> player = new AtomicReference<>();
    AtomicReference<PlaybackException> error = new AtomicReference<>();
    AtomicReference<String> decoderName = new AtomicReference<>();
    CountDownLatch ended = new CountDownLatch(1);
    try (ActivityScenario<DecoderHarnessActivity> scenario = ActivityScenario.launch(DecoderHarnessActivity.class)) {
      try {
        scenario.onActivity(activity -> {
          DefaultRenderersFactory factory = new DefaultRenderersFactory(activity) {
            @Override protected void buildVideoRenderers(Context context, int extensionMode,
                androidx.media3.exoplayer.mediacodec.MediaCodecSelector selector, boolean fallback,
                Handler handler, VideoRendererEventListener listener, long joiningTime, ArrayList<Renderer> out) {
              // No MediaCodec video renderer is registered: a successful frame must be software-decoded.
              out.add(new ExperimentalFfmpegVideoRenderer(joiningTime, handler, listener, 50));
            }
          };
          factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
          ExoPlayer engine = new ExoPlayer.Builder(activity, factory).build();
          player.set(engine);
          engine.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onVideoDecoderInitialized(EventTime time, String name, long at, long duration) {
              decoderName.set(name);
            }
          });
          engine.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException failure) { error.set(failure); ended.countDown(); }
            @Override public void onPlaybackStateChanged(int state) { if (state == Player.STATE_ENDED) ended.countDown(); }
          });
          engine.setVideoSurfaceView(activity.videoSurface);
          for (int index = 0; index < (replace ? 30 : 1); index++) {
            engine.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(clip)));
            engine.prepare();
          }
          engine.play();
        });
        assertTrue("Software playback must end within its deadline", ended.await(20, TimeUnit.SECONDS));
        assertNull("Media3 software playback must not report an error", error.get());
        assertNotNull("Software decoder initialization must be observed", decoderName.get());
        assertTrue(decoderName.get(), decoderName.get().startsWith("ffmpeg"));
      } finally { scenario.onActivity(activity -> { if (player.get() != null) player.get().release(); }); }
    } finally { assertTrue(clip.delete()); }
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      assertFalse("No software video worker may survive player release", thread.isAlive() && thread.getName().equals("UA-Cast:FfmpegVideo"));
    }
  }
}
