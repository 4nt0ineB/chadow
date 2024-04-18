package fr.uge.chadow.client.cli.display.view;

import fr.uge.chadow.client.cli.CLIColor;
import fr.uge.chadow.client.cli.display.Selectable;
import fr.uge.chadow.client.cli.display.View;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SelectorView<T> implements Selectable<T>, View {
  
  
  private final List<Map.Entry<T, List<String>>> linesByItem;
  private final String title;
  private final int lines;
  private final int cols;
  private final Function<? super T, String> mapper;
  private ScrollableView scrollableView;
  private int itemPointerIndex = 0;
  
  public SelectorView(String title, int lines, int cols, List<Map.Entry<T, List<String>>> linesByItem,
                      ScrollableView scrollableView, Function<? super T, String> mapper) {
    this.linesByItem = linesByItem;
    this.scrollableView = scrollableView;
    this.title = title;
    this.lines = lines;
    this.cols = cols;
    this.mapper = mapper;
  }
  
  public void selectorUp() {
    itemPointerIndex = Math.max(0, itemPointerIndex - 1);
  }
  
  public void selectorDown() {
    itemPointerIndex = Math.min(linesByItem.size() - 1, itemPointerIndex + 1);
  }
  
  @Override
  public T get() {
    return linesByItem.get(itemPointerIndex)
                      .getKey();
  }
  
  @Override
  public void scrollPageUp() {
    scrollableView.scrollPageUp();
  }
  
  @Override
  public void scrollPageDown() {
    scrollableView.scrollPageDown();
  }
  
  @Override
  public void scrollBottom() {
    scrollableView.scrollBottom();
  }
  
  @Override
  public void scrollTop() {
    scrollableView.scrollTop();
  }
  
  @Override
  public void scrollLineDown() {
    scrollableView.scrollLineDown();
  }
  
  @Override
  public void scrollLineUp() {
    scrollableView.scrollLineUp();
  }
  
  @Override
  public void setDimensions(int lines, int cols) {
    var select = View.selectorFromList(title, lines, cols, linesByItem.stream()
                                                                      .map(Map.Entry::getKey)
                                                                      .toList(), mapper);
    this.scrollableView = select.scrollableView;
    this.linesByItem.clear();
    this.linesByItem.addAll(select.linesByItem);
  }
  
  /**
   * Draw the selector view.
   * We draw the scrollable view.
   * Then we overlay the selected item with a different color.
   */
  @Override
  public void draw() {
    scrollableView.draw();
    if (linesByItem.isEmpty()) {
      return;
    }
    var lineIndex = 1 + itemPointerIndex;
    if (!selectedItemIsVisible()) {
      return;
    }
    
    // Not extacly it, but it's a good start we must use A() and B() to get the visible part of the scrollable view
    /*for (var line : linesByItem.get(itemPointerIndex)
                               .getValue()) {
      View.moveCursorToPosition(1, 1 + lineIndex);
      System.out.printf(STR."\{STR."\{CLIColor.ORANGE}\{CLIColor.BOLD}%-\{cols}s\{CLIColor.RESET}"}%n", line);
      lineIndex++;
    }*/

    
    
    // Get the cursor back to the end of the scrollable view
    View.moveCursorToPosition(1, lines - 2);
  }
  
  /**
   * Test if the selected item index is visible in the scrollable view or not.
   * Do nothing if the selected item index is invisible.
   *
   * @return true if the selected item index is visible, false otherwise.
   */
  private boolean selectedItemIsVisible() {
    var selectedItemFirstLine = linesByItem.subList(0, itemPointerIndex)
                                           .stream()
                                           .mapToInt(e -> e.getValue()
                                                           .size())
                                           .sum();
    var selectedItemLastLine = selectedItemFirstLine + linesByItem.get(itemPointerIndex)
                                                                  .getValue()
                                                                  .size();
    var scroller = scrollableView.getScroller();
    var viewFirstLine = scroller.getA();
    var viewLastLine = scroller.getB();
    return selectedItemFirstLine >= viewFirstLine && selectedItemLastLine <= viewLastLine;
  }
  
  @Override
  public void clear() {
    scrollableView.clear();
  }
  
  public boolean isAtBottom() {
    return scrollableView.isAtBottom();
  }
  
  public void setAtSamePosition(SelectorView<?> other) {
    this.scrollableView.setAsSamePosition(other.scrollableView);
  }
  
}