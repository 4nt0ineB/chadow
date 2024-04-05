package fr.uge.chadow.cli.display.view;

import fr.uge.chadow.cli.display.View;

import java.util.List;

public class CantConnectScreenView implements View {
  private final int lines;
  private final int cols;
  
  public CantConnectScreenView(int lines, int cols) {
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and cols must be positive");
    }
    this.lines = lines;
    this.cols = cols;
  }
  
  @Override
  public void setDimensions(int lines, int cols) {
  
  }
  public void draw() {
    var logo = List.of("┏┓┓    ┓"
                      ,"┃ ┣┓┏┓┏┫┏┓┓┏┏"
                      ,"┗┛┗┗┗┗┗┗┗┛┗┛┛ can't reach the server...");
    var logoWidth = logo.stream().mapToInt(String::length).max().orElse(0);
    var logoHeight = logo.size();
    var logoX = (cols - logoWidth) / 2;
    var logoY = (lines - logoHeight) / 2;
    for (var i = 0; i < logoHeight; i++) {
      var line = logo.get(i);
      System.out.print("\033[" + (logoY + i) + ";" + logoX + "H" + line);
    }
    for (var i = 0; i < lines - View.maxLinesView(lines); i++) {
      System.out.print("\033[" + (logoY + logoHeight + i) + ";1H" + " ".repeat(cols));
    }
  }
  
  @Override
  public void clear() {
    View.clear(lines);
  }
  
  @Override
  public void scrollPageUp() {
  
  }
  
  @Override
  public void scrollPageDown() {
  
  }
  
  @Override
  public void scrollBottom() {
  
  }
  
  @Override
  public void scrollTop() {
  
  }
  
  @Override
  public void scrollLineDown() {
  
  }
  
  @Override
  public void scrollLineUp() {
  
  }
}