package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

public record HereChunk(long offset, byte[] payload) implements Frame {
    
    @Override
    public ByteBuffer toByteBuffer() {
      var buffer = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + Integer.BYTES + payload.length);
      return buffer.put(Opcode.HERECHUNK.toByte())
                  .putLong(offset)
                  .putInt(payload.length)
                  .put(payload);
    }
}