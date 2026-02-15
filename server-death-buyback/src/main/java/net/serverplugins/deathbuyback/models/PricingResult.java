package net.serverplugins.deathbuyback.models;

public record PricingResult(double baseWorth, double buybackPrice, int itemCount) {
    public static PricingResult empty() {
        return new PricingResult(0, 0, 0);
    }

    public boolean isEmpty() {
        return itemCount == 0;
    }
}
