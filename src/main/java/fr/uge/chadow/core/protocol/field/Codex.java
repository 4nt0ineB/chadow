package fr.uge.chadow.core.protocol.field;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class manages a codex
 * creation / download / upload
 * on the client machine
 */
public record Codex(String id, String name, Codex.FileInfo[] files) {
  private static final Charset UTF8 = StandardCharsets.UTF_8;

  public long totalSize() {
    return Arrays.stream(files).mapToLong(FileInfo::length).sum();
  }

  public record FileInfo(String id,
                         String filename,
                         long length,
                         String relativePath) {
    public FileInfo {
      Objects.requireNonNull(id);
      Objects.requireNonNull(filename);
      Objects.requireNonNull(relativePath);
    }
    
    private ByteBuffer toByteBuffer() {
      var bbId = UTF8.encode(id);
      var bbFilename = UTF8.encode(filename);
      var bbAbsolutePath = UTF8.encode(relativePath);
      var bufferSize = Integer.BYTES + bbId.remaining() + Integer.BYTES + bbFilename.remaining() + Long.BYTES + Integer.BYTES + bbAbsolutePath.remaining();
      return ByteBuffer.allocate(bufferSize)
              .putInt(bbId.remaining())
              .put(bbId)
              .putInt(bbFilename.remaining())
              .put(bbFilename)
              .putLong(length)
              .putInt(bbAbsolutePath.remaining())
              .put(bbAbsolutePath);
    }
  }

  public ByteBuffer toByteBuffer() {
    var bbId = UTF8.encode(id);
    var bbName = UTF8.encode(name);
    var bbNumberFiles = files.length;
    var bbFiles = ByteBuffer.allocate(0);
    for (var file : files) {
      var fileBuffer = file.toByteBuffer();
      fileBuffer.flip();
      bbFiles.flip();
      var tmpBuff = ByteBuffer.allocate(bbFiles.remaining() + fileBuffer.remaining());
      tmpBuff.put(bbFiles);
      tmpBuff.put(fileBuffer);
      bbFiles = tmpBuff;
    }
    bbFiles.flip();
    // id, name size, name, number of files, files
    var bufferSize = Integer.BYTES + bbId.remaining() + Integer.BYTES + bbName.remaining() + Integer.BYTES + bbFiles.remaining();
    return ByteBuffer.allocate(bufferSize)
            .putInt(bbId.remaining())
            .put(bbId)
            .putInt(bbName.remaining())
            .put(bbName)
            .putInt(bbNumberFiles)
            .put(bbFiles);
  }
}