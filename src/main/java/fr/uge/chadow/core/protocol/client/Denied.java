package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public record Denied(String codexId) implements Frame {
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  
  @Override
  public ByteBuffer toByteBuffer() {
    var bbCodexId = UTF8.encode(codexId);
    var buffer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bbCodexId.remaining());
    return buffer.put(Opcode.toByte(this.getClass()))
                 .putInt(bbCodexId.remaining())
                 .put(bbCodexId);
  }
}