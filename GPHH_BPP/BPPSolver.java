import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Solver that applies a GP heuristic to solve a BPP instance.
 * Items are processed in their original input order (online BPP).
 * For each item, the GP heuristic is evaluated on each feasible bin,
 * and the item is placed in the bin with the highest heuristic score.
 * A new bin is opened if no existing bin can fit the item.
 */
public class BPPSolver {
    
    /**
     * Solve a BPP instance using the given heuristic.
     * Items are processed in their original order (online BPP).
     *
     * @param instance BPP instance to solve
     * @param heuristic GP heuristic to use
     * @return Solution
     */
    public Solution solve(BPPInstance instance, Heuristic heuristic) {
        int[] items = instance.getItems();
        Solution sol = solveWithOrder(items, heuristic, instance.getCapacity());
        sol.setInstanceName(instance.getName());
        return sol;
    }
    
    /**
     * Solve with a specific item order (fast version for training).
     * @param items Item sizes in specific order
     * @param heuristic GP heuristic to use
     * @param capacity Bin capacity
     * @return Solution
     */
    public Solution solveWithOrder(int[] items, Heuristic heuristic, int capacity) {
        return solveWithOrder(items, heuristic, capacity, null);
    }

    /**
     * Solve with an optional item shuffle determined by a seed.
     * If seed is null, items are processed in original order.
     * @param items Item sizes in original order
     * @param heuristic GP heuristic to use
     * @param capacity Bin capacity
     * @param seed Random seed for shuffling; null means no shuffle
     * @return Solution
     */
    public Solution solveWithOrder(int[] items, Heuristic heuristic, int capacity, Long seed) {
        int[] workItems = items;
        if (seed != null) {
            workItems = items.clone();
            Random rnd = new Random(seed);
            for (int i = workItems.length - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int tmp = workItems[i];
                workItems[i] = workItems[j];
                workItems[j] = tmp;
            }
        }
        return solveWithOrderRaw(workItems, heuristic, capacity);
    }

    private static final int TAIL_BUFFER_SIZE = 100;

    /**
     * Core solve loop on a pre-ordered workItems array.
     * Phase 1: GP heuristic on first (N - TAIL_BUFFER_SIZE) items.
     * Phase 2: BFD re-optimization of the last TAIL_BUFFER_SIZE items.
     */
    private Solution solveWithOrderRaw(int[] workItems, Heuristic heuristic, int capacity) {
        int itemCount = workItems.length;
        int bufferSize = Math.min(TAIL_BUFFER_SIZE, itemCount);
        int mainItemCount = itemCount - bufferSize;

        List<Bin> bins = new ArrayList<>();
        bins.add(new Bin(capacity));

        Memory memory = new Memory();

        // --- Phase 1: GP heuristic on first (N - bufferSize) items ---
        for (int itemIdx = 0; itemIdx < mainItemCount; itemIdx++) {
            int pieceSize = workItems[itemIdx];

            int bestBinIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestRemainingSpace = Integer.MAX_VALUE;

            for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
                Bin bin = bins.get(binIdx);
                int remainingSpace = bin.getEmptiness() - pieceSize;
                if (remainingSpace < 0) continue;

                BPPState state = createState(workItems, itemIdx, bin, memory);
                double score = heuristic.evaluate(state);

                if (score > bestScore ||
                    (Math.abs(score - bestScore) < 1e-6 && remainingSpace < bestRemainingSpace)) {
                    bestScore = score;
                    bestBinIdx = binIdx;
                    bestRemainingSpace = remainingSpace;
                }
            }

            if (bestBinIdx == -1) {
                bins.add(new Bin(capacity));
                bestBinIdx = bins.size() - 1;
            }

            bins.get(bestBinIdx).addItem(itemIdx + 1, pieceSize);
            memory.addItem(pieceSize);
        }

        // --- Phase 2: Buffer re-optimization (BFD) ---
        // Collect buffered items: original 1-based index and size
        int[][] bufferItems = new int[bufferSize][2];
        for (int i = 0; i < bufferSize; i++) {
            int originalIdx = mainItemCount + i + 1;
            int size = workItems[mainItemCount + i];
            bufferItems[i][0] = originalIdx;
            bufferItems[i][1] = size;
        }
        // Sort descending by size (Best Fit Decreasing)
        Arrays.sort(bufferItems, (a, b) -> Integer.compare(b[1], a[1]));

        for (int i = 0; i < bufferSize; i++) {
            int originalIdx = bufferItems[i][0];
            int pieceSize = bufferItems[i][1];

            // Best Fit: find bin with smallest gap that fits this item
            int bestBinIdx = -1;
            int bestRemainingSpace = Integer.MAX_VALUE;

            for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
                Bin bin = bins.get(binIdx);
                int remainingSpace = bin.getEmptiness() - pieceSize;
                if (remainingSpace < 0) continue;
                if (remainingSpace < bestRemainingSpace) {
                    bestRemainingSpace = remainingSpace;
                    bestBinIdx = binIdx;
                }
            }

            if (bestBinIdx == -1) {
                bins.add(new Bin(capacity));
                bestBinIdx = bins.size() - 1;
            }

            bins.get(bestBinIdx).addItem(originalIdx, pieceSize);
        }
        return new Solution("solved", bins);
    }
    
    /**
     * Create a BPPState for evaluating placement of item at index in a specific bin.
     */
    private BPPState createState(int[] items, int itemIdx, Bin bin, Memory memory) {
        BPPState state = new BPPState(items, bin.getCapacity());
        state.setCurrentPosition(itemIdx);
        state.setBinFullness(bin.getFullness());
        state.setMemory(memory.copy());
        return state;
    }
}
