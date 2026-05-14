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
 * Training mode:  java -cp out Main --train
 * Test mode:      java -cp out Main -s instance_file -o solution_file [-t max_time] [--weighted-voting]
 *
 * Compile: javac -d out *.java
 */
public class Main {

    private static final long DEFAULT_TIME_LIMIT = 10000;
    private static final String ENSEMBLE_FILE = "ensemble.ser";
    private static final int NUM_CLASSES = 4;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--train")) {
            runTrainingMode();
        } else {
            runTestMode(args);
        }
    }

    private static void runTrainingMode() {
        System.out.println("=== Training Mode: Island Model GP ===");

        System.out.println("Loading training set...");
        List<BPPInstance> trainingSet = loadTrainingSet("dualdistribution/train");
        System.out.println("Training instances: " + trainingSet.size());

        if (trainingSet.isEmpty()) {
            System.err.println("Error: No training instances found.");
            System.exit(1);
        }

        long startTime = System.currentTimeMillis();
        List<Heuristic> heuristics = IslandModelGP.trainAll(trainingSet);
        long elapsed = System.currentTimeMillis() - startTime;

        if (heuristics.isEmpty()) {
            System.err.println("Error: No heuristics evolved successfully.");
            System.exit(1);
        }

        System.out.println("Evolution completed in " + elapsed + "ms (" + (elapsed / 1000.0) + "s)");
        System.out.println("Heuristics evolved: " + heuristics.size());
        for (int i = 0; i < heuristics.size(); i++) {
            Heuristic h = heuristics.get(i);
            System.out.println("  h" + (char)('A' + i) + ": size=" + h.getSize() + ", depth=" + h.getDepth());
        }

        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(ENSEMBLE_FILE))) {
            out.writeObject(heuristics);
            System.out.println("Ensemble saved to: " + ENSEMBLE_FILE);
        } catch (IOException e) {
            System.err.println("Error saving ensemble: " + e.getMessage());
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
        boolean useWeightedVoting = false;

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
            } else if (args[i].equals("--weighted-voting")) {
                useWeightedVoting = true;
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
            File ensembleFile = new File(ENSEMBLE_FILE);
            if (!ensembleFile.exists()) {
                System.err.println("Error: Ensemble file not found: " + ENSEMBLE_FILE);
                System.err.println("Run 'java -cp out Main --train' first.");
                System.exit(1);
            }

            System.out.println("Loading ensemble from: " + ENSEMBLE_FILE);
            List<Heuristic> heuristicsList;
            try (ObjectInputStream in = new ObjectInputStream(
                    new FileInputStream(ENSEMBLE_FILE))) {
                heuristicsList = (List<Heuristic>) in.readObject();
            }
            Heuristic[] heuristics = heuristicsList.toArray(new Heuristic[0]);
            System.out.println("Ensemble loaded: " + heuristics.length + " heuristics");
            for (int i = 0; i < heuristics.length; i++) {
                System.out.println("  h" + (char)('A' + i) + ": size=" + heuristics[i].getSize() +
                                 ", depth=" + heuristics[i].getDepth());
            }

            System.out.println("Loading instance: " + instancePath);
            BPPInstance instance = BPPInstance.load(instancePath);
            System.out.println("Instance: " + instance.getName() +
                             ", Items: " + instance.getItemCount() +
                             ", Capacity: " + instance.getCapacity());

            double l2Bound = L2BoundCalculator.calculate(instance);
            instance.setVerifiedL2Bound(l2Bound);

            long startTime = System.currentTimeMillis();
            long deadline = startTime + maxTime;

            System.out.println("Solving (time limit: " + maxTime + "ms)" +
                             (useWeightedVoting ? " with weighted voting" : " with memory switching"));

            ParallelShuffleEnsemble ensemble = new ParallelShuffleEnsemble(
                heuristics, instance.getCapacity(), useWeightedVoting);

            ParallelShuffleEnsemble.EnsembleResult result = ensemble.run(instance.getItems(), maxTime);

            Solution bestSolution = result.solution;
            int bestBinCount = result.bestBinCount;
            long totalElapsed = result.elapsedMs;

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
        System.out.println("    java -cp out Main --train");
        System.out.println();
        System.out.println("  Testing:");
        System.out.println("    java -cp out Main -s instance_file -o solution_file [-t max_time] [--weighted-voting]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -s instance_file    Path to the BPP instance file");
        System.out.println("  -o solution_file   Path to save the solution");
        System.out.println("  -t max_time        Maximum time in milliseconds (default: 10000)");
        System.out.println("  --weighted-voting  Use weighted voting (Strategy 2b) instead of hard-switch (Strategy 2a)");
        System.out.println("  -h, --help         Show this help message");
    }
}
