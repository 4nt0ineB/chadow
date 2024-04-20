package fr.uge.chadow.client;

import fr.uge.chadow.client.cli.display.View;
import fr.uge.chadow.core.protocol.field.Codex;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

public class CodexController {
  
  private static final String ALGORITHM = "SHA-1";
  private static final Logger logger = Logger.getLogger(Codex.class.getName());
  private static final HashMap<String, CodexStatus> codexes = new HashMap<>();
  private Path defaultDownloadPath;
  
  public CodexController(String defaultDownloadPath) throws IOException {
    Objects.requireNonNull(defaultDownloadPath);
    changeDefaultDownloadPath(defaultDownloadPath);
  }
  
  /**
   * Change the default download path
   * @param path the new default download path
   *             @throws IllegalArgumentException if the path is not a directory
   * @throws IOException if the directory couldn't be created
   */
  public void changeDefaultDownloadPath(String path) throws IOException {
    Objects.requireNonNull(path);
    var dir = Paths.get(path);
    var file = dir.toFile();
    if(!file.exists()) {
      try {
        if(!file.mkdirs()) {
          throw new IOException(STR."Couldn't create directory \"\{path}\"");
        }
      } catch (SecurityException e) {
        throw new IOException(STR."Couldn't create directory \"\{path}\"", e);
      }
    }
    defaultDownloadPath = dir;
  }
  
  private CodexStatus addCodex(Codex codex, Path root) {
    var codexStatus = new CodexStatus(codex, root.toString());
    var existing = codexes.putIfAbsent(codex.id(), codexStatus);
    return existing == null ? codexStatus : existing;
  }
  
  public Optional<CodexStatus> getCodexStatus(String id) {
    return Optional.ofNullable(codexes.get(id));
  }
  
  /**
   * Check if a codex is downloading
    * @param id the id of the codex
   * @return true if the codex is downloading
   */
  public boolean isDownloading(String id) {
    return Optional.ofNullable(codexes.get(id))
                   .map(CodexStatus::isDownloading).orElse(false);
  }
  
  /**
   * Check if a codex is sharing
   * @param id the id of the codex
   * @return true if the codex is sharing
   */
  public boolean isSharing(String id) {
    return Optional.ofNullable(codexes.get(id))
                   .map(CodexStatus::isSharing).orElse(false);
  }
  
  /**
   * Set the codex into sharing mode
   * @param id the id of the codex
   */
  public void share(String id) {
    var codexStatus = codexes.get(id);
    if(codexStatus.isDownloading()){
      throw new IllegalStateException("Codex is downloading, can't share it");
    }
    codexStatus.share();
    log(codexStatus.codex(),"is now sharing");
  }
  
  /**
   * Set the codex into downloading mode
   * @param id the id of the codex
   */
  public void download(String id) {
    var codexStatus = codexes.get(id);
    if(codexStatus.isSharing()){
      throw new IllegalStateException("Codex is sharing, can't download it");
    }
    codexStatus.download();
    log(codexStatus.codex(),"is now downloading");
  }
  
  /**
   * Stop sharing a codex
   * @param id the id of the codex
   */
  public void stopSharing(String id) {
    var codexStatus = codexes.get(id);
    codexStatus.stopSharing();
    log(codexStatus.codex(),"stops sharing");
  }
  
  /**
   * Stop downloading a codex
   * @param id the id of the codex
   */
  public void stopDownloading(String id) {
    var codexStatus = codexes.get(id);
    codexStatus.stopDownloading();
    log(codexStatus.codex(),"stops downloading");
  }
  
  /**
   * Add a codex fetched from the Server
   * @param codex the codex to add
   * @return the status of the codex
   */
  CodexStatus addFromFetchedCodex(Codex codex) {
    return addCodex(codex, defaultDownloadPath);
  }
  
  /**
   * Get the status of all codexes
   * @return a list of all codexes status
   */
  public List<CodexStatus> codexesStatus() {
    return codexes.values().stream().toList();
  }
  
  /**
   * Check if a codex exists locally
   * @param id the id of the codex
   * @return true if the codex exists
   */
  public boolean codexExists(String id) {
    return codexes.containsKey(id);
  }
  
  /**
   * Get a chunk of data from a codex
   * @param wantedCodexId  the id of the codex to get the data from
   * @param offset the offset of the chunk
   * @param length the length of the chunk
   * @return a ByteBuffer in write mode
   * @throws IllegalArgumentException if the codex is not found
   */
  public byte[] getChunk(String wantedCodexId, long offset, int length) throws IOException {
    var codexStatus = codexes.get(wantedCodexId);
    if(codexStatus == null){
      throw new IllegalArgumentException("Codex not found");
    }
    return codexStatus.getChunk(offset, length);
  }
  
  /**
   * Write a chunk of data in a codex
   * @param id the id of the codex
   * @param offsetInCodex the offset in the codex
   * @param payload the data to write
   * @throws IOException if the codex is not found or if an error occurs while writing the data
   */
  public void writeChunk(String id, long offsetInCodex, byte[] payload) throws IOException {
    Objects.requireNonNull(id);
    Objects.requireNonNull(payload);
    var codexStatus = codexes.get(id);
    if(codexStatus == null){
      throw new IllegalArgumentException("Codex not found");
    }
    codexStatus.writeChunk(offsetInCodex, payload);
  }
  
  /**
   * Create the file tree of a codex
   * @param codexId the id of the codex
   * @throws IOException if the codex is not found or if an error occurs while creating the file tree
   */
  public void createFileTree(String codexId) throws IOException {
    codexes.get(codexId).createFileTree();
  }
  
  /**
   * Create a codex from a file or a directory
   * @param codexName the name of the codex
   * @param directory the path of the file or directory
   * @return the status of the codex
   * @throws IOException if the file or directory is not found
   * @throws NoSuchAlgorithmException if the defined hashing algorithm is not found on the system
   */
  public CodexStatus createFromPath(String codexName, String directory) throws IOException, NoSuchAlgorithmException {
    var rootPath = new File(directory);
    var fileInfoList = new ArrayList<Codex.FileInfo>();
    logger.info(STR."Creating codex of \"\{rootPath}\"");
    // get all files
    if(rootPath.isFile()){
      fileInfoList.add(fileInfofromPath(rootPath.getPath(), rootPath.toPath()));
    }if(rootPath.isDirectory()) {
      Path start = Paths.get(rootPath.getAbsolutePath());
      try(var walker = Files.walk(start).filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString))){
        var it = walker.iterator();
        while(it.hasNext()){
          var path = it.next();
          fileInfoList.add(fileInfofromPath(rootPath.getPath(), path));
        }
      }
    }
    var id = View.bytesToHexadecimal(computeId(codexName, fileInfoList));
    var files = new Codex.FileInfo[fileInfoList.size()];
    for (int i = 0; i < fileInfoList.size(); i++) {
      files[i] = fileInfoList.get(i);
    }
    var codex = new Codex(id, codexName, files);
    addCodex(codex, rootPath.toPath()) ;
    var codexStatus = codexes.get(id);
    codexStatus.setAllComplete();
    return codexStatus;
  }
  
  private void log(Codex codex, String message) {
    logger.info(STR."Codex \{codex.name()} with id: \{codex.id()} - \{message}");
  }
  
  /**
   * Compute the id of a codex
   * @param name the name of the codex
   * @param files the list of files in the codex
   * @return the id of the codex as a byte array
   */
  private static byte[] computeId(String name, List<Codex.FileInfo> files) {
    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      digest.update(name.getBytes());
      for (var file : files) {
        digest.update(file.id().getBytes());
        digest.update(file.filename().getBytes());
        digest.update(Long.toString(file.length()).getBytes());
      }
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Read a file and compute its fingerprint by chunk of 4096 bytes
   * @param file the file to read
   * @return the fingerprint of the file as a byte array
   * @throws NoSuchAlgorithmException if the defined hashing algorithm is not found on the system
   * @throws IOException if an error occurs while reading the file
   */
  private static byte[] fingerprint(File file) throws NoSuchAlgorithmException, IOException {
    MessageDigest md = MessageDigest.getInstance(ALGORITHM);
    try (FileInputStream inputStream = new FileInputStream(file)) {
      byte[] dataBytes = new byte[4096];
      int read;
      while ((read = inputStream.read(dataBytes)) != -1) {
        md.update(dataBytes, 0, read);
      }
    }
    return md.digest();
  }
  
  /**
   * Create a FileInfo from a file path
   * @param path the path of the file
   * @return the FileInfo of the file
   * @throws NoSuchAlgorithmException if the defined hashing algorithm is not found on the system
   * @throws IOException if an error occurs while reading the file
   */
  private static Codex.FileInfo fileInfofromPath(String root, Path path) throws NoSuchAlgorithmException, IOException {
    var file = path.toFile();
    var id = View.bytesToHexadecimal(fingerprint(file));
    return new Codex.FileInfo(id, file.getName(), file.length(), file.getParent().substring(Math.min(root.length(), file.getParent().length())));
  }
  
  /**
   * Close the codex controller
   * Stops all sharing and downloading codexes
   */
  public void close() {
    codexes.values().forEach(CodexStatus::stopSharing);
    codexes.values().forEach(CodexStatus::stopDownloading);
  }
  
}