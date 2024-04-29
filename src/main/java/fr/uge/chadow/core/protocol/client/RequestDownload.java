package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record RequestDownload(String codexId, byte mode, int numberOfSharers, int numberOfProxies) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcodeByte = Opcode.toByte(this.getClass());
    var codexIdByteBuffer = UTF_8.encode(codexId);
    var bb = ByteBuffer.allocate(Byte.BYTES * 2 + Integer.BYTES * 3 + codexIdByteBuffer.remaining());
    return bb.put(opcodeByte)
            .putInt(codexIdByteBuffer.remaining())
            .put(codexIdByteBuffer)
            .put(mode)
            .putInt(numberOfSharers)
            .putInt(numberOfProxies);
  }
}