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

    // ===== Memory-based Heuristic Switching (Strategies 2a / 2b) =====

    private static final int WARMUP_SIZE = 50;

    /**
     * Run warmup: process the first WARMUP_SIZE items with the given heuristic,
     * collecting memory statistics. Placement result is discarded.
     * @return Memory containing statistics from the warmup run
     */
    public Memory runWarmup(int[] items, Heuristic heuristic, int capacity) {
        int warmupCount = Math.min(WARMUP_SIZE, items.length);
        Memory warmupMemory = new Memory();
        List<Bin> bins = new ArrayList<>();
        bins.add(new Bin(capacity));

        for (int itemIdx = 0; itemIdx < warmupCount; itemIdx++) {
            int pieceSize = items[itemIdx];

            int bestBinIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestRemainingSpace = Integer.MAX_VALUE;

            for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
                Bin bin = bins.get(binIdx);
                int remainingSpace = bin.getEmptiness() - pieceSize;
                if (remainingSpace < 0) continue;

                BPPState state = createState(items, itemIdx, bin, warmupMemory);
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
            warmupMemory.addItem(pieceSize);
        }
        return warmupMemory;
    }

    /**
     * Solve with memory-based heuristic switching.
     * Strategy 2a (hard-switch): warmup with hA → decide → full solve with selected heuristic.
     * Strategy 2b (weighted voting): enabled via useWeightedVoting flag.
     * @param items item sizes
     * @param heuristics array of K heuristics (from island model ensemble)
     * @param capacity bin capacity
     * @param seed random seed for item shuffling (null = no shuffle)
     * @param useWeightedVoting true to use Strategy 2b, false for Strategy 2a
     * @return best solution found
     */
    public Solution solveWithMemorySwitching(int[] items, Heuristic[] heuristics,
                                              int capacity, Long seed, boolean useWeightedVoting) {
        if (useWeightedVoting) {
            return solveWithWeightedVoting(items, heuristics, capacity, seed);
        }
        // --- Strategy 2a: Hard-switch ---
        // 1. Warmup with island A's heuristic to get memory statistics
        Heuristic warmupH = heuristics[0];
        Memory warmupMemory = runWarmup(items, warmupH, capacity);
        double ave = warmupMemory.getAverage();

        // 2. Decision based on memory AVE
        Heuristic selected;
        String reason;
        if (ave > 45) {
            selected = heuristics.length > 2 ? heuristics[2] : heuristics[0]; // hC if available
            reason = "high AVE=" + String.format("%.1f", ave) + " → deep heuristic";
        } else if (ave < 36) {
            selected = heuristics.length > 1 ? heuristics[1] : heuristics[0]; // hB if available
            reason = "low AVE=" + String.format("%.1f", ave) + " → compact heuristic";
        } else {
            selected = heuristics[0]; // hA (balanced)
            reason = "mid AVE=" + String.format("%.1f", ave) + " → balanced heuristic";
        }

        // 3. Full solve with the selected heuristic (fresh shuffle from seed)
        Solution sol = solveWithOrder(items, selected, capacity, seed);
        return sol;
    }

    /**
     * Strategy 2b: Weighted voting across all heuristics.
     * For each item, each heuristic scores every feasible bin.
     * Final score = sum(w_i * score_i), weights derived from warmup memory AVE.
     */
    Solution solveWithWeightedVoting(int[] items, Heuristic[] heuristics,
                                              int capacity, Long seed) {
        // 1. Warmup with island A to get memory AVE
        Memory warmupMemory = runWarmup(items, heuristics[0], capacity);
        double ave = warmupMemory.getAverage();
        double[] weights = computeVotingWeights(ave, heuristics.length);

        // 2. Shuffle items
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

        // 3. Solve with weighted voting
        int itemCount = workItems.length;
        int bufferSize = Math.min(TAIL_BUFFER_SIZE, itemCount);
        int mainItemCount = itemCount - bufferSize;

        List<Bin> bins = new ArrayList<>();
        bins.add(new Bin(capacity));
        Memory memory = new Memory();

        for (int itemIdx = 0; itemIdx < mainItemCount; itemIdx++) {
            int pieceSize = workItems[itemIdx];

            int bestBinIdx = -1;
            double bestWeightedScore = Double.NEGATIVE_INFINITY;
            int bestRemainingSpace = Integer.MAX_VALUE;

            for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
                Bin bin = bins.get(binIdx);
                int remainingSpace = bin.getEmptiness() - pieceSize;
                if (remainingSpace < 0) continue;

                double weightedScore = 0.0;
                for (int k = 0; k < heuristics.length; k++) {
                    BPPState state = createState(workItems, itemIdx, bin, memory);
                    weightedScore += weights[k] * heuristics[k].evaluate(state);
                }

                if (weightedScore > bestWeightedScore ||
                    (Math.abs(weightedScore - bestWeightedScore) < 1e-6 && remainingSpace < bestRemainingSpace)) {
                    bestWeightedScore = weightedScore;
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

        // 4. Buffer re-optimization (BFD)
        int[][] bufferItems = new int[bufferSize][2];
        for (int i = 0; i < bufferSize; i++) {
            bufferItems[i][0] = mainItemCount + i + 1;
            bufferItems[i][1] = workItems[mainItemCount + i];
        }
        Arrays.sort(bufferItems, (a, b) -> Integer.compare(b[1], a[1]));

        for (int i = 0; i < bufferSize; i++) {
            int originalIdx = bufferItems[i][0];
            int pieceSize = bufferItems[i][1];

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
     * Compute voting weights from memory AVE.
     * Returns array of K weights (sum = 1.0).
     */
    private double[] computeVotingWeights(double ave, int k) {
        double[] weights = new double[k];
        if (k == 1) {
            weights[0] = 1.0;
            return weights;
        }

        double w_bimodal = Math.max(0, 1.0 - Math.abs(ave - 40.5) / 8.5);
        if (w_bimodal < 0.3) {
            // Close to unimodal peak: use only the closest specialist
            if (ave >= 41.5 && k > 2) {
                weights[2] = 1.0; // hC
            } else if (ave < 41.5 && k > 1) {
                weights[1] = 1.0; // hB
            } else {
                weights[0] = 1.0; // hA fallback
            }
        } else {
            // Bimodal zone: hA (balanced, index 0) + closest specialist
            weights[0] = 0.5;
            if (ave >= 41.5 && k > 2) {
                weights[2] = 0.5; // hC
            } else if (k > 1) {
                weights[1] = 0.5; // hB
            } else {
                weights[0] = 1.0;
            }
        }
        return weights;
    }
}
