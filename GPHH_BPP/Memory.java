import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Memory mechanism that tracks the last 200 items seen during BPP solving.
 * Tracks recent item sizes so heuristics can exploit sequential patterns in the instance.
 * Used to compute memory-based terminals (MIN, MAX, AVE, FE, FL, FXE, FXL).
 */
public class Memory implements Serializable {
    
    // Sliding window size: how many recent items to remember.
    // 200 covers ~2x the bin capacity in dual-distribution instances.
    public static final int MEMORY_SIZE = 200;

    private List<Integer> items;
    private int totalSum;
    private int count;

    public Memory() {
        this.items = new ArrayList<>();
        this.totalSum = 0;
        this.count = 0;
    }

    public Memory(Memory other) {
        this.items = new ArrayList<>(other.items);
        this.totalSum = other.totalSum;
        this.count = other.count;
    }

    public void addItem(int size) {
        if (items.size() >= MEMORY_SIZE) {
            int removed = items.remove(0);
            totalSum -= removed;
            count--;
        }
        items.add(size);
        totalSum += size;
        count++;
    }

    public double getMin() {
        if (items.isEmpty()) {
            return 0.0;
        }
        int min = Integer.MAX_VALUE;
        for (int size : items) {
            if (size < min) {
                min = size;
            }
        }
        return min;
    }

    public double getMax() {
        if (items.isEmpty()) {
            return 0.0;
        }
        int max = Integer.MIN_VALUE;
        for (int size : items) {
            if (size > max) {
                max = size;
            }
        }
        return max;
    }

    public double getAverage() {
        if (items.isEmpty()) {
            return 0.0;
        }
        return (double) totalSum / count;
    }
    
    /**
     * Get the proportion of memory items that fit into the given space.
     * An item fits if its size <= space.
     * @param space Available space
     * @return Proportion (0.0 to 1.0), or 0.0 if memory is empty
     */
    public double getFittingRatio(int space) {
        if (items.isEmpty()) {
            return 0.0;
        }
        int fitting = 0;
        for (int size : items) {
            if (size <= space) {
                fitting++;
            }
        }
        return (double) fitting / count;
    }
    
    /**
     * Get the proportion of memory items that almost exactly fit the given space.
     * An item almost exactly fits if: 0 < space - size <= threshold.
     * @param space Available space
     * @param threshold Maximum allowed gap
     * @return Proportion (0.0 to 1.0), or 0.0 if memory is empty
     */
    public double getExactFittingRatio(int space, int threshold) {
        if (items.isEmpty()) {
            return 0.0;
        }
        int exactFitting = 0;
        for (int size : items) {
            int gap = space - size;
            if (gap > 0 && gap <= threshold) {
                exactFitting++;
            }
        }
        return (double) exactFitting / count;
    }
    
    /**
     * Get the proportion of memory items with size less than threshold.
     * Used by the FI function.
     * @param threshold Threshold value
     * @return Proportion (0.0 to 1.0), or 0.0 if memory is empty
     */
    public double getProportionBelow(double threshold) {
        if (items.isEmpty()) {
            return 0.0;
        }
        int below = 0;
        for (int size : items) {
            if (size < threshold) {
                below++;
            }
        }
        return (double) below / count;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
        totalSum = 0;
        count = 0;
    }

    public Memory copy() {
        return new Memory(this);
    }
}
