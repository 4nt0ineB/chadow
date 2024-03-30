package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.client.ClientConsoleController.Mode;
import fr.uge.chadow.core.reader.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class Display {
  
  private final ClientConsoleController controller;
  //private final List<Message> messages = new ArrayList<>();
  //private final SortedSet<String> users = new TreeSet<>();
  private final Scroller messageScroller;
  private final Scroller userScroller;
  
  //private final LinkedBlockingQueue<Message> messagesQueue = new LinkedBlockingQueue<>();
  
  private final AtomicBoolean viewCanRefresh;
  private final ReentrantLock lock = new ReentrantLock();
  private int lines;
  private int columns;
  private Mode mode = Mode.CHAT_LIVE_REFRESH;
  private ScrollableView helpView;
  
  
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
  }
  
  static String lowInfoBar(int columns) {
    String sb = String.valueOf(CLIColor.CYAN) +
        CLIColor.BOLD +
        "\u25A0".repeat(columns) +
        CLIColor.RESET +
        "\n";
    return sb;
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
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Start the loop that will refresh the view
   */
  public void startLoop() {
    System.out.print(CLIColor.CLEAR);
    System.out.flush();
    while (true) {
      try {
        if (viewCanRefresh.get()) {
          draw();
          if (mode == Mode.CHAT_LIVE_REFRESH) {
            // Wait for incoming messages
            /*var incomingMessage = messagesQueue.take();
            messages.add(incomingMessage);*/
            messageScroller.setLines(controller.numberofMessages());
          }
        }
        Thread.sleep(100);
      } catch (InterruptedException | IOException e) {
        e.printStackTrace();
      }
    }
  }
  
  public void clear() {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
  }
  
  public void draw() throws IOException {
    
    drawInContext();
    View.moveToInputField(lines);
    
  }
  
  /**
   * Draw the view in the current context aka mode
   *
   * @throws IOException
   */
  private void drawInContext() throws IOException {
    
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
    }
    //System.out.print(View.thematicBreak(columns));
    System.out.print(lowInfoBar(columns));
    System.out.print(inputField());
    
  }
  
/*  public void addMessage(Message message) {
    Objects.requireNonNull(message);
    lock.lock();
    try {
      messagesQueue.put(message);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);  // TODO ?
    } finally {
      lock.unlock();
    }
  }*/
  
 /* public void addUser(String user) {
    Objects.requireNonNull(user);
    lock.lock();
    try {
      users.add(user);
      userScroller.setLines(users.size());
      userScroller.setCurrentLine(0);
    } finally {
      lock.unlock();
    }
  }*/
  
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
                        .isBlank() ? " ".repeat(10) : View.formatDate(message.epoch()) + "  ";
      colsRemaining -= date.length();
      var user = ("%" + maxUserLength + "s").formatted(message.login());
      colsRemaining -= user.length();
      colsRemaining -= maxUserLength + 5; // right side pannel of users + margin ( | ) and (│ )
      var who = ("%s" + loginColor + CLIColor.BOLD + "%s" + CLIColor.RESET).formatted(date, user);
      var separator = message.login().isBlank() ? CLIColor.BOLD  + " │ " + CLIColor.RESET: " ▒ ";
      var messageLine = (loginColor + separator + CLIColor.RESET + "%-" + colsRemaining + "s").formatted(message.txt());
      messageLine = View.beautifyCodexLink(messageLine);
      var formatedLine = String.format("%s%s" + CLIColor.CYAN + "│ ",
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
            .append("++ (" + (users.size() - lineIndex) + " more)");
        }
      }
      sb.append(CLIColor.RESET)
        .append("\n");
      who = " ".repeat(maxUserLength + 7);
      lineIndex++;
      
    }
    // Draw empty remaining lines on screen and display side panel of users
    for (; lineIndex < maxLinesView; lineIndex++) {
      sb.append(String.format("%-" + (columns - maxUserLength - 2) + "s" + CLIColor.CYAN + "│ ", " "));
      if (users.size() > lineIndex) {
        sb.append(String.format("%-" + (maxUserLength) + "s", users.get(lineIndex)));
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
    var title = ("%-" + colsRemaining + "s ").formatted("CHADOW CLIENT on " + controller.clientServerHostName());
    colsRemaining -= title.length() + 2; // right side pannel of users + margin (  )
    var totalUsers = (CLIColor.BOLD + "" + CLIColor.BLACK + "%-" + getMaxUserLength() + "s").formatted("(" + controller.totalUsers() + ")");
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
          .formatted("[" + CLIColor.BOLD + CLIColor.CYAN + controller.clientLogin() + CLIColor.RESET + "]");
    } else {
      // (getMaxUserLength() + 50)
      inputField = ("%s\n")
          .formatted(CLIColor.GREY + "[" + CLIColor.GREY + CLIColor.BOLD + controller.clientLogin() + CLIColor.RESET + CLIColor.GREY + "]" + CLIColor.RESET);
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
    
    var sanitizedLines = View.splitAndSanitize(message.txt(), lineLength);
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
    }
    
  }
  
  public void scrollerUp() {
    
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollUp(View.maxLinesView(lines));
      case USERS_SCROLLER -> userScroller.scrollUp(View.maxLinesView(lines));
      case HELP_SCROLLER -> helpView.scrollUp(View.maxLinesView(lines));
    }
    
  }
  
  public void scrollerDown() {
    
    switch (mode) {
      case CHAT_SCROLLER -> messageScroller.scrollDown(View.maxLinesView(lines));
      case USERS_SCROLLER -> userScroller.scrollDown(View.maxLinesView(lines));
      case HELP_SCROLLER -> helpView.scrollDown(View.maxLinesView(lines));
    }
    
  }
  
  private ScrollableView helpView() {
    var txt = """
        :h, :help - Display this help, scroll with e and s
        :u, :users - focus on the users list, enable scrolling with e and s
        :c, :chat - back to the chat in live reload focus
        :m, :msg - on the chat, enable scrolling through the messages with e and s
        :w, :whisper <username> - goes to the private discussion view with the other user (TODO)
        :r <lines> <columns> - Resize the view\s
        :create <path > - Create a codex from a folder or directory                       (TODO)
        :share <SHA-1> - Share a codex with the given SHA-1                               (TODO)
        :unshare <SHA-1> - Unshare a codex with the given SHA-1                           (TODO)
        :mycdx cdx - Display the list of your codex
        :cdx <SHA-1> - Retrieves and display the codex info with the given SHA-1          (TODO)
        
        :exit - Exit the application                                                      (WIP)
        
        """;
    return View.scrollableViewFromString("Help", controller.clientLogin(), lines, columns, txt);
  }
}