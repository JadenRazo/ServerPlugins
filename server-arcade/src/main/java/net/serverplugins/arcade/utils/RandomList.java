package net.serverplugins.arcade.utils;

import java.util.*;

/**
 * A weighted random selection list. Elements with higher weights have higher chance of being
 * selected.
 */
public class RandomList<T> {

    private final List<WeightedElement<T>> elements = new ArrayList<>();
    private final Random random = new Random();
    private int totalWeight = 0;

    public void addElement(T element, int weight) {
        if (weight <= 0) return;
        elements.add(new WeightedElement<>(element, weight));
        totalWeight += weight;
    }

    public T getRandomElement() {
        if (elements.isEmpty() || totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (WeightedElement<T> we : elements) {
            cumulative += we.weight;
            if (roll < cumulative) {
                return we.element;
            }
        }

        return elements.get(elements.size() - 1).element;
    }

    public List<T> getAllElements() {
        return elements.stream().map(we -> we.element).toList();
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public void clear() {
        elements.clear();
        totalWeight = 0;
    }

    private record WeightedElement<T>(T element, int weight) {}
}
