import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Solver that applies a GP heuristic (or ensemble) to solve a BPP instance.
 * Items are processed in their original input order (online BPP).
 * For each item, the GP heuristic evaluates every feasible bin and the item
 * is placed in the bin with the highest heuristic score. A new bin is opened
 * if no existing bin can fit the item.
 *
 * Ensemble mode: each heuristic runs the full instance independently;
 * the solution with the fewest bins is returned.
 */
public class BPPSolver {

    // — Public entry points —

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
        Solution sol = solveWithOrder(items, new Heuristic[]{heuristic}, instance.getCapacity());
        sol.setInstanceName(instance.getName());
        return sol;
    }

    /**
     * Solve with a specific item order (fast version for training).
     *
     * @param items Item sizes in specific order
     * @param heuristic GP heuristic to use
     * @param capacity Bin capacity
     * @return Solution
     */
    public Solution solveWithOrder(int[] items, Heuristic heuristic, int capacity) {
        return solveWithOrderRaw(items, new Heuristic[]{heuristic}, capacity, null);
    }

    /**
     * Solve with a specific item order using an ensemble of heuristics.
     * Each heuristic scores every feasible bin; the bin with the highest
     * average score is selected. Ties are broken by smallest remaining space.
     *
     * @param items Item sizes in specific order
     * @param heuristics Array of GP heuristics (must have length >= 1)
     * @param capacity Bin capacity
     * @return Solution
     */
    public Solution solveWithOrder(int[] items, Heuristic[] heuristics, int capacity) {
        return solveWithOrderRaw(items, heuristics, capacity, null);
    }

    /**
     * Solve with a specific item order using an ensemble of individuals.
     * Each heuristic is run independently on the full instance.
     * The solution with the fewest bins is returned.
     *
     * @param items Item sizes in specific order
     * @param individuals Array of GP individuals
     * @param capacity Bin capacity
     * @return Solution with minimum bin count
     */
    public Solution solveWithOrder(int[] items, Individual[] individuals, int capacity) {
        if (individuals.length == 0) return null;
        if (individuals.length == 1) {
            return solveWithOrder(items, individuals[0].getHeuristic(), capacity);
        }

        Solution best = null;
        for (Individual ind : individuals) {
            Solution sol = solveWithOrder(items, ind.getHeuristic(), capacity);
            if (best == null || sol.getBinCount() < best.getBinCount()) {
                best = sol;
            }
        }
        return best;
    }

    /**
     * Solve with a specific item order and optional shuffle.
     *
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
        return solveWithOrderRaw(workItems, new Heuristic[]{heuristic}, capacity, rng);
    }

    // — Core private loop —

    /**
     * Core solve loop on a pre-ordered workItems array.
     * Ensemble version: each feasible bin is scored by all heuristics,
     * and the item is placed in the bin with the highest average score.
     *
     * @param items Pre-ordered item sizes
     * @param heuristics Array of GP heuristics (must have length >= 1)
     * @param capacity Bin capacity
     * @param rng Random instance for tie-breaking; may be null
     * @return Solution
     */
    private Solution solveWithOrderRaw(int[] items, Heuristic[] heuristics, int capacity, Random rng) {
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
                double avgScore = 0.0;
                for (Heuristic h : heuristics) {
                    avgScore += h.evaluate(state);
                }
                avgScore /= heuristics.length;

                if (avgScore > bestScore ||
                    (Math.abs(avgScore - bestScore) < 1e-6 && remainingSpace < bestRemainingSpace)) {
                    bestScore = avgScore;
                    bestBinIdx = binIdx;
                    bestRemainingSpace = remainingSpace;
                } else if (Math.abs(avgScore - bestScore) < 1e-6 && remainingSpace == bestRemainingSpace) {
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


    // — Helpers —

    private BPPState createState(int[] items, int itemIdx, Bin bin, Memory memory) {
        BPPState state = new BPPState(items, bin.getCapacity());
        state.setCurrentPosition(itemIdx);
        state.setBinFullness(bin.getFullness());
        state.setMemory(memory.copy());
        return state;
    }
}
