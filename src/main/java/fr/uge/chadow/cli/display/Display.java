package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.client.ClientConsoleController.Mode;
import fr.uge.chadow.client.Codex;
import fr.uge.chadow.core.reader.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fr.uge.chadow.cli.display.View.splitAndSanitize;
import static fr.uge.chadow.cli.display.View.colorize;

public class Display {
  private static final Logger logger = Logger.getLogger(Display.class.getName());
  
  private final ClientConsoleController controller;
  private final Scroller messageScroller;
  private final Scroller userScroller;
  
  private final AtomicBoolean viewCanRefresh;
  private final ReentrantLock lock = new ReentrantLock();
  private int lines;
  private int columns;
  private Mode mode = Mode.CHAT_LIVE_REFRESH;
  private ScrollableView helpView;
  private ScrollableView codexView;
  
  
  public Display(int lines, int cols, AtomicBoolean viewCanRefresh, ClientConsoleController controller) {
    Objects.requireNonNull(viewCanRefresh);
    Objects.requireNonNull(controller);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and cols must be positive");
    }
    this.controller = controller;
    this.lines = lines;
    this.columns = cols;
    this.viewCanRefresh = viewCanRefresh;
    this.messageScroller = new Scroller(0, View.maxLinesView(lines));
    this.userScroller = new Scroller(0, View.maxLinesView(lines));
    helpView = helpView();
    helpView.moveToTop();
  }
  
  static String lowInfoBar(int columns) {
    return STR."\{String.valueOf(CLIColor.CYAN)}\{CLIColor.BOLD}\{"\u25A0".repeat(columns)}\{CLIColor.RESET}\n";
  }
  
  /**
   * Set the dimensions of the view
   *
   * @param lines
   * @param cols
   */
  public void setDimensions(int lines, int cols) {
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    lock.lock();
    try {
      this.lines = lines;
      this.columns = cols;
      helpView.setDimensions(lines, cols);
      codexView.setDimensions(lines, cols);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Start the loop that will refresh the view
   */
  public void startLoop() throws InterruptedException, IOException {
    System.out.print(CLIColor.CLEAR);
    System.out.flush();
    while (!Thread.interrupted()) {
      if (viewCanRefresh.get()) {
        draw();
        if (mode == Mode.CHAT_LIVE_REFRESH) {
          messageScroller.setLines(controller.numberOfMessages());
        }
      }
      Thread.sleep(200);
    }
  }
  
  public void clear() {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
  }
  
  /**
   * Draw the view in the current context aka mode
   *
   * @throws IOException
   */
  public void draw() throws IOException {
    
    switch (mode) {
      case CHAT_LIVE_REFRESH,
          CHAT_SCROLLER,
          USERS_SCROLLER -> {
        clear();
        System.out.print(chatHeader());
        System.out.print(chatDisplay());
      }
      case HELP_SCROLLER -> {
        helpView.clear();
        helpView.draw();
      }
      case CODEX_DETAILS -> {
        codexView.clear();
        codexView.draw();
      }
    }
    //System.out.print(View.thematicBreak(columns));
    System.out.print(lowInfoBar(columns));
    System.out.print(inputField());
    View.moveToInputField(lines);
  }
  
  /**
   * Get the max length of the usernames
   * Default size is 5
   *
   * @return
   */
  private int getMaxUserLength() {
    
    return Math.max(controller.users()
                              .stream()
                              .mapToInt(String::length)
                              .max()
                              .orElse(0), 15);
    
    
  }
  
  /**
   * Print the chat display
   * The chat display is composed of the chat area and the user presence area.
   * where each line is formatted as follow:
   * <[date] [userx] | [message] > | [usery presence if any]
   *
   * @throws IOException
   */
  private String chatDisplay() throws IOException {
    var sb = new StringBuilder();
    var maxUserLength = getMaxUserLength();
    var lineIndex = 0;
    var maxLinesView = View.maxLinesView(lines);
    var colsRemaining = columns - getMaxUserLength() - 2;
    // chat | presence
    var users = getUsersToDisplay();
    var iterator = getFormattedMessages().iterator();
    var loginColor = "";
    while (iterator.hasNext() && lineIndex < maxLinesView) {
      var message = iterator.next();
      if (!message.login()
                  .isBlank()) {
        loginColor = CLIColor.stringToColor(message.login());
      }
      colsRemaining = columns;
      var date = message.login()
                        .isBlank() ? " ".repeat(10) : STR."\{View.formatDate(message.epoch())}  ";
      colsRemaining -= date.length();
      var user = (STR."%\{maxUserLength}s").formatted(message.login());
      colsRemaining -= user.length();
      colsRemaining -= maxUserLength + 5; // right side pannel of users + margin ( | ) and (│ )
      var who = (STR."%s\{loginColor}\{CLIColor.BOLD}%s\{CLIColor.RESET}").formatted(date, user);
      var separator = message.login()
                             .isBlank() ? STR."\{CLIColor.BOLD} │ \{CLIColor.RESET}" : " ▒ ";
      var messageLine = (STR."\{loginColor}\{separator}\{CLIColor.RESET}%-\{colsRemaining}s").formatted(message.txt());
      messageLine = View.beautifyCodexLink(messageLine);
      var formatedLine = String.format(STR."%s%s\{CLIColor.CYAN}│ ",
          who,
          messageLine);
      sb.append(formatedLine);
      // display (or not) the user presence. Paginate if necessary
      if (lineIndex < users.size()) {
        if (lineIndex < maxLinesView - 1) {
          sb.append(CLIColor.CYAN);
          sb.append(users.get(lineIndex));
        } else {
          sb.append(CLIColor.BOLD)
            .append(CLIColor.ORANGE)
            .append(STR."++ (\{users.size() - lineIndex} more)");
        }
      }
      sb.append(CLIColor.RESET)
        .append("\n");
      who = " ".repeat(maxUserLength + 7);
      lineIndex++;
      
    }
    // Draw empty remaining lines on screen and display side panel of users
    for (; lineIndex < maxLinesView; lineIndex++) {
      sb.append(String.format(STR."%-\{columns - maxUserLength - 2}s\{CLIColor.CYAN}│ ", " "));
      if (users.size() > lineIndex) {
        sb.append(String.format(STR."%-\{maxUserLength}s", users.get(lineIndex)));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
  
  private String chatHeader() {
    var sb = new StringBuilder();
    var colsRemaining = columns - getMaxUserLength() - 2;
    sb.append(CLIColor.CYAN_BACKGROUND);
    sb.append(CLIColor.WHITE);
    var title = (STR."%-\{colsRemaining}s ").formatted(STR."CHADOW CLIENT on \{controller.clientServerHostName()} (" + lines + "x" + columns + ")");
    colsRemaining -= title.length() + 2; // right side pannel of users + margin (  )
    var totalUsers = (STR."\{CLIColor.BOLD}\{CLIColor.BLACK}%-\{getMaxUserLength()}s").formatted(STR."(\{controller.totalUsers()})");
    colsRemaining -= totalUsers.length();
    sb.append("%s %s".formatted(title, totalUsers));
    sb.append(" ".repeat(Math.max(0, colsRemaining)));
    sb.append(CLIColor.RESET);
    sb.append('\n');
    return sb.toString();
  }
  
  private String inputField() {
    var inputField = "";
    if (!viewCanRefresh.get()) {
      // (getMaxUserLength() + 21)
      inputField = ("%s\n")
          .formatted(STR."[\{CLIColor.BOLD}\{CLIColor.CYAN}\{controller.clientLogin()}\{CLIColor.RESET}]");
    } else {
      // (getMaxUserLength() + 50)
      inputField = ("%s\n")
          .formatted(STR."\{CLIColor.GREY}[\{CLIColor.GREY}\{CLIColor.BOLD}\{controller.clientLogin()}\{CLIColor.RESET}\{CLIColor.GREY}]\{CLIColor.RESET}");
    }
    return inputField + View.inviteCharacter() + CLIColor.BOLD;
  }
  
  /**
   * Get the last messages to display
   *
   * @return
   */
  private List<Message> getFormattedMessages() {
    
    var subList = getMessagesToDisplay();
    var list = subList.stream()
                      .flatMap(message -> formatMessage(message, msgLineLength()))
                      .collect(Collectors.toList());
    return list.subList(Math.max(0, list.size() - View.maxLinesView(lines)), list.size());
    
  }
  
  
  private List<Message> getMessagesToDisplay() {
    
    var messages = controller.messages();
    
    if (messages.size() <= View.maxLinesView(lines)) {
      return messages;
    }
    if (mode == Mode.CHAT_LIVE_REFRESH) {
      return messages.subList(Math.max(0, messages.size() - View.maxLinesView(lines)), messages.size());
    }
    return messages.subList(messageScroller.getA(), messageScroller.getB());
    
  }
  
  private List<String> getUsersToDisplay() {
    var users = controller.users();
    if (users.size() <= View.maxLinesView(lines)) {
      return new ArrayList<>(users);
    }
    if (mode == Mode.CHAT_LIVE_REFRESH) {
      return users.stream()
                  .toList();
    }
    return users.stream()
                .skip(userScroller.getA())
                .toList();
  }
  
  private int msgLineLength() {
    return columns - (getMaxUserLength() * 2) - 8 - 7;
  }
  
  /**
   * Sanitize and format the message to display.
   * If the message is too long, it will be split into multiple lines.
   * the first line will contain the user login and date, the following lines will only contain the message.
   * This allows to display the message in a more readable way.
   *
   * @param message
   * @param lineLength
   * @return
   */
  private Stream<Message> formatMessage(Message message, int lineLength) {
    
    var sanitizedLines = splitAndSanitize(message.txt(), lineLength);
    return IntStream.range(0, sanitizedLines.size())
                    .mapToObj(index -> new Message(index == 0 ? message.login() : "", sanitizedLines.get(index), message.epoch()));
    
  }
  
  public Mode getMode() {
    return mode;
  }
  
  public void setMode(Mode mode) {
    this.mode = mode;
    if (mode == Mode.HELP_SCROLLER) {
      helpView = helpView();
      helpView.moveToTop();
    } else if (mode == Mode.CODEX_DETAILS) {
      var codex = controller.currentCodex();
      assert codex != null;
      codexView = codexView(codex);
      codexView.moveToTop();
    }
  }
  
  public void scrollerPageUp() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollPageUp();
      case USERS_SCROLLER -> userScroller.scrollPageUp();
      case HELP_SCROLLER -> helpView.scrollPageUp();
      case CODEX_DETAILS -> codexView.scrollPageUp();
    }
  }
  
  public void scrollerPageDown() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollPageDown();
      case USERS_SCROLLER -> userScroller.scrollPageDown();
      case HELP_SCROLLER -> helpView.scrollPageDown();
      case CODEX_DETAILS -> codexView.scrollPageDown();
    }
  }
  
  public void scrollerBottom() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.setLines(controller.numberOfMessages());
      case USERS_SCROLLER -> userScroller.setLines(controller.users().size());
      case HELP_SCROLLER -> helpView.moveToBottom();
      case CODEX_DETAILS -> codexView.moveToBottom();
    }
  }
  
  public void scrollerLineUp() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollUp(1);
      case USERS_SCROLLER -> userScroller.scrollUp(1);
      case HELP_SCROLLER -> helpView.scrollUp(1);
      case CODEX_DETAILS -> codexView.scrollUp(1);
    }
  }
  
  public void scrollerLineDown() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollDown(1);
      case USERS_SCROLLER -> userScroller.scrollDown(1);
      case HELP_SCROLLER -> helpView.scrollDown(1);
      case CODEX_DETAILS -> codexView.scrollDown(1);
    }
  }
  
  private ScrollableView helpView() {
    var txt = """
        
        ##  ┓┏  ┓
        ##  ┣┫┏┓┃┏┓
        ##  ┛┗┗━┗┣┛
        ##       ┛
        
        
        'scrollable' elements:
          e - scroll one page up
          s - scroll one page down
          r - scroll one line up
          d - scroll one line down
          t - scroll to the top
          b - scroll to the bottom
        
        [GLOBAL COMMANDS]
          :h, :help - Display this help (scrollable)
          :c, :chat - Back to the chat in live reload
          :w, :whisper <username> - Display the private discussion with the other user (TODO)
          :d - Update and draw the display
          :r <lines> <columns> - Resize the view
          :new <codexName>, <path> - Create a codex from a file or directory
          and display the details of new created codex (WIP). (mind the space between , and <path>)
          
          :mycdx - Display the list of your codex
          :cdx:<SHA-1> - Retrieves and display the codex info with the given SHA-1          (TODO)
          :exit - Exit the application                                                      (WIP)
          
        [CHAT]
          when the live reload is disabled (indicated by the coloured input field)
          any input not starting with ':' will be considered as a message to be sent
          
          :m, :msg - Focus on chat (scrollable)
          :u, :users - Focus on the users list (scrollable)
          
        [CODEX]
        (scrollable)
        :share - Share/stop sharing the codex
        :download - Download/stop downloading the codex
        
        """;

    return View.scrollableViewFromString("Help", lines, columns, txt);
  }
  

  private ScrollableView codexView(Codex codex) {
    var sb = new StringBuilder();
    
    var splash = """
        ## ┏┓   ┓
        ## ┃ ┏┓┏┫┏┓┓┏
        ## ┗┛┗┛┻┗┗━┛┗
        
        """;
    sb.append(splash);
    sb.append("cdx:")
      .append(Codex.fingerprintAsString(codex.id()))
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
      .append("\n\n");
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
    return View.scrollableViewFromString(STR."[Codex] \{codex.name()}", lines, columns, sb.toString());
  }
  
  public void scrollerTop() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.moveToTop();
      case USERS_SCROLLER -> userScroller.moveToTop();
      case HELP_SCROLLER -> helpView.moveToTop();
      case CODEX_DETAILS -> codexView.moveToTop();
    }
  }
  

}
