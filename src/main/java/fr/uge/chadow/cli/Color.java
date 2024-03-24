package fr.uge.chadow.cli;

import java.util.Objects;

public record Color(int r, int g, int b) {
  public Color {
    Objects.checkIndex(r, 256);
    Objects.checkIndex(g, 256);
    Objects.checkIndex(b, 256);
  }
}