package fr.uge.chadow;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
  private static void usage() {
    var str = """
        [Client]
          # Mandatory settings
            --login:<string>
              Your login
              
            --hostname:<string>
              Server's hostname
              
            --port:<port>
              Server's port
          
          # Optional settings
            --y:<int>
              Display height
              
            --x:<int>
              Display width
            
            --downloadPath:<path>
              Workspace path. Default is the "Downloads" folder of your use depending on your OS (e.g: ~/Downloads on Unix)
              The files of a codex named "MyCodex" will be downloaded in "downloadPath/MyCodex".
          
        [Server]
          --server <port>
            Start the server on the given port
        """;
    System.out.println(str);
  }
  
  private static void server(String[] args) throws IOException {
    if (args.length != 2) {
      usage();
      return;
    }
    new Server(Integer.parseInt(args[1])).start();
  }
  
  private static void client(String[] args) throws IOException {
    if(args[0].matches("-h|--help")) {
      usage();
      return;
    }
    
    // parse args
    var sp = new SettingsParser()
        .addAsString("downloadPath", SettingsParser.Settings.defaultDownloadPath())
        .addAsInt("port", 7777)
        .addAsInt("y", 30)
        .addAsInt("x", 80)
        .addAsBoolean("log", false)
        .addStringSettings("login", "hostname");
    
    SettingsParser.Settings settings = null;
    var settingString = String.join("", args);
    
    try {
      settings = sp.parse(settingString);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
    
    var rootLogger = Logger.getLogger("");
    if(!settings.getBool("log")) {
      rootLogger.setLevel(Level.OFF);
    } else {
      // We want to log in files and not in the console
      // from https://stackoverflow.com/a/2533250
      // removes the console handler
      for(var handler : rootLogger.getHandlers()) {
        rootLogger.removeHandler(handler);
      }
      // set the file handler
      FileHandler fileHandler = new FileHandler(STR.".logs/\{settings.getStr("login")}.log", false);
      fileHandler.setFormatter(new SimpleFormatter());
      rootLogger.addHandler(fileHandler);
      Logger.getLogger(Main.class.getName()).info(settingString);
    }
    
    var codexController = new CodexController(settings.getStr("downloadPath"));
    var serverSocket = new InetSocketAddress(settings.getStr("hostname"), settings.getInt("port"));
    var api = new ClientAPI(settings.getStr("login"), serverSocket, codexController);
    new ClientConsoleController(settings.getInt("y"), settings.getInt("x"), api).start();
  }
  
  public static void main(String[] args) throws IOException {
    if(args.length == 0) {
      usage();
      return;
    }
    
    if(args[0].equals("--server")) {
      server(args);
    } else {
      client(args);
    }
    
  }
}