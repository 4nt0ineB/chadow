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
          
            --sharersRequired:<int>
              Number (at max) of sharers asked to the server when downloading. Default is 5.
              
            --proxyChainSize:<int>
              Size of the proxy chain in hidden download mode. Default is 1.
             
            --debug:<boolean>
              Debug mode. Default is false.
            
            --chunkSize:<int>
              Size of the chunks in bytes in Kb. Default is 128 Kb. Shouldn't be changed unless you know what you're doing.
              
            --maxAcceptedChunkSize:<int>
              Maximum size of a chunk that can be accepted in Kb. Default is 512 Kb. Allows to prevent bandwidth exhaustion from
              other clients by closing connection that request too big chunks.
              
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
        // optional
        .addAsInt("port", 7777)
        .addAsInt("y", 30)
        .addAsInt("x", 80)
        .addAsInt("sharersRequired", 5)
        .addAsInt("proxyChainSize", 1)
        .addAsBoolean("debug", false)
        .addAsInt("chunkSize", 128) // 128KB
        .addAsInt("maxAcceptedChunkSize", 512) // 512KB
        // mandatory
        .addAsString("downloadPath", SettingsParser.Settings.defaultDownloadPath())
        .addAsBoolean("log", false)
        .addStringSettings("login", "hostname");
    
    SettingsParser.Settings settings = null;
    var settingString = String.join("", args);
    System.out.println(settingString);
    
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
      FileHandler fileHandler = new FileHandler(STR."logs/\{settings.getStr("login")}.log", false);
      fileHandler.setFormatter(new SimpleFormatter());
      rootLogger.addHandler(fileHandler);
      Logger.getLogger(Main.class.getName()).info(settingString);
    }
    
    var codexController = new CodexController(settings.getStr("downloadPath"), settings.getInt("chunkSize") * 1024);
    var serverSocket = new InetSocketAddress(settings.getStr("hostname"), settings.getInt("port"));
    var api = new ClientAPI(settings.getStr("login"), serverSocket, codexController, settings);
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