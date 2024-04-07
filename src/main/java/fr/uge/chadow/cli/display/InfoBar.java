package fr.uge.chadow.cli.display;

import java.util.ArrayList;

public class InfoBar {
  
  private record Message(int priority, String message) {
    enum PRIORITY {
      ERROR,
      INFO
    }
  }
  
  private final ArrayList<String> errors = new ArrayList<>();
  private final ArrayList<String> infos = new ArrayList<>();
  
  public void addError(String error) {
    errors.add(error);
  }
  
  public void addInfo(String info) {
    infos.add(info);
  }
  
  public void clearErrors() {
    errors.clear();
  }
  
}