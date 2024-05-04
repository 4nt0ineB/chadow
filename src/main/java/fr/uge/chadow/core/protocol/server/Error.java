package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Error(String message) implements Frame {

  @Override
  public ByteBuffer toByteBuffer() {
    var opcodeByte = Opcode.toByte(this.getClass());
    var bbMessage = UTF_8.encode(message);
    var bb = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bbMessage.remaining());
    return bb.put(opcodeByte).putInt(bbMessage.remaining()).put(bbMessage);
  }
}
