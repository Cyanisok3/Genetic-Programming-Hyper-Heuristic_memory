import java.util.ArrayList;
import java.util.List;

/**
 * Solver that applies a GP heuristic to solve a BPP instance.
 * 
 * This is an ONLINE BPP solver - items are processed in their original input order
 * without any global sorting. This follows the paper's approach and CW requirements.
 * 
 * Reference: Burke et al. 2010 - "There is no global sorting of items"
 * 
 * Two-stage decision model (inspired by Jin et al. 2024):
 * - Stage 1 (coarse filtering): Fast elimination of unpromising bins using simple rules.
 *   Rule 1: Skip bins where the item does not fit.
 *   Rule 2: Skip bins where remaining space > pieceSize + THRESHOLD (too loose).
 *   Rule 3: If more than MAX_CANDIDATES remain, keep only the N with smallest remaining space.
 * - Stage 2 (fine scoring): Evaluate GP heuristic on the filtered candidate set.
 *   This mirrors the two-stage rollout decision model from Jin et al. 2024, adapted for BPP.
 * 
 * Reference: Jin et al. 2024, Memetic Computing - "two-stage adaptive rollout decision model"
 */
public class BPPSolver {
    
    // Stage 1 filter parameters (inspired by Jin et al. 2024 Section 4.1-4.2)
    private static final int THRESHOLD = 20;       // Max extra space beyond pieceSize
    private static final int MAX_CANDIDATES = 3;  // Keep top N bins after filtering
    
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

            // Stage 1: Coarse filtering (0 computation cost)
            List<int[]> candidates = new ArrayList<>();
            for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
                Bin bin = bins.get(binIdx);
                int remainingSpace = bin.getEmptiness() - pieceSize;
                // Rule 1: must fit
                if (remainingSpace < 0) continue;
                // Rule 2: skip bins that are too loose (remaining space >> piece size)
                if (remainingSpace > pieceSize + THRESHOLD) continue;
                // Keep [binIdx, remainingSpace] as candidate
                candidates.add(new int[]{binIdx, remainingSpace});
            }

            // Stage 2: Fine scoring with GP heuristic
            int bestBinIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestRemainingSpace = Integer.MAX_VALUE;

            // Rule 3: if too many candidates, keep only the tightest N
            if (candidates.size() > MAX_CANDIDATES) {
                candidates.sort((a, b) -> Integer.compare(a[1], b[1]));
                candidates = new ArrayList<>(candidates.subList(0, MAX_CANDIDATES));
            }

            // Evaluate GP heuristic on each candidate bin
            for (int[] cand : candidates) {
                int binIdx = cand[0];
                int remainingSpace = cand[1];
                Bin bin = bins.get(binIdx);
                BPPState state = createState(items, itemIdx, bin, memory);
                double score = heuristic.evaluate(state);

                // Best Fit Decreasing tiebreaker: prefer bins with less remaining space
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
    private BPPState createState(int[] items, int itemIdx, Bin bin, Memory memory) {
        // Create base state
        BPPState state = new BPPState(items, bin.getCapacity());
        
        // Set the state to reflect BEFORE placing the item
        // currentPosition = itemIdx (we're evaluating the item at this index)
        // binFullness = current bin's fullness (before adding this item)
        state.setCurrentPosition(itemIdx);
        state.setBinFullness(bin.getFullness());
        
        // Copy memory
        state.setMemory(memory.copy());
        
        return state;
    }
}
