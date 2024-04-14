package fr.uge.chadow;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
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
  
  private static void client(String[] args) throws IOException, InterruptedException {
    if (args.length < 5) {
      usage();
      return;
    }
    InetSocketAddress socket = null;
    int lines, cols;
    try {
      socket = new InetSocketAddress(args[1], Integer.parseInt(args[2]));
      lines = Integer.parseInt(args[3]);
      cols = Integer.parseInt(args[4]);
    } catch (NumberFormatException e) {
      usage();
      return;
    }
    var login = args[0];
    var api = new ClientAPI(login, socket);
    new ClientConsoleController(lines, cols, api).start();
  }
  
  public static void main(String[] args) throws IOException, InterruptedException {
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