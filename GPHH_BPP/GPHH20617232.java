import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the GPHH BPP solver.
 *
 * Training mode: java GPHH20617232 --train
 *                OR java -cp out GPHH20617232 --train
 * Test mode:     java GPHH20617232 -s instance_file -o solution_file [-t max_time]
 *                OR java -cp out GPHH20617232 -s instance_file -o solution_file [-t max_time]
 * Compile: [javac *.java] OR [javac -d out *.java]
 */
public class GPHH20617232 {

    private static final long DEFAULT_TIME_LIMIT = 9999;
    private static final String SERIALIZED_HEURISTIC = "best_heuristic.ser";
    private static final int NUM_CLASSES = 4;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--train")) {
            runTrainingMode();
        } else {
            runTestMode(args);
        }
    }

    private static void runTrainingMode() {
        System.out.println("=== Training Mode ===");

        System.out.println("Loading training set...");
        List<BPPInstance> trainingSet = loadTrainingSet("dualdistribution/train");
        System.out.println("Training instances: " + trainingSet.size());

        if (trainingSet.isEmpty()) {
            System.err.println("Error: No training instances found.");
            System.exit(1);
        }

        System.out.println("Evolving heuristic (no time limit)...");
        long startTime = System.currentTimeMillis();

        GeneticProgramming gp = new GeneticProgramming();
        Heuristic best = gp.evolve(trainingSet);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Evolution completed in " + elapsed + "ms (" + (elapsed / 1000.0) + "s)");
        System.out.println("Best heuristic: " + best);
        System.out.println("Tree size: " + best.getSize() + " nodes, depth: " + best.getDepth());

        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(SERIALIZED_HEURISTIC))) {
            out.writeObject(best);
            System.out.println("Heuristic saved successfully.");
        } catch (IOException e) {
            System.err.println("Error saving heuristic: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<BPPInstance> loadTrainingSet(String baseDir) {
        List<BPPInstance> trainingSet = new ArrayList<>();

        for (int c = 1; c <= NUM_CLASSES; c++) {
            File classDir = new File(baseDir, "class" + c);
            if (classDir.exists() && classDir.isDirectory()) {
                File[] files = classDir.listFiles((d, name) -> name.endsWith(".txt"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            BPPInstance instance = BPPInstance.load(file.getPath());
                            trainingSet.add(instance);
                        } catch (IOException e) {
                            System.err.println("Warning: Could not load " + file.getName());
                        }
                    }
                }
            }
        }

        return trainingSet;
    }

    private static void runTestMode(String[] args) {
        String instancePath = null;
        String solutionPath = null;
        long maxTime = DEFAULT_TIME_LIMIT;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-s") && i + 1 < args.length) {
                instancePath = args[++i];
            } else if (args[i].equals("-o") && i + 1 < args.length) {
                solutionPath = args[++i];
            } else if (args[i].equals("-t") && i + 1 < args.length) {
                try {
                    maxTime = Long.parseLong(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid time value.");
                    System.exit(1);
                }
            } else if (args[i].equals("-h") || args[i].equals("--help")) {
                printUsage();
                System.exit(0);
            }
        }

        if (instancePath == null || solutionPath == null) {
            printUsage();
            System.exit(1);
        }

        try {
            File heuristicFile = new File(SERIALIZED_HEURISTIC);
            if (!heuristicFile.exists()) {
                System.err.println("Error: Heuristic file not found: " + SERIALIZED_HEURISTIC);
                System.exit(1);
            }

            System.out.println("Loading heuristic from: " + SERIALIZED_HEURISTIC);
            Heuristic heuristic;
            try (ObjectInputStream in = new ObjectInputStream(
                    new FileInputStream(SERIALIZED_HEURISTIC))) {
                heuristic = (Heuristic) in.readObject();
            }
            System.out.println("Heuristic loaded: " + heuristic);
            System.out.println("Tree size: " + heuristic.getSize() + " nodes, depth: " + heuristic.getDepth());

            System.out.println("Loading instance: " + instancePath);
            BPPInstance instance = BPPInstance.load(instancePath);
            System.out.println("Instance: " + instance.getName() +
                             ", Items: " + instance.getItemCount() +
                             ", Capacity: " + instance.getCapacity());

            double l2Bound = L2BoundCalculator.calculate(instance);
            instance.setVerifiedL2Bound(l2Bound);

            System.out.println("Solving instance (time limit: " + maxTime + "ms)...");

            BPPSolver solver = new BPPSolver();
            int capacity = instance.getCapacity();
            int[] items = instance.getItems();

            long deadline = System.currentTimeMillis() + maxTime;
            int trialIndex = 0;
            int bestBinCount = Integer.MAX_VALUE;
            Solution bestSolution = null;

            while (System.currentTimeMillis() < deadline) {
                long seed = System.nanoTime() ^ (long) trialIndex;
                Solution sol = solver.solveWithOrder(items, heuristic, capacity, seed);
                if (sol.getBinCount() < bestBinCount) {
                    bestBinCount = sol.getBinCount();
                    bestSolution = sol;
                }
                trialIndex++;
            }

            if (bestSolution == null) {
                bestSolution = solver.solveWithOrder(items, heuristic, capacity, null);
                bestBinCount = bestSolution.getBinCount();
            }

            bestSolution.setInstanceName(instance.getName());
            bestSolution.setL2Bound(l2Bound);

            System.out.println("Solution: " + bestBinCount + " bins used");
            System.out.println("L2 lower bound: " + (int) l2Bound);
            System.out.println("Ratio: " + String.format("%.4f", (double) bestBinCount / l2Bound));
            System.out.println("Gap (bins - L2): " + (bestBinCount - (int) l2Bound));

            System.out.println("Saving solution to: " + solutionPath);
            bestSolution.save(solutionPath);
            System.out.println("Done!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (ClassNotFoundException e) {
            System.err.println("Error: Heuristic class not found during deserialization.");
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Training (no time limit):");
        System.out.println("    java GPHH20617232 --train");
        System.out.println();
        System.out.println("  Testing:");
        System.out.println("    java GPHH20617232 -s instance_file -o solution_file [-t max_time]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -s instance_file    Path to the BPP instance file");
        System.out.println("  -o solution_file   Path to save the solution");
        System.out.println("  -t max_time        Maximum time in milliseconds (default: 9999)");
        System.out.println("  -h, --help         Show this help message");
    }
}
