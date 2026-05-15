import java.util.ArrayList;
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
     * Items are processed in the given order.
     * @param items Item sizes in specific order
     * @param heuristic GP heuristic to use
     * @param capacity Bin capacity
     * @return Solution
     */
    public Solution solveWithOrder(int[] items, Heuristic heuristic, int capacity) {
        return solveWithOrderRaw(items, heuristic, capacity, null);
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
        Random rng = (seed != null) ? new Random(seed) : null;
        int[] workItems = items;
        if (seed != null) {
            workItems = items.clone();
            Random shuffleRng = new Random(seed);
            for (int i = workItems.length - 1; i > 0; i--) {
                int j = shuffleRng.nextInt(i + 1);
                int tmp = workItems[i];
                workItems[i] = workItems[j];
                workItems[j] = tmp;
            }
        }
        return solveWithOrderRaw(workItems, heuristic, capacity, rng);
    }

    /**
     * Core solve loop on a pre-ordered workItems array.
     * GP heuristic evaluates each feasible bin; item goes to best-scoring bin.
     * @param items Pre-ordered item sizes
     * @param heuristic GP heuristic to use
     * @param capacity Bin capacity
     * @param rng Random instance for tie-breaking; may be null
     * @return Solution
     */
    private Solution solveWithOrderRaw(int[] items, Heuristic heuristic, int capacity, Random rng) {
        List<Bin> bins = new ArrayList<>();
        bins.add(new Bin(capacity));
        Memory memory = new Memory();

        for (int itemIdx = 0; itemIdx < items.length; itemIdx++) {
            int pieceSize = items[itemIdx];

            int bestBinIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestRemainingSpace = Integer.MAX_VALUE;

            for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
                Bin bin = bins.get(binIdx);
                int remainingSpace = bin.getEmptiness() - pieceSize;
                if (remainingSpace < 0) continue;

                BPPState state = createState(items, itemIdx, bin, memory);
                double score = heuristic.evaluate(state);

                if (score > bestScore ||
                    (Math.abs(score - bestScore) < 1e-6 && remainingSpace < bestRemainingSpace)) {
                    bestScore = score;
                    bestBinIdx = binIdx;
                    bestRemainingSpace = remainingSpace;
                } else if (Math.abs(score - bestScore) < 1e-6 && remainingSpace == bestRemainingSpace) {
                    // Random tie-breaking: when score and remainingSpace are equal,
                    // randomly choose between the current bin and this one
                    if (rng != null && rng.nextBoolean()) {
                        bestBinIdx = binIdx;
                    }
                }
            }

            if (bestBinIdx == -1) {
                bins.add(new Bin(capacity));
                bestBinIdx = bins.size() - 1;
            }

            bins.get(bestBinIdx).addItem(itemIdx + 1, pieceSize);
            memory.addItem(pieceSize);
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
