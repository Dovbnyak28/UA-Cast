# JNI_OnLoad uses literal class/member names. Keep only that bridge and its buffer fields.
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder { *; }
-keep class androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoDecoder { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer {
    public <init>(android.os.Handler, androidx.media3.exoplayer.audio.AudioRendererEventListener, androidx.media3.exoplayer.audio.AudioSink);
}
-keep class androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer {
    public <init>(long, android.os.Handler, androidx.media3.exoplayer.video.VideoRendererEventListener, int);
}
-keep class androidx.media3.decoder.VideoDecoderOutputBuffer {
    java.nio.ByteBuffer data;
    int width;
    int height;
    boolean initForYuvFrame(int, int, int, int, int);
}
-keepclassmembers class androidx.media3.decoder.DecoderOutputBuffer { long timeUs; }
# This type occurs in JNI_OnLoad's hard-coded audio method descriptors, not just Java calls.
-keepnames class androidx.media3.decoder.SimpleDecoderOutputBuffer
