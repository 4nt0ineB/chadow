package fr.uge.chadow.client.cli.display;


import java.util.logging.Logger;

public class Scroller {
  private static final Logger logger = Logger.getLogger(Scroller.class.getName());
  private int pageHeight;
  private int currentLine;
  private int lines;
  
  
  public Scroller(int lines, int pageHeight) {
    this.pageHeight = pageHeight;
    this.lines = lines;
    currentLine = Math.max(lines - pageHeight, 0);
  }
  

  
  /**
   * Reset the scroller
   *
   * @param lines
   */
  public void setLines(int lines) {
    this.lines = lines;
    moveToBottom();
  }
  
  public void scrollPageUp() {
    scrollUp(pageHeight);
    logger.info("scroll page up");
  }
  
  public void scrollPageDown() {
    scrollDown(pageHeight);
    logger.info("scroll page down");
  }
  
  public void scrollDown(int n) {
    currentLine = Math.min(maxPosition(), n + currentLine);
    logger.info("currentLine: " + currentLine + " maxPosition: " + maxPosition() + " n: " + " " + n);
    logger.info("scroll down");
  }
  
  public void scrollUp(int n) {
    if (currentLine > 0) {
      currentLine = Math.max(0, currentLine - n);
      logger.info("scroll up");
    }
  }
  
  public int getA() {
    return currentLine;
  }
  
  public int getB() {
    return Math.min(lines, currentLine + pageHeight);
  }
  
  public void moveToTop() {
    currentLine = 0;
  }
  
  public void moveToBottom() {
    scrollDown(maxPosition());
  }
  
  public boolean isAtBottom() {
    return currentLine == Math.max(0, lines - pageHeight);
  }
  
  public int getLines() {
    return lines;
  }
  
  public int getCurrentLine() {
    return currentLine;
  }
  
  public void setCurrentLine(int currentLine) {
    this.currentLine = currentLine;
  }
  
  public void setPageHeight(int i) {
    currentLine = Math.max(0, currentLine - pageHeight + i);
    pageHeight = i;
  }

  private int maxPosition() {
    logger.info("lines: " + lines + " pageHeight: " + pageHeight);
    return Math.max(0, lines - pageHeight);
  }
}