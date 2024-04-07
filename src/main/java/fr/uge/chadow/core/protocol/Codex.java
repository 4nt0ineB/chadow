package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class manages a codex
 * creation / download / upload
 * on the client machine
 */
public record Codex(String id, String name, Codex.FileInfo[] files) implements Frame {
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  
  public long totalSize() {
    return Arrays.stream(files).mapToLong(FileInfo::length).sum();
  }
  
  public record FileInfo(String id,
                         String filename,
                         long length,
                         String absolutePath) {
    public FileInfo {
      Objects.requireNonNull(id);
      Objects.requireNonNull(filename);
      Objects.requireNonNull(absolutePath);
    }
  }
  
  @Override
  public ByteBuffer toByteBuffer() {
    var op = Opcode.PROPOSE;
    var bbId = UTF8.encode(id);
    var bbName = UTF8.encode(name);
    var bbNumberFiles = files.length;
    var bbFiles = ByteBuffer.allocate(0);
    for(var file: files){
      var bbFileId = UTF8.encode(file.id());
      var bbFilename = UTF8.encode(file.filename());
      var fileBuffer = ByteBuffer.allocate(Integer.BYTES + bbFileId.remaining() + Integer.BYTES + bbFilename.remaining());
      fileBuffer
          .putInt(bbFileId.remaining())
          .put(bbFileId)
          .putInt(bbFilename.remaining())
          .put(bbFilename);
      var tmpBuff = ByteBuffer.allocate(bbFiles.flip().remaining() + fileBuffer.flip().remaining());
      tmpBuff.put(bbFiles);
      tmpBuff.put(fileBuffer);
      bbFiles = tmpBuff.compact();
    }
    bbFiles.flip();
    // opcode, id, name size, name, number of files, files
    var bufferSize = Byte.BYTES + Integer.BYTES + bbId.remaining() + Integer.BYTES +   bbName.remaining() + Integer.BYTES + bbFiles.remaining();
    return ByteBuffer.allocate(bufferSize)
                     .put((byte) op.ordinal()) // @Todo op.getByte()
                     .putInt(bbId.remaining())
                     .put(bbId)
                     .putInt(bbName.remaining())
                     .put(bbName)
                     .putInt(bbNumberFiles)
                     .put(bbFiles);
  }
}