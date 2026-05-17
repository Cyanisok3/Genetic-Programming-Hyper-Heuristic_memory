/**
 * Calculator for L2 lower bound (Martello & Toth, 1990).
 */
public class L2BoundCalculator {

    public static double calculate(BPPInstance instance) {
        int capacity = instance.getCapacity();
        int[] items = instance.getItems();
        
        // Lower bound 1: ceil(sum of all items / capacity)
        int totalSize = 0;
        for (int item : items) {
            totalSize += item;
        }
        double lowerBound1 = Math.ceil((double) totalSize / capacity);
        
        // Lower bound 2: count of items larger than half the capacity
        int halfCapacity = capacity / 2;
        int countLarge = 0;
        for (int item : items) {
            if (item > halfCapacity) {
                countLarge++;
            }
        }
        
        // L2 = max(LB1, LB2)
        return Math.max(lowerBound1, countLarge);
    }
}
