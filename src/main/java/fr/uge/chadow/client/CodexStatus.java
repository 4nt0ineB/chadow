package fr.uge.chadow.client;

import fr.uge.chadow.core.protocol.field.Codex;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class CodexStatus {
    private static final Logger logger = Logger.getLogger(CodexStatus.class.getName());
    private static int CHUNK_SIZE = 1024;
    private boolean downloading = false;
    private boolean sharing = false;
    private final Codex codex;
    private final HashMap<Codex.FileInfo, BitSet> chunks = new HashMap<>();
    private final String root;
    private final ReentrantLock lock = new ReentrantLock();
    // caching files readers
    private RandomAccessFile currentDownloadingFile = null; // we download the codex sequentially
    private final RandomAccessFile[] sharedFiles;
    
    
    public int numberOfChunks(Codex.FileInfo file) {
      return (int) Math.ceil((double) file.length() / CHUNK_SIZE);
    }
    
    
    CodexStatus(Codex codex, String root) {
      this.codex = codex;
      this.root = root;
      
      var files = codex.files();
      for (Codex.FileInfo file : files) {
        chunks.put(file, new BitSet(numberOfChunks(file)));
      }
      sharedFiles = new RandomAccessFile[files.length];
    }
    
    /**
     * Create the file tree of a codex
     */
    public void createFileTree() throws IOException {
      
      try {
        var codexFolder = Paths.get(root(), codex().name());
        if(!Files.exists(codexFolder)){
          Files.createDirectories(codexFolder);
        }
        for (var file : codex().files()) {
          
          var path = Paths.get(codexFolder.toString(), file.relativePath(), file.filename());
          var parent = path.getParent();
       /* logger.info(STR."Creating file tree for dir parent \{parent}");
        logger.info(STR."Creating file tree for path \{path}");*/
          if(!Files.exists(parent)){
            Files.createDirectories(parent);
          }
          if(!Files.exists(path)){
            Files.createFile(path);
          }
        }
      } catch (IOException e) {
        logger.severe(STR."Error while creating file tree :" + e.getMessage());
        throw new RuntimeException(e);
      }
      
    }
    
    public String root() {
      return root;
    }
    
    public boolean isComplete() {
      lock.lock();
      try {
        return chunks.entrySet().stream().allMatch(e -> numberOfChunks(e.getKey()) == e.getValue().cardinality());
      } finally {
        lock.unlock();
      }
    }
    
    public boolean isComplete(Codex.FileInfo file) {
      lock.lock();
      try {
        return chunks.get(file).cardinality() == numberOfChunks(file);
      } finally {
        lock.unlock();
      }
    }
    
    public int completedChunks(Codex.FileInfo file) {
      lock.lock();
      try {
        return chunks.get(file).cardinality();
      } finally {
        lock.unlock();
      }
    }
    
    public double completionRate(Codex.FileInfo file) {
      return (double) completedChunks(file) / numberOfChunks(file);
    }
    
    public Codex codex() {
      return codex;
    }
    
    public String id() {
      return codex.id();
    }
    
    public void setAllComplete() {
      chunks.entrySet().forEach(e -> e.getValue().flip(0, numberOfChunks(e.getKey())));
    }
  
  public boolean isDownloading() {
    return downloading;
  }
  
  public boolean isSharing() {
    return sharing;
  }
  
  public void share() {
    sharing = true;
  }
  
  public void download() {
      downloading = true;
  }
  
  public void stopSharing() {
      sharing = false;
      for(var i = 0; i < sharedFiles.length; i++) {
        if(sharedFiles[i] != null) {
          try {
            sharedFiles[i].close();
            sharedFiles[i] = null;
          } catch (IOException e) {
            logger.severe(STR."Error while closing file \{codex.files()[i].filename()} : " + e.getMessage());
          }
        }
      }
  }
  
  public void stopDownloading() {
      downloading = false;
      if(currentDownloadingFile != null) {
        try {
          currentDownloadingFile.close();
          currentDownloadingFile = null;
        } catch (IOException e) {
          logger.severe(STR."Error while closing file : " + e.getMessage());
        }
      }
  }
  
  public record Chunk(long offset, int length) {}
    
    /**
     * Get the next random chunk to download
     * @return
     */
    public Chunk nextRandomChunk() {
      lock.lock();
      try {
        // random file
        var firstNonCompletedFileIndex = firstNonCompletedFileIndex();
        var nonCompletedFile = codex.files()[firstNonCompletedFileIndex];
        logger.info(STR."Chunk for the file \{nonCompletedFile.filename()}");
        // random chunk in file
        var bitSet = chunks.get(nonCompletedFile);
        var nonBitSet = new BitSet(numberOfChunks(nonCompletedFile));
        nonBitSet.flip(0, numberOfChunks(nonCompletedFile));
        nonBitSet.andNot(bitSet);
        var nonCompletedChunks = nonBitSet.stream().toArray();
        var random = new Random();
        logger.info(STR."Nbr of non completed chunks: \{nonCompletedChunks.length}");
        var chunkIndex = nonCompletedChunks[random.nextInt(nonCompletedChunks.length)];
        logger.info(STR."Select chunk: \{chunkIndex}");
        
        var offsetInFile = (long) chunkIndex * CHUNK_SIZE;
        var offsetInCodex = fileOffset(firstNonCompletedFileIndex) + offsetInFile;
        /*if(offsetInCodex + CHUNK_SIZE > codex.totalSize()) {
          logger.severe(STR."FIle size : \{codex.totalSize()} / \{chunkIndex * CHUNK_SIZE}");
          logger.severe(offsetInFile + "/" + fileOffset(randomNonCompletedFileIndex) + "=" + offsetInCodex);
          throw new Error("Chunk out of codex : " + offsetInCodex + " + " + CHUNK_SIZE + " > " + codex.totalSize());
        }*/
        var l = Math.min(CHUNK_SIZE, (int) (nonCompletedFile.length() - offsetInFile));
        logger.info(STR."Offset in codex: \{offsetInCodex} / length: \{l}");
        return new Chunk(offsetInCodex, Math.min(CHUNK_SIZE, (int) (nonCompletedFile.length() - offsetInFile)));
      } finally {
        lock.unlock();
      }
    }
    
    
    /**
     * Get the data of a chunk of the codex
     * anywhere in the codex, supposing that the chunk is bound to a single file
     * @param offsetInCodex
     * @param length
     * @return
     * @throws IOException
     */
    public byte[] getChunk(long offsetInCodex, int length) throws IOException {
      var fileIndex = fileIndex(offsetInCodex, length);
      var file = codex.files()[fileIndex];
      var path = Paths.get(root, file.relativePath(), file.filename());
      var fileOffset = offsetInCodex - fileOffset(fileIndex);
      //logger.info(STR."Reading \{length} bytes from \{path} at offset \{fileOffset}");
      var data = new byte[length];
      var raf = sharedFiles[fileIndex];
      if(raf == null) {
        raf = new RandomAccessFile(path.toString(), "r");
        sharedFiles[fileIndex] = raf;
      }
      raf.seek(fileOffset);
      raf.read(data, 0, Math.min(length, (int) (file.length() - fileOffset)));
      /*try (var inputStream = new FileInputStream(path.toFile())) {
        inputStream.skip(offset);
        inputStream.read(data, 0, length);
      }*/
      return data;
    }
    
    public void writeChunk(long offsetInCodex, byte[] payload) throws IOException {
      Objects.requireNonNull(payload);
      var fileIndex = fileIndex(offsetInCodex, payload.length);
      var offsetInFile = offsetInCodex - fileOffset(fileIndex);
      Objects.checkIndex(offsetInFile + payload.length, codex().totalSize() + 1);
      var file = codex().files()[fileIndex];
      var path = Paths.get(root(), codex().name(), file.relativePath(), file.filename());
      // sequential download
      if(currentDownloadingFile == null) {
        currentDownloadingFile = new RandomAccessFile(path.toString(), "rw");
      }
      currentDownloadingFile.seek(offsetInFile);
      currentDownloadingFile.write(payload);
      var chunkIndex = (int) (offsetInFile / CodexStatus.CHUNK_SIZE);
      chunks.get(file).set(chunkIndex);
      if(isComplete(file)){
        currentDownloadingFile.close();
        currentDownloadingFile = null;
      }
    }
    
    /**
     * Get the index of a random non completed file
     * @return
     */
    private int randomNonCompletedFileIndex() {
      lock.lock();
      try {
        var nonCompletedFiles = Arrays.stream(codex.files()).filter(file -> !isComplete(file)).toArray(Codex.FileInfo[]::new);
        var random = new Random();
        return random.nextInt(nonCompletedFiles.length);
      } finally {
        lock.unlock();
      }
    }
    
    private int firstNonCompletedFileIndex() {
      lock.lock();
      try {
        var files = codex.files();
        for (int i = 0; i < files.length; i++) {
          if(!isComplete(files[i])){
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
     * @param index
     * @return
     */
    private long fileOffset(int index) {
      lock.lock();
      try {
        var files = codex.files();
        var offset = 0L;
        for(int i = 0; i < index; i++){
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
     * @param offsetInCodex
     * @param length
     * @return
     * @throws IllegalArgumentException if the offsetInCodex is not in the codex
     * or if the chunk lap over two files.
     */
    private int fileIndex(long offsetInCodex, int length) {
      var codexTotalSize = codex.totalSize();
      if(offsetInCodex > codexTotalSize){
        throw new IllegalArgumentException("Offset is out of the codex : " + offsetInCodex + " > " + codexTotalSize);
      }
      var files = codex.files();
      long currentOffset = 0;
      for (int i = 0; i < files.length; i++) {
        var file = files[i];
        var fileLength = file.length();
        if(offsetInCodex >= currentOffset && offsetInCodex < currentOffset + fileLength){
          if(offsetInCodex + length > currentOffset + fileLength){
            logger.severe(STR."Offset \{offsetInCodex} + length \{length} > file offset \{currentOffset} + file length \{fileLength}");
            throw new IllegalArgumentException("Chunk lap over two files");
          }
          return i;
        }
        currentOffset += fileLength;
      }
      throw new IllegalArgumentException("Offset is out of the codex");
    }
    
    public static int chunkSize() {
      return CHUNK_SIZE;
    }
  }