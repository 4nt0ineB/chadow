package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.Client;
import fr.uge.chadow.core.Message;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Display {
  
  private final Client client;
  private final List<Message> messages = new ArrayList<>();
  private final SortedSet<String> users = new TreeSet<>();
  private final Scroller messageScroller;
  private final Scroller userScroller;
  
  private final LinkedBlockingQueue<Message> messagesQueue = new LinkedBlockingQueue<>();
  
  private final AtomicBoolean viewCanRefresh;
  private final ReentrantLock lock = new ReentrantLock();
  private int lines;
  private int columns;
  private MODE mode = MODE.CHAT_LIVE_REFRESH;
  private ScrollableView helpView;
  public Display(Client client, int lines, int cols, AtomicBoolean viewCanRefresh) {
    Objects.requireNonNull(client);
    Objects.requireNonNull(viewCanRefresh);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and cols must be positive");
    }
    this.client = client;
    this.lines = lines;
    this.columns = cols;
    this.viewCanRefresh = viewCanRefresh;
    this.messageScroller = new Scroller(0, View.maxLinesView(lines));
    this.userScroller = new Scroller(0, View.maxLinesView(lines));
    helpView = helpView();
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
          if (mode == MODE.HELP_SCROLLER) {
            helpView.draw();
          } else {
            draw();
            if (mode == MODE.CHAT_LIVE_REFRESH) {
              // Wait for incoming messages
              var incomingMessage = messagesQueue.take();
              messages.add(incomingMessage);
              messageScroller.setLines(messages.size());
            }
          }
        }
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
  
  public void draw() {
    try {
      View.moveCursorToPosition(1, 1);
      View.clearDisplayArea(lines);
      drawInContext();
      View.moveToInputField(lines);
    } catch (IOException e) {
      throw new RuntimeException(e); // TODO ?
    }
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
          USERS_SCROLLER -> printChatDisplay();
      case HELP_SCROLLER -> helpView.draw();
    }
  }
  
  /**
   * Process the message or the command
   *
   * @param input
   * @return true if the user can type again, otherwise it's the view's turn.
   * @throws InterruptedException
   */
  public boolean processInput(String input) throws InterruptedException {
    lock.lock();
    try {
      return switch (input) {
        case ":c", ":chat" -> {
          mode = MODE.CHAT_SCROLLER;
          messageScroller.setLines(messages.size());
          yield true;
        }
        case ":u", ":users" -> {
          mode = MODE.USERS_SCROLLER;
          userScroller.setLines(messages.size());
          userScroller.setCurrentLine(0);
          yield true;
        }
        case ":m", ":message" -> {
          mode = MODE.CHAT_LIVE_REFRESH;
          yield false;
        }
        case ":exit" -> {
          throw new Error("TODO exit");
          //yield false;
        }
        case ":h", ":help" -> {
          mode = MODE.HELP_SCROLLER;
          helpView = helpView();
          yield true;
        }
        // specific mode commands
        default -> switch (mode) {
          case CHAT_LIVE_REFRESH -> processInputModeLiveRefresh(input);
          case CHAT_SCROLLER -> processInputModeScroller(input, messageScroller);
          case USERS_SCROLLER -> processInputModeScroller(input, userScroller);
          case HELP_SCROLLER -> processInputModeScroller(input, helpView.scroller());
        };
      };
    } finally {
      lock.unlock();
    }
  }
  
  private boolean processInputModeLiveRefresh(String input) throws InterruptedException {
    if (!input.startsWith(":") && !input.isBlank()) {
      // just to see the message in the chat for now
      addMessage(new Message(client.login(), input, System.currentTimeMillis()));
      // when everything will be connected, will be
      // client.sendMessage(input);
    }
    return false;
  }
  
  private boolean processInputModeScroller(String input, Scroller scroller) {
    switch (input) {
      case "e" -> scroller.scrollUp(View.maxLinesView(lines));
      case "s" -> scroller.scrollDown(View.maxLinesView(lines));
    }
    return true;
  }
  
  public void addMessage(Message message) {
    Objects.requireNonNull(message);
    try {
      System.err.println(message);
      messagesQueue.put(message);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);  // TODO ?
    }
  }
  
  public void addUser(String user) {
    Objects.requireNonNull(user);
    lock.lock();
    try {
      users.add(user);
      userScroller.setLines(users.size());
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Get the max length of the usernames
   * Default size is 5
   *
   * @return
   */
  private int getMaxUserLength() {
    return Math.max(users.stream()
                         .mapToInt(String::length)
                         .max()
                         .orElse(0), 5);
  }
  
  /**
   * Print the chat display
   * The chat display is composed of the chat area and the user presence area.
   * where each line is formatted as follow:
   * <[date] [userx] | [message] > | [usery presence if any]
   *
   * @throws IOException
   */
  private void printChatDisplay() throws IOException {
    var sb = new StringBuilder();
    var maxUserLength = getMaxUserLength();
    var lineIndex = 0;
    var maxLinesView = View.maxLinesView(lines);
    var colsRemaining = columns - getMaxUserLength() - 2;
    sb.append(chatHeader());
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
      var messageLine = (loginColor + " | " + CLIColor.RESET + "%-" + colsRemaining + "s").formatted(message.txt());
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
    sb.append(View.thematicBreak(columns));
    sb.append(inputField());
    System.out.print(sb);
    sb.setLength(0);
  }
  
  private String chatHeader() {
    var sb = new StringBuilder();
    var colsRemaining = columns - getMaxUserLength() - 2;
    sb.append(CLIColor.CYAN_BACKGROUND);
    sb.append(CLIColor.WHITE);
    var title = ("%-" + colsRemaining + "s ").formatted("CHADOW CLIENT on " + client.serverHostName());
    colsRemaining -= title.length() + 2; // right side pannel of users + margin (  )
    var totalUsers = (CLIColor.BOLD + "" + CLIColor.BLACK + "%-" + getMaxUserLength() + "s").formatted("(" + users.size() + ")");
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
          .formatted("[" + CLIColor.BOLD + CLIColor.CYAN + client.login() + CLIColor.RESET + "]");
    } else {
      // (getMaxUserLength() + 50)
      inputField = ("%s\n")
          .formatted(CLIColor.GREY + "[" + CLIColor.GREY + CLIColor.BOLD + client.login() + CLIColor.RESET + CLIColor.GREY + "]" + CLIColor.RESET);
    }
    return inputField + "> ";
  }
  
  /**
   * Get the last messages to display
   *
   * @return
   */
  private List<Message> getFormattedMessages() {
    var subList = getMessagesToDisplay();
    return subList.stream()
                  .flatMap(message -> formatMessage(message, msgLineLength()))
                  .collect(Collectors.toList());
  }
  
  private List<Message> getMessagesToDisplay() {
    if (messages.size() <= View.maxLinesView(lines)) {
      return messages;
    }
    if (mode == MODE.CHAT_LIVE_REFRESH) {
      return messages.subList(Math.max(0, messages.size() - View.maxLinesView(lines)), messages.size());
    }
    return messages.subList(messageScroller.getA(), messageScroller.getB());
  }
  
  private List<String> getUsersToDisplay() {
    if (users.size() <= View.maxLinesView(lines)) {
      return new ArrayList<>(users);
    }
    if (mode == MODE.CHAT_LIVE_REFRESH) {
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
  
  private ScrollableView helpView() {
    var txt = """
        :h, :help - Display this help, scroll with e and s
        :u, :users - focus on the users list, enable scrolling with e and s
        :c, :chat - focus on the chat, enable scrolling with e and s\s
        :m, :msg - back to the chat in live reload
        :w, :whisper <username> - goes to the private discussion view with the other user (WIP)
        :r <lines> <columns> - Resize the view\s
        :cdx <SHA-1> - Display the codex info with the given SHA-1                        (WIP)
        :exit - Exit the application                                                      (WIP)
        """;
    return View.scrollableViewString(client, "Help", lines, columns, txt, viewCanRefresh);
  }
  
  private enum MODE {
    CHAT_LIVE_REFRESH,
    CHAT_SCROLLER,
    USERS_SCROLLER,
    HELP_SCROLLER,
  }
  
}