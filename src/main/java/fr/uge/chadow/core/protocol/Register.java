package fr.uge.chadow.core.protocol;

import fr.uge.chadow.client.Frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public record Register (String username) implements Frame {
  private static final Charset UTF_8 = StandardCharsets.UTF_8;
  
  @Override
  public ByteBuffer toByteBuffer() {
    var op = Opcode.REGISTER;
    var username = UTF_8.encode(this.username);
    var frameSize = Byte.BYTES + username.remaining();
    var frame = ByteBuffer.allocate(frameSize)
        .put((byte) op.ordinal())
        .put(username);
    return frame.compact();
  }

}
