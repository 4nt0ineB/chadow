package fr.uge.chadow.cli.view;

import fr.uge.chadow.cli.CLIColor;

public abstract class View {
  abstract void processInput(String input);
  abstract void setDimensions(int lines, int cols);
  abstract int lines();
  
  static void moveCursorToPosition(int x, int y) {
    System.out.print("\033[" + y + ";" + x + "H");
    System.out.flush();
  }
  
  /**
   * Clear everything above the input field.
   */
  void clearDisplayArea() {
    for (int i = 0; i < lines() - 2; i++) {
      View.moveCursorToPosition(1, i + 1); // Move cursor to each line in the chat area
      System.out.print(CLIColor.CLEAR_LINE); // Clear the line
    }
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
  }
}
