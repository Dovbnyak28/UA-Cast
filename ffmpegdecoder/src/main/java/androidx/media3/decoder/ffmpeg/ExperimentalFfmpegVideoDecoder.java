/*
 * Copyright (C) 2019 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 * Modified for UA-Cast: bounded multi-output decoding, generation-based flush and EOS draining.
 */
package androidx.media3.decoder.ffmpeg;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.Decoder;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** One native context/worker, bounded pools, multiple frames per packet and delayed-frame EOS drain. */
@UnstableApi
final class ExperimentalFfmpegVideoDecoder
    implements Decoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException> {
  private static final int SUCCESS = 0, INVALID_DATA = -1, AGAIN = -3, END_OF_STREAM = -5;
  private final Object lock = new Object();
  private final ArrayDeque<DecoderInputBuffer> availableInputs = new ArrayDeque<>();
  private final ArrayDeque<DecoderInputBuffer> queuedInputs = new ArrayDeque<>();
  private final ArrayDeque<VideoDecoderOutputBuffer> availableOutputs = new ArrayDeque<>();
  private final ArrayDeque<VideoDecoderOutputBuffer> queuedOutputs = new ArrayDeque<>();
  private final String codecName;
  private final Format format;
  private final Thread worker;
  private long nativeContext;
  @Nullable private DecoderInputBuffer dequeuedInput;
  @Nullable private FfmpegDecoderException failure;
  private volatile int outputMode;
  private long outputStartTimeUs = C.TIME_UNSET;
  private long generation;
  private volatile boolean released;

  ExperimentalFfmpegVideoDecoder(int inputCount, int outputCount, int initialInputSize,
      int threads, Format format) throws FfmpegDecoderException {
    if (inputCount < 1 || inputCount > 16 || outputCount < 2 || outputCount > 16 ||
        initialInputSize < 0 || initialInputSize > DecoderBufferSupport.MAX_PACKET_BYTES) {
      throw new FfmpegDecoderException("Invalid decoder buffer configuration");
    }
    if (!FfmpegLibrary.isAvailable()) throw new FfmpegDecoderException("Native library unavailable");
    this.format = format;
    String codec = format.sampleMimeType == null ? null : FfmpegLibrary.getCodecName(format.sampleMimeType);
    if (codec == null) throw new FfmpegDecoderException("Unsupported video codec");
    codecName = codec;
    byte[] extra = DecoderBufferSupport.concatenateExtraData(format.initializationData);
    // Allocate pools before opening native resources or starting the worker.
    for (int index = 0; index < inputCount; index++) {
      DecoderInputBuffer buffer = new DecoderInputBuffer(
          DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT, FfmpegLibrary.getInputBufferPaddingSize());
      buffer.ensureSpaceForWrite(initialInputSize);
      availableInputs.addLast(buffer);
    }
    for (int index = 0; index < outputCount; index++) {
      availableOutputs.addLast(new VideoDecoderOutputBuffer(this::releaseOutput));
    }
    nativeContext = ffmpegInitialize(codecName, extra.length == 0 ? null : extra,
        Math.max(1, Math.min(threads, 4)), format.rotationDegrees, format.width, format.height);
    if (nativeContext == 0) throw new FfmpegDecoderException("Native video initialization failed");
    worker = new Thread(this::runWorker, "UA-Cast:FfmpegVideo");
    try { worker.start(); } catch (RuntimeException | OutOfMemoryError error) {
      ffmpegRelease(nativeContext); nativeContext = 0; throw error;
    }
  }

  @Override public String getName() { return "ffmpeg" + FfmpegLibrary.getVersion() + "-" + codecName; }
  void setOutputMode(@C.VideoOutputMode int mode) { outputMode = mode; }
  @Override public void setOutputStartTimeUs(long timeUs) {
    synchronized (lock) { outputStartTimeUs = timeUs; }
  }
  @Override @Nullable public DecoderInputBuffer dequeueInputBuffer() throws FfmpegDecoderException {
    synchronized (lock) {
      checkUsable();
      if (dequeuedInput != null) throw new IllegalStateException("An input is already dequeued");
      dequeuedInput = availableInputs.pollFirst();
      return dequeuedInput;
    }
  }
  @Override public void queueInputBuffer(DecoderInputBuffer buffer) throws FfmpegDecoderException {
    synchronized (lock) {
      checkUsable();
      if (buffer != dequeuedInput) throw new IllegalArgumentException("Input is not owned by this decoder");
      queuedInputs.addLast(buffer); dequeuedInput = null; lock.notifyAll();
    }
  }
  @Override @Nullable public VideoDecoderOutputBuffer dequeueOutputBuffer() throws FfmpegDecoderException {
    synchronized (lock) { checkUsable(); return queuedOutputs.pollFirst(); }
  }
  @Override public void flush() {
    synchronized (lock) {
      generation++;
      if (dequeuedInput != null) { recycleInput(dequeuedInput); dequeuedInput = null; }
      while (!queuedInputs.isEmpty()) recycleInput(queuedInputs.removeFirst());
      while (!queuedOutputs.isEmpty()) recycleOutput(queuedOutputs.removeFirst());
      lock.notifyAll();
    }
  }
  @Override public synchronized void release() {
    synchronized (lock) {
      if (released) return;
      released = true; lock.notifyAll();
    }
    // Never free a context while its worker is still decoding, even if join is interrupted.
    boolean interrupted = false;
    while (worker.isAlive()) {
      try { worker.join(); } catch (InterruptedException error) { interrupted = true; }
    }
    ffmpegRelease(nativeContext); nativeContext = 0;
    synchronized (lock) {
      queuedInputs.clear(); availableInputs.clear(); queuedOutputs.clear(); availableOutputs.clear();
      dequeuedInput = null;
    }
    if (interrupted) Thread.currentThread().interrupt();
  }
  void renderToSurface(VideoDecoderOutputBuffer output, Surface surface) throws FfmpegDecoderException {
    if (released || nativeContext == 0 || output.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV) {
      throw new FfmpegDecoderException("Invalid surface render state");
    }
    int result = ffmpegRenderFrame(nativeContext, surface, output, output.width, output.height);
    if (result != SUCCESS) throw new FfmpegDecoderException("Surface render failed: " + result);
  }
  private void checkUsable() throws FfmpegDecoderException {
    if (failure != null) throw failure;
    if (released) throw new FfmpegDecoderException("Decoder is released");
  }
  private void recycleInput(DecoderInputBuffer input) { input.clear(); availableInputs.addLast(input); }
  private void recycleOutput(VideoDecoderOutputBuffer output) { output.clear(); availableOutputs.addLast(output); }
  private void releaseOutput(VideoDecoderOutputBuffer output) {
    synchronized (lock) { if (!released) recycleOutput(output); lock.notifyAll(); }
  }

  private void runWorker() {
    long workerGeneration = 0, fallbackTimeUs = 0;
    boolean canReceive = false, retryingPacket = false, eosSent = false, eosDone = false, firstFrame = false;
    int skipped = 0;
    @Nullable DecoderInputBuffer input = null;
    @Nullable VideoDecoderOutputBuffer output = null;
    try {
      while (true) {
        boolean reset = false;
        synchronized (lock) {
          while (!released && workerGeneration == generation &&
              (eosDone || (canReceive ? availableOutputs.isEmpty() : input == null && queuedInputs.isEmpty()))) {
            lock.wait();
          }
          if (released) return;
          if (workerGeneration != generation) {
            if (input != null) { recycleInput(input); input = null; }
            workerGeneration = generation; reset = true;
          } else if (canReceive) {
            output = availableOutputs.removeFirst();
          } else if (input == null) {
            input = queuedInputs.removeFirst();
          }
        }
        if (reset) {
          if (ffmpegReset(nativeContext) == 0) throw new FfmpegDecoderException("Native flush failed");
          canReceive = retryingPacket = eosSent = eosDone = firstFrame = false;
          skipped = 0; continue;
        }
        if (canReceive) {
          output.init(fallbackTimeUs, outputMode, null);
          int result = ffmpegReceiveFrame(nativeContext, outputMode, output, false);
          if (result == AGAIN) {
            if (retryingPacket || eosSent) throw new FfmpegDecoderException("FFmpeg made no progress while draining");
            canReceive = false;
          } else if (result == END_OF_STREAM) {
            if (!eosSent) throw new FfmpegDecoderException("Unexpected native EOS");
            output.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
            eosDone = true; canReceive = false;
          } else if (result != SUCCESS) {
            throw new FfmpegDecoderException("Native receive failed: " + result);
          } else {
            retryingPacket = false; output.format = format;
          }
          synchronized (lock) {
            boolean stale = workerGeneration != generation || released;
            boolean beforeStart = outputStartTimeUs != C.TIME_UNSET && output.timeUs < outputStartTimeUs;
            if (stale || result == AGAIN || (result == SUCCESS && beforeStart)) {
              if (result == SUCCESS && !stale) skipped++;
              recycleOutput(output);
            } else {
              if (firstFrame && result == SUCCESS) { output.addFlag(C.BUFFER_FLAG_FIRST_SAMPLE); firstFrame = false; }
              output.skippedOutputBufferCount = skipped; skipped = 0;
              queuedOutputs.addLast(output);
            }
            output = null;
          }
          continue;
        }
        if (input == null) throw new FfmpegDecoderException("Missing worker input");
        int result;
        if (input.isEndOfStream()) {
          result = ffmpegSendEndOfStream(nativeContext);
        } else {
          ByteBuffer data = input.data;
          if (data == null || data.limit() > DecoderBufferSupport.MAX_PACKET_BYTES) {
            throw new FfmpegDecoderException("Invalid encoded packet");
          }
          result = ffmpegSendPacket(nativeContext, data, data.limit(), input.timeUs);
        }
        if (result == AGAIN) { retryingPacket = true; canReceive = true; continue; }
        if (result != SUCCESS && result != INVALID_DATA) throw new FfmpegDecoderException("Native send failed: " + result);
        eosSent = input.isEndOfStream(); fallbackTimeUs = input.timeUs; firstFrame |= input.isFirstSample();
        synchronized (lock) { recycleInput(input); input = null; }
        canReceive = true;
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      synchronized (lock) { failure = new FfmpegDecoderException("Decoder worker interrupted", error); }
    } catch (FfmpegDecoderException | RuntimeException | OutOfMemoryError error) {
      synchronized (lock) {
        failure = error instanceof FfmpegDecoderException ? (FfmpegDecoderException) error
            : new FfmpegDecoderException("Unexpected video decode failure", error);
      }
    } finally {
      synchronized (lock) {
        if (input != null) recycleInput(input);
        if (output != null) recycleOutput(output);
      }
    }
  }

  private native long ffmpegInitialize(String codecName, @Nullable byte[] extraData, int threads, int degree, int width, int height);
  private native long ffmpegReset(long context);
  private native void ffmpegRelease(long context);
  private native int ffmpegRenderFrame(long context, Surface surface, VideoDecoderOutputBuffer output, int width, int height);
  private native int ffmpegSendPacket(long context, ByteBuffer data, int length, long timeUs);
  private native int ffmpegSendEndOfStream(long context);
  private native int ffmpegReceiveFrame(long context, int mode, VideoDecoderOutputBuffer output, boolean decodeOnly);
}
