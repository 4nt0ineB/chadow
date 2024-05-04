package fr.uge.chadow.client;

import fr.uge.chadow.client.cli.CLIColor;
import fr.uge.chadow.client.cli.display.Display;
import fr.uge.chadow.client.cli.display.InfoBar;
import fr.uge.chadow.client.cli.display.View;
import fr.uge.chadow.client.cli.display.view.*;
import fr.uge.chadow.core.protocol.client.Search;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.server.SearchResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The client console controller is the main class of the client in console mode.
 * It manages the display, the input, the commands and the user interactions.
 */
public class ClientConsoleController {
  public enum Mode {
    CHAT_LIVE_REFRESH,        // default
    CHAT_SCROLLER,            // :c, :chat
    USERS_SCROLLER,           // :u, :users
    HELP_SCROLLER,            // :h, :help
    CODEX_SEARCH,             // :search <name>
    CODEX_DETAILS,            // :cdx:<fingerprint>
    CODEX_LIST,               // :mycdx
    DIRECT_MESSAGES_LIVE,     // :w, :whisper <username>
    DIRECT_MESSAGES_SCROLLER, // :m, :msg
    DIRECT_MESSAGES_LIST      // :ws, :whispers
  }
  
  //// app management
  private final static Logger logger = Logger.getLogger(ClientConsoleController.class.getName());
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
  private CodexStatus selectedCodexForDetails;
  private Search lastSearch;
  private final List<SearchResponse.Result> lastSearchResults = new ArrayList<>();
  private Mode mode;
  private int lines;
  private int cols;
  
  public ClientConsoleController(int lines, int cols, ClientAPI api) {
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.lines = lines;
    this.cols = cols;
    this.api = api;
    mainView = new ChatView(lines, cols, api);
    currentView = mainView;
    privateMessageView = new PrivateMessageView(lines, cols, api);
    mode = Mode.CHAT_LIVE_REFRESH;
  }
  
  /**
   * Start the client:
   * - start the api and wait for the connection to the server
   * - start the display
   * - start the input reader
   */
  public void start() {
    // Starts the api thread
    Thread.ofPlatform().daemon().start(() -> {
      try {
        api.startService();
      } catch (IOException | InterruptedException e) {
        logger.severe(STR."Can't connect to the server.\{e.getMessage()}");
        exitNicely();
      }
    });
    try {
      api.waitForConnection();
    } catch (InterruptedException e) {
      var errorView = new CantConnectScreenView(lines, cols);
      errorView.draw();
      logger.info(e.getMessage());
      return;
    }
    // Starts display thread
    infoBar = new InfoBar(cols, this::updateInfoBar);
    display = new Display(lines, cols, this, infoBar, api);
    setCurrentView(mainView);
    startDisplay();
    // start the input reader
    try {
      startReader();
    } catch (UncheckedIOException | IOException e) {
      logger.severe(STR."The console was interrupted.\{e.getCause()}");
    } finally {
      exitNicely();
    }
  }
  
  public void updateInfoBar(InfoBar infoBar) {
    infoBar.clear();
    var unreadDiscussions = api.discussionWithUnreadMessages();
    unreadDiscussions.forEach(discussion -> {
      var username = discussion.username();
      var color = CLIColor.stringToColor(username);
      var txt = (STR."<\{color}\{CLIColor.BOLD}%s\{CLIColor.RESET}>")
          .formatted(username);
      infoBar.addInfo(txt);
    });
  }
  
  public CodexStatus currentCodex() {
    return selectedCodexForDetails;
  }
  
  public void startDisplay() {
    Thread.ofPlatform()
          .daemon()
          .start(() -> {
            try {
              logger.info(STR."Display starts with (\{lines} rows \{cols} cols)");
              display.startLoop();
            } catch (InterruptedException e) {
              logger.severe(STR."The console display was interrupted. \{e.getMessage()}");
            } finally {
              exitNicely();
            }
          });
  }
  
  public void drawDisplay() {
    try {
      display.clear();
      display.draw();
    } catch (IOException e) {
      logger.severe(STR."Error while drawing display.\{e.getMessage()}");
      exitNicely();
    }
  }
  
  public void clearDisplayAndMore() {
    View.clearDisplayAndMore(lines);
  }
  
  /**
   * Stop the auto refresh
   */
  public void stopAutoRefresh() {
    viewCanRefresh.set(false);
    clearDisplayAndMore();
    drawDisplay();
  }
  
  public AtomicBoolean viewCanRefresh() {
    return viewCanRefresh;
  }
  
  /**
   * Is alive if not dead... duh
   *
   * @return true if the client can keep running, false otherwise
   */
  public boolean isAlive() {
    return !mustClose && api.isConnected();
  }
  
  /**
   * Process the message or the command
   *
   * @param input the user input
   */
  public void processInput(String input) {
    logger.info(STR."Processing input: \{input}");
    // Each method tries to match the input
    // if it does not match it returns an empty Optional
    var canTypeAgain = globalCommandDisplayChat(input)
        .or(() -> globalCommandDisplayDM(input))
        .or(() -> globalCommandDisplayCodexes(input))
        .or(() -> globalCommandHelp(input))
        .or(() -> globalCommandExit(input))
        .or(() -> globalCommandResize(input))
        .or(() -> globalCommandNewCodex(input))
        .or(() -> globalCommandCodexDetail(input))
        .or(() -> globalCommandWhisper(input))
        .or(() -> globalCommandDraw(input))
        .or(() -> globalCommandSearch(input))
        .or(() -> globalCommandLastSearch(input))
        .or(() -> Optional.of(processCommandInContext(input)));
    clearDisplayAndMore();
    viewCanRefresh.set(!canTypeAgain.orElse(false));
    drawDisplay();
  }
  
  private Optional<Boolean> globalCommandLastSearch(String input) {
    if(input.equals(":f")) {
      if(lastSearch != null){
        mode = Mode.CODEX_SEARCH;
        setCurrentView(currentSelector);
        return Optional.of(true);
      }
    }
    return Optional.empty();
  }
  
  private void exitNicely() {
    api.close();
    mustClose = true;
  }
  
  private void setCurrentView(View view) {
    currentView = view;
    display.setView(view);
  }
  
  private boolean processCommandInContext(String input) {
    return switch (mode) {
      case CHAT_LIVE_REFRESH -> processInputModeLiveRefresh(input);
      case CHAT_SCROLLER, USERS_SCROLLER, HELP_SCROLLER -> processInputModeScroller(input);
      case CODEX_DETAILS -> processInputModeCodexDetails(input);
      case CODEX_LIST -> processInputModeCodexList(input);
      case DIRECT_MESSAGES_LIVE -> processInputModeDirectMessagesLive(input);
      case DIRECT_MESSAGES_SCROLLER -> processInputModeDMScroller(input);
      case DIRECT_MESSAGES_LIST -> processCommandDirectMessagesList(input);
      case CODEX_SEARCH -> processCommandSearch(input);
    };
  }
  
  private boolean processCommandSearch(String input) {
    switch (input) {
      // Search pagination : continue searching if bottom is displayed
      case "s", "b", "d" -> {
        if(lastSearchResults.size() >= View.maxLinesView(lines) &&  currentSelector.isAtBottom()) {
          var newSearch = lastSearch.nextPage(View.maxLinesView(lines), lastSearchResults.size());
          var newResponse = api.searchCodexes(newSearch);
          if(newResponse.isPresent()){
            var results = newResponse.orElseThrow();
            lastSearch = newSearch;
            lastSearchResults.addAll(Arrays.asList(results.results()));
            var newSelector = View.selectorFromList("Search results", lines, cols, lastSearchResults, View::codexSearchResultShortDescription);
            newSelector.setAtSamePosition(currentSelector);
            currentSelector = newSelector;
            setCurrentView(currentSelector);
          }
        }
       return processInputModeSelector(input);
      }
      case ":s", ":select" -> {
        mode = Mode.CODEX_DETAILS;
        var selectedCodexFromSearch = (SearchResponse.Result) currentSelector.get();
        var askCodexDetailsFromServer = api.getCodex(selectedCodexFromSearch.codexId());
        if(askCodexDetailsFromServer.isEmpty()){
          return true;
        }
        selectedCodexForDetails = askCodexDetailsFromServer.orElseThrow();
        setCurrentView(new CodexView(selectedCodexForDetails, lines, cols, api));
        currentView.scrollTop();
        return true;
      }
      default -> {
       return processInputModeSelector(input);
      }
    }
  }
  
  private Optional<Boolean> globalCommandDraw(String input) {
    if (input.equals(":d")) {
      logger.info("Redrawing display");
      drawDisplay();
      return Optional.of(!viewCanRefresh.get());
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> globalCommandDisplayDM(String input) {
    if (input.equals(":ws") || input.equals(":whispers")) {
      setMessageList();
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private void setMessageList() {
    mode = Mode.DIRECT_MESSAGES_LIST;
    var list = api.getAllDirectMessages()
                  .stream()
                  .sorted(Comparator.comparingLong(dm -> dm.getLastMessage()
                                                           .map(WhisperMessage::epoch)
                                                           .orElse(Long.MIN_VALUE)))
                  .toList();
    currentSelector = View.selectorFromList("Direct messages", lines, cols, list, directMessages -> {
      var str = View.directMessageShortDescription(directMessages);
      return View.responsiveCut(str, cols);
    });
    setCurrentView(currentSelector);
    logger.info("Displaying whispers");
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
      return Optional.of(false);
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
          setCurrentView(new CodexView(selectedCodexForDetails, lines, cols, api));
          currentView.scrollTop();
        }
        return true;
      }
      case ":live" -> {
        return false;
      }
      default -> {
        return processCommandDownload(input)
            .orElseGet(() -> processInputModeScroller(input));
      }
    }
  }
  
  private Optional<Boolean> processCommandDownload(String input) {
    var pattern = Pattern.compile("(?>:download|:dl)(?>\\s+(?<hidden>(hidden|h)(?>\\s+(?<size>\\d+))?))?");
    var matcher = pattern.matcher(input);
    if (matcher.find()) {
      var hidden = Optional.ofNullable(matcher.group("hidden")).isPresent();
      var chainSize = Optional.ofNullable(matcher.group("size"))
                        .map(Integer::parseInt)
                        .orElse(0);
      var codexId = currentCodex().id();
      if (!currentCodex().isComplete()) {
        if (api.isDownloading(codexId)) {
          api.stopDownloading(currentCodex().id());
        } else {
          api.download(currentCodex().id(), hidden, chainSize);
        }
        mode = Mode.CODEX_DETAILS;
        setCurrentView(new CodexView(selectedCodexForDetails, lines, cols, api));
        currentView.scrollTop();
        return Optional.of(false);
      }
      return Optional.of(true);
    }
    return Optional.empty();
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
      case ":rm" -> {
        var selected = (DirectMessages) currentSelector.get();
        api.deleteDirectMessagesWith(selected.id());
        
        mode = Mode.DIRECT_MESSAGES_LIST;
        var list = api.getAllDirectMessages()
                      .stream()
                      .sorted(Comparator.comparingLong(dm -> dm.getLastMessage()
                                                               .map(WhisperMessage::epoch)
                                                               .orElse(Long.MIN_VALUE)))
                      .toList();
        var newSelector = View.selectorFromList("Direct messages", lines, cols, list, directMessages -> {
          var str = View.directMessageShortDescription(directMessages);
          return View.responsiveCut(str, cols);
        });
        
        currentSelector.selectorUp();
        newSelector.setAtSamePosition(currentSelector);
        setCurrentView(newSelector);
        logger.info("Displaying whispers");
        
        return true;
      }
      case ":s", ":select" -> {
        var selected = (DirectMessages) currentSelector.get();
        mode = Mode.DIRECT_MESSAGES_LIVE;
        privateMessageView.setPrivateDiscussion(selected);
        privateMessageView.setMode(mode);
        setCurrentView(privateMessageView);
        currentView.scrollTop();
        return true;
      }
      default -> {
        return processInputModeSelector(input);
      }
    }
  }
  
  private boolean processInputModeDirectMessagesLive(String input) {
    switch (input) {
      case ":delete" -> {
        var receiver = privateMessageView.receiver();
        api.deleteDirectMessagesWith(receiver.id());
        setMessageList();
        return true;
      }
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
      case ":s", ":select" -> {
        mode = Mode.CODEX_DETAILS;
        selectedCodexForDetails = (CodexStatus) currentSelector.get();
        logger.info(STR."see cdx: \{selectedCodexForDetails.id()}");
        setCurrentView(new CodexView(selectedCodexForDetails, lines, cols, api));
        currentView.scrollTop();
        logger.info(STR."see cdx: \{selectedCodexForDetails.id()}");
        return true;
      }
      default -> {
        return processInputModeSelector(input);
      }
    }
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
  
  private boolean processInputModeSelector(String input) {
    switch (input) {
      case "y" -> currentSelector.selectorUp();
      case "h" -> currentSelector.selectorDown();
      default -> processInputModeScroller(input);
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
        setCurrentView(new CodexView(selectedCodexForDetails, lines, cols, api));
        currentView.scrollTop();
        logger.info(STR."Codex created with id: \{selectedCodexForDetails.id()}");
        return Optional.of(true);
      }
      return Optional.empty();
    } catch (IOException e) {
      exitNicely();
    } catch (NoSuchAlgorithmException e) {
      logger.severe(STR."Error while creating new codex. The \{e.getMessage()}");
      exitNicely();
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> globalCommandCodexDetail(String input) {
    var patternRetrieve = Pattern.compile(":cdx:(.*)");
    var matcherRetrieve = patternRetrieve.matcher(input);
    if (matcherRetrieve.find()) {
      var fingerprint = matcherRetrieve.group(1);
      logger.info(STR.":cdx: \{fingerprint}\n");
      Optional<CodexStatus> codex = api.getCodex(fingerprint);
      if(codex.isEmpty()){
        return Optional.of(true);
      }
      selectedCodexForDetails = codex.orElseThrow();
      mode = Mode.CODEX_DETAILS;
      setCurrentView(new CodexView(selectedCodexForDetails, lines, cols, api));
      currentView.scrollTop();
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  private Optional<Boolean> globalCommandSearch(String input) {
    var regex = ":(?>f|find)\\s+(?>(?<at>:at)?(?<range>:(?>before|after))?\\s+(?>(?<date>\\d{1,2}/\\d{1,2}/\\d{4})(?>\\s+(?<time>\\d{1,2}:\\d{1,2}))?))?(?>\\s+(?<field>date|name):(?<order>asc|desc))?(?<name>.*)";
    var patternSearch = Pattern.compile(regex);
    var matcher = patternSearch.matcher(input);
    if (matcher.find()) {
      var at = matcher.group("at");
      var range = matcher.group("range");
      var date = matcher.group("date");
      var time = matcher.group("time");
      var field = matcher.group("field"); // @Todo handle sorting
      var order = matcher.group("order"); //
      var name = matcher.group("name");
      logger.info(STR."Local is : \{Locale.getDefault()
                                          .toString()}");
      logger.info(STR."Search: \{at} \{range} \{date} \{time} \{field} \{order} \{name}");
      var options = 0;
      if (at != null) {
        options |= Search.Option.AT_DATE.value();
      }
      if (range != null) {
        if (range.equals(":before")) {
          options |= Search.Option.BEFORE_DATE.value();
        } else {
          options |= Search.Option.AFTER_DATE.value();
        }
      }
      var epoch = 0L;
      if (date != null) {
        var datetimeStr = time == null ? date : STR."\{date} \{time}";
        var simpleFormatter = new SimpleDateFormat();
        var dateFormats = View.getDateFormats(Locale.getDefault());
        Long parsedEpoch = null;
        for(var format : dateFormats){
          try {
            simpleFormatter.applyPattern(format);
            var parsedDate = simpleFormatter.parse(datetimeStr);
            parsedEpoch = parsedDate.toInstant().getEpochSecond();
          } catch (DateTimeParseException | ParseException e) {
            logger.warning(STR."Error while parsing date: \{e.getMessage()}");
          }
        }
        if(parsedEpoch == null){
          return Optional.of(false);
        }
        epoch = parsedEpoch;
        logger.info(STR."Parsed date: \{epoch}");
      }
      var search = new Search(name, options, epoch, View.maxLinesView(lines), 0);
      var response = api.searchCodexes(search);
      if(response.isEmpty()){
        return Optional.of(false);
      }
      var results = response.orElseThrow();
      lastSearch = search;
      lastSearchResults.clear();
      lastSearchResults.addAll(Arrays.asList(results.results()));
      
      if(field != null){
        Comparator<SearchResponse.Result> comparator;
        List<SearchResponse.Result> sortedResults;
        if(field.equals("date")){
          comparator = Comparator.comparing(SearchResponse.Result::creationDate);
        } else {
          comparator =  Comparator.comparing(SearchResponse.Result::codexName);
        }
        sortedResults = lastSearchResults.stream().sorted(comparator).toList();
        if(order != null && order.equals("desc")){
          sortedResults = sortedResults.reversed();
        }
        lastSearchResults.clear();
        lastSearchResults.addAll(sortedResults);
      }
      
      mode = Mode.CODEX_SEARCH;
      currentSelector = View.selectorFromList("Search results", lines, cols, lastSearchResults, View::codexSearchResultShortDescription);
      setCurrentView(currentSelector);
      return Optional.of(true);
    }
    return Optional.empty();
  }
  
  /**
   * Read user input from the console
   * We talk about writeMode when autoRefresh is disabled.
   * In autoRefresh mode (set to true), incoming messages are displayed automatically.
   * The user can press enter to stop the auto refresh
   * to be able to type a message (or a command).
   * When the user press enter again the message (or the command) is processed,
   * then the display goes back into autoRefresh mode.
   * <p>
   * In writeMode (autoRefresh set to false), the user can escape a line with '\' followed by enter.
   * This is useful to write a multiline message.
   *
   * @throws IOException if an I/O error occurs
   */
  public void startReader() throws IOException {
    var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    var inputField = new StringBuilder();
    var c = 0;
    var escape = false;
    while ((c = reader.read()) != -1 && isAlive()) {
      var canRefresh = viewCanRefresh().get();
      if (canRefresh) {
        if (c == '\n') {
          stopAutoRefresh();
        }
      } else {
        if (escape && !inputField.toString().endsWith("\n")) {
          // escape '\' is replaced as line break in the inputField
          System.out.print(View.inviteCharacter());
          inputField.append('\n');
          escape = false;
        } else {
          // we escape with '\'
          if (c == '\\') {
            escape = true;
          } else if (c == '\n') {
            processInput(inputField.toString());
            inputField.setLength(0);
          } else {
            inputField.append((char) c);
          }
        }
      }
    }
  }
  
  private ScrollableView helpView() {
    var txt = """
            ##  ┓┏  ┓
            ##  ┣┫┏┓┃┏┓
            ##  ┛┗┗━┗┣┛
            ##       ┛
            
            User Interaction:
            - When your [username] is greyed out, your input is disabled.
            - Press enter to switch to input mode and enable your input. Your [username] will be colored.
            - The input field allows multiline input. It works by writing the character \\ before pressing enter.\s
            
            Scrollable mode:
              e - scroll one page up
              s - scroll one page down
              r - scroll one line up
              d - scroll one line down
              t - scroll to the top
              b - scroll to the bottom
              
            Selectable mode (also scrollable):
              y - move selector up
              h - move selector down
              :s, :select - Select the item
              ! scrolling also moves the selector !
              
            [GLOBAL COMMANDS]
              :h, :help
                Display this help (scrollable)
                
              :c, :chat
                Back to the [CHAT] in live refresh
                
              :w, :whisper <username> (message)
                Create and display a new DM with a user. If (message) is present,
                send the message also
                
              :ws,:whispers
                Display the list of DM [Direct Message list]
                
              :d
                Update and draw the display
                
              :r <lines> <columns>
                Resize the view
                
              :new <codexName>, <path>
                Create a codex from a file or directory and display the [CODEX]
                and display the details of new created [CODEX] info
                
              :f, :find [:at(:before|:after)) <date>] [(name|date):(asc|desc)] <name>
                Interrogate the server for codexes
                
                :at - Search at a specific date
                :before - Search before a specific date
                :after - Search after a specific date
                <date> - The date to search (dd/MM/yyyy or MM/dd/yyyy with optionally HH:mm)
                name/date - Sort by name or date
                asc/desc - Sort ascending or descending. Default is ascending
                
                Examples:
                  :f :at:before 12/12/2021 name:asc my road trip photos
                  :f date:desc the.matrix.1999
                 
                  To search all codexes
                  :f <space>
                
              :f - Go back to the last search results
              
              :mycdx
                Display the [CODEX LIST]
                
              :cdx:<SHA-1>
                Retrieves and display the [CODEX] info with the given SHA-1
                if the codex is not present locally, the server will be interrogated
                
              :exit - Exit the application
              
            [CHAT]
              when the live refresh is disabled (indicated by the coloured input field)
              any input not starting with ':' will be considered as a message to be sent
              
              :m, :msg - Focus on chat (scrollable)
              :u, :users - Focus on the users list (scrollable)
              
            [DM list]
              (selectable)
              :s, :select - Select the direct message
              :rm - Delete the focused discussion
              
            [DM]
              :m, :msg - Enable scrolling (scrollable)
              :w - Enables chat mode
              :delete - Delete the discussion
              
            [CODEX]
              (scrollable)
              :share - Share/stop sharing the codex
              :dl, :download (h|hidden)
                Download/stop downloading the codex, when downloading live refresh is enabled
              :live - Switch to live refresh to see the changes in real time
            """;
    
    return View.scrollableFromString("Help", lines, cols, txt);
  }
}