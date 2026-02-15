package net.serverplugins.api.utils;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

/**
 * A weighted random selection data structure. Uses a TreeMap for O(log n) random selection.
 *
 * @param <T> The type of elements to store
 */
public class RandomList<T> {

    private static final Random RANDOM = new Random();
    private int totalWeight;
    private final NavigableMap<Integer, T> elements = new TreeMap<>();

    /**
     * Adds an element with a specified chance/weight. Higher weight = higher probability of
     * selection.
     *
     * @param element The element to add
     * @param chance The weight/chance value (must be > 0)
     */
    public void addElement(T element, int chance) {
        if (chance <= 0) {
            return;
        }
        totalWeight += chance;
        elements.put(totalWeight, element);
    }

    /**
     * Gets a random element based on weighted probability.
     *
     * @return A randomly selected element, or null if empty
     */
    public T getRandomElement() {
        if (elements.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int value = RANDOM.nextInt(totalWeight);
        return elements.higherEntry(value).getValue();
    }

    /**
     * @return The total cumulative weight of all elements
     */
    public int getTotalWeight() {
        return totalWeight;
    }

    /**
     * @return The number of elements in the list
     */
    public int size() {
        return elements.size();
    }

    /**
     * @return true if the list has no elements
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /** Clears all elements and resets the weight. */
    public void clear() {
        elements.clear();
        totalWeight = 0;
    }
}
