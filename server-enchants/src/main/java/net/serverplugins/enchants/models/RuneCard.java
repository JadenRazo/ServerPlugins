package net.serverplugins.enchants.models;

public class RuneCard {

    private final RuneType runeType;
    private final int pairId;
    private boolean faceUp;
    private boolean matched;

    public RuneCard(RuneType runeType, int pairId) {
        this.runeType = runeType;
        this.pairId = pairId;
        this.faceUp = false;
        this.matched = false;
    }

    public RuneType getRuneType() {
        return runeType;
    }

    public int getPairId() {
        return pairId;
    }

    public boolean isFaceUp() {
        return faceUp;
    }

    public void setFaceUp(boolean faceUp) {
        this.faceUp = faceUp;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public void flip() {
        this.faceUp = !this.faceUp;
    }
}
