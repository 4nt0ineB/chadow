package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.client.Recipient;
import fr.uge.chadow.core.protocol.WhisperMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fr.uge.chadow.cli.display.View.splitAndSanitize;

public class PrivateMessageView implements View {
  private final ClientConsoleController controller;
  private final Scroller messageScroller;
  
  private final AtomicBoolean viewCanRefresh;
  private final ReentrantLock lock = new ReentrantLock();
  private int lines;
  private int cols;
  private ClientConsoleController.Mode mode = ClientConsoleController.Mode.PRIVATE_MESSAGE_LIVE;
  private Recipient recipient;
  private List<WhisperMessage> messages = new ArrayList<>();
  
  public PrivateMessageView(int lines, int cols, AtomicBoolean viewCanRefresh, ClientConsoleController controller) {
    Objects.requireNonNull(viewCanRefresh);
    Objects.requireNonNull(controller);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and cols must be positive");
    }
    this.controller = controller;
    this.lines = lines;
    this.cols = cols;
    this.viewCanRefresh = viewCanRefresh;
    this.messageScroller = new Scroller(0, View.maxLinesView(lines));
  }
  
  public void setReceiver(Recipient recipient) {
    Objects.requireNonNull(recipient);
    this.recipient = recipient;
    updateMessages();
  }
  
  private void updateMessages(){
    messages = recipient.messages();
    messageScroller.setLines(messages.size());
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
      this.cols = cols;
    } finally {
      lock.unlock();
    }
  }
  
  public void clear() {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
  }
  
  /**
   * Draw the view in the current mode
   *
   * @throws IOException
   */
  public void draw() throws IOException {
    clear();
    System.out.print(chatHeader());
    System.out.print(chatDisplay());
    if (mode == ClientConsoleController.Mode.PRIVATE_MESSAGE_LIVE) {
      updateMessages();
    }
  }
  
  /**
   * Get the max length of the usernames
   * Default size is 5
   *
   * @return
   */
  private int getMaxUserLength() {
    return Math.max(5, Math.max(recipient.username().length(), controller.clientLogin().length()));
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
    var colsRemaining = cols - getMaxUserLength() - 2;
    // chat | presence
    var iterator = getFormattedMessages().iterator();
    var loginColor = "";
    while (iterator.hasNext() && lineIndex < maxLinesView) {
      var message = iterator.next();
      if (!message.username()
                  .isBlank()) {
        loginColor = CLIColor.stringToColor(message.username());
      }
      colsRemaining = cols;
      var date = message.username()
                        .isBlank() ? " ".repeat(10) : STR."\{View.formatDate(message.epoch())}  ";
      colsRemaining -= date.length();
      var user = (STR."%\{maxUserLength}s").formatted(message.username());
      colsRemaining -= user.length();
      colsRemaining -= maxUserLength + 5; // right side pannel of users + margin ( | ) and (│ )
      var who = (STR."%s\{loginColor}\{CLIColor.BOLD}%s\{CLIColor.RESET}").formatted(date, user);
      var separator = message.username()
                             .isBlank() ? STR."\{CLIColor.BOLD} │ \{CLIColor.RESET}" : " ▒ ";
      var messageLine = (STR."\{loginColor}\{separator}\{CLIColor.RESET}%-\{colsRemaining}s").formatted(message.txt());
      messageLine = View.beautifyCodexLink(messageLine);
      var formatedLine = String.format(STR."%s%s\{CLIColor.CYAN}",
          who,
          messageLine);
      sb.append(formatedLine);
      sb.append(CLIColor.RESET)
        .append("\n");
      who = " ".repeat(maxUserLength + 7);
      lineIndex++;
      
    }
    // Draw empty remaining lines on screen and display side panel of users
    for (; lineIndex < maxLinesView; lineIndex++) {
      sb.append(String.format(STR."%-\{cols - 1}s\{CLIColor.CYAN}", " "));
      sb.append("\n");
    }
    return sb.toString();
  }
  
  private String chatHeader() {
    var sb = new StringBuilder();
    sb.append(CLIColor.CYAN_BACKGROUND);
    sb.append(CLIColor.WHITE);
    var title = (STR."%-\{cols - 1}s ").formatted(STR."CHADOW CLIENT on \{controller.clientServerHostName()} (" + lines + "x" + cols + STR.") Private message with \{recipient.username()}");
    sb.append(title);
    sb.append(CLIColor.RESET);
    sb.append('\n');
    return sb.toString();
  }
  
  /**
   * Get the last messages to display
   *
   * @return
   */
  private List<WhisperMessage> getFormattedMessages() {
    var subList = getMessagesToDisplay();
    var list = subList.stream()
                      .flatMap(message -> formatMessage(message, msgLineLength()))
                      .collect(Collectors.toList());
    return list.subList(Math.max(0, list.size() - View.maxLinesView(lines)), list.size());
    
  }
  
  
  private List<WhisperMessage> getMessagesToDisplay() {
    if (messages.size() <= View.maxLinesView(lines)) {
      return messages;
    }
    if (mode == ClientConsoleController.Mode.CHAT_LIVE_REFRESH) {
      return messages.subList(Math.max(0, messages.size() - View.maxLinesView(lines)), messages.size());
    }
    return messages.subList(messageScroller.getA(), messageScroller.getB());
    
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
   * @param message
   * @param lineLength
   * @return
   */
  private Stream<WhisperMessage> formatMessage(WhisperMessage message, int lineLength) {
    
    var sanitizedLines = splitAndSanitize(message.txt(), lineLength);
    return IntStream.range(0, sanitizedLines.size())
                    .mapToObj(index -> new WhisperMessage(index == 0 ? message.username() : "", sanitizedLines.get(index), message.epoch()));
    
  }
  
  public ClientConsoleController.Mode getMode() {
    return mode;
  }
  
  public void setMode(ClientConsoleController.Mode mode) {
    this.mode = mode;
  }
  
  @Override
  public void scrollPageUp() {
    switch (mode) {
      case PRIVATE_MESSAGE_SCROLLER -> messageScroller.scrollPageUp();
    }
  }
  
  @Override
  public void scrollPageDown() {
    switch (mode) {
      case PRIVATE_MESSAGE_SCROLLER -> messageScroller.scrollPageDown();
    }
  }
  
  @Override
  public void scrollBottom() {
    switch (mode) {
      case PRIVATE_MESSAGE_SCROLLER -> messageScroller.setLines(controller.numberOfMessages());
    }
  }
  
  
  @Override
  public void scrollLineUp() {
    switch (mode) {
      case PRIVATE_MESSAGE_SCROLLER -> messageScroller.scrollUp(1);
    }
  }
  
  @Override
  public void scrollLineDown() {
    switch (mode) {
      case PRIVATE_MESSAGE_SCROLLER -> messageScroller.scrollDown(1);
    }
  }
  
  @Override
  public void scrollTop() {
    switch (mode) {
      case PRIVATE_MESSAGE_SCROLLER -> messageScroller.moveToTop();
    }
  }
  
  public Recipient receiver() {
    return recipient;
  }
}