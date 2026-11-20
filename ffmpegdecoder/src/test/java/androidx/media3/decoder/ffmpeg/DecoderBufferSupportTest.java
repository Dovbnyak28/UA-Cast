package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Test;

public class DecoderBufferSupportTest {
  @Test public void retainedPacketIsDirectPaddedAndDoesNotChangeInput() {
    ByteBuffer input = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
    input.limit(3);
    input.position(2);
    ByteBuffer copy = DecoderBufferSupport.copyPadded(input, 64);
    assertTrue(copy.isDirect());
    assertEquals(2, input.position());
    assertEquals(3, copy.limit());
    assertEquals(67, copy.capacity());
    assertEquals(1, copy.get(0));
    assertEquals(3, copy.get(2));
    copy.limit(copy.capacity());
    for (int index = 3; index < 67; index++) assertEquals(0, copy.get(index));
  }

  @Test public void retainedPacketDoesNotAliasInputRecycledByMedia3() {
    ByteBuffer input = ByteBuffer.wrap(new byte[] {1, 2});
    ByteBuffer copy = DecoderBufferSupport.copyPadded(input, 64);
    input.put(0, (byte) 99);
    assertEquals(1, copy.get(0));
  }

  @Test public void extraDataIsOrderedAndIndependentOfSourceArrays() {
    byte[] input = {1, 2};
    byte[] output = DecoderBufferSupport.concatenateExtraData(List.of(input, new byte[] {3}));
    input[0] = 99;
    assertArrayEquals(new byte[] {1, 2, 3}, output);
  }

  @Test public void oversizedExtraDataFailsBeforeUnboundedAllocation() {
    byte[] item = new byte[DecoderBufferSupport.MAX_EXTRA_DATA_BYTES];
    assertThrows(IllegalArgumentException.class,
        () -> DecoderBufferSupport.concatenateExtraData(List.of(item, new byte[] {1})));
  }

  @Test public void invalidPaddingAndOversizedPacketsAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> DecoderBufferSupport.copyPadded(ByteBuffer.allocate(1), -1));
    assertThrows(IllegalArgumentException.class,
        () -> DecoderBufferSupport.copyPadded(ByteBuffer.allocate(1), Integer.MAX_VALUE));
    assertThrows(IllegalArgumentException.class,
        () -> DecoderBufferSupport.copyPadded(ByteBuffer.allocate(DecoderBufferSupport.MAX_PACKET_BYTES + 1), 64));
  }
}
