package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScrollableView implements View {
  
  
  private final ArrayList<String> textLines;
  private final Scroller scroller;
  private final String title;
  private int lines;
  private int columns;
  
  public ScrollableView(String title, int lines, int cols, List<String> textLines) {
    Objects.requireNonNull(title);
    Objects.requireNonNull(textLines);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.lines = lines;
    this.columns = cols;
    this.title = title;
    this.textLines = new ArrayList<>(textLines);
    this.scroller = new Scroller(textLines.size(), View.maxLinesView(lines));
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
  public void draw() {
    System.out.print(chatHeader());
    System.out.print(view());
  }
  
  private String view() {
    var sb = new StringBuilder();
    var lineIndex = 0;
    var maxLinesView = View.maxLinesView(lines);
    var start = scroller.getA();
    var end = scroller.getB();
    var formattedLines = View.splitAndSanitize(textLines.subList(start, end), columns);
    for (; lineIndex < maxLinesView && lineIndex < formattedLines.size(); lineIndex++) {
      sb.append("%s".formatted(View.beautifyCodexLink(formattedLines.get(lineIndex))))
        .append("\n");
    }
    sb.append("\n".repeat(Math.max(0, maxLinesView - lineIndex)));
    return sb.toString();
  }
  
  @Override
  public void clear() {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
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
  
  public void scrollPageUp() {
    scroller.scrollPageUp();
  }
  
  public void scrollUp(int lines) {
    scroller.scrollUp(lines);
  }
  
  public void scrollDown(int lines) {
    scroller.scrollDown(lines);
  }
  
  public void moveToTop() {
    scroller.moveToTop();
  }
  
  public void moveToBottom() {
    scroller.moveToBottom();
  }
  
  public void scrollPageDown() {
    scroller.scrollPageDown();
  }
}
