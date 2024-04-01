package fr.uge.chadow.core.packet;

public record Message(String login, String txt, long epoch) {}