package com.example.cardcounter;

public enum Suit {
    HEARTS("♥", "Hearts"),
    DIAMONDS("♦", "Diamonds"),
    SPADES("♠", "Spades"),
    CLUBS("♣", "Clubs");

    private final String symbol;
    private final String displayName;

    Suit(String symbol, String displayName) {
        this.symbol = symbol;
        this.displayName = displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDisplayName() {
        return displayName;
    }
}