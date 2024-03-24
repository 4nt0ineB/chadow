package fr.uge.chadow.cli;

import fr.uge.chadow.client.Client;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import fr.uge.chadow.core.Message;

public class ClientConsole {
  private static final Logger logger = Logger.getLogger(ClientConsole.class.getName());
  private final Client client;
  private int lines;
  private int columns;
  private final List<Message> messages = new ArrayList<>();
  private final List<String> users = new ArrayList<>();
  private int userDisplayOffset;
  private int maxUserDisplay;
  private String input = "";
  
  public ClientConsole(Client client, int lines, int columns) {
    Objects.requireNonNull(client);
    if(lines <= 0 || columns <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.client = client;
    this.lines = lines;
    this.columns = columns;
  }
  
  private void updateScreen() {
    System.out.println(CLIColor.CLEAR);
    this.maxUserDisplay = lines - 2;
  }
  
  private static void printHelp() {
    var txt = "HELP ----------------\n";
    txt += "\tINFO display connected clients\n";
    System.out.println(txt);
  }
  
  public void start() {
    updateScreen();
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
    messages.add(new Message("test", "test"));
    messages.add(new Message("test", "hello how are you"));
    messages.add(new Message("Morpheus", "Wake up, Neo..."));
    messages.add(new Message("Morpheus", "The Matrix has you..."));
    messages.add(new Message("Morpheus", "Follow the white rabbit"));
    messages.add(new Message("Neo", "what the hell is this"));
    messages.add(new Message("Neo", "Just going to bed now"));
    messages.add(new Message("Alan1", "Master CONTROL PROGRAM\nRELEASE TRON JA 307020...\nI HAVE PRIORITY ACCESS 7\n"));
    
  
    
    chat_loop();
    /*
    try {
      try (var scanner = new Scanner(System.in)) {
        while (scanner.hasNextLine()) {
          switch (scanner.nextLine()) {
            case "INFO" -> {
              //System.out.println("There are " + server.countClients() + " clients");
            }
            case "SHUTDOWN" -> {
              System.out.println("The server is no more accepting new clients");
              //server.shutdown();
            }
            case "SHUTDOWNNOW" -> {
              System.out.println("The server has shutdown");
              System.out.println("Bye");
              //server.shutDownNow();
              return;
            }
            case "HELP" -> printHelp();
            default -> {
              var msg = scanner.nextLine();
              client.sendMessage(msg);
            }
          }
        }
      }
      logger.info("Console thread stopping");
    } catch (InterruptedException e) {
      logger.info("Console thread has been interrupted");
    }*/
  }
  
  public void chat_loop() {
    var sb = new StringBuilder();
    var maxUserLength = getMaxUserLength();
    var lineIndex = 0;
    // header
    var colsRemaining = columns - maxUserLength - 2;
    sb.append(CLIColor.CYAN_BACKGROUND);
    sb.append(CLIColor.WHITE);
    var title = ("%-" + colsRemaining + "s ").formatted("CHADOW CLIENT on " + client.serverHostName());
    colsRemaining -= title.length() + 2; // right side pannel of users + margin (  )
    var totalUsers = (CLIColor.BOLD + "" + CLIColor.BLACK + "%-"+ maxUserLength +"s").formatted("(" + users.size() + ")");
    colsRemaining -= totalUsers.length();
    sb.append("%s %s".formatted(title, totalUsers));
    sb.append(" ".repeat(Math.max(0, colsRemaining)));
    sb.append(CLIColor.RESET);
    sb.append('\n');
    // chat | presence
    for (var message: messages) {
      colsRemaining = columns;
      var date = getADate() + "  ";
      colsRemaining -= date.length();
      var user = ("%" + maxUserLength + "s").formatted(message.login());
      colsRemaining -= user.length();
      var messageParts = new ArrayList<String>();
      colsRemaining -= maxUserLength + 5; // right side pannel of users + margin ( | ) and (│ )
      var messageLines = sanitizeAndSplit(message.txt(), colsRemaining);
      var who = "%s%s".formatted(date, user);
      // breaking down the message into multiple lines
      for (var messageLine: messageLines) {
        messageLine = (" | %-" + colsRemaining + "s").formatted(messageLine);
        var formatedLine = String.format("%s%s" + CLIColor.CYAN + "│ %s\n",
            who,
            messageLine,
            lineIndex < users.size() ? users.get(lineIndex) : "");
        sb.append(formatedLine).append(CLIColor.RESET);
        who = " ".repeat(maxUserLength + 7);
        lineIndex++;
      }
    }
    // remaining lines on screen : display side pannel of users
    // we keep a line for the thematic break and the input
    for (; users.size() > lineIndex && lineIndex < lines - 2; lineIndex++) {
      sb.append(String.format("%-"+ (columns - maxUserLength - 2) +"s" + CLIColor.CYAN + "| " , " "));
      sb.append(String.format("%-"+ (maxUserLength)  +"s\n", users.get(lineIndex)));
    }
    // thematic break
    sb.append(CLIColor.CYAN).append(CLIColor.BOLD);
    sb.append("—".repeat(columns));
    sb.append(CLIColor.RESET);
    sb.append("\n");
    sb.append(("%"+ (maxUserLength + 7) +"s: ").formatted( client.login()));
    sb.append(CLIColor.RESET + "]");
    System.out.println(sb);
  }
  
  private int getMaxUserLength() {
    return users.stream().mapToInt(String::length).max().orElse(0);
  }
  
  /**
   * Split the message into multiple lines if it exceeds the maxCharacters
   * or if it contains a newline character.
   * Replace tabulation with 4 spaces
   * @param message
   * @param maxCharacters
   * @return
   */
  private List<String> sanitizeAndSplit(String message, int maxCharacters) {
    var lines = new ArrayList<String>();
    message = message.replace("\t", "    ");
    var messageLines = message.split("\n");
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
  
  
  
  // just for testing
  private String getADate() {
    Random random = new Random();
    var year = 2024;
    var month = random.nextInt(12) + 1;
    var day = random.nextInt(YearMonth.of(year, month).lengthOfMonth()) + 1;
    var hour = random.nextInt(24);
    var minute = random.nextInt(60);
    
    // Create LocalDateTime object
    var randomDateTime = LocalDateTime.of(year, month, day, hour, minute);
    
    // Format the date
    var formatter = DateTimeFormatter.ofPattern("HH:mm");
    var formattedDate = randomDateTime.format(formatter);
    return formattedDate;
  }
}

// stty size
//  java -jar --enable-preview target/chadow-1.0.0.jar localhost 7777 25 238