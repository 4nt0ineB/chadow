package fr.uge.chadow.client;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.cli.InputReader;
import fr.uge.chadow.cli.display.Display;
import fr.uge.chadow.cli.display.InfoBar;
import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.cli.display.view.*;
import fr.uge.chadow.core.protocol.field.Codex;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static fr.uge.chadow.cli.display.View.colorize;

public class ClientController {

  //// app management
  private final static Logger logger = Logger.getLogger(ClientController.class.getName());
  private final InetSocketAddress serverAddress;
  private final ClientAPI api;
  //// display management
  private final AtomicBoolean viewCanRefresh = new AtomicBoolean(true);
  private final ChatView mainView;
  private final PrivateMessageView privateMessageView;
  private volatile boolean mustClose = false;
  private InfoBar infoBar;
  private Display display;
  private SelectorView<?> currentSelector;
  private View currentView;
  private CodexController.CodexStatus selectedCodexForDetails;
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
  
  public boolean mustClose() {
    return mustClose || !api.isConnected();
  }
  
  public void start() {
    // Starts the client thread
    startClient();
    if (!api.isConnected()) {
      logger.severe("The client was not able to connect to the server.");
      var errorView = new CantConnectScreenView(lines, cols);
      errorView.draw();
      return;
    }
    // Starts display thread
    infoBar = new InfoBar(cols, this::updateInfoBar);
    display = new Display(lines, cols, this, infoBar, api);
    InputReader inputReader = new InputReader(this);
    setCurrentView(mainView);
    startDisplay();
    // start the input reader
    try {
      inputReader.start();
    } catch (UncheckedIOException | IOException | InterruptedException | NoSuchAlgorithmException e) {
      logger.severe(STR."The console was interrupted.\{e.getCause()}");
    } finally {
      exitNicely();
    }
  }
  
  public void updateInfoBar(InfoBar infoBar) {
    infoBar.clear();
    var unreadDiscussions = api.discussionWithUnreadMessages();
    unreadDiscussions.forEach(discussion -> {
      var txt = (STR."<\{CLIColor.YELLOW_BACKGROUND}\{CLIColor.BOLD}\{CLIColor.BLACK}%s\{CLIColor.RESET}>")
          .formatted(discussion.username());
      infoBar.addInfo(txt);
    });
  }

  public void startClient() {
    try {
      Thread.ofPlatform()
            .daemon()
            .start(() -> {
              try {
                logger.info("Client starts");
                new Client(serverAddress, api).launch();
              } catch (IOException e) {
                logger.severe(STR."The client was interrupted. \{e.getMessage()}");
              }
            });
    } catch (UncheckedIOException e) {
      logger.severe(STR."The client was interrupted while starting.\{e.getCause()}");
      return;
    }
    try {
      api.waitForConnection();
    } catch (InterruptedException e) {
      logger.severe(STR."The client was interrupted while waiting for connection.\{e.getCause()}");
    }
  }
  
  public CodexController.CodexStatus currentCodex() {
    return selectedCodexForDetails;
  }
  
  public void startDisplay() {
    Thread.ofPlatform()
          .daemon()
          .start(() -> {
            try {
              logger.info(STR."Display starts with (\{lines} rows \{cols} cols)");
              display.startLoop();
            } catch (IOException | InterruptedException e) {
              logger.severe(STR."The console display was interrupted. \{e.getMessage()}");
            } finally {
              mustClose = true;
            }
          });
  }
  
  public void drawDisplay() {
    try {
      display.clear();
      display.draw();
    } catch (IOException e) {
      logger.severe(STR."Error while drawing display.\{e.getMessage()}");
      mustClose = true;
    }
  }
  
  public void clearDisplayAndMore() {
    View.clearDisplayAndMore(lines, lines);
  }
  
  private void exitNicely() {
    // just for now
    api.close();
    mustClose = true;
    // client.shutdown();
    System.exit(0);
  }
  
  private void setCurrentView(View view) {
    currentView = view;
    display.setView(view);
  }
  
  /**
   * Process the message or the command
   *
   * @param input the user input
   */
  public void processInput(String input) {
    logger.info(STR."Processing input: \{input}");
    var canRetype = globalCommandDisplayChat(input)
        .or(() -> globalCommandDisplayDM(input))
        .or(() -> globalCommandDisplayCodexes(input))
        .or(() -> globalCommandHelp(input))
        .or(() -> globalCommandExit(input))
        .or(() -> globalCommandResize(input))
        .or(() -> globalCommandNewCodex(input))
        .or(() -> globalCommandCodexDetail(input))
        .or(() -> globalCommandWhisper(input))
        .or(() -> globalCommandDraw(input))
        .or(() -> Optional.of(processCommandInContext(input)));
    clearDisplayAndMore();
    viewCanRefresh.set(!canRetype.orElse(false));
    drawDisplay();
  }

  private boolean processCommandInContext(String input) {
    return switch (mode) {
      case CHAT_LIVE_REFRESH -> processInputModeLiveRefresh(input);
      case CHAT_SCROLLER, USERS_SCROLLER, HELP_SCROLLER -> processInputModeScroller(input);
      case CODEX_SEARCH -> throw new Error("Not implemented");
      case CODEX_DETAILS -> processInputModeCodexDetails(input);
      case CODEX_LIST -> processInputModeCodexList(input);
      case DIRECT_MESSAGES_LIVE -> processInputModeDirectMessagesLive(input);
      case DIRECT_MESSAGES_SCROLLER -> processInputModeDMScroller(input);
      case DIRECT_MESSAGES_LIST -> processCommandDirectMessagesList(input);
    };
  }

  private Optional<Boolean> globalCommandDraw(String input) {
    if (input.equals(":d")) {
      drawDisplay();
      return Optional.of(!viewCanRefresh.get());
    }
    return Optional.empty();
  }

  private Optional<Boolean> globalCommandDisplayDM(String input) {
    if (input.equals(":ws") || input.equals(":whispers")) {
      mode = Mode.DIRECT_MESSAGES_LIST;
      var list = api.getAllDirectMessages()
                    .stream()
                    .sorted(Comparator.comparingLong(dm -> dm.getLastMessage()
                                                             .map(WhisperMessage::epoch)
                                                             .orElseGet(() -> Long.MIN_VALUE)))
                    .toList();
      currentSelector = View.selectorFromList("Direct messages", lines, cols, list, directMessages -> {
        var str = View.directMessageShortDescription(directMessages);
        var lengthWithoutEscapeCodes = CLIColor.countLengthWithoutEscapeCodes(str);
        var numberOfEscapeCodes = str.length() - lengthWithoutEscapeCodes;
        var length = Math.min(str.length(), cols + numberOfEscapeCodes);
        return str.substring(0, length) + CLIColor.RESET;
      });
      setCurrentView(currentSelector);
      logger.info("Displaying whispers");
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private Optional<Boolean> globalCommandDisplayCodexes(String input) {
    if (input.equals(":mycdx")) {
      mode = Mode.CODEX_LIST;
      currentSelector = View.selectorFromList("My cdx", lines, cols, api.codexes(), View::codexShortDescription);
      setCurrentView(currentSelector);
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private Optional<Boolean> globalCommandHelp(String input) {
    if (input.equals(":h") || input.equals(":help")) {
      setCurrentView(helpView());
      mode = Mode.HELP_SCROLLER;
      currentView.scrollTop();
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private Optional<Boolean> globalCommandExit(String input) {
    if (input.equals(":exit")) {
      exitNicely();
      return Optional.of(false);
    }
    return Optional.empty();
  }

  private Optional<Boolean> globalCommandDisplayChat(String input) {
    if (input.equals(":c") || input.equals(":chat")) {
      mode = Mode.CHAT_LIVE_REFRESH;
      mainView.setMode(mode);
      setCurrentView(mainView);
      drawDisplay();
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private boolean processInputModeCodexDetails(String input) {
    switch (input) {
      case ":share" -> {
        var codexId = currentCodex().id();
        if (currentCodex().isComplete()) {
          if (api.isSharing(codexId)) {
            api.stopSharing(codexId);
          } else {
            api.share(codexId);
          }
          mode = Mode.CODEX_DETAILS;
          setCurrentView(codexView(currentCodex()));
          currentView.scrollTop();
        }
      }
      case ":download" -> {
        var codexId = currentCodex().id();
        if (!currentCodex().isComplete()) {
          if (api.isDownloading(codexId)) {
            api.stopDownloading(currentCodex().id());
          } else {
            api.download(currentCodex().id());
          }
          mode = Mode.CODEX_DETAILS;
          setCurrentView(codexView(currentCodex()));
          currentView.scrollTop();
        }
      }
      default -> {
        return processInputModeScroller(input);
      }
    }
    return true;
  }
  
  private boolean processInputModeDMScroller(String input) {
    if (input.equals(":w")) {
      mode = Mode.DIRECT_MESSAGES_LIVE;
      mainView.setMode(mode);
      setCurrentView(mainView);
      currentView.scrollTop();
      return true;
    }
    return processInputModeScroller(input);
  }
  
  private boolean processCommandDirectMessagesList(String input) {
    switch (input) {
      case "y" -> currentSelector.selectorUp();
      case "h" -> currentSelector.selectorDown();
      case ":s", ":see" -> {
        var selected = (DirectMessages) currentSelector.get();
        mode = Mode.DIRECT_MESSAGES_LIVE;
        privateMessageView.setPrivateDiscussion(selected);
        privateMessageView.setMode(mode);
        setCurrentView(privateMessageView);
        currentView.scrollTop();
      }
    };
    return true;
  }

  private boolean processInputModeDirectMessagesLive(String input) {
    switch (input) {
      case ":m", ":msg" -> {
        mode = Mode.DIRECT_MESSAGES_SCROLLER;
        privateMessageView.setMode(mode);
        setCurrentView(privateMessageView);
        currentView.scrollTop();
        return true;
      }
      default -> {
        if (!input.startsWith(":") && !input.isBlank()) {
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
      case ":s", ":see" -> {
        mode = Mode.CODEX_DETAILS;
        selectedCodexForDetails = (CodexController.CodexStatus) currentSelector.get();
        logger.info(STR."see cdx: \{selectedCodexForDetails.id()}");
        setCurrentView(codexView(selectedCodexForDetails));
        currentView.scrollTop();
        logger.info(STR."see cdx: \{selectedCodexForDetails.id()}");
      }
    }
    return true;
  }
  
  private boolean processInputModeLiveRefresh(String input) {
    switch (input) {
      case ":u", ":users" -> {
        logger.info("focusing users list");
        mode = Mode.USERS_SCROLLER;
        mainView.setMode(mode);
        setCurrentView(mainView);
        return true;
      }
      case ":m", ":msg" -> {
        logger.info("focusing messages list");
        mode = Mode.CHAT_SCROLLER;
        mainView.setMode(mode);
        setCurrentView(mainView);
        return true;
      }
      default -> {
        if (!input.startsWith(":") && !input.isBlank()) {
          api.sendPublicMessage(input);
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
  
  private Optional<Boolean> globalCommandWhisper(String input) {
    var patternWhisper = Pattern.compile(":w\\s+(\\S+)(\\s+(.*))?");
    var matcherWhisper = patternWhisper.matcher(input);
    if (matcherWhisper.find()) {
      var receiverUsername = matcherWhisper.group(1);
      var eventualMessage = matcherWhisper.group(3);
      mode = Mode.DIRECT_MESSAGES_LIVE;
      var receiver = api.getDirectMessagesOf(receiverUsername);
      if (receiver.isEmpty()) {
        return Optional.of(true);
      }
      privateMessageView.setPrivateDiscussion(receiver.orElseThrow());
      mode = Mode.DIRECT_MESSAGES_LIVE;
      privateMessageView.setMode(mode);
      setCurrentView(privateMessageView);
      if (eventualMessage != null && !eventualMessage.isBlank()) {
        api.whisper(receiver.orElseThrow()
                            .id(), eventualMessage);
      }
      drawDisplay();
      return Optional.of(false);
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> globalCommandResize(String input) {
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
          privateMessageView.setDimensions(x, y);
          mainView.setDimensions(x, y);
          infoBar.setDimensions(x);
          drawDisplay();
        } catch (NumberFormatException e) {
          return Optional.of(false);
        }
        return Optional.of(false);
      }
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> globalCommandNewCodex(String input) {
    try {
      var patternNew = Pattern.compile(":new\\s+(.*),\\s+(.*)");
      var matcher = patternNew.matcher(input);
      if (matcher.find()) {
        var codexName = matcher.group(1);
        var path = matcher.group(2);
        logger.info(STR.":create \{path}\n");
        selectedCodexForDetails = api.addCodex(codexName, path);
        mode = Mode.CODEX_DETAILS;
        setCurrentView(codexView(selectedCodexForDetails));
        currentView.scrollTop();
        logger.info(STR."Codex created with id: \{selectedCodexForDetails.id()}");
        return Optional.of(true);
      }
      return Optional.empty();
    } catch (IOException e) {
      mustClose = true;
    } catch (NoSuchAlgorithmException e) {
      logger.severe(STR."Error while creating new codex. The \{e.getMessage()}");
      mustClose = true;
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> globalCommandCodexDetail(String input) {
    var patternRetrieve = Pattern.compile(":cdx:(.*)");
    var matcherRetrieve = patternRetrieve.matcher(input);
    if (matcherRetrieve.find()) {
      var fingerprint = matcherRetrieve.group(1);
      logger.info(STR.":cdx: \{fingerprint}\n");
      Optional<CodexController.CodexStatus> codex = null;
      try {
        codex = api.getCodex(fingerprint);
      } catch (InterruptedException e) {
        logger.severe(STR."Error while retrieving codex.\{e.getMessage()}");
        mustClose = true;
      }
      selectedCodexForDetails = codex.orElseThrow();
      mode = Mode.CODEX_DETAILS;
      setCurrentView(codexView(selectedCodexForDetails));
      currentView.scrollTop();
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private ScrollableView helpView() {
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
          :w, :whisper <username> (message)- Create and display a new DM with a user
            if (message) is present, send the message also
          :ws, :whispers - Display the list of DM (selectable)
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
          
        [Direct Messages]
          :m, :msg - Focus on the chat (scrollable)
          :w - Enables back live reload
          
        [CODEX]
        (scrollable)
        :share - Share/stop sharing the codex
        :download - Download/stop downloading the codex
        
        """;
    
    return View.scrollableFromString("Help", lines, cols, txt);
  }
  
  private ScrollableView codexView(CodexController.CodexStatus codexStatus) {
    try {
      var codex = codexStatus.codex();
      var sb = new StringBuilder();
      var splash = """
          ## ┏┓   ┓
          ## ┃ ┏┓┏┫┏┓┓┏
          ## ┗┛┗┛┻┗┗━┛┗
          
          """;
      sb.append(splash);
      sb.append("cdx:")
        .append(codex.id())
        .append("\n");
      if (codexStatus.isComplete()) {
        sb.append(CLIColor.BLUE)
          .append("▓ Complete\n")
          .append(CLIColor.RESET);
      }
      if (api.isDownloading(codex.id()) || api.isSharing(codex.id())) {
        sb.append(CLIColor.ITALIC)
          .append(CLIColor.BOLD)
          .append(CLIColor.ORANGE)
          .append(api.isDownloading(codex.id()) ? "▓ Downloading ..." : api.isSharing(codex.id()) ? "▓ Sharing... " : "")
          .append(CLIColor.RESET)
          .append("\n\n");
      }
      sb.append(colorize(CLIColor.BOLD, "Title: "))
        .append(codex.name())
        .append("\n");
      var infoFiles = codex.files();
      sb.append(colorize(CLIColor.BOLD, "Number of files:  "))
        .append(infoFiles.length)
        .append("\n");
      sb.append(colorize(CLIColor.BOLD, "Total size:   "))
        .append(View.bytesToHumanReadable(codex.totalSize()))
        .append("\n");
      sb.append("Local Path: ")
        .append(codexStatus.root())
        .append("\n\n");
      sb.append(colorize(CLIColor.BOLD, "Files:  \n"));
      Arrays.stream(infoFiles)
            .collect(Collectors.groupingBy(Codex.FileInfo::absolutePath))
            .forEach((dir, files) -> {
              logger.info(STR."dir: \{dir}");
              sb.append(colorize(CLIColor.BOLD, STR."[\{dir}]\n"));
              files.forEach(file -> sb.append("\t- ")
                                      .append(CLIColor.BOLD)
                                      .append("%10s".formatted(View.bytesToHumanReadable(file.length())))
                                      .append("  ")
                                      .append("%.2f%%".formatted(codexStatus.completionRate(file) * 100))
                                      .append("  ")
                                      .append(CLIColor.RESET)
                                      .append(file.filename())
                                      .append("\n"));
            });
      return View.scrollableFromString(STR."[Codex] \{codex.name()}", lines, cols, sb.toString());
    } catch (Exception e) {
      logger.severe(STR."Error while creating codex view.\{e.getMessage()}");
      mustClose = true;
      exitNicely();
      System.exit(0);
    }
    return null;
  }

  public enum Mode {
    CHAT_LIVE_REFRESH, // default
    CHAT_SCROLLER,     // :c, :chat
    USERS_SCROLLER,    // :u, :users
    HELP_SCROLLER,     // :h, :help
    CODEX_SEARCH,      // :search <name>
    CODEX_DETAILS,     // :cdx:<fingerprint>
    CODEX_LIST,        // :mycdx
    DIRECT_MESSAGES_LIVE,   // :w, :whisper <username>
    DIRECT_MESSAGES_SCROLLER, // :m, :msg
    DIRECT_MESSAGES_LIST // :ws, :whispers
  }
}