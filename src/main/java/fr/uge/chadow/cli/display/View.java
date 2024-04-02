package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.Codex;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface View {
  
  void setDimensions(int lines, int cols);
  void draw() throws IOException;
  void clear();
  void scrollPageUp();
  void scrollPageDown();
  void scrollBottom();
  void scrollTop();
  void scrollLineDown();
  void scrollLineUp();
  
  static void moveCursorToPosition(int x, int y) {
    System.out.print(STR."\033[\{y};\{x}H");
    System.out.flush();
  }
  
  static String thematicBreak(int columns) {
    String sb = STR."\{String.valueOf(CLIColor.CYAN)}\{CLIColor.BOLD}\{"—".repeat(columns)}\{CLIColor.RESET}\n";
    return sb;
  }
  
  static String inviteCharacter() {
    return STR."\{CLIColor.BOLD}\u00BB ";
  }
  
  /**
   * Clear the display, everything above the input field.
   */
  static void clearDisplayArea(int lines) {
    clearDisplayAndMore(lines, lines);
  }
  
  /**
   * Clear the display + the other lines
   *
   * @param rest
   */
  static void clearDisplayAndMore(int lines, int rest) {
    //clearAllScreen();
    for (int i = 0; i < lines + lines; i++) {
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
   * Colorize codex (cdx:SHA-1) links present in string
   */
  static String beautifyCodexLink(String txt) {
    var sb = new StringBuilder();
    return txt.replaceAll("cdx:([a-fA-F0-9]{40})",
        STR."\{CLIColor.BOLD}\{CLIColor.GREEN}cdx:$1\{CLIColor.RESET}");
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
    var messageLines = txt.split("\\n", -1);
    for (var line : messageLines) {
      if (line.isEmpty()) {
        lines.add("");
        continue;
      }
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
        lines.add(line.substring(start, end));
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
    View.moveCursorToPosition(3, lines);
  }
  
  static String colorize(CLIColor color, String txt) {
    return (STR."\{color}%s\{CLIColor.RESET}").formatted(txt);
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
  
  static String bytesToHumanReadable(long bytes) {
    if (bytes < 0) {
      throw new IllegalArgumentException("bytes must be positive");
    }
    if (bytes < 1024) {
      return STR."\{bytes} B";
    }
    if (bytes < 1024 * 1024) {
      return String.format("%.2f KB", (double) bytes / 1024);
    }
    if (bytes < 1024 * 1024 * 1024) {
      return String.format("%.2f MB", (double) bytes / (1024 * 1024));
    }
    if (bytes < 1024L * 1024 * 1024 * 1024) {
      return String.format("%.2f GB", (double) bytes / (1024 * 1024 * 1024));
    }
    if (bytes < 1024L * 1024 * 1024 * 1024 * 1024) {
      return String.format("%.2f TB", (double) bytes / (1024 * 1024 * 1024 * 1024));
    }
    return "+inf (╯°□°)╯︵ ┻━┻";
  }
  
  /**
   * Convert a fingerprint sha1 to a hexadecimal formatted string
   * @param bytes
   * @return
   */
  static String bytesToHexadecimal(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for(byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  /**
   * Create a scrollable view from a string
   *
   * @param title
   * @param lines
   * @param cols
   * @param help
   * @return
   */
  static Scrollable scrollableFromString(String title, int lines, int cols, String help) {
    var textLines = splitAndSanitize(help, cols);
    return new Scrollable(title, lines, cols, textLines);
  }
  
  static <T> Selector<T> selectorFromList(String title, int lines, int cols, List<T> list, Function<? super T, String> mapper) {
    var linesByItem = new ArrayList<Map.Entry<T, List<String>>>();
    var linesToDisplay = new ArrayList<String>();
    for(var item : list){
      var str = mapper.apply(item);
      var formattedDescription = splitAndSanitize(str, cols);
      linesToDisplay.addAll(formattedDescription);
      linesByItem.add(Map.entry(item, formattedDescription));
    }
    return new Selector<>(title, lines, cols, linesByItem, new Scrollable(title, lines, cols, linesToDisplay), mapper);
  }
  
  static String codexShortDescription(Codex cdx) {
    return STR."\{cdx.name()} ─ \{cdx.files().size()} files \{bytesToHumanReadable(cdx.totalSize())}";
  }
}
