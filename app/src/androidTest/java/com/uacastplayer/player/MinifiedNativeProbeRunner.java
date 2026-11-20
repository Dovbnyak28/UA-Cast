package com.uacastplayer.player;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;

/** SDK-only minified probe: no AndroidX test/Kotlin facade calls across target/test classloaders. */
@androidx.media3.common.util.UnstableApi
public final class MinifiedNativeProbeRunner extends Instrumentation {
  @Override public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  @Override public void onStart() {
    Bundle result = new Bundle();
    try {
      if (!FfmpegLibrary.isAvailable()) throw new AssertionError("Minified APK could not load FFmpeg JNI");
      if (!"LGPL version 2.1 or later".equals(FfmpegLibrary.getNativeLicense())) {
        throw new AssertionError("Unexpected compiled native licence");
      }
      if (FfmpegLibrary.getVersion() == null) throw new AssertionError("Native version unavailable");
      // Loading calls JNI_OnLoad, which validates every hard-coded native class/method descriptor.
      result.putString("stream", "Minified native JNI registration/licence probe passed\nOK (1 test)\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      result.putString("stream", "Minified JNI probe FAILED: " + failure.getClass().getName() + "\n");
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
