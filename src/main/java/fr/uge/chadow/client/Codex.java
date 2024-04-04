package fr.uge.chadow.client;

import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

/**
 * This class manages a codex
 * creation / download / upload
 * on the client machine
 */
public class Codex implements Frame {
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  
  public static int CHUNK_SIZE = 1024;
  
  public record FileInfo(byte[] fingerprint,
                         String filename,
                         long length,
                         String absolutePath,
                         BitSet chunks) {
    
    public FileInfo {
      Objects.requireNonNull(fingerprint);
      Objects.requireNonNull(filename);
      Objects.requireNonNull(absolutePath);
    }
    
    public int numberOfChunks() {
      return (int) Math.ceil((double) length / CHUNK_SIZE);
    }
    
    public boolean isComplete() {
      return chunks.cardinality() == chunks.size();
    }
    
    public int completedChunks() {
      return chunks.cardinality();
    }
    
    public int totalChunks() {
      return chunks.size();
    }
    
    public double completionRate() {
      return (double) completedChunks() / totalChunks();
    }
  }
  
  private static final String ALGORITHM = "SHA-1";
  private static final Logger logger = Logger.getLogger(Codex.class.getName());
  private final byte[] id;
  private final String name;
  private final List<FileInfo> files;
  private final long totalSize;
  private final String root;
  
  private boolean downloading = false;
  private boolean sharing = false;
  
  private Codex(byte[] id, String name, String root, List<FileInfo> files) {
    this.id = id;
    this.name = name;
    this.files = files;
    this.root = root;
    this.totalSize = files.stream().mapToLong(FileInfo::length).sum();
  }
  
  @Override
  public ByteBuffer toByteBuffer() {
    var op = Opcode.PROPOSE;
    var idHexa = UTF8.encode(idToHexadecimal());
    var bbName = UTF8.encode(name);
    var bbNumberFiles = files.size();
    var bbFiles = ByteBuffer.allocate(files.size() * 2 * Integer.BYTES + files.size() * bbName.remaining());
    files.forEach(file -> {
      var bbFilename = UTF8.encode(file.filename());
      bbFiles
          .put(file.fingerprint)
          .putInt(bbFilename.remaining())
          .put(bbFilename)
          .putInt(file.chunks().size());
    });
    bbFiles.flip();
    // opcode, id, name size, name, number of files, files
    var bufferSize = Byte.BYTES + 20 +  Integer.BYTES + bbName.remaining() + Integer.BYTES + bbFiles.remaining();
    return ByteBuffer.allocate(bufferSize)
                     .put((byte) op.ordinal()) // @Todo op.getByte()
                     .putInt(idHexa.remaining())
                     .put(idHexa)
                     //.put(id())
                     .putInt(bbName.remaining())
                     .put(bbName)
                     .putInt(bbNumberFiles)
                     .put(bbFiles);
  }
  
  public boolean isComplete() {
    return files.stream().allMatch(FileInfo::isComplete);
  }
  
  public void share() {
    if(downloading){
      throw new IllegalStateException("Codex is downloading, can't share it");
    }
    sharing = true;
    log("is now sharing");
  }
  
  public void download() {
    if(sharing){
      throw new IllegalStateException("Codex is sharing, can't download it");
    }
    downloading = true;
    log("is now downloading");
  }
  
  public void stopSharing() {
    sharing = false;
    log("stops sharing");
  }
  
  public void stopDownloading() {
    downloading = false;
    log("stops downloading");
  }
  
  private void log(String message) {
    logger.info(STR."Codex \{name} with id: \{View.bytesToHexadecimal(id)} - \{message}");
  }
  
  public long totalSize() {
    return totalSize;
  }
  
  public String root() {
    return root;
  }
  
  public boolean isDownloading() {
    return downloading;
  }
  
  public boolean isSharing() {
    return sharing;
  }
  
  public List<FileInfo> files() {
    return Collections.unmodifiableList(files);
  }
  
  public String name() {
    return name;
  }
  
  public byte[] id() {
    return id;
  }
  
  public String idToHexadecimal() {
    return View.bytesToHexadecimal(id);
  }
  
  /**
   * Create a codex from a file or a directory
   * @param codexName
   * @param directory
   * @return
   * @throws IOException
   * @throws NoSuchAlgorithmException
   */
  static Codex fromPath(String codexName, String directory) throws IOException, NoSuchAlgorithmException {
    var rootPath = new File(directory);
    var fileInfo = new ArrayList<FileInfo>();
    logger.info(STR."Creating codex from \{rootPath}");
    // get all files
    if(rootPath.isFile()){
      fileInfo.add(fileInfofromPath(rootPath.getPath(), rootPath.toPath()));
    } else if(rootPath.isDirectory()) {
      Path start = Paths.get(rootPath.getAbsolutePath());
      var it = Files.walk(start).filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).iterator();
      while(it.hasNext()){
        var path = it.next();
        fileInfo.add(fileInfofromPath(rootPath.getPath(), path));
      }
    }
    var id = computeId(codexName, fileInfo);
    return new Codex(id, codexName, rootPath.toString(), fileInfo);
  }
  
  /**
   * Compute the id of a codex
   * @param name
   * @param files
   * @return
   */
  private static byte[] computeId(String name, List<FileInfo> files) {
    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      digest.update(name.getBytes());
      for (var file : files) {
        digest.update(file.fingerprint());
        digest.update(file.filename().getBytes());
        digest.update(Long.toString(file.length()).getBytes());
      }
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Read a file and compute its fingerprint by chunk of 8192 bytes
   * @param file
   * @return
   * @throws NoSuchAlgorithmException
   * @throws IOException
   */
  private static byte[] fingerprint(File file) throws NoSuchAlgorithmException, IOException {
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    try (FileInputStream inputStream = new FileInputStream(file)) {
      byte[] dataBytes = new byte[8192];
      int read;
      while ((read = inputStream.read(dataBytes)) != -1) {
        md.update(dataBytes, 0, read);
      }
    }
    return md.digest();
  }
  
  /**
   * Create a FileInfo from a file path
   * @param path
   * @return
   * @throws NoSuchAlgorithmException
   * @throws IOException
   */
  private static FileInfo fileInfofromPath(String root, Path path) throws NoSuchAlgorithmException, IOException {
    var file = path.toFile();
    var fingerprint = fingerprint(file);
    var bitSet = new BitSet((int) Math.ceil(file.length() / CHUNK_SIZE));
    // creation of codex from local file, then all chunks are available
    bitSet.flip(0, bitSet.size());
    // logger.info(STR."Creating FileInfo for \{file.getName()} with parent \{file.getParent()} with removed root \{root} : \{file.getParent().replace(root, "")}");
    return new FileInfo(fingerprint, file.getName(), file.length(), file.getParent().substring(root.length()), bitSet);
  }
  
}