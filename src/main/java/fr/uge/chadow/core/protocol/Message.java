package fr.uge.chadow.core.protocol;

public record Message(String login, String txt, long epoch) {}