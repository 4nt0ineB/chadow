package fr.uge.chadow.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class manages a codex
 * creation / download / upload
 * on the client machine
 */
public class Codex {
  

  
  public record FileInfo(byte[] fingerprint, String filename, long length, String absolutePath) {}
  
  private static final String ALGORITHM = "SHA-1";
  
  private static final Logger logger = Logger.getLogger(Codex.class.getName());
  private final byte[] id;
  private final String name;
  private final List<FileInfo> files;
  
  private boolean downloading = false;
  private boolean sharing = false;
  
  private Codex(byte[] id, String name, List<FileInfo> files) {
    this.id = id;
    this.name = name;
    this.files = files;
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
    
    // get all files
    if(rootPath.isFile()){
      fileInfo.add(fileInfofromPath(rootPath.toPath()));
    } else if(rootPath.isDirectory()) {
      Path start = Paths.get(rootPath.getAbsolutePath());
      var it = Files.walk(start).filter(Files::isRegularFile).iterator();
      while(it.hasNext()){
        var path = it.next();
        fileInfo.add(fileInfofromPath(path));
      }
    }
    var id = computeId(codexName, fileInfo);
    logger.info("Codex created with id: " + fingerprintAsString(id));
    return new Codex(id, codexName, fileInfo);
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
   * Convert a fingerprint sha1 to a string
   * @param fingerprint
   * @return
   */
  public static String fingerprintAsString(byte[] fingerprint) {
    StringBuilder sb = new StringBuilder();
    for(byte b : fingerprint) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  /**
   * Create a FileInfo from a file path
   * @param path
   * @return
   * @throws NoSuchAlgorithmException
   * @throws IOException
   */
  private static FileInfo fileInfofromPath(Path path) throws NoSuchAlgorithmException, IOException {
    var file = path.toFile();
    var fingerprint = fingerprint(file);
    return new FileInfo(fingerprint, file.getName(), file.length(), file.getParent());
  }
  
}
