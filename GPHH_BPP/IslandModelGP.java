import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Island Model GP: runs 4 islands in parallel, each evolving a separate heuristic.
 * Islands differ in random seed and depth constraints, providing diversity.
 */
public class IslandModelGP {

    private static final Island[] ISLANDS = {
        new Island(42L,  4, 6, "A"),   // balanced: base config (matches original GP)
        new Island(137L, 3, 5, "B"),   // compact: shallower, narrower trees
        new Island(256L, 4, 6, "C"),   // deep: minD=4 (deeper than A/B), explores complex interactions
        new Island(999L, 5, 6, "D"),   // wide-medium: deeper min, same max as A
    };

    /**
     * Run all islands in parallel and collect the evolved heuristics.
     * @param trainingSet training instances
     * @return list of 4 Heuristic objects (one per island)
     */
    public static List<Heuristic> trainAll(List<BPPInstance> trainingSet) {
        System.out.println("=== Island Model GP: " + ISLANDS.length + " islands ===");
        for (Island isl : ISLANDS) {
            System.out.println("  Island " + isl.name + ": seed=" + isl.seed +
                             ", minDepth=" + isl.minDepth + ", maxDepth=" + isl.maxDepth);
        }

        ExecutorService exec = Executors.newFixedThreadPool(ISLANDS.length);
        List<Future<Heuristic>> futures = new ArrayList<>();

        for (Island island : ISLANDS) {
            futures.add(exec.submit(() -> {
                System.out.println("Island " + island.name + " starting...");
                Heuristic h = GeneticProgramming.evolveIsland(
                    island.seed, island.minDepth, island.maxDepth, trainingSet);
                System.out.println("Island " + island.name + " done. Heuristic: " + h);
                return h;
            }));
        }

        List<Heuristic> results = new ArrayList<>();
        for (int i = 0; i < ISLANDS.length; i++) {
            try {
                Heuristic h = futures.get(i).get();
                if (h != null) {
                    results.add(h);
                    System.out.println("Island " + ISLANDS[i].name + " result: " +
                                     "size=" + h.getSize() + ", depth=" + h.getDepth());
                } else {
                    System.err.println("Island " + ISLANDS[i].name + " returned null heuristic.");
                }
            } catch (Exception e) {
                System.err.println("Island " + ISLANDS[i].name + " failed: " + e.getMessage());
            }
        }

        exec.shutdown();
        System.out.println("Island Model GP complete: " + results.size() + "/" + ISLANDS.length + " heuristics evolved.");
        return results;
    }

    /**
     * Island configuration record.
     */
    private static class Island {
        final long seed;
        final int minDepth;
        final int maxDepth;
        final String name;

        Island(long seed, int minDepth, int maxDepth, String name) {
            this.seed = seed;
            this.minDepth = minDepth;
            this.maxDepth = maxDepth;
            this.name = name;
        }
    }
}
