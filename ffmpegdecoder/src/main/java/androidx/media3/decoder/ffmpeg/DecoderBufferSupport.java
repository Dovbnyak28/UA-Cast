package androidx.media3.decoder.ffmpeg;

import java.nio.ByteBuffer;
import java.util.List;

/** Bounded, padded copies for packets retained after Media3 returns an input to its pool. */
final class DecoderBufferSupport {
  static final int MAX_PACKET_BYTES = 16 * 1024 * 1024;
  static final int MAX_EXTRA_DATA_BYTES = 1024 * 1024;

  private DecoderBufferSupport() {}

  static ByteBuffer copyPadded(ByteBuffer source, int padding) {
    int size = source.limit();
    if (padding < 0 || padding > 4096 || size > MAX_PACKET_BYTES) {
      throw new IllegalArgumentException("Invalid decoder packet size or padding");
    }
    ByteBuffer input = source.duplicate();
    input.position(0);
    ByteBuffer copy = ByteBuffer.allocateDirect(size + padding);
    copy.put(input);
    for (int index = 0; index < padding; index++) {
      copy.put((byte) 0);
    }
    copy.flip();
    copy.limit(size);
    return copy;
  }

  static byte[] concatenateExtraData(List<byte[]> data) {
    int total = validateExtraData(data);
    ByteBuffer output = ByteBuffer.allocate(total);
    for (byte[] item : data) {
      output.put(item);
    }
    return output.array();
  }

  static int validateExtraData(List<byte[]> data) {
    long total = 0;
    for (byte[] item : data) {
      total += item.length;
      if (total > MAX_EXTRA_DATA_BYTES) {
        throw new IllegalArgumentException("Decoder initialization data exceeds safety limit");
      }
    }
    return (int) total;
  }
}
