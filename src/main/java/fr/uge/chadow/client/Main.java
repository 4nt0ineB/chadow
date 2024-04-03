package fr.uge.chadow.client;

import fr.uge.chadow.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
  private static void usage() {
    System.out.println("Usage: " +
        "--server <port>" +
        "otherwise default is client : "+
        "<hostname> <port> <lines> <columns>");
  }
  
  private static void server(String[] args) throws IOException {
    if (args.length != 2) {
      usage();
      return;
    }
    new Server(Integer.parseInt(args[1])).launch();
  }
  
  private static void client(String[] args) throws IOException, InterruptedException {
    if (args.length < 4) {
      usage();
      return;
    }
    var socket = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
    var lines = Integer.parseInt(args[2]);
    var columns = Integer.parseInt(args[3]);
    new ClientConsoleController(new Client("Flynn", socket), lines, columns).start();
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
