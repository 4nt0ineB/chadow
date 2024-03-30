package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.Client;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public interface View {
  static void moveCursorToPosition(int x, int y) {
    System.out.print("\033[" + y + ";" + x + "H");
    System.out.flush();
  }
  
  static String thematicBreak(int columns) {
    String sb = String.valueOf(CLIColor.CYAN) +
        CLIColor.BOLD +
        "—".repeat(columns) +
        CLIColor.RESET +
        "\n";
    return sb;
  }
  
  static String inviteCharacter() {
    return CLIColor.BOLD  + "\u00BB ";
  }
  
  /**
   * Clear the display, everything above the input field.
   */
  static void clearDisplayArea(int lines) {
    for (int i = 0; i < lines - 2; i++) {
      View.moveCursorToPosition(1, i + 1); // Move cursor to each line in the chat area
      System.out.print(CLIColor.CLEAR_LINE); // Clear the line
    }
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
  }
  
  /**
   * Clear the display + the other lines
   *
   * @param rest
   */
  static void clearDisplayAndMore(int lines, int rest) {
    for (int i = 0; i < lines + rest; i++) {
      View.moveCursorToPosition(1, i + 1);
      System.out.print(CLIColor.CLEAR_LINE);
    }
  }
  
  /**
   * Calculate the maximum number of lines of content that can be displayed
   * (thus excluding the input field and the header)
   *
   * @param lines
   * @return
   */
  static int maxLinesView(int lines) {
    return lines - 4;
  }
  
  /**
   * Create a scrollable view from a string
   *
   * @param title
   * @param login
   * @param lines
   * @param cols
   * @param help
   * @return
   */
  static ScrollableView scrollableViewFromString(String title, String login, int lines, int cols, String help) {
    var textLines = splitAndSanitize(help, cols);
    return new ScrollableView(title, login, lines, cols, textLines);
  }
  
  /**
   * Colorize codex (cdx:SHA-1) links present in string
   */
  static String beautifyCodexLink(String txt) {
    var sb = new StringBuilder();
    return txt.replaceAll("cdx:([a-fA-F0-9]{40})",
        CLIColor.BOLD + "" + CLIColor.GREEN + "cdx:$1" + CLIColor.RESET);
  }
  
  /**
   * Split the string into multiple lines if it exceeds the maxCharacters of the chat view
   * or if it contains a newline character.
   * Replace tabulation by 4 spaces.
   *
   * @param txt
   * @param maxCharacters
   * @return
   */
  static List<String> splitAndSanitize(String txt, int maxCharacters) {
    var lines = new ArrayList<String>();
    txt = txt.replace("\t", "    ");
    var messageLines = txt.split("\n");
    for (var line : messageLines) {
      int start = 0;
      while (start < line.length()) {
        var end = Math.min(start + maxCharacters, line.length());
        if (end - start > maxCharacters) {
          // on cherche d'abord le dernier espace avant maxCharacters pour découper
          // sinon on découpe quand même
          int lastSpace = line.substring(start, end).lastIndexOf(' ');
          if (lastSpace != -1) {
            end = start + lastSpace;
          } else {
            end = start + maxCharacters;
          }
        }
        lines.add(line.substring(start, end).trim());
        start = end;
      }
    }
    return lines;
  }
  
  static List<String> splitAndSanitize(List<String> txt, int maxCharacters) {
    var lines = new ArrayList<String>();
    for (var line : txt) {
      lines.addAll(splitAndSanitize(line, maxCharacters));
    }
    return lines;
  }
  
  /**
   * Move the cursor to the input field
   *
   * @param lines
   */
  static void moveToInputField(int lines) {
    View.moveCursorToPosition(2, lines);
  }
  
  /**
   * Format the date to HH:mm:ss
   *
   * @param millis
   * @return
   */
  static String formatDate(long millis) {
    var date = new Date(millis);
    var formatter = new SimpleDateFormat("HH:mm:ss");
    return formatter.format(date);
  }
  
  /**
   * Process the message or the command
   *
   * @param input
   * @return true if the user can type again, otherwise it's the view's turn.
   * @throws InterruptedException
   */
  boolean processInput(String input) throws InterruptedException;
  
  void setDimensions(int lines, int cols);
  
  void pin();
  
  void draw();
  
  void clearDisplayAndMore(int rest);
  
  void clear();
}
