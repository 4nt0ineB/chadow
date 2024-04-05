package fr.uge.chadow.client;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.cli.InputReader;
import fr.uge.chadow.cli.display.*;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static fr.uge.chadow.cli.display.View.colorize;

public class ClientController {
  
  

  
  public enum Mode {
    CHAT_LIVE_REFRESH, // default
    CHAT_SCROLLER,     // :c, :chat
    USERS_SCROLLER,    // :u, :users
    HELP_SCROLLER,     // :h, :help
    CODEX_SEARCH,      // :search <name>
    CODEX_DETAILS,     // :cdx:<fingerprint>
    CODEX_LIST,        // :mycdx
    PRIVATE_MESSAGE_LIVE,   // :w, :whisper <username>
    PRIVATE_MESSAGE_SCROLLER // :m, :msg
  }
  //// app management
  private final static Logger logger = Logger.getLogger(ClientController.class.getName());
  private final InetSocketAddress serverAddress;
  private final ClientAPI api;
  private volatile boolean mustClose = false;
  //// display management
  private Display display;
  private View currentView;
  private Selector<?> currentSelector;
  private final ChatView mainView;
  private final PrivateMessageView privateMessageView;
  private final AtomicBoolean viewCanRefresh = new AtomicBoolean(true);
  private Codex selectedCodexForDetails;
  private Mode mode;
  private int lines;
  private int cols;
  
  public ClientController(String login, InetSocketAddress serverAddress, int lines, int cols) {
    Objects.requireNonNull(login);
    Objects.requireNonNull(serverAddress);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.lines = lines;
    this.cols = cols;
    this.serverAddress = serverAddress;
    api = new ClientAPI(login);
    mainView = new ChatView(lines, cols, api);
    currentView = mainView;
    privateMessageView = new PrivateMessageView(lines, cols, api);
    mode = Mode.CHAT_LIVE_REFRESH;
  }
  
  public void stopAutoRefresh() throws IOException {
    viewCanRefresh.set(false);
    clearDisplayAndMore();
    drawDisplay();
  }
  
  public AtomicBoolean viewCanRefresh() {
    return viewCanRefresh;
  }
  
  public View currentView() {
      return currentView;
  }
  
  public void start() throws IOException {
    // Creates the display
    display = new Display(lines, cols, this, api);
    InputReader inputReader = new InputReader(this);
    logger.info(STR."Starting console (\{lines} rows \{cols} cols");
    // Starts the client
    startClient(new Client(serverAddress, api));
    // Draws the display one time at the beginning,
    // even if the client is not connected
    display.draw();
    // Starts display thread
    startDisplay();
    // start the input reader
    try {
      inputReader.start();
    } catch (UncheckedIOException | IOException | InterruptedException | NoSuchAlgorithmException e) {
      logger.severe(STR."The client was interrupted.\{e.getMessage()}");
    } finally {
      exitNicely();
    }
  }
  
  public void startClient(Client client) {
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
  
  public Codex currentCodex() {
    return selectedCodexForDetails;
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
      display.clear();
      display.draw();
  }
  
  public void clearDisplayAndMore() {
    View.clearDisplayAndMore(lines, lines);
  }
  
  private void exitNicely() {
    // inputReader.stop();
    // display.stop();
    // client.shutdown();
    // just for now
    System.exit(0);
  }
  
  /**
   * Process the message or the command
   *
   * @param input the user input
   * @return true if the user can type again, otherwise it's the view's turn.
   */
  public void processInput(String input) throws IOException {
    logger.info(STR."Processing input: \{input}");
    var letwrite =  processComplexCommands(input).orElseGet(() -> switch (input) {
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
        mode = Mode.CHAT_LIVE_REFRESH;
        mainView.setMode(mode);
        currentView = mainView;
        try {
          drawDisplay();
          logger.info(STR."Processing input: \{input} 2");
        } catch (IOException e) {
          logger.severe(STR."Error while drawing display.\{e.getMessage()}");
          throw new UncheckedIOException(e);
        }
        yield false;
      }
      case ":mycdx" -> {
        mode = Mode.CODEX_LIST;
        currentSelector = View.selectorFromList("My cdx", lines, cols, api.codexes(), View::codexShortDescription);
        currentView = currentSelector;
        yield true;
      }
      case ":exit" -> {
        exitNicely();
        yield false;
      }
      case ":h", ":help" -> {
        currentView = helpView();
        mode = Mode.HELP_SCROLLER;
        currentView.scrollTop();
        yield true;
      }
      default -> processCommandInContext(input);
    });
    clearDisplayAndMore();
    viewCanRefresh.set(!letwrite);
    drawDisplay();
  }
  
  private boolean processInputModeCodexDetails(String input) {
    switch (input) {
      case ":share" -> {
        if(currentCodex().isComplete()) {
          if(currentCodex().isSharing()) {
            currentCodex().stopSharing();
          }else {
            currentCodex().share();
            api.propose(currentCodex());
          }
          mode = Mode.CODEX_DETAILS;
          currentView = codexView(currentCodex());
          currentView.scrollTop();
        }
      }
      case ":download" -> {
        if(!currentCodex().isComplete()) {
          if(currentCodex().isDownloading()) {
            api.stopDownloading(currentCodex().idToHexadecimal());
          }else {
            api.download(currentCodex().idToHexadecimal());
          }
          mode = Mode.CODEX_DETAILS;
          currentView = codexView(currentCodex());
          currentView.scrollTop();
        }
      }
      default -> {
        return processInputModeScroller(input);
      }
    }
    return true;
  }
  
  private boolean processCommandInContext(String input) {
    return switch (mode) {
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
      case PRIVATE_MESSAGE_LIVE -> processInputModePrivateMessageLive(input);
      case PRIVATE_MESSAGE_SCROLLER ->{
        if(input.equals(":w")) {
          mode = Mode.PRIVATE_MESSAGE_LIVE;
          mainView.setMode(mode);
          currentView = mainView;
          currentView.scrollTop();
          yield true;
        }
        yield processInputModeScroller(input);
      }
    };
  }
  
  private boolean processInputModePrivateMessageLive(String input) {
    switch (input) {
      case ":m", ":msg" -> {
        mode = Mode.PRIVATE_MESSAGE_SCROLLER;
        privateMessageView.setMode(mode);
        currentView = privateMessageView;
        currentView.scrollTop();
        return true;
      }
      default -> {
        if(!input.startsWith(":") && !input.isBlank()) {
          var receiver = privateMessageView.receiver();
          api.whisper(receiver.id(), input);
        }
        return false;
      }
    }
  }
  
  private boolean processInputModeCodexList(String input) {
    switch (input) {
      case "y" -> currentSelector.selectorUp();
      case "h" -> currentSelector.selectorDown();
      case ":s", ":see" ->{
        mode = Mode.CODEX_DETAILS;
        selectedCodexForDetails = (Codex) currentSelector.get();
        currentView = codexView(selectedCodexForDetails);
        currentView.scrollTop();
        logger.info(STR."see cdx: \{View.bytesToHexadecimal(selectedCodexForDetails.id())}");
      }
    }
    return true;
  }
  
  private boolean processInputModeLiveRefresh(String input) throws InterruptedException {
    switch (input) {
      case ":u", ":users" -> {
        logger.info("focusing users list");
        mode = Mode.USERS_SCROLLER;
        mainView.setMode(mode);
        currentView = mainView;
        return true;
      }
      case ":m", ":msg" -> {
        logger.info("focusing messages list");
        mode = Mode.CHAT_SCROLLER;
        mainView.setMode(mode);
        currentView = mainView;
        return true;
      }
      default -> {
        if(!input.startsWith(":") && !input.isBlank()) {
          api.yell(input);
        }
        return false;
      }
    }
  }
  
  private boolean processInputModeScroller(String input) {
    switch (input) {
      case "e" -> currentView.scrollPageUp();
      case "s" -> currentView.scrollPageDown();
      case "t" -> currentView.scrollTop();
      case "b" -> currentView.scrollBottom();
      case "r" -> currentView.scrollLineUp();
      case "d" -> currentView.scrollLineDown();
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
        .or(() -> {
          try {
            return commandCdxDetails(input);
          } catch (InterruptedException e) {
            throw new RuntimeException(e); // @Todo
          }
        })
        .or(() -> commandWhisper(input))
        ;
  }
  
  private Optional<Boolean> commandWhisper(String input) {
    var patternWhisper = Pattern.compile(":w\\s+(.*)");
    var matcherWhisper = patternWhisper.matcher(input);
    if (matcherWhisper.find()) {
      var receiverUsername = matcherWhisper.group(1);
      mode = Mode.PRIVATE_MESSAGE_LIVE;
      var receiver = api.getRecipient(receiverUsername);
      if(receiver.isEmpty()) {
        return Optional.of(true);
      }
      privateMessageView.setReceiver(receiver.orElseThrow());
      mode = Mode.PRIVATE_MESSAGE_LIVE;
      privateMessageView.setMode(mode);
      currentView = privateMessageView;
      try {
        drawDisplay();
      } catch (IOException e) {
        logger.severe(STR."Error while drawing display.\{e.getMessage()}");
        throw new UncheckedIOException(e);
      }
      
      return Optional.of(false);
    }
    return Optional.empty();
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
      api.addCodex(codex);
      mode = Mode.CODEX_DETAILS;
      currentView = codexView(codex);
      currentView.scrollTop();
      logger.info(STR."Codex created with id: \{codex.idToHexadecimal()}");
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> commandCdxDetails(String input) throws InterruptedException {
    var patternRetrieve = Pattern.compile(":cdx:(.*)");
    var matcherRetrieve = patternRetrieve.matcher(input);
    if (matcherRetrieve.find()) {
      var fingerprint = matcherRetrieve.group(1);
      logger.info(STR.":cdx: \{fingerprint}\n");
      var codex = api.getCodex(fingerprint);
      selectedCodexForDetails = codex.orElseThrow();
      mode = Mode.CODEX_DETAILS;
      currentView = codexView(selectedCodexForDetails);
      currentView.scrollTop();
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private Scrollable helpView() {
    var txt = """
        ##  ┓┏  ┓
        ##  ┣┫┏┓┃┏┓
        ##  ┛┗┗━┗┣┛
        ##       ┛
        
        'scrollable':
          e - scroll one page up
          s - scroll one page down
          r - scroll one line up
          d - scroll one line down
          t - scroll to the top
          b - scroll to the bottom
          
        'selectable' (is scrollable):
          y - move selector up
          h - move selector down
          :s, :see - Select the item
          
        [GLOBAL COMMANDS]
          :h, :help - Display this help (scrollable)
          :c, :chat - Back to the [CHAT] in live reload
          :w, :whisper <username> - Start a new private discussion with a user
          :d - Update and draw the display
          :r <lines> <columns> - Resize the view
          :new <codexName>, <path> - Create a codex from a file or directory
          \tand display the details of new created [CODEX] info (mind the space between , and <path>)
          
          :mycdx - Display the list of your codex (selectable)
          :cdx:<SHA-1> - Retrieves and display the [CODEX] info with the given SHA-1
          \tif the codex is not present locally, the server will be interrogated        (TODO)
          :exit - Exit the application                                                  (WIP)
          
        [CHAT]
          when the live reload is disabled (indicated by the coloured input field)
          any input not starting with ':' will be considered as a message to be sent
          
          :m, :msg - Focus on chat (scrollable)
          :u, :users - Focus on the users list (scrollable)
          
        [PRIVATE MESSAGES]
          :m, :msg - Focus on the chat (scrollable)
          :w - Enables back live reload
          
        [CODEX]
        (scrollable)
        :share - Share/stop sharing the codex
        :download - Download/stop downloading the codex
        
        """;
    
    return View.scrollableFromString("Help", lines, cols, txt);
  }
  
  private Scrollable codexView(Codex codex) {
    var sb = new StringBuilder();
    
    var splash = """
        ## ┏┓   ┓
        ## ┃ ┏┓┏┫┏┓┓┏
        ## ┗┛┗┛┻┗┗━┛┗
        
        """;
    sb.append(splash);
    sb.append("cdx:")
      .append(View.bytesToHexadecimal(codex.id()))
      .append("\n");
    if (codex.isComplete()) {
      sb.append(CLIColor.BLUE)
        .append("▓ Complete\n")
        .append(CLIColor.RESET);
    }
    if (codex.isDownloading() || codex.isSharing()) {
      sb.append(CLIColor.ITALIC)
        .append(CLIColor.BOLD)
        .append(CLIColor.ORANGE)
        .append(codex.isDownloading() ? "▓ Downloading ..." : codex.isSharing() ? "▓ Sharing... " : "")
        .append(CLIColor.RESET)
        .append("\n\n");
    }
    
    sb.append(colorize(CLIColor.BOLD, "Title: "))
      .append(codex.name())
      .append("\n");
    var infoFiles = codex.files();
    sb.append(colorize(CLIColor.BOLD, "Number of files:  "))
      .append(codex.files()
                   .size())
      .append("\n");
    sb.append(colorize(CLIColor.BOLD, "Total size:   "))
      .append(View.bytesToHumanReadable(codex.totalSize()))
      .append("\n");
    sb.append("Local Path: ").append(codex.root()).append("\n\n");
    sb.append(colorize(CLIColor.BOLD, "Files:  \n"));
    infoFiles.stream()
             .collect(Collectors.groupingBy(Codex.FileInfo::absolutePath))
             .forEach((dir, files) -> {
               sb.append(colorize(CLIColor.BOLD, STR."[\{dir}]\n"));
               files.forEach(file -> sb.append("\t- ")
                                       .append(CLIColor.BOLD)
                                       .append("%10s".formatted(View.bytesToHumanReadable(file.length())))
                                       .append("  ")
                                       .append("%.2f%%".formatted(file.completionRate() * 100))
                                       .append("  ")
                                       .append(CLIColor.RESET)
                                       .append(file.filename())
                                       .append("\n"));
             });
    return View.scrollableFromString(STR."[Codex] \{codex.name()}", lines, cols, sb.toString());
  }
}