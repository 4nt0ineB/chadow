package fr.uge.chadow.client.cli.display;

import fr.uge.chadow.client.cli.CLIColor;
import fr.uge.chadow.client.cli.display.view.ScrollableView;
import fr.uge.chadow.client.cli.display.view.SelectorView;
import fr.uge.chadow.client.CodexStatus;
import fr.uge.chadow.client.DirectMessages;
import fr.uge.chadow.core.protocol.server.SearchResponse;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

public interface View {
  
  static void moveCursorToPosition(int x, int y) {
    System.out.print(STR."\033[\{y};\{x}H");
    System.out.flush();
  }
  
  static String inviteCharacter() {
    return STR."\{CLIColor.BOLD}\u00BB ";
  }
  
  /**
   * Clear the display, everything above the input field.
   */
  static void clearDisplayArea(int lines) {
    clearDisplayAndMore(lines);
  }
  
  static void clear(int lines) {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
  }
  
  /**
   * Clear the display + the other lines
   */
  static void clearDisplayAndMore(int lines) {
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
   * @param lines the number of lines of the terminal
   * @return the maximum number of lines of content that can be displayed
   * (excluding the input field and the header)
   */
  static int maxLinesView(int lines) {
    return lines - 4;
  }
  
  /**
   * Colorize codex (cdx:SHA-1) links present in string
   */
  static String beautifyCodexLink(String txt) {
    return txt.replaceAll("cdx:([a-fA-F0-9]{40})",
        STR."\{CLIColor.BOLD}\{CLIColor.GREEN}cdx:$1\{CLIColor.RESET}");
  }
  
  /**
   * Split the string into multiple lines if it exceeds the maxCharacters
   * or if it contains a newline character.
   * Replace tabulation by 4 spaces.
   *
   * @param txt the string to split
   * @param maxCharacters the maximum number of characters per line
   * @return the list of lines
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
          int lastSpace = line.substring(start, end)
                              .lastIndexOf(' ');
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
  
  /**
   * Split and sanitize a list of strings
   *
   * @param txt the list of strings to split
   * @param maxCharacters the maximum number of characters per line
   * @return the list of lines
   */
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
   * @param lines the number of lines of the terminal
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
   * @param millis the date in milliseconds
   * @return the formatted date
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
    if (bytes < 1000) {
      return STR."\{bytes} o";
    }
    if (bytes < 1000 * 1000) {
      return String.format("%.2f Ko", (bytes / 1000d));
    }
    if (bytes < 1000 * 1000 * 1000) {
      return String.format("%.2f Mo", ((bytes / (1000d * 1000))));
    }
    if (bytes < 1000L * 1000 * 1000 * 1000) {
      return String.format("%.2f Go", (bytes / (1000d * 1000 * 1000)));
    }
    if (bytes < 1000L * 1000L * 1000L * 1000L * 1000L) {
      return String.format("%.2f To", (bytes / (1000d * 1000d * 1000d * 1000d)));
    }
    return "+inf (╯°□°)╯︵ ┻━┻";
  }
  
  /**
   * Convert a fingerprint sha1 to a hexadecimal formatted string
   *
   * @param bytes the fingerprint
   * @return the hexadecimal formatted string
   */
  static String bytesToHexadecimal(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  /**
   * Create a scrollable view from a string
   */
  static ScrollableView scrollableFromString(String title, int lines, int cols, String help) {
    var textLines = splitAndSanitize(help, cols);
    return new ScrollableView(title, lines, cols, textLines);
  }
  
  /**
   * Create a scrollable view from a list of objects
   * and the given mapper function to convert the object to a string
   * @param title the title of the view
   * @param lines the number of lines
   * @param cols the number of columns
   * @param list the list of objects
   * @param mapper the function to convert the object to a string
   * @return the scrollable view
   */
  static <T> SelectorView<T> selectorFromList(String title, int lines, int cols, List<T> list,
                                              Function<? super T, String> mapper) {
    var linesByItem = new ArrayList<Map.Entry<T, List<String>>>();
    var linesToDisplay = new ArrayList<String>();
    var totalLines = 0;
    for (var item : list) {
      var str = mapper.apply(item);
      var formattedDescription = splitAndSanitize(str, cols);
      totalLines += formattedDescription.size();
      linesByItem.add(Map.entry(item, formattedDescription));
    }
    
    var digitsInNumberOfLines = String.valueOf(totalLines).length();
    var lineNumber = 0;
    for (var item : linesByItem) {
      var itemLines = item.getValue();
      for (int j = 0; j < itemLines.size(); j++) {
        var line = STR."%\{digitsInNumberOfLines}d| %s".formatted(lineNumber, itemLines.get(j));
        var lineWithLineNumber = responsiveCut(line, cols);
        item.getValue().set(j, lineWithLineNumber);
        lineNumber++;
        linesToDisplay.add(lineWithLineNumber);
      }
    }
    
    return new SelectorView<>(title, lines, cols, linesByItem, new ScrollableView(title, lines, cols, linesToDisplay), mapper);
  }
  
  static String codexSearchResultShortDescription(SearchResponse.Result searchResult) {
    var formats = getDateFormats(Locale.getDefault());
    var date = new Date(searchResult.creationDate());
    var formatter = new SimpleDateFormat(formats[0]);
    var formattedDate = formatter.format(date);
    return STR."[\{formattedDate.substring(0, formattedDate.length()-6)}] \{CLIColor.BOLD}%-6s\{CLIColor.RESET} ─ \{searchResult.codexName()}".formatted(STR."(\{searchResult.sharers()}s)");
  }
  
  static String codexShortDescription(CodexStatus codexStatus) {
    var codex = codexStatus.codex();
    return STR."\{codex.name()} ─ \{codex.files().length} files \{bytesToHumanReadable(codex.totalSize())}";
  }
  
  static String directMessageShortDescription(DirectMessages dm) {
    var optLastMessage = dm.getLastMessage();
    var username = STR." \{CLIColor.BOLD}\{dm.username()}\{CLIColor.RESET}";
    if (optLastMessage.isEmpty()) {
      return username;
    }
    var message = optLastMessage.orElseThrow();
    return STR."\{username} ─ \{formatDate(message.epoch())} ─ \{message.txt().replace("\\s", "")}";
  }
  
  static String[] getDateFormats(Locale locale) {
    String[] dateFormats;
    if (locale.getLanguage().equals("fr")) {
      dateFormats = new String[]{"dd/MM/yyyy HH:mm", "dd/MM/yyyy"};
    } else {
      dateFormats = new String[]{"MM/dd/yyyy HH:mm", "MM/dd/yyyy"};
    }
    return dateFormats;
  }
  
  /**
   * Cut the string to the maximum number of characters without counting the escape codes (colors)
   * @param str  the string to cut
   * @param maxCharacters the maximum number of characters
   * @return the cut string
   */
  static String responsiveCut(String str, int maxCharacters) {
    var lengthWithoutEscapeCodes = CLIColor.countLengthWithoutEscapeCodes(str);
    var numberOfEscapeCodes = str.length() - lengthWithoutEscapeCodes;
    var length = Math.min(str.length(), maxCharacters + numberOfEscapeCodes);
    return str.substring(0, length) + CLIColor.RESET;
  }
  
  void setDimensions(int lines, int cols);
  
  void draw() throws IOException;
  
  void clear();
  
  void scrollPageUp();
  
  void scrollPageDown();
  
  void scrollBottom();
  
  void scrollTop();
  
  void scrollLineDown();
  
  void scrollLineUp();
}