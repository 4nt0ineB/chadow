package fr.uge.chadow.client.cli.display.view;

import fr.uge.chadow.client.cli.CLIColor;
import fr.uge.chadow.client.cli.display.Scroller;
import fr.uge.chadow.client.cli.display.View;

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
    System.out.print(header());
    System.out.print(view());
  }
  
  String view() {
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
    View.clear(lines);
  }
  
  public String header() {
    var sb = new StringBuilder();
    var colsRemaining = columns;
    var title = (STR."%-\{colsRemaining}s").formatted(STR."CHADOW CLIENT \{this.title}");
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
  
  public void scrollPageDown() {
    scroller.scrollPageDown();
  }
  
  @Override
  public void scrollBottom() {
    scroller.moveToBottom();
  }
  
  @Override
  public void scrollTop() {
    scroller.moveToTop();
  }
  
  @Override
  public void scrollLineDown() {
    scroller.scrollDown(1);
  }
  
  @Override
  public void scrollLineUp() {
    scroller.scrollUp(1);
  }
  
  public Scroller getScroller() {
    return scroller;
  }
  
  public void setAsSamePosition(ScrollableView other) {
    scroller.setLines(other.scroller.getLines());
    scroller.setCurrentLine(other.scroller.getCurrentLine());
  }
  
  public boolean isAtBottom() {
    return scroller.isAtBottom();
  }
}