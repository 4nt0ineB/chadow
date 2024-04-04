package fr.uge.chadow;

import fr.uge.chadow.client.Client;
import fr.uge.chadow.client.ClientConsoleController;
import fr.uge.chadow.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;

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
    new Server(Integer.parseInt(args[1])).launch();
  }
  
  private static void client(String[] args) throws IOException, InterruptedException {
    if (args.length < 5) {
      usage();
      return;
    }
    var socket = new InetSocketAddress(args[1], Integer.parseInt(args[2]));
    var lines = Integer.parseInt(args[3]);
    var columns = Integer.parseInt(args[4]);
    var random = new Random();
    new ClientConsoleController(new Client(args[0], socket), lines, columns).start();
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