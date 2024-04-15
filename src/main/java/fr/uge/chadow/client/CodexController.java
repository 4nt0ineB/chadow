package fr.uge.chadow.client;

import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.core.protocol.field.Codex;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class CodexController {
  
  private static final String ALGORITHM = "SHA-1";
  private static final Logger logger = Logger.getLogger(Codex.class.getName());
  private static final HashMap<String, CodexStatus> codexes = new HashMap<>();
  
  private CodexStatus addCodex(Codex codex, String root) {
    var codexStatus = new CodexStatus(codex, root);
    var existing = codexes.putIfAbsent(codex.id(), codexStatus);
    return existing == null ? codexStatus : existing;
  }
  
  public Optional<CodexStatus> getCodexStatus(String id) {
    return Optional.ofNullable(codexes.get(id));
  }
  
  public boolean isComplete(String id) {
    return codexes.get(id).isComplete();
  }
  
  public boolean isDownloading(String id) {
    var codexStatus = codexes.get(id);
    return Optional.ofNullable(codexStatus.isDownloading()).orElse(false);
  }
  
  public boolean isSharing(String id) {
    var codexStatus = codexes.get(id);
    return Optional.ofNullable(codexStatus.isSharing()).orElse(false);
  }
  
  public void share(String id) {
    var codexStatus = codexes.get(id);
    if(codexStatus.isDownloading()){
      throw new IllegalStateException("Codex is downloading, can't share it");
    }
    codexStatus.share();
    log(codexStatus.codex(),"is now sharing");
  }
  
  public void download(String id) {
    var codexStatus = codexes.get(id);
    if(codexStatus.isSharing()){
      throw new IllegalStateException("Codex is sharing, can't download it");
    }
    codexStatus.download();
    log(codexStatus.codex(),"is now downloading");
  }
  
  public void stopSharing(String id) {
    var codexStatus = codexes.get(id);
    codexStatus.stopSharing();
    log(codexStatus.codex(),"stops sharing");
  }
  
  public void stopDownloading(String id) {
    var codexStatus = codexes.get(id);
    codexStatus.stopDownloading();
    log(codexStatus.codex(),"stops downloading");
  }
  
  private void log(Codex codex, String message) {
    logger.info(STR."Codex \{codex.name()} with id: \{codex.id()} - \{message}");
  }
  
  CodexStatus fromFetchedCodex(Codex codex, String root) {
    return addCodex(codex, root);
  }
 
  
  /**
   * Create a codex from a file or a directory
   * @param codexName
   * @param directory
   * @return
   * @throws IOException
   * @throws NoSuchAlgorithmException
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
      var it = Files.walk(start).filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).iterator();
      while(it.hasNext()){
        var path = it.next();
        fileInfoList.add(fileInfofromPath(rootPath.getPath(), path));
      }
    }
    var id = View.bytesToHexadecimal(computeId(codexName, fileInfoList));
    var files = new Codex.FileInfo[fileInfoList.size()];
    for (int i = 0; i < fileInfoList.size(); i++) {
      files[i] = fileInfoList.get(i);
    }
    var codex = new Codex(id, codexName, files);
    addCodex(codex, rootPath.toString()) ;
    var codexStatus = codexes.get(id);
    codexStatus.setAllComplete();
    return codexStatus;
  }
  
  /**
   * Compute the id of a codex
   * @param name
   * @param files
   * @return
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
  private static Codex.FileInfo fileInfofromPath(String root, Path path) throws NoSuchAlgorithmException, IOException {
    var file = path.toFile();
    var id = View.bytesToHexadecimal(fingerprint(file));
    var size = (int) Math.ceil((double) file.length() / CodexStatus.chunkSize());
    var bitSet = new BitSet(size);
    // creation of codex from local file, then all chunks are available
    bitSet.flip(0, size);
    // logger.info(STR."Creating FileInfo for \{file.getName()} with parent \{file.getParent()} with removed root \{root} : \{file.getParent().replace(root, "")}");
    return new Codex.FileInfo(id, file.getName(), file.length(), file.getParent().substring(root.length()));
  }
  
  public Collection<CodexStatus> codexesStatus() {
    return codexes.values();
  }
  
  public boolean codexExists(String id) {
    return codexes.containsKey(id);
  }
  
  /**
   *
   * @param wantedCodexId
   * @param offset
   * @param length
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
  
  public void writeChunk(String id, long offsetInCodex, byte[] payload) throws IOException {
    Objects.requireNonNull(id);
    Objects.requireNonNull(payload);
    var codexStatus = codexes.get(id);
    if(codexStatus == null){
      throw new IllegalArgumentException("Codex not found");
    }
    codexStatus.writeChunk(offsetInCodex, payload);
  }
  
  public void createFileTree(String codexId) throws IOException {
    codexes.get(codexId).createFileTree();
  }
  
}