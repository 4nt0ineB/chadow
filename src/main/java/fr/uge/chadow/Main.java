package fr.uge.chadow;

import fr.uge.chadow.cli.ClientConsole;
import fr.uge.chadow.client.Client;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
  private static void usage() {
    System.out.println("Usage: hostname port lines columns");
  }
  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length < 4) {
      usage();
      return;
    }
    new ClientConsole(new Client("Flynn", new InetSocketAddress(args[0], Integer.parseInt(args[1]))), Integer.parseInt(args[2]), Integer.parseInt(args[3])).start();
  }
}
