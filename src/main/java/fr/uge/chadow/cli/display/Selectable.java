package fr.uge.chadow.cli.display;

public interface Selectable<T> {
  void selectorUp();
  void selectorDown();
  T get();
}
