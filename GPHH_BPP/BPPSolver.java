import java.util.ArrayList;
import java.util.List;

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
        int itemCount = items.length;

        List<Bin> bins = new ArrayList<>();
        bins.add(new Bin(capacity));

        Memory memory = new Memory();

        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            int pieceSize = items[itemIdx];

            // Evaluate GP heuristic on each existing bin to find the best one
            int bestBinIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestRemainingSpace = Integer.MAX_VALUE;

            for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
                Bin bin = bins.get(binIdx);
                int remainingSpace = bin.getEmptiness() - pieceSize;

                // Skip bins where the item does not fit
                if (remainingSpace < 0) continue;

                // Evaluate heuristic on this candidate
                BPPState state = createState(items, itemIdx, bin, memory, bins.size());
                double score = heuristic.evaluate(state);

                // Best Fit tiebreaker: prefer bins with less remaining space
                if (score > bestScore ||
                    (Math.abs(score - bestScore) < 1e-6 && remainingSpace < bestRemainingSpace)) {
                    bestScore = score;
                    bestBinIdx = binIdx;
                    bestRemainingSpace = remainingSpace;
                }
            }

            // If no existing bin fits, open a new bin
            if (bestBinIdx == -1) {
                bins.add(new Bin(capacity));
                bestBinIdx = bins.size() - 1;
            }

            // Place item in best bin (add 1 for 1-based indexing in output)
            bins.get(bestBinIdx).addItem(itemIdx + 1, pieceSize);

            // Add item to memory (for future terminal calculations)
            memory.addItem(pieceSize);
        }

        return new Solution("solved", bins);
    }
    
    /**
     * Create a BPPState for evaluating placement of item at index in a specific bin.
     */
    private BPPState createState(int[] items, int itemIdx, Bin bin, Memory memory, int binCount) {
        BPPState state = new BPPState(items, bin.getCapacity());
        state.setCurrentPosition(itemIdx);
        state.setBinFullness(bin.getFullness());
        state.setMemory(memory.copy());
        state.setBinCount(binCount);
        return state;
    }
}
