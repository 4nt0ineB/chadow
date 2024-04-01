package fr.uge.chadow.client;

import fr.uge.chadow.cli.InputReader;
import fr.uge.chadow.cli.display.Display;
import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.core.protocol.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ClientConsoleController {
  public enum Mode {
    CHAT_LIVE_REFRESH, // default
    CHAT_SCROLLER,     // :c, :chat
    USERS_SCROLLER,    // :u, :users
    HELP_SCROLLER,     // :h, :help
    CODEX_SEARCH,      // :search <name>
    CODEX_DETAILS,     // :cdx:<fingerprint>
    CODEX_LIST,        // :mycdx
  }
  private final static Logger logger = Logger.getLogger(ClientConsoleController.class.getName());
  private final Client client;
  private final InputReader inputReader;
  private final Display display;
  private final ArrayList<Message> publicMessages = new ArrayList<>();
  private final HashMap<String, List<Message>> privateMessages = new HashMap<>();
  private final SortedSet<String> users = new TreeSet<>();
  private final HashMap<String, Codex> codexes = new HashMap<>();
  private Codex selectedCodexForDetails;
  private volatile boolean mustClose = false;
  
  // we can't have reentrant locks because this thread runs inputReader that call display
  // that itself call this controller to get the messages and users.
  // Plus we have incoming messages from client, so can have concurrent modifications on the lists
  private final Object lock = new Object();
  private int lines;
  private int cols;
  
  public ClientConsoleController(Client client, int lines, int cols) {
    Objects.requireNonNull(client);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.client = client;
    this.lines = lines;
    this.cols = cols;
    AtomicBoolean viewCanRefresh = new AtomicBoolean(true);
    display = new Display(lines, cols, viewCanRefresh, this);
    inputReader = new InputReader(viewCanRefresh, this);
  }
  
  public List<Message> messages() {
    synchronized (lock) {
      return Collections.unmodifiableList(publicMessages);
    }
  }
  
  public List<Codex> codexes() {
    synchronized (lock) {
      return codexes.values().stream().toList();
    }
  }
  
  public List<String> users() {
    synchronized (lock) {
      return users.stream()
                  .toList();
    }
  }
  
  public int totalUsers() {
    synchronized (lock) {
      return users.size();
    }
  }
  
  public String clientLogin() {
    return client.login();
  }
  
  public String clientServerHostName() {
    return client.serverHostName();
  }
  
  public void start() throws IOException {
    logger.info(STR."Starting console (\{lines} rows \{cols} cols");
    // client
    startClient();
    display.draw();
    // thread that manages the display
    startDisplay();
    // for dev: fake messages
    fillWithFakeData();
    client.subscribe(this::addMessage);
    // Thread that manages the user inputs
    try {
      inputReader.start();
    } catch (UncheckedIOException | IOException | InterruptedException | NoSuchAlgorithmException e) {
      logger.severe(STR."The client was interrupted.\{e.getMessage()}");
    } finally {
      exitNicely();
    }
  }
  
  public int numberOfMessages() {
    synchronized (lock) {
      return publicMessages.size();
    }
  }
  
  public void addMessage(Message message) {
    synchronized (lock) {
      publicMessages.add(message);
    }
  }
  
  public Codex currentCodex() {
    return selectedCodexForDetails;
  }
  
  public void startClient() {
    Thread.ofPlatform()
          .daemon()
          .start(() -> {
            try {
              client.launch();
            } catch (IOException e) {
              logger.severe(STR."Client was interrupted. \{e.getMessage()}");
            } finally {
              mustClose = true;
            }
          });
  }
  
  public void startDisplay() {
    Thread.ofPlatform()
          .daemon()
          .start(() -> {
            try {
              display.startLoop();
            } catch (IOException | InterruptedException e) {
              logger.severe(STR."The console display was interrupted. \{e.getMessage()}");
            } finally {
              mustClose = true;
            }
          });
  }
  
  public void drawDisplay() throws IOException {
    synchronized (lock) {
      display.clear();
      display.draw();
    }
  }
  
  public void clearDisplayAndMore(int numberOfLineBreak) {
    View.clearDisplayAndMore(lines, numberOfLineBreak);
  }
  
  private void exitNicely() {
    // inputReader.stop();
    // display.stop();
    // client.shutdown();
    // just for now
    System.exit(0);
  }
  
  /**
   * Create a splash screen logo with a list of messages
   * showing le title "Chadow" in ascii art and the version
   */
  private List<Message> splashLogo() {
    return List.of(
        new Message("", "┏┓┓    ┓", 0),
        new Message("", "┃ ┣┓┏┓┏┫┏┓┓┏┏", 0),
        new Message("", "┗┛┗┗┗┗┗┗┗┛┗┛┛ v1.0.0 - Bastos & Sebbah", 0)
    );
  }
  
  /**
   * Process the message or the command
   *
   * @param input the user input
   * @return true if the user can type again, otherwise it's the view's turn.
   */
  public boolean processInput(String input) throws IOException {
    logger.info(STR."Processing input: \{input}");
    return processComplexCommands(input).orElseGet(() -> switch (input) {
      case ":d" -> {
        try {
          drawDisplay();
        } catch (IOException e) {
          logger.severe(STR."Error while drawing display.\{e.getMessage()}");
          throw new UncheckedIOException(e);
        }
        yield true;
      }
      case ":c", ":chat" -> {
        display.setMode(Mode.CHAT_LIVE_REFRESH);
        yield false;
      }
      case ":exit" -> {
        exitNicely();
        yield false;
      }
      case ":h", ":help" -> {
        display.setMode(Mode.HELP_SCROLLER);
        yield true;
      }
      default -> processCommandInContext(input);
    });
  }
  
  private boolean processInputModeCodexDetails(String input) {
    switch (input) {
      case ":share" -> {
        if(currentCodex().isComplete()) {
          if(currentCodex().isSharing()) {
            currentCodex().stopSharing();
          }else {
            currentCodex().share();
          }
          display.setMode(Mode.CODEX_DETAILS);
        }
      }
      case ":download" -> {
        if(!currentCodex().isComplete()) {
          if(currentCodex().isDownloading()) {
            currentCodex().stopDownloading();
          }else {
            currentCodex().download();
          }
          display.setMode(Mode.CODEX_DETAILS);
        }
      }
      default -> {
        return processInputModeScroller(input);
      }
    }
    return true;
  }
  
  private boolean processCommandInContext(String input) {
    return switch (display.getMode()) {
      case CHAT_LIVE_REFRESH -> {
        try {
          yield processInputModeLiveRefresh(input);
        } catch (InterruptedException e) {
          logger.severe(STR."Interrupted while processing input in live refresh mode.\{e.getMessage()}");
          throw new RuntimeException(e);
        }
      }
      case CHAT_SCROLLER, USERS_SCROLLER, HELP_SCROLLER -> processInputModeScroller(input);
      case CODEX_SEARCH -> throw new Error("Not implemented");
      case CODEX_DETAILS -> processInputModeCodexDetails(input);
      case CODEX_LIST -> processInputModeCodexList(input);
    };
  }
  
  private boolean processInputModeCodexList(String input) {
    return switch(input) {
      case  ":see" -> {
        // select codex
        yield true;
      }
      default -> false;
    };
  }
  
  private boolean processInputModeLiveRefresh(String input) throws InterruptedException {
    if (!input.startsWith(":") && !input.isBlank()) {
      client.sendMessage(input);
      logger.info(STR."send message: \{input}");
    }
    switch (input) {
      case ":u", ":users" -> {
        display.setMode(Mode.USERS_SCROLLER);
        return true;
      }
      case ":m", ":msg" -> {
        display.setMode(Mode.CHAT_SCROLLER);
        return true;
      }
    }
    return false;
  }
  
  private boolean processInputModeScroller(String input) {
    switch (input) {
      case "e" -> display.scrollerPageUp();
      case "s" -> display.scrollerPageDown();
      case "t" -> display.scrollerTop();
      case "b" -> display.scrollerBottom();
      case "r" -> display.scrollerLineUp();
      case "d" -> display.scrollerLineDown();
    }
    return true;
  }
  
  private Optional<Boolean> processComplexCommands(String input) throws IOException {
    return commandResize(input)
        .or(() -> {
          try {
            return commandNew(input);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          } catch (NoSuchAlgorithmException e) {
            logger.severe(STR."Error while creating new codex. The \{e.getMessage()}");
            throw new RuntimeException(e);
          } finally {
            mustClose = true;
          }
        })
        .or(() -> commandCdxDetails(input)) ;
  }
  
  private Optional<Boolean> commandResize(String input) throws IOException {
    if (input.startsWith(":r ")) {
      var split = input.split(" ");
      if (split.length == 3) {
        try {
          var x = Integer.parseInt(split[1]);
          var y = Integer.parseInt(split[2]);
          if (x <= 0 || y <= 0) {
            return Optional.of(false);
          }
          cols = x;
          lines = y;
          display.setDimensions(x, y);
          drawDisplay();
        } catch (NumberFormatException e) {
          return Optional.of(false);
        }
        return Optional.of(false);
      }
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> commandNew(String input) throws IOException, NoSuchAlgorithmException {
    var patternNew = Pattern.compile(":new\\s+(.*),\\s+(.*)");
    var matcher = patternNew.matcher(input);
    if (matcher.find()) {
      var codexName = matcher.group(1);
      var path = matcher.group(2);
      logger.info(STR.":create \{path}\n");
      var codex = Codex.fromPath(codexName, path);
      selectedCodexForDetails = codex;
      codexes.put(Codex.fingerprintAsString(codex.id()), codex);
      display.setMode(Mode.CODEX_DETAILS);
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> commandCdxDetails(String input) {
    var patternRetrieve = Pattern.compile(":cdx:(.*)");
    var matcherRetrieve = patternRetrieve.matcher(input);
    if (matcherRetrieve.find()) {
      var fingerprint = matcherRetrieve.group(1);
      logger.info(STR.":cdx: \{fingerprint}\n");
      var codex = codexes.get(fingerprint);
      if (codex == null) {
        logger.info("Codex not found");
        // client.retrieveCodex(fingerprint);
        return Optional.of(false);
      }
      selectedCodexForDetails = codex;
      display.setMode(Mode.CODEX_DETAILS);
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private void fillWithFakeData() {
    var users = new String[]{"test", "Morpheus", "Trinity", "Neo", "Flynn", "Alan", "Lora", "Gandalf", "Bilbo", "SKIDROW", "Antoine"};
    this.users.addAll(Arrays.asList(users));
    var messages = new Message[]{
        new Message("test", "test", System.currentTimeMillis()),
        new Message("test", "hello how are you", System.currentTimeMillis()),
        new Message("Morpheus", "Wake up, Neo...", System.currentTimeMillis()),
        new Message("Morpheus", "The Matrix has you...", System.currentTimeMillis()),
        new Message("Neo", "what the hell is this", System.currentTimeMillis()),
        new Message("Alan1", "Master CONTROL PROGRAM\nRELEASE TRON JA 307020...\nI HAVE PRIORITY ACCESS 7", System.currentTimeMillis()),
        new Message("SKIDROW", "Here is the codex of the FOSS (.deb) : cdx:1eb49a28a0c02b47eed4d0b968bb9aec116a5a47", System.currentTimeMillis()),
        new Message("Antoine", "Le lien vers le sujet : http://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html", System.currentTimeMillis())
    };
    this.publicMessages.addAll(splashLogo());
    this.publicMessages.addAll(Arrays.asList(messages));
  }
}