package fr.uge.chadow.core.protocol.field;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Username(String username) {
  public ByteBuffer toByteBuffer() {
    var bbUsername = UTF_8.encode(username);
    var bbMsg = ByteBuffer.allocate(bbUsername.remaining() + Integer.BYTES);
    return bbMsg.putInt(bbUsername.remaining()).put(bbUsername);
  }
}
