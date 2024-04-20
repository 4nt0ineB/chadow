package fr.uge.chadow;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.server.Server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());
  private static void usage() {
    System.out.println("Usage: " +
        "--server <port>\n" +
        "otherwise default is client : "+
        "<login> <hostname> <port> <lines> <columns>");
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
        .addStringSettings("login", "hostname")
        .addIntSettings("y", "x");
    
    SettingsParser.Settings settings = null;
    var settingString = String.join("", args);
    logger.info(settingString);
    
    try {
      settings = sp.parse(settingString);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
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
    /*Logger rootLogger = Logger.getLogger("");
    rootLogger.setLevel(Level.OFF);*/
    if(args[0].equals("--server")) {
      server(args);
    } else {
      client(args);
    }
    
  }
}