package fr.uge.chadow.cli;

import fr.uge.chadow.cli.view.View;
import fr.uge.chadow.client.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import fr.uge.chadow.core.Message;

public class ClientConsole {
  
  private static final Logger logger = Logger.getLogger(ClientConsole.class.getName());
  private final Client client;
  private final List<Message> messages = new ArrayList<>();
  private final List<String> users = new ArrayList<>();
  private final LinkedBlockingQueue<Message> messagesQueue = new LinkedBlockingQueue<>();
  private int lines;
  private int columns;
  private AtomicBoolean autoRefresh = new AtomicBoolean(true);
  private int userDisplayOffset;
  private int maxUserDisplay;
  
  public ClientConsole(Client client, int lines, int columns) {
    Objects.requireNonNull(client);
    if(lines <= 0 || columns <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.client = client;
    this.lines = lines;
    this.columns = columns;
  }
  
  private static void printHelp() {
    var txt = "HELP ----------------\n";
    txt += "\tINFO display connected clients\n";
    System.out.println(txt);
  }
  
  
  public void start() throws IOException, InterruptedException {
    
    System.out.println("Running with " + lines + " lines and " + columns + " columns");
    
    users.add("test");
    users.add("Morpheus");
    users.add("Trinity");
    users.add("Neo");
    users.add("Flynn");
    users.add("Alan");
    users.add("Lora");
    users.add("Gandalf");
    users.add("ThorinSonOfThrainSonOfThror");
    users.add("Bilbo");
    users.add("SKIDROW");
    users.add("Antoine");
    
    messages.add(new Message("test", "test"));
    messages.add(new Message("test", "hello how are you"));
    messages.add(new Message("Morpheus", "Wake up, Neo..."));
    messages.add(new Message("Morpheus", "The Matrix has you..."));
    messages.add(new Message("Morpheus", "Follow the white rabbit"));
    messages.add(new Message("Neo", "what the hell is this"));
    messages.add(new Message("Neo", "Just going to bed now"));
    messages.add(new Message("Alan1", "Master CONTROL PROGRAM\nRELEASE TRON JA 307020...\nI HAVE PRIORITY ACCESS 7"));
    messages.add(new Message("SKIDROW", "Here is the codex for the game: cdx:1eb49a28a0c02b47eed4d0b968bb9aec116a5a47"));
    messages.add(new Message("Antoine", "Le lien vers le sujet : http://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html"));
    
    Thread.ofPlatform().daemon().start(() -> {
      while (true) {
        try {
          Thread.sleep(1000); // Sleep for 1 second
          messagesQueue.put(new Message("test", "test"));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });
    
    Thread.ofPlatform().daemon().start(() -> {
      try {
        chat_loop();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      
    });
    
    
    userInput();
  }
  
  private int msgLineLength() {
    return columns - getMaxUserLength() * 2  - 2 - 7 + 5;
  }
  
  /**
   * Main chat loop
   * @throws IOException
   */
  public void chat_loop() throws IOException {
    System.out.print(CLIColor.CLEAR);
    System.out.flush();
    var maxUserLength = getMaxUserLength();
   var position = new int[]{maxUserLength + 10, lines};
    while (true) {
      try {
        if(autoRefresh.get()) {
          drawDiscussionThread();
          // Wait for incoming messages
          var incomingMessage = messagesQueue.take();
          messages.add(incomingMessage);
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
  
  /**
   * Draw the chat area
   * @throws IOException
   */
  private void drawDiscussionThread() throws IOException {
    var maxUserLength = getMaxUserLength();
    var position = new int[]{maxUserLength + 10, lines};
    moveCursorToPosition(1, 1);
    //clearDisplayArea();
    printChatDisplay();
    moveCursorToPosition(position[0], position[1]);
  }
  
  /**
   * Read user input from the console
   * We talk about writeMode when autoRefresh is disabled.
   * In autoRefresh mode (set to true), incoming messages are displayed automatically.
   * The user can press enter to stop the auto refresh
   * to be able to type a message (or a command).
   * When the user press enter again the message (or the command) is processed,
   * then the chat goes back into autoRefresh mode.
   *
   * In writeMode (autoRefresh set to false), the user can escape a line with '\' followed by enter.
   * This is useful to write a multiline message.
   *
   *
   * @throws IOException
   */
  private void userInput() throws IOException, InterruptedException {
    // input
    var maxUserLength = getMaxUserLength();
    var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String inputField = "";
    var c = 0;
    var escape = false;
    
    while ((c = reader.read()) != -1) {
      if(autoRefresh.get()) {
        if (c == '\n') {
          var refresh = autoRefresh.get();
          autoRefresh.set(!refresh);
          drawDiscussionThread();
        }
      }else {
        if (escape && !inputField.endsWith("\n")) {
          System.out.print((" ".repeat(maxUserLength + 8) + "> "));
          inputField += '\n';
          escape = false;
        } else {
          // we escape with '\', and is stored has line break
          if (c == '\\' ) {
            escape = true;
          } else if (c == '\n') {
            processInput(inputField);
            inputField = "";
            var refresh = autoRefresh.get();
            autoRefresh.set(!refresh);
          } else {
            inputField += (char) c;
          }
        }
      }
    }
  }
  
  /**
   * Process the message or the command
   * @param msg
   * @throws InterruptedException
   */
  private void processInput(String msg) throws InterruptedException {
    if(msg.isBlank()) {
      return;
    }
    switch (msg) {
      case ":h", ":help" -> printHelp();
     // case ":m"
      case ":exit" -> {}
      default -> {
        messagesQueue.put(new Message(client.login(), msg));
      }
    }
  }
  
  /**
   * Print the chat display
   * The chat display is composed of the chat area and the user presence area.
   * where each line is formatted as follow:
   * <[date] [userx] | [message] > | [usery presence if any]
   * @throws IOException
   */
  private void printChatDisplay() throws IOException {
    var sb = new StringBuilder();
    var maxUserLength = getMaxUserLength();
    var lineIndex = 0;
    var maxChatLines = lines -3;
    var colsRemaining = columns - getMaxUserLength() - 2;
    sb.append(chatHeader());
    // chat | presence
    var iterator = getFormattedMessages().iterator();
    for (; iterator.hasNext() && lineIndex < maxChatLines; ) {
        var message = iterator.next();
      colsRemaining = columns;
      var date = getADate() + "  ";
      colsRemaining -= date.length();
      var user = ("%" + maxUserLength + "s").formatted(message.login());
      colsRemaining -= user.length();
      var messageParts = new ArrayList<String>();
      colsRemaining -= maxUserLength + 5; // right side pannel of users + margin ( | ) and (│ )
      var who = ("%s"+ CLIColor.stringToColor(message.login()) + CLIColor.BOLD  + "%s" + CLIColor.RESET).formatted(date, user);
      var messageLine = (" | %-" + colsRemaining + "s").formatted(message.txt());
      messageLine = beautifyCodexLink(messageLine);
        var formatedLine = String.format("%s%s" + CLIColor.CYAN + "│ ",
            who,
            messageLine);
        sb.append(formatedLine);
        // display (or not) the user presence. Paginate if necessary
        if(lineIndex < users.size()){
          if(lineIndex < maxChatLines -1) {
            sb.append(CLIColor.CYAN);
            sb.append(users.get(lineIndex));
          } else {
            sb.append(CLIColor.BOLD).append(CLIColor.ORANGE)
              .append("++ (" + (users.size() - lineIndex) + " more)");
          }
        }
        sb.append(CLIColor.RESET)
          .append("\n");
        who = " ".repeat(maxUserLength + 7);
        lineIndex++;
      
    }
    // Draw empty remaining lines on screen and display side panel of users
    for (; lineIndex < maxChatLines; lineIndex++) {
      sb.append(String.format("%-"+ (columns - maxUserLength - 2) +"s" + CLIColor.CYAN + "│ " , " "));
      if(users.size() > lineIndex){
        sb.append(String.format("%-"+ (maxUserLength)  +"s", users.get(lineIndex)));
      }
      sb.append("\n");
    }
    sb.append(thematicBreak());
    sb.append(inputField());
    System.out.print(sb);
    sb.setLength(0);
  }
  
  /**
   *  Colorize codex (cdx:SHA-1) links present in string
   */
  private String beautifyCodexLink(String txt) {
    var sb = new StringBuilder();
    return txt.replaceAll("cdx:([a-fA-F0-9]{40})", CLIColor.BOLD + "" + CLIColor.GREEN + "cdx:$1" + CLIColor.RESET);
  }
  
  /**
   * Get the last messages to display
   * @return
   */
  private List<Message> getFormattedMessages() {
    var subList = messages.subList(Math.max(0, messages.size() - maxChatLines()), messages.size());
    return subList.stream()
                  .flatMap(message -> formatMessage(message, msgLineLength()))
                  .collect(Collectors.toList());
  }
  
  /**
   * Sanitize and format the message to display.
   * If the message is too long, it will be split into multiple lines.
   * the first line will contain the user login and date, the following lines will only contain the message.
   * This allows to display the message in a more readable way.
   * @param message
   * @param lineLength
   * @return
   */
  private Stream<Message> formatMessage(Message message, int lineLength) {
    var sanitizedLines = splitAndSanitize(message, lineLength);
    return IntStream.range(0, sanitizedLines.size())
                    .mapToObj(index -> new Message(index == 0 ? message.login() : "", sanitizedLines.get(index)));
  }
  
  /**
   * Split the message into multiple lines if it exceeds the maxCharacters of the chat view
   * or if it contains a newline character.
   * Replace tabulation by 4 spaces.
   * @param message
   * @param maxCharacters
   * @return
   */
  private static List<String> splitAndSanitize(Message message, int maxCharacters) {
    var lines = new ArrayList<String>();
    var txt = message.txt().replace("\t", "    ");
    var messageLines = txt.split("\n");
    for (var line: messageLines) {
      var currentLine = "";
      for(var word: line.split(" ")) {
        if (currentLine.length() + word.length() > maxCharacters) {
          lines.add(currentLine);
          currentLine = "";
        }
        currentLine += word + " ";
      }
      lines.add(currentLine);
    }
    return lines;
  }
  
  private int maxChatLines() {
    return lines - 3;
  }
  
  /**
   * Get the max length of the usernames
   * Default size is 5
   * @return
   */
  private int getMaxUserLength() {
    return Math.max(users.stream().mapToInt(String::length).max().orElse(0), 5);
  }
  
  private String chatHeader() {
    var sb = new StringBuilder();
    var colsRemaining = columns - getMaxUserLength() - 2;
    sb.append(CLIColor.CYAN_BACKGROUND);
    sb.append(CLIColor.WHITE);
    var title = ("%-" + colsRemaining + "s ").formatted("CHADOW CLIENT on " + client.serverHostName());
    colsRemaining -= title.length() + 2; // right side pannel of users + margin (  )
    var totalUsers = (CLIColor.BOLD + "" + CLIColor.BLACK + "%-"+ getMaxUserLength() +"s").formatted("(" + users.size() + ")");
    colsRemaining -= totalUsers.length();
    sb.append("%s %s".formatted(title, totalUsers));
    sb.append(" ".repeat(Math.max(0, colsRemaining)));
    sb.append(CLIColor.RESET);
    sb.append('\n');
    return sb.toString();
  }
  
  private String thematicBreak() {
    var sb = new StringBuilder();
    sb.append(CLIColor.CYAN).append(CLIColor.BOLD);
    sb.append("—".repeat(columns));
    sb.append(CLIColor.RESET);
    sb.append("\n");
    return sb.toString();
  }
  
  private String inputField() {
    var inputField = "";
    if(autoRefresh.get()) {
      inputField = ("%"+ (getMaxUserLength() + 21) + "s> %n")
          .formatted("[" + CLIColor.BOLD + CLIColor.CYAN + client.login() + CLIColor.RESET + "]");
    }else{
      inputField = ("%"+ (getMaxUserLength() + 50) + "s  %n")
          .formatted(CLIColor.GREY + "[" + CLIColor.GREY + CLIColor.BOLD + client.login() + CLIColor.RESET + CLIColor.GREY + "]" + CLIColor.RESET);
    }
    return inputField;
  }
  
  // just for testing
  private String getADate() {
    var date = LocalDateTime.now();
    var formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    var formattedDate = date.format(formatter);
    return formattedDate;
  }
}


// stty size
//  java -jar --enable-preview target/chadow-1.0.0.jar localhost 7777 25 238