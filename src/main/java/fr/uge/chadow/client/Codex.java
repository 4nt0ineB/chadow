package fr.uge.chadow.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * This class manages a codex
 * creation / download / upload
 * on the client machine
 */
public class Codex {
  private static String ALGORITHM = "SHA-1";
  
  private static final Logger logger = Logger.getLogger(Codex.class.getName());
  private final byte[] id;
  private final String codexName;
  private final List<String> paths;
  private final List<byte[]> filesFingerprints;
  
  private boolean downloading = false;
  private boolean sharing = false;
  
  private Codex(byte[] id, String codexName, List<String> paths, List<byte[]> filesFingerprints) {
    this.id = id;
    this.codexName = codexName;
    this.paths = paths;
    this.filesFingerprints = filesFingerprints;
  }
  
  public String getIdAsString() {
    return fingerprintAsString(id);
  }
  
  private static byte[] hashId(String codexName, List<String> paths, List<byte[]> filesFingerprints) {
    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      digest.update(codexName.getBytes());
      for(var path : paths) {
        digest.update(path.getBytes());
      }
      for(var fingerprint : filesFingerprints) {
        digest.update(fingerprint);
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
  
  public static String fingerprintAsString(byte[] fingerprint) {
    StringBuilder sb = new StringBuilder();
    for(byte b : fingerprint) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  static Codex fromPath(String codexName, String directory) throws IOException {
    var rootPath = new File(directory);
    var paths = new ArrayList<String>();
    var fingerprints = new ArrayList<byte[]>();
    // get all files
    if(rootPath.isFile()){
      paths.add(rootPath.getAbsolutePath());
    } else if(rootPath.isDirectory()) {
      Path start = Paths.get(rootPath.getAbsolutePath());
      Files.walk(start)
           .filter(Files::isRegularFile)
           .forEach(file -> {
             paths.add(file.toString());
           });
    }
    // compute fingerprints
    for(var path : paths) {
      try {
        var file = new File(path);
        var hash = fingerprint(file);
        fingerprints.add(hash);
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }
    var id = hashId(codexName, paths, fingerprints);
    logger.info("Codex created with id: " + fingerprintAsString(id));
    return new Codex(id, codexName, paths, fingerprints);
  }
  
}
