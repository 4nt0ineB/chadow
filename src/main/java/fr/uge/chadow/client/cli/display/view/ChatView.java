package fr.uge.chadow.client.cli.display.view;

import fr.uge.chadow.client.cli.CLIColor;
import fr.uge.chadow.client.cli.display.Scroller;
import fr.uge.chadow.client.cli.display.View;
import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.core.protocol.YellMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fr.uge.chadow.client.cli.display.View.splitAndSanitize;

public class ChatView implements View {
  private final ClientAPI clientAPI;
  private final Scroller messageScroller;
  private final Scroller userScroller;
  
  private final ReentrantLock lock = new ReentrantLock();
  private int lines;
  private int cols;
  private ClientConsoleController.Mode mode = ClientConsoleController.Mode.CHAT_LIVE_REFRESH;
  private List<YellMessage> formattedMessages = new ArrayList<>();
  
  
  public ChatView(int lines, int cols, ClientAPI controller) {
    Objects.requireNonNull(controller);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and cols must be positive");
    }
    this.clientAPI = controller;
    this.lines = lines;
    this.cols = cols;
    this.messageScroller = new Scroller(0, View.maxLinesView(lines));
    this.userScroller = new Scroller(0, View.maxLinesView(lines));
  }
  
  /**
   * Set the dimensions of the view
   *
   * @param lines the number of lines
   * @param cols the number of columns
   */
  public void setDimensions(int lines, int cols) {
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    lock.lock();
    try {
      this.lines = lines;
      this.cols = cols;
      messageScroller.setPageHeight(View.maxLinesView(lines));
      userScroller.setPageHeight(View.maxLinesView(lines));
    } finally {
      lock.unlock();
    }
  }
  
  public void clear() {
    View.clear(lines);
  }
  
  /**
   * Draw the view in the current mode
   */
  public void draw() {
    clear();
    System.out.print(chatHeader());
    System.out.print(chatDisplay());
  }
  
  /**
   * Get the max length of the usernames
   * Default size is 5
   *
   * @return the length of the longest username
   */
  private int getMaxUserLength() {
    return clientAPI.users()
                    .stream()
                    .mapToInt(String::length)
                    .max()
                    .orElse(5);
  }
  
  /**
   * Print the chat display
   * The chat display is composed of the chat area and the user presence area.
   * where each line is formatted as follow:
   * <[date] [userx] | [message] > | [usery presence if any]
   *
   */
  private String chatDisplay() {
    var sb = new StringBuilder();
    var maxUserLength = getMaxUserLength();
    var lineIndex = 0;
    var maxLinesView = View.maxLinesView(lines);
    var colsRemaining = cols - getMaxUserLength() - 2;
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
      colsRemaining = cols;
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
            .append(CLIColor.ORANGE);
          var paginationInfo = STR."++ (\{users.size() - lineIndex} more)";
          if(maxUserLength >= paginationInfo.length()) {
            sb.append(paginationInfo);
          } else {
            sb.append("-->");
          }
        }
      }
      sb.append(CLIColor.RESET)
        .append("\n");
      who = " ".repeat(maxUserLength + 7);
      lineIndex++;
      
    }
    // Draw empty remaining lines on screen and display side panel of users
    for (; lineIndex < maxLinesView; lineIndex++) {
      sb.append(String.format(STR."%-\{cols - maxUserLength - 2}s\{CLIColor.CYAN}│ ", " "));
      if (users.size() > lineIndex) {
        sb.append(String.format(STR."%-\{maxUserLength}s", users.get(lineIndex)));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
  
  private String chatHeader() {
    var sb = new StringBuilder();
    var colsRemaining = cols - getMaxUserLength() - 2;
    sb.append(CLIColor.CYAN_BACKGROUND);
    sb.append(CLIColor.WHITE);
    var proxies = clientAPI.howManyProxy();
    var title = (STR."%-\{colsRemaining}s ").formatted(STR."CHADOW CLIENT (\{cols},\{lines}) | \{proxies} prox\{proxies > 1 ? "ies" : "y"}");
    colsRemaining -= title.length() + 2; // right side pannel of users + margin (  )
    var totalUsers = (STR."\{CLIColor.BOLD}\{CLIColor.BLACK}%-\{getMaxUserLength()}s").formatted(STR."(\{clientAPI.totalUsers()})");
    colsRemaining -= totalUsers.length();
    sb.append("%s %s".formatted(title, totalUsers));
    sb.append(" ".repeat(Math.max(0, colsRemaining)));
    sb.append(CLIColor.RESET);
    sb.append('\n');
    return sb.toString();
  }
  
  /**
   * Get the last messages to display
   *
   * @return the list of messages to display
   */
  private List<YellMessage> getFormattedMessages() {
    var list = getMessagesToDisplay();
    return list.subList(Math.max(0, list.size() - View.maxLinesView(lines)), list.size());
    
  }
  
  
  private List<YellMessage> getMessagesToDisplay() {
    if (mode == ClientConsoleController.Mode.CHAT_LIVE_REFRESH) {
      var messages = clientAPI.getPublicMessages();
      var list = messages.stream()
                         .flatMap(msg -> formatMessage(msg, msgLineLength()))
                         .toList();
      formattedMessages = list;
      messageScroller.setLines(list.size());
      return list.subList(Math.max(0, messages.size() - View.maxLinesView(lines)), list.size());
    }
    return formattedMessages.subList(messageScroller.getA(), messageScroller.getB()).stream().toList();
  }
  
  private List<String> getUsersToDisplay() {
    var users = clientAPI.users();
    if (users.size() <= View.maxLinesView(lines)) {
      return new ArrayList<>(users);
    }
    if (mode == ClientConsoleController.Mode.CHAT_LIVE_REFRESH) {
      return users.stream()
                  .toList();
    }
    return users.stream()
                .skip(userScroller.getA())
                .toList();
  }
  
  private int msgLineLength() {
    return cols - (getMaxUserLength() * 2) - 8 - 7;
  }
  
  /**
   * Sanitize and format the message to display.
   * If the message is too long, it will be split into multiple lines.
   * the first line will contain the user login and date, the following lines will only contain the message.
   * This allows to display the message in a more readable way.
   *
   * @param message the message to format
   * @param lineLength the max length of a line
   * @return a stream of formatted messages
   */
  private Stream<YellMessage> formatMessage(YellMessage message, int lineLength) {
    
    var sanitizedLines = splitAndSanitize(message.txt(), lineLength);
    return IntStream.range(0, sanitizedLines.size())
                    .mapToObj(index -> new YellMessage(index == 0 ? message.login() : "", sanitizedLines.get(index), message.epoch()));
    
  }
  
  public ClientConsoleController.Mode getMode() {
    return mode;
  }
  
  public void setMode(ClientConsoleController.Mode mode) {
    this.mode = mode;
    if(mode == ClientConsoleController.Mode.CHAT_SCROLLER) {
      messageScroller.moveToBottom();
    }
  }
  
  @Override
  public void scrollPageUp() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollPageUp();
      case USERS_SCROLLER -> userScroller.scrollPageUp();
    }
  }
  
  @Override
  public void scrollPageDown() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollPageDown();
      case USERS_SCROLLER -> userScroller.scrollPageDown();
    }
  }
  
  @Override
  public void scrollBottom() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.moveToBottom();
      case USERS_SCROLLER -> userScroller.moveToBottom();
    }
  }
  
  
  @Override
  public void scrollLineUp() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollUp(1);
      case USERS_SCROLLER -> userScroller.scrollUp(1);
    }
  }
  
  @Override
  public void scrollLineDown() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollDown(1);
      case USERS_SCROLLER -> userScroller.scrollDown(1);
    }
  }
  
  @Override
  public void scrollTop() {
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.moveToTop();
      case USERS_SCROLLER -> userScroller.moveToTop();
    }
  }
  
}