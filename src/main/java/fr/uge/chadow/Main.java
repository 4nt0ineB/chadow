package fr.uge.chadow;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
  private static void usage() {
    var str = """
        [Client]
          App start as a Chadow client by default.
          
          --hostname:<string>
            Server's hostname. Default is localhost.
          
          --login:<string>
            Your login. Default is a random generated one.
          
          --port:<port>
            Server's listening port. Default is 7777.
          
          --y:<int>
            Display height. Default is 30.
            
          --x:<int>
            Display width. Default is 80.
          
          --downloadPath:<path>
            Workspace path.
            Default is the "Downloads" folder depending on your OS (e.g: ~/Downloads on Unix)
            The files of a codex named "MyCodex" will be downloaded in "<downloadPath>/MyCodex".
        
          --sharersRequired:<int>
            Number (at max) of sharers asked to the server when downloading.
            Default is 5.
            
          --proxyChainSize:<int>
            Size of the proxy chain in hidden download mode.
            Default is 1.
           
          --debug:<boolean>
            Debug mode. Default is false.
          
          --chunkSize:<int>
            Size of the chunks in bytes in Kb. Default is 128 Kb. Shouldn't be changed unless you know what you're doing.
            
          --maxAcceptedChunkSize:<int>
            Maximum size of a chunk that can be accepted in Kb. Allows to prevent bandwidth exhaustion from
            other clients by closing connection that request too big chunks.
            Default is 512 Kb.
            
          --requestCodexTimeout:<int>
            Timeout in second before requesting a codex gives up.
            Default is 5 seconds.
            
          --downloadRequestTimeout:<int>
            Cooldown time in second before looking for sockets received from the server after a download request.
            Default is 5 seconds.
          
          --searchTimeout:<int>
            Timeout in second before a search gives up.
            Default is 5 seconds.
            
          --newSocketRequestTimeout:<int>
            Timeout in second after which new sockets a requested for current downloads.
            Default is 1 minute.
             
       [Server]
          In order to start the app as a Chadow server the first parameter must be --server
          
          --port:<port>
            Start the server on the given port.
            Default is 7777.
            
          --maxUsernameLength:<int>
            Maximum length of a username.
            Default is 20.
          
        """;
    System.out.println(str);
  }
  
  private static void server(String[] args) throws IOException {
    // parse args
    var sp = new SettingsParser()
        .addAsInt("port", 7777)
        .addAsInt("maxUsernameLength", 20)
        .addAsString("downloadPath", Settings.defaultDownloadPath())
        .addAsBoolean("log", false);
    
    Settings settings = null;
    var settingString = String.join("", args);
    
    try {
      settings = sp.parse(settingString);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
    settings.addStringSettings("logFileName", "server");
    switchLoggingMode(settings);
    Logger.getLogger(Main.class.getName()).info(settingString);
    new Server(settings)
        .start();
  }
  
  private static void client(String[] args) throws IOException {
    // parse args
    var sp = new SettingsParser()
        .addAsString("login", Settings.randomLogin())
        .addAsString("hostname", "localhost")
        .addAsInt("port", 7777)
        .addAsInt("y", 30)
        .addAsInt("x", 80)
        .addAsInt("sharersRequired", 5)
        .addAsInt("proxyChainSize", 1)
        .addAsBoolean("debug", false)
        .addAsInt("chunkSize", 128) // 128KB
        .addAsInt("maxAcceptedChunkSize", 512) // 512KB
        .addAsInt("requestCodexTimeout", 5)
        .addAsInt("downloadRequestTimeout", 5)
        .addAsInt("searchTimeout", 5)
        .addAsInt("newSocketRequestTimeout", 60)
        .addAsString("downloadPath", Settings.defaultDownloadPath())
        .addAsBoolean("log", false);
    
    Settings settings = null;
    var settingString = String.join("", args);
    
    try {
      settings = sp.parse(settingString);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
    settings.addStringSettings("logFileName", settings.getStr("login"));
    switchLoggingMode(settings);
    Logger.getLogger(Main.class.getName()).info(settingString);
    
    var codexController = new CodexController(settings.getStr("downloadPath"), settings.getInt("chunkSize") * 1024);
    var serverSocket = new InetSocketAddress(settings.getStr("hostname"), settings.getInt("port"));
    var api = new ClientAPI(serverSocket, codexController, settings);
    new ClientConsoleController(settings.getInt("y"), settings.getInt("x"), api)
        .start();
  }
  
  private static void switchLoggingMode(Settings settings) throws IOException {
    var rootLogger = Logger.getLogger("");
    if(!settings.getBool("log")){
      rootLogger.setLevel(Level.OFF);
      return;
    }
    // We want to log in files and not in the console
    // from https://stackoverflow.com/a/2533250
    // removes the console handler
    for(var handler : rootLogger.getHandlers()) {
      rootLogger.removeHandler(handler);
    }
    // set the file handler
    FileHandler fileHandler = new FileHandler(STR."logs/\{settings.getStr("logFileName")}.log", false);
    fileHandler.setFormatter(new SimpleFormatter());
    rootLogger.addHandler(fileHandler);
    
  }
  
  public static void main(String[] args) throws IOException {
    if(args.length == 0 || args[0].matches("-h|--help")) {
      usage();
      return;
    }
    if(args[0].equals("--server")) {
      server(Arrays.stream(args).skip(1).toArray(String[]::new));
    } else {
      client(args);
    }
  }
}