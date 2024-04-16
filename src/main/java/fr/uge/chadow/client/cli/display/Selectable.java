package fr.uge.chadow.client.cli.display;

public interface Selectable<T> {
  void selectorUp();
  void selectorDown();
  T get();
}