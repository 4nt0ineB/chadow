package fr.uge.chadow.client;

import fr.uge.chadow.cli.InputReader;
import fr.uge.chadow.cli.display.Display;
import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.core.reader.Message;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ClientConsoleController {
  private final static Logger logger = Logger.getLogger(ClientConsoleController.class.getName());
  private final Client client;
  private final InputReader inputReader;
  private final Display display;
  private final ArrayList<Message> publicMessages = new ArrayList<>();
  private final HashMap<String, List<Message>> privateMessages = new HashMap<>();
  private final SortedSet<String> users = new TreeSet<>();
  private final HashMap<String, Codex> codexes = new HashMap<>();
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
    //System.err.println("----------------------Running with " + lines + " lines and " + cols + " columns");
    
    // client
    Thread.ofPlatform()
          .daemon()
          .start(() -> {
            try {
              client.launch();
            } catch (IOException e) {
              // TODO: handle exception -> inform the user and quit
              throw new RuntimeException(e);
            }
          });
    display.draw();
    // thread that manages the display
    Thread.ofPlatform()
          .daemon()
          .start(display::startLoop);
    // for dev: fake messages
    fillWithFakeData();
    client.subscribe(this::addMessage);
    // Thread that manages the user inputs
    try {
      inputReader.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      logger.severe("User input reader was interrupted." + e.getMessage());
    }
  }
  
  public int numberofMessages() {
    synchronized (lock) {
      return publicMessages.size();
    }
  }
  
  public void addMessage(Message message) {
    synchronized (lock) {
      publicMessages.add(message);
    }
  }
  
  /**
   * Process the message or the command
   *
   * @param input
   * @return true if the user can type again, otherwise it's the view's turn.
   * @throws InterruptedException
   */
  public boolean processInput(String input) throws InterruptedException, IOException {
    if (input.startsWith(":r ")) {
      var split = input.split(" ");
      if (split.length == 3) {
        try {
          var x = Integer.parseInt(split[1]);
          var y = Integer.parseInt(split[2]);
          if (x <= 0 || y <= 0) {
            return false;
          }
          cols = x;
          lines = y;
          display.setDimensions(x, y);
          drawDisplay();
        } catch (NumberFormatException e) {
          return false;
        }
        return false;
      }
    }
    // pattern to get a file path
    // https://stackoverflow.com/a/33021907
    var pattern = Pattern.compile(":create\\s+(.*)");
    var matcher = pattern.matcher(input);
    if (matcher.find()) {
      var path = matcher.group(1);
      logger.info(":create " + path + "\n");
      var localCodex = Codex.fromPath("My first codex", path);
      codexes.put(localCodex.getIdAsString(), localCodex);
      return false;
    }
    
    return switch (input) {
      case ":m", ":message" -> {
        display.setMode(Mode.CHAT_SCROLLER);
        yield true;
      }
      case ":u", ":users" -> {
        display.setMode(Mode.USERS_SCROLLER);
        yield true;
      }
      case ":c", ":chat" -> {
        display.setMode(Mode.CHAT_LIVE_REFRESH);
        yield false;
      }
      case ":create" -> {
        //var localCodex = LocalCodex.fromDirectory();
        
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
      // specific mode commands
      default -> switch (display.getMode()) {
        case CHAT_LIVE_REFRESH -> processInputModeLiveRefresh(input);
        case CHAT_SCROLLER, USERS_SCROLLER, HELP_SCROLLER -> processInputModeScroller(input);
        case CODEX_SEARCH -> false;
        case CODEX_DETAILS -> false;
      };
    };
  }
  
  public void drawDisplay() throws IOException {
    synchronized (lock) {
      display.clear();
      display.draw();
    }
    
  }
  
  private boolean processInputModeLiveRefresh(String input) throws InterruptedException {
    if (!input.startsWith(":") && !input.isBlank()) {
      client.sendMessage(input);
    }
    return false;
  }
  
  private boolean processInputModeScroller(String input) {
    switch (input) {
      case "e" -> display.scrollerUp();
      case "s" -> display.scrollerDown();
    }
    return true;
  }
  
  public void clearDisplayAndMore(int numberOfLineBreak) {
    View.clearDisplayAndMore(lines, numberOfLineBreak);
  }
  
  private void fillWithFakeData() {
    var users = new String[]{"test", "Morpheus", "Trinity", "Neo", "Flynn", "Alan", "Lora", "Gandalf", "Bilbo", "SKIDROW", "Antoine"};
    this.users.addAll(Arrays.asList(users));
    var messages = new Message[]{
        new Message("test", "test", System.currentTimeMillis()),
        new Message("test", "hello how are you", System.currentTimeMillis()),
        new Message("Morpheus", "Wake up, Neo...", System.currentTimeMillis()),
        new Message("Morpheus", "The Matrix has you...", System.currentTimeMillis()),
        new Message("Morpheus", "Follow the white rabbit", System.currentTimeMillis()),
        new Message("Neo", "what the hell is this", System.currentTimeMillis()),
        new Message("Neo", "Just going to bed now", System.currentTimeMillis()),
        new Message("Alan1", "Master CONTROL PROGRAM\nRELEASE TRON JA 307020...\nI HAVE PRIORITY ACCESS 7", System.currentTimeMillis()),
        new Message("SKIDROW", "Here is the codex of the FOSS (.deb) : cdx:1eb49a28a0c02b47eed4d0b968bb9aec116a5a47", System.currentTimeMillis()),
        new Message("Antoine", "Le lien vers le sujet : http://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html", System.currentTimeMillis())
    };
    this.publicMessages.addAll(splashLogo());
    this.publicMessages.addAll(Arrays.asList(messages));
  }
  
  private void exitNicely() {
    // inputReader.stop();
    // display.stop();
    // client.stop();
    
    // just for now
    System.exit(0);
  }
  
  /**
   * Create a splash screen logo with a list of messages
   * showing le title "Chadow" in ascii art and the version
   */
  private List<Message> splashLogo() {
    return List.of(
        new Message("", "╔═╗┬ ┬┌─┐┌┬┐┌─┐┬ ┬", 0),
        new Message("", "║  ├─┤├─┤ │││ ││││", 0),
        new Message("", "╚═╝┴ ┴┴ ┴─┴┘└─┘└┴┘ v1.0.0 - by Bastos & Sebbah", 0)
    );
  }
  
  public enum Mode {
    CHAT_LIVE_REFRESH,
    CHAT_SCROLLER,
    USERS_SCROLLER,
    HELP_SCROLLER,
    CODEX_SEARCH,
    CODEX_DETAILS
  }
}

/*

╔═╗┬ ┬┌─┐┌┬┐┌─┐┬ ┬
║  ├─┤├─┤ │││ ││││
╚═╝┴ ┴┴ ┴─┴┘└─┘└┴┘
   ___ _               _
  / __\ |__   __ _  __| | _____      __
 / /  | '_ \ / _` |/ _` |/ _ \ \ /\ / /
/ /___| | | | (_| | (_| | (_) \ V  V /
\____/|_| |_|\__,_|\__,_|\___/ \_/\_/
 

   ____ _               _
  / ___| |__   __ _  __| | _____      __
 | |   | '_ \ / _` |/ _` |/ _ \ \ /\ / /
 | |___| | | | (_| | (_| | (_) \ V  V /
  \____|_| |_|\__,_|\__,_|\___/ \_/\_/
  

















 */