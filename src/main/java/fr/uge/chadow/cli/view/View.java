package fr.uge.chadow.cli.view;

import fr.uge.chadow.cli.CLIColor;

public interface View {
  boolean processInput(String input) throws InterruptedException;
  void setDimensions(int lines, int cols);
  void pin();
  void draw();
  void clearViewAndRest(int rest);
  
  static void moveCursorToPosition(int x, int y) {
    System.out.print("\033[" + y + ";" + x + "H");
    System.out.flush();
  }
  
 
  
  
}
