import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

/**
 * Parallel shuffle ensemble: evaluates multiple item orderings using ForkJoin parallelism.
 *
 * Design:
 *   - Top level: one warmup pass with island A's heuristic, produces memory AVE.
 *   - Decision: use AVE to select a heuristic (Strategy 2a) or compute weights (Strategy 2b).
 *   - Each ShuffleTask receives the selected heuristic(s) and just runs the solve —
 *     no redundant warmup inside tasks.
 *
 * For K>1 with hard-switch (2a): each shuffle evaluates ONLY the selected heuristic
 * (the decision is made once per warmup run, not once per shuffle).
 * For weighted voting (2b): each shuffle evaluates all heuristics with computed weights.
 */
public class ParallelShuffleEnsemble {

    private final Heuristic[] heuristics;
    private final int capacity;
    private final BPPSolver solver;
    private final ForkJoinPool pool;
    private final boolean useWeightedVoting;

    /**
     * The heuristic selected by the warmup decision, or null if using weighted voting.
     */
    private Heuristic selectedHeuristic;

    public ParallelShuffleEnsemble(Heuristic[] heuristics, int capacity, boolean useWeightedVoting) {
        this.heuristics = heuristics;
        this.capacity = capacity;
        this.solver = new BPPSolver();
        this.pool = ForkJoinPool.commonPool();
        this.useWeightedVoting = useWeightedVoting;
        this.selectedHeuristic = null;
    }

    /**
     * Run the ensemble: warmup once at top level, then run parallel shuffle tasks.
     *
     * @param items item sizes (original order)
     * @param timeLimitMs maximum time in milliseconds
     * @return best solution found
     */
    public EnsembleResult run(int[] items, long timeLimitMs) {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + timeLimitMs;

        // --- Top-level warmup ---
        // Strategy 2a: warmup once, decide once
        // Strategy 2b: warmup once, compute weights once
        double ave;
        if (!useWeightedVoting) {
            Memory warmupMemory = solver.runWarmup(items, heuristics[0], capacity);
            ave = warmupMemory.getAverage();
            if (ave > 45) {
                selectedHeuristic = heuristics.length > 2 ? heuristics[2] : heuristics[0];
                System.out.println("Warmup: AVE=" + String.format("%.1f", ave) + " → deep heuristic (hC)");
            } else if (ave < 36) {
                selectedHeuristic = heuristics.length > 1 ? heuristics[1] : heuristics[0];
                System.out.println("Warmup: AVE=" + String.format("%.1f", ave) + " → compact heuristic (hB)");
            } else {
                selectedHeuristic = heuristics[0];
                System.out.println("Warmup: AVE=" + String.format("%.1f", ave) + " → balanced heuristic (hA)");
            }
        }

        // --- Parallel shuffle execution ---
        int bestBinCount = Integer.MAX_VALUE;
        Solution bestSolution = null;
        int totalShuffles = 0;
        long seedCounter = System.nanoTime();

        while (System.currentTimeMillis() < deadline) {
            int batchSize = Math.min(BATCH_SIZE, remainingBatches(deadline));
            if (batchSize <= 0) break;

            List<RecursiveTask<Solution>> taskList = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                long seed = seedCounter++;
                taskList.add(new ShuffleTask(items, seed));
            }

            ForkJoinTask.invokeAll(taskList);

            for (RecursiveTask<Solution> task : taskList) {
                Solution sol = task.join();
                totalShuffles++;
                if (sol.getBinCount() < bestBinCount) {
                    bestBinCount = sol.getBinCount();
                    bestSolution = sol;
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println("  [shuffle " + totalShuffles + "] bins=" + bestBinCount +
                                     " (elapsed: " + elapsed + "ms)");
                }
            }
        }

        if (bestSolution == null) {
            bestSolution = solver.solveWithMemorySwitching(
                items, heuristics, capacity, null, useWeightedVoting);
            bestBinCount = bestSolution.getBinCount();
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        System.out.println("Ensemble complete: " + totalShuffles + " shuffles" +
                          (useWeightedVoting ? " (weighted voting)" : " (hard-switch: " + selectedHeuristic + ")") +
                          " in " + totalElapsed + "ms");

        return new EnsembleResult(bestSolution, bestBinCount, totalShuffles, totalElapsed);
    }

    private int remainingBatches(long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) return 0;
        return (int) Math.min(BATCH_SIZE, remaining / 1);
    }

    /**
     * ShuffleTask: evaluates one shuffle with the pre-selected strategy.
     */
    private class ShuffleTask extends RecursiveTask<Solution> {
        private final int[] items;
        private final long seed;

        ShuffleTask(int[] items, long seed) {
            this.items = items;
            this.seed = seed;
        }

        @Override
        protected Solution compute() {
            if (useWeightedVoting) {
                // Weighted voting: evaluate all heuristics with computed weights
                return solver.solveWithWeightedVoting(items, heuristics, capacity, seed);
            } else {
                // Hard-switch: use the top-level selected heuristic
                return solver.solveWithOrder(items, selectedHeuristic, capacity, seed);
            }
        }
    }

    public static class EnsembleResult {
        public final Solution solution;
        public final int bestBinCount;
        public final int totalShuffles;
        public final long elapsedMs;

        public EnsembleResult(Solution solution, int bestBinCount, int totalShuffles, long elapsedMs) {
            this.solution = solution;
            this.bestBinCount = bestBinCount;
            this.totalShuffles = totalShuffles;
            this.elapsedMs = elapsedMs;
        }
    }

    private static final int BATCH_SIZE = 32;
}
