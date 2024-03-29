package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.Client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScrollableView implements View {
  
  
  private final ArrayList<String> textLines;
  private final Scroller scroller;
  private final String title;
  private int lines;
  private int columns;
  private final String login;
  
  public ScrollableView(String title, String login, int lines, int cols, List<String> textLines) {
    Objects.requireNonNull(title);
    Objects.requireNonNull(login);
    Objects.requireNonNull(textLines);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.lines = lines;
    this.columns = cols;
    this.title = title;
    this.login = login;
    this.textLines = new ArrayList<>(textLines);
    this.scroller = new Scroller(textLines.size(), View.maxLinesView(lines));
  }
  
  @Override
  public boolean processInput(String input) throws InterruptedException {
    switch (input) {
      case "e" -> scroller.scrollUp(View.maxLinesView(lines));
      case "s" -> scroller.scrollDown(View.maxLinesView(lines));
    }
    return true;
  }
  
  @Override
  public void setDimensions(int lines, int cols) {
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.lines = lines;
    this.columns = cols;
  }
  
  @Override
  public void pin() {
    draw();
  }
  
  @Override
  public void draw() {
    System.out.println(view());
  }
  
  private String view() {
    var sb = new StringBuilder();
    var lineIndex = 0;
    var maxLinesView = View.maxLinesView(lines);
    var start = scroller.getA();
    var end = scroller.getB();
    sb.append(chatHeader());
    for (; lineIndex < textLines.size(); lineIndex++) {
      sb.append(textLines.get(lineIndex))
        .append("\n");
    }
    sb.append("\n".repeat(maxLinesView - lineIndex));
    sb.append(View.thematicBreak(columns));
    sb.append(inputField());
    return sb.toString();
  }
  
  @Override
  public void clearDisplayAndMore(int rest) {
    View.clearDisplayAndMore(lines, rest);
  }
  
  @Override
  public void clear() {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
  }
  
  private String chatHeader() {
    var sb = new StringBuilder();
    var colsRemaining = columns;
    var title = ("%-" + colsRemaining + "s").formatted("CHADOW CLIENT " + this.title);
    sb.append(CLIColor.CYAN_BACKGROUND)
      .append(CLIColor.WHITE)
      .append("%s".formatted(title))
      .append(CLIColor.RESET)
      .append('\n');
    return sb.toString();
  }
  
  private String inputField() {
    var inputField = ("%s\n").formatted("[" + CLIColor.BOLD + CLIColor.CYAN + login + CLIColor.RESET + "]");
    return inputField + "> ";
  }
  
  public void scrollUp(int lines) {
    scroller.scrollUp(lines);
  }
  
  public void scrollDown(int lines) {
    scroller.scrollDown(lines);
  }

}
