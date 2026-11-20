package androidx.media3.decoder.ffmpeg;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;

/** Dedicated test-only display target, unrelated to the installed UA-Cast Activity. */
public final class DecoderHarnessActivity extends Activity {
  SurfaceView videoSurface;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    videoSurface = new SurfaceView(this);
    setContentView(videoSurface);
  }
}
