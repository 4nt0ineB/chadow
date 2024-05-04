package fr.uge.chadow.client.cli.display;

import fr.uge.chadow.client.cli.CLIColor;
import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.ClientConsoleController;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Display class is responsible for drawing the view in the console
 */
public class Display {
  private final ClientConsoleController controller;
  private final ClientAPI api;
  private final ReentrantLock lock = new ReentrantLock();
  private final InfoBar infoBar;
  private int lines;
  private int cols;
  private View currentView;
  
  public Display(int lines, int cols, ClientConsoleController controller, InfoBar infoBar, ClientAPI api) {
    Objects.requireNonNull(controller);
    Objects.requireNonNull(api);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and cols must be positive");
    }
    this.api = api;
    this.controller = controller;
    this.infoBar = infoBar;
    this.lines = lines;
    this.cols = cols;
  }
  
  /**
   * Set the dimensions of the view
   */
  public void setDimensions(int lines, int cols) {
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    lock.lock();
    try {
      this.lines = lines;
      this.cols = cols;
      currentView.setDimensions(lines, cols);
      infoBar.setDimensions(cols);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Start the loop that will refresh the view
   */
  public void startLoop() throws InterruptedException {
    System.out.print(CLIColor.CLEAR);
    System.out.flush();
    while (!Thread.interrupted() && controller.isAlive()) {
      var canRefresh = controller.viewCanRefresh().get();
      if (canRefresh) {
        try {
          draw();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      Thread.sleep(400);
    }
  }
  
  public void clear() {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
  }
  
  public void setView(View view) {
    currentView = view;
  }
  
  /**
   * Draw the view in the current mode
   *
   * @throws IOException
   */
  public void draw() throws IOException {
    clear();
    currentView.draw();
    //System.out.print(View.thematicBreak(cols));
    //System.out.print(lowInfoBar(cols));
    infoBar.draw();
    if(api.isConnected()) {
      System.out.print(inputField());
      View.moveToInputField(lines);
    }
  }
  
  private String inputField() {
    var inputField = "";
    if (!controller.viewCanRefresh().get()) {
      // (getMaxUserLength() + 21)
      inputField = ("%s\n")
          .formatted(STR."[\{CLIColor.BOLD}\{CLIColor.CYAN}\{api.login()}\{CLIColor.RESET}]");
    } else {
      // (getMaxUserLength() + 50)
      inputField = ("%s\n")
          .formatted(STR."\{CLIColor.GREY}[\{CLIColor.GREY}\{CLIColor.BOLD}\{api.login()}\{CLIColor.RESET}\{CLIColor.GREY}]\{CLIColor.RESET}");
    }
    return inputField + View.inviteCharacter() + CLIColor.BOLD;
  }
  
}