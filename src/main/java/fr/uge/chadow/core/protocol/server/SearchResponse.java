package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record SearchResponse(Result[] results) implements Frame {
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  @Override
  public ByteBuffer toByteBuffer() {
    var bb = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
    bb.put(Opcode.toByte(this.getClass()))
        .putInt(results.length);
    for(var result : results) {
      var resultByteBuffer = result.toByteBuffer().flip();
      bb.flip();
      var newBB = ByteBuffer.allocate(bb.remaining() + resultByteBuffer.remaining());
      newBB.put(bb).put(resultByteBuffer);
      bb = newBB;
    }
    return bb;
  }
  
  public record Result(String codexName, String codexId, long creationDate, int sharers) {
    public Result {
      Objects.requireNonNull(codexName);
      Objects.requireNonNull(codexId);
      if(creationDate < 0) {
        throw new IllegalArgumentException("Creation date must be positive");
      }
      if(sharers < 0) {
        throw new IllegalArgumentException("Sharers must be positive");
      }
    }
    
    private ByteBuffer toByteBuffer() {
      var bbCodexName = UTF8.encode(codexName);
      var bbCodexId = UTF8.encode(codexId);
      var bbSize = Integer.BYTES + bbCodexName.remaining() + Integer.BYTES + bbCodexId.remaining() + Long.BYTES + Integer.BYTES;
      var bb = ByteBuffer.allocate(bbSize);
      return bb
          .putInt(bbCodexName.remaining())
          .put(bbCodexName)
          .putInt(bbCodexId.remaining())
          .put(bbCodexId)
          .putLong(creationDate)
          .putInt(sharers);
    }
  }
}