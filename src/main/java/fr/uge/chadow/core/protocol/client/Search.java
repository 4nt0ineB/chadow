package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public record Search(String codexName, int options, long date,  int results, int offset) implements Frame {
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final Logger logger = Logger.getLogger(Search.class.getName());
  
  
  public Search nextPage(int pageSize, int offset) {
    logger.info("pagesize %d offset %d".formatted(pageSize, offset));
    return new Search(codexName, options, date, pageSize, offset);
  }
  
  @Override
  public ByteBuffer toByteBuffer() {
    var bbCodexName = UTF8.encode(codexName);
    var bbSize = Byte.BYTES + Integer.BYTES + bbCodexName.remaining() + Integer.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES;
    var bb = ByteBuffer.allocate(bbSize);
    return bb.put(Opcode.toByte(this.getClass()))
        .putInt(bbCodexName.remaining())
        .put(bbCodexName)
        .putInt(options)
        .putLong(date)
        .putInt(results)
        .putInt(offset);
  }
  
  public enum Option {
    AT_DATE(1),
    BEFORE_DATE(2),
    AFTER_DATE(4);
    
    private final int value;
    
    Option(int value) {
      this.value = value;
    }
    
    public int value() {
      return value;
    }
    
  }
}