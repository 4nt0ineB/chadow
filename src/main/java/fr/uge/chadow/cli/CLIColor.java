package fr.uge.chadow.cli;

import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Provides ANSI colors
 */
public enum CLIColor {


  ITALIC("\u001B[3m"),
  BOLD("\u001B[1m"),
  RESET("\u001B[0m"),
  RESET2("\033[39m\\033[49m"),
  GREY("\033[38;5;59m"),
  BLACK("\u001B[30m"),
  RED("\u001B[31m"),
  GREEN("\u001B[32m"),
  YELLOW("\u001B[33m"),
  BLUE("\u001B[34m"),
  PURPLE("\u001B[35m"),
  CYAN("\u001B[36m"),
  WHITE("\u001B[37m"),
  ORANGE("\033[38;5;202m"),
  BBLUE("\033[38;5;33m"),
  BLACK_BACKGROUND("\u001B[40m"),
  RED_BACKGROUND("\u001B[41m"),
  GREEN_BACKGROUND("\u001B[42m"),
  YELLOW_BACKGROUND("\u001B[43m"),
  BLUE_BACKGROUND("\u001B[44m"),
  PURPLE_BACKGROUND("\u001B[45m"),
  CYAN_BACKGROUND("\u001B[46m"),
  WHITE_BACKGROUND("\u001B[47m"),
  CLEAR("\033[H\033[2J"),
  CLEAR_LINE("\033[2K");

  private final String str;

  CLIColor(String str) {
    this.str = str;
  }

  @Override
  public String toString() {
    return str;
  }

  /**
   * Make a 8-bit color ANSI escape sequence for
   * given rgb values
   *
   * @param r red
   * @param g green
   * @param b blue
   * @return an ansi escape sequence
   */
  public static String rgb(int r, int g, int b) {
    return "\033[38;2;" + (r & 0xFF) + ";" + (g & 0xFF) + ";" + (b & 0xFF) + "m";
  }

  /**
   * Generate a fancy random ansi color
   * based on the hashcode of a string
   *
   * @param input
   * @return
   */
  public static String stringToColor(String input) {
    Objects.requireNonNull(input);
    if (input.isEmpty()) return "";
    int hash = input.hashCode();
    Random random = new Random(hash);
    int r = random.nextInt(146) + 110;
    int g = random.nextInt(100) + 110;
    int b = random.nextInt(100) + 110;
    return rgb(r, g, b);
  }
  
  public static int countLengthWithoutEscapeCodes(String str) {
    String escapeCodePattern = "\\u001B\\[\\d+m|\\033\\[\\d+;\\d+;\\d+m|\\033\\[\\d+;\\d+m|\\033\\[\\d+m";
    Pattern pattern = Pattern.compile(escapeCodePattern);
    String strWithoutEscapeCodes = pattern.matcher(str).replaceAll("");
    return strWithoutEscapeCodes.length();
  }

}