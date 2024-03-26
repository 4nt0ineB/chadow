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
    var hostname = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
    var lines = Integer.parseInt(args[2]);
    var columns = Integer.parseInt(args[3]);
    new ClientConsole(new Client("Flynn", hostname), lines, columns)
        .start();
  }
}
