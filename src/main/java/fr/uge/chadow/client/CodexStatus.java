package fr.uge.chadow.client;

import fr.uge.chadow.core.protocol.field.Codex;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class CodexStatus {
  
  public record Chunk(long offset, int length) {
  }
  private static final Logger logger = Logger.getLogger(CodexStatus.class.getName());
  private final int chunkSize;
  private final Codex codex;
  private final HashMap<Codex.FileInfo, BitSet> chunks = new HashMap<>();
  private final String root;
  private final ReentrantLock lock = new ReentrantLock();
  private final RandomAccessFile[] sharedFiles; // caching files readers (for sharing)
  private boolean downloading = false;
  private boolean downloadHidden = false;
  private boolean sharing = false;
  private final boolean isDir;
  // caching a file reader, 1 because we download the codex sequentially
  private RandomAccessFile currentDownloadingFile = null;
  
  // track download speed
  private long totalBytesPassed = 0;
  private long downloadStartTime;
  
  CodexStatus(Codex codex, String root, int chunkSize, boolean isDir) {
    this.codex = codex;
    this.root = root;
    this.chunkSize = chunkSize;
    // initialize chunks bitset
    var files = codex.files();
    for (Codex.FileInfo file : files) {
      chunks.put(file, new BitSet(numberOfChunks(file)));
    }
    sharedFiles = new RandomAccessFile[files.length];
    this.isDir = isDir;
  }
  
  public int numberOfChunks(Codex.FileInfo file) {
    return (int) Math.ceil((double) file.length() / chunkSize);
  }
  
  /**
   * Create the file tree of a codex
   */
  public void createFileTree() throws IOException {
    var codexFolder = Paths.get(root(), codex().name());
    if (!Files.exists(codexFolder)) {
      Files.createDirectories(codexFolder);
    }
    for (var file : codex().files()) {
      var path = Paths.get(codexFolder.toString(), file.relativePath(), file.filename());
      var parent = path.getParent();
      if (!Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      if (!Files.exists(path)) {
        Files.createFile(path);
      }
    }
  }
  
  public String root() {
    return root;
  }
  
  public boolean isComplete() {
    lock.lock();
    try {
      return chunks.entrySet()
                   .stream()
                   .allMatch(e -> numberOfChunks(e.getKey()) == e.getValue()
                                                                 .cardinality());
    } finally {
      lock.unlock();
    }
  }
  
  public boolean isDownloadingHidden() {
    return downloadHidden;
  }
  
  public boolean isComplete(Codex.FileInfo file) {
    lock.lock();
    try {
      return chunks.get(file)
                   .cardinality() == numberOfChunks(file);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Get the number of completed chunks of a file
   * @param file the file
   * @return the number of completed chunks
   */
  public int completedChunks(Codex.FileInfo file) {
    lock.lock();
    try {
      return chunks.get(file)
                   .cardinality();
    } finally {
      lock.unlock();
    }
  }
  
  public double completionRate(Codex.FileInfo file) {
    return (double) completedChunks(file) / numberOfChunks(file);
  }
  
  public double completionRate() {
    lock.lock();
    try {
      return (double) chunks.values()
                            .stream()
                            .mapToInt(BitSet::cardinality)
                            .sum() / chunks.keySet()
                                           .stream()
                                           .mapToInt(this::numberOfChunks)
                                           .sum();
    } finally {
      lock.unlock();
    }
  }
  
  public Codex codex() {
    return codex;
  }
  
  public String id() {
    return codex.id();
  }
  
  void setAllComplete() {
    lock.lock();
    try {
      chunks.forEach((key, value) -> value.flip(0, numberOfChunks(key)));
    } finally {
      lock.unlock();
    }
  }
  
  public boolean isDownloading() {
    return downloading;
  }
  
  public boolean isSharing() {
    return sharing;
  }
  
  void share() {
    sharing = true;
    totalBytesPassed = 0;
    downloadStartTime = System.currentTimeMillis();
  }
  
  void download(boolean hidden) {
    downloading = true;
    downloadHidden = hidden;
    totalBytesPassed = 0;
    downloadStartTime = System.currentTimeMillis();
  }
  
  void stopSharing() {
    lock.lock();
    try {
      sharing = false;
      for (var i = 0; i < sharedFiles.length; i++) {
        if (sharedFiles[i] != null) {
          try {
            sharedFiles[i].close();
            sharedFiles[i] = null;
          } catch (IOException e) {
            logger.severe(STR."Error while closing file \{codex.files()[i].filename()} : " + e.getMessage());
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }
  
  void stopDownloading() {
    lock.lock();
    try {
      downloading = false;
      if (currentDownloadingFile != null) {
        try {
          currentDownloadingFile.close();
          currentDownloadingFile = null;
        } catch (IOException e) {
          logger.severe(STR."Error while closing file : \{e.getMessage()}");
        }
      }
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Get the next random chunk to download of first non completed file
   * in the codex
   *
   * @return the chunk
   */
  public Chunk nextRandomChunk() {
    lock.lock();
    try {
      // random file
      var firstNonCompletedFileIndex = firstNonCompletedFileIndex();
      var nonCompletedFile = codex.files()[firstNonCompletedFileIndex];
      // random chunk in file
      var bitSet = chunks.get(nonCompletedFile);
      var nonBitSet = new BitSet(numberOfChunks(nonCompletedFile));
      nonBitSet.flip(0, numberOfChunks(nonCompletedFile));
      nonBitSet.andNot(bitSet);
      var nonCompletedChunks = nonBitSet.stream()
                                        .toArray();
      var random = new Random();
      var chunkIndex = nonCompletedChunks[random.nextInt(nonCompletedChunks.length)];
      logger.info(STR."Remainging chunks for the file \{nonCompletedFile.filename()} : \{nonCompletedChunks.length}");
      
      var offsetInFile = (long) chunkIndex * chunkSize;
      long offsetInCodex = fileOffset(firstNonCompletedFileIndex) + offsetInFile;
      var length = Math.min(chunkSize, (nonCompletedFile.length() - offsetInFile));
      return new Chunk(offsetInCodex, (int) length);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Get the data of a chunk of the codex
   * anywhere in the codex, supposing that the chunk is bound to a single file
   *
   * @param offsetInCodex the offset of the chunk in the codex
   * @param length       the length of the chunk
   * @return the data of the chunk
   * @throws IOException if the offset is out of the codex or if the chunk lap over two files.
   * or if an error occurs while reading the file
   */
  byte[] getChunk(long offsetInCodex, int length) throws IOException {
    lock.lock();
    try {
      var fileIndex = fileIndex(offsetInCodex, length);
      var file = codex.files()[fileIndex];
      
      var fileOffset = offsetInCodex - fileOffset(fileIndex);
      var data = new byte[length];
      var raf = sharedFiles[fileIndex];
      if (raf == null) {
        Path path;
        if(isDir) {
          path = Paths.get(root, file.relativePath(), file.filename());
        } else {
          path = Paths.get(root);
        }
        // we cache the file reader, will be closed when the codex is complete
        // or the sharing is stopped
        raf = new RandomAccessFile(path.toString(), "r");
        sharedFiles[fileIndex] = raf;
      }
      raf.seek(fileOffset);
      var readLength = (int) Math.min(length, file.length() - fileOffset);
      totalBytesPassed += readLength;
      raf.read(data, 0, readLength);
      return data;
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Write a chunk of data the codex.
   * Compute which file the data belongs to and write it in the file.
   *
   * @param offsetInCodex the offset of the chunk in the codex
   * @param payload      the data of the chunk
   * @throws IOException if the offset is out of the codex or if the chunk lap over two files.
   */
  void writeChunk(long offsetInCodex, byte[] payload) throws IOException {
    lock.lock();
    try {
      Objects.requireNonNull(payload);
      var fileIndex = fileIndex(offsetInCodex, payload.length);
      var offsetInFile = offsetInCodex - fileOffset(fileIndex);
      Objects.checkIndex(offsetInFile + payload.length, codex().totalSize() + 1);
      var file = codex().files()[fileIndex];
      totalBytesPassed += payload.length;
      // sequential download
      if (currentDownloadingFile == null) {
        var path = Paths.get(root(), codex().name(), file.relativePath(), file.filename());
        currentDownloadingFile = new RandomAccessFile(path.toString(), "rw");
      }
      currentDownloadingFile.seek(offsetInFile);
      currentDownloadingFile.write(payload);
      var chunkIndex = (int) (offsetInFile / chunkSize);
      chunks.get(file)
            .set(chunkIndex);
      if (isComplete(file)) {
        currentDownloadingFile.close();
        currentDownloadingFile = null;
        logger.info(STR."File \{file.filename()} is complete");
        if(isComplete()) {
          logger.info(STR."Codex \{codex.id()} is complete");
          stopDownloading();
        }
      }
    } finally {
       lock.unlock();
    }
  }
  
  /**
   * Get the index of the first non completed file
   *
   * @return the index of the file
   */
  private int firstNonCompletedFileIndex() {
    lock.lock();
    try {
      var files = codex.files();
      for (int i = 0; i < files.length; i++) {
        if (!isComplete(files[i])) {
          return i;
        }
      }
      return -1;
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Get the offset (bytes) of the file in the codex
   *
   * @param index the index of the file
   * @return the offset in the codex;
   */
  private long fileOffset(int index) {
    lock.lock();
    try {
      var files = codex.files();
      var offset = 0L;
      for (int i = 0; i < index; i++) {
        offset += files[i].length();
      }
      return offset;
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Get the index of the file in the codex
   * with the given chunk (offsetInCodex and length)
   *
   * @param offsetInCodex the offset of the chunk in the codex
   * @param length       the length of the chunk
   * @return the index of the file
   * @throws IllegalArgumentException if the offsetInCodex is not in the codex
   *                                  or if the chunk lap over two files.
   */
  private int fileIndex(long offsetInCodex, int length) {
    var codexTotalSize = codex.totalSize();
    if (offsetInCodex > codexTotalSize) {
      throw new IllegalArgumentException(STR."Offset is out of the codex : \{offsetInCodex} > \{codexTotalSize}");
    }
    var files = codex.files();
    long currentOffset = 0L;
    for (int i = 0; i < files.length; i++) {
      var file = files[i];
      var fileLength = file.length();
      if (offsetInCodex >= currentOffset && offsetInCodex < currentOffset + fileLength) {
        if (offsetInCodex + length > currentOffset + fileLength) {
          logger.severe(STR."Offset \{offsetInCodex} + length \{length} > file offset \{currentOffset} + file length \{fileLength}");
          throw new IllegalArgumentException("Chunk lap over two files");
        }
        return i;
      }
      currentOffset += fileLength;
    }
    throw new IllegalArgumentException("Offset is out of the codex");
  }
  
  /**
   * Get the download speed in bytes per second if downloading
   * Get the upload speed in bytes per second if sharing
   * @return the speed in bytes per second
   */
  public double getCurrentSpeed() {
    long currentTime = System.currentTimeMillis();
    long downloadTimeInSeconds = (currentTime - downloadStartTime) / 1000;
    if (downloadTimeInSeconds == 0) return 0;
    return (double) totalBytesPassed / downloadTimeInSeconds;
  }
  
}