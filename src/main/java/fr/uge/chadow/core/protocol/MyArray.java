package fr.uge.chadow.core.protocol;

public record MyArray<T extends Record>(int size, T[] values) {

}
