package fr.uge.chadow.client;

import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.core.protocol.Codex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
  
  private void addCodex(Codex codex, String root) {
    codexes.putIfAbsent(codex.id(), new CodexStatus(codex, root));
  }
  
  public CodexStatus getCodexStatus(String id) {
    return codexes.get(id);
  }
  
  public boolean isComplete(String id) {
    return codexes.get(id).isComplete();
  }
  
  public boolean isDownloading(String id) {
    var codexStatus = codexes.get(id);
    return codexStatus.downloading;
  }
  
  public boolean isSharing(String id) {
    var codexStatus = codexes.get(id);
    return codexStatus.sharing;
  }
  
  public void share(String id) {
    var codexStatus = codexes.get(id);
    if(codexStatus.downloading){
      throw new IllegalStateException("Codex is downloading, can't share it");
    }
    codexStatus.sharing = true;
    log(codexStatus.codex,"is now sharing");
  }
  
  public void download(String id) {
    var codexStatus = codexes.get(id);
    if(codexStatus.sharing){
      throw new IllegalStateException("Codex is sharing, can't download it");
    }
    codexStatus.downloading = true;
    log(codexStatus.codex,"is now downloading");
  }
  
  public void stopSharing(String id) {
    var codexStatus = codexes.get(id);
    codexStatus.sharing = false;
    log(codexStatus.codex,"stops sharing");
  }
  
  public void stopDownloading(String id) {
    var codexStatus = codexes.get(id);
    codexStatus.downloading = false;
    log(codexStatus.codex,"stops downloading");
  }
  
  private void log(Codex codex, String message) {
    logger.info(STR."Codex \{codex.name()} with id: \{codex.id()} - \{message}");
  }
  
  /**
   * Create a codex from a file or a directory
   * @param codexName
   * @param directory
   * @return
   * @throws IOException
   * @throws NoSuchAlgorithmException
   */
  CodexStatus createFromPath(String codexName, String directory) throws IOException, NoSuchAlgorithmException {
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
    var bitSet = new BitSet((int) Math.ceil((double) file.length() / CodexStatus.CHUNK_SIZE));
    // creation of codex from local file, then all chunks are available
    bitSet.flip(0, bitSet.size());
    // logger.info(STR."Creating FileInfo for \{file.getName()} with parent \{file.getParent()} with removed root \{root} : \{file.getParent().replace(root, "")}");
    return new Codex.FileInfo(id, file.getName(), file.length(), file.getParent().substring(root.length()));
  }
  
  public Collection<CodexStatus> codexesStatus() {
    return codexes.values();
  }
  
  public static class CodexStatus {
    
    public static int CHUNK_SIZE = 1024;
    private boolean downloading = false;
    private boolean sharing = false;
    private final Codex codex;
    private final HashMap<String, BitSet> chunks = new HashMap<>();
    private final String root;
    
    public int numberOfChunks(Codex.FileInfo file) {
      return (int) Math.ceil((double) file.length() / CHUNK_SIZE);
    }
    
    private CodexStatus(Codex codex, String root) {
      this.codex = codex;
      this.root = root;
      for (var file : codex.files()) {
        chunks.put(file.id(), new BitSet(numberOfChunks(file)));
      }
    }
    
    public String root() {
      return root;
    }
    
    public boolean isComplete() {
      return chunks.values().stream().allMatch(bitSet -> bitSet.cardinality() == bitSet.size());
    }
    
    public boolean isComplete(Codex.FileInfo file) {
      return chunks.get(file.id()).cardinality() == numberOfChunks(file);
    }
    
    public int completedChunks(Codex.FileInfo file) {
      return chunks.get(file.id()).cardinality();
    }
    
    public int totalChunks(Codex.FileInfo file) {
      return chunks.get(file.id()).size();
    }
    
    public double completionRate(Codex.FileInfo file) {
      return (double) completedChunks(file) /totalChunks(file);
    }
    
    public Codex codex() {
      return codex;
    }
    
    public String id() {
      return codex.id();
    }
    
    public void setAllComplete() {
      chunks.values().forEach(bitSet -> bitSet.flip(0, bitSet.size()));
    }
  }
}