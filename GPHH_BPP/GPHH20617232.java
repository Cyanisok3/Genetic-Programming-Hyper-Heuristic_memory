import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the GPHH BPP solver.
 *
 * Training mode: java GPHH20617232 --train [--seed N]
 *                OR java -cp out GPHH20617232 --train [--seed N]
 * Test mode:     java GPHH20617232 -s instance_file -o solution_file [-t max_time]
 *                OR java -cp out GPHH20617232 -s instance_file -o solution_file [-t max_time]
 * Compile: [javac *.java] OR [javac -d out *.java]
 */
public class GPHH20617232 {

    private static final long DEFAULT_TIME_LIMIT = 9999;
    private static final String HEURISTICS_DIR = "best_heristics";
    private static final int NUM_CLASSES = 4;

    public static void main(String[] args) {
        Long seed = null;
        boolean trainMode = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--train")) {
                trainMode = true;
            } else if (args[i].equals("--seed") && i + 1 < args.length) {
                try {
                    seed = Long.parseLong(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid seed value.");
                    System.exit(1);
                }
            }
        }

        if (trainMode) {
            runTrainingMode(seed);
        } else {
            runTestMode(args);
        }
    }

    private static void runTrainingMode(Long seedOpt) {
        long seed = (seedOpt != null) ? seedOpt : System.currentTimeMillis();
        System.out.println("=== Training Mode (seed=" + seed + ") ===");

        // Parameter overrides based on seed for ensemble diversity.
        Config.INSTANCE.resetAdaptiveMutation();
        System.out.println("Config: depth=6, elite=2, tournament=20 (defaults)");

        System.out.println("Loading training set...");
        List<BPPInstance> trainingSet = loadTrainingSet("dualdistribution/train");
        System.out.println("Training instances: " + trainingSet.size());

        if (trainingSet.isEmpty()) {
            System.err.println("Error: No training instances found.");
            System.exit(1);
        }

        System.out.println("Evolving heuristic (no time limit)...");
        long startTime = System.currentTimeMillis();

        GeneticProgramming gp = new GeneticProgramming(seed);
        Heuristic best = gp.evolve(trainingSet);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Evolution completed in " + elapsed + "ms (" + (elapsed / 1000.0) + "s)");
        System.out.println("Best heuristic: " + best);
        System.out.println("Tree size: " + best.getSize() + " nodes, depth: " + best.getDepth());

        String outputFile = HEURISTICS_DIR + File.separator + "best_heuristic_" + seed + ".ser";
        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(outputFile))) {
            out.writeObject(best);
            System.out.println("Heuristic saved to: " + outputFile);
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
        String heuristicsDirOverride = null;

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
            } else if (args[i].equals("-f") && i + 1 < args.length) {
                heuristicsDirOverride = args[++i];
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
            File heuristicsDir;
            File[] serFiles;
            if (heuristicsDirOverride != null) {
                File f = new File(heuristicsDirOverride);
                if (f.isFile()) {
                    heuristicsDir = f.getParentFile();
                    serFiles = new File[]{f};
                } else {
                    heuristicsDir = f;
                    serFiles = heuristicsDir.listFiles((dir, name) -> name.endsWith(".ser"));
                }
            } else {
                heuristicsDir = new File(HEURISTICS_DIR);
                serFiles = heuristicsDir.listFiles((dir, name) -> name.endsWith(".ser"));
            }
            if (serFiles == null || serFiles.length == 0) {
                System.err.println("Error: No .ser files found.");
                System.exit(1);
            }
            System.out.println("Loading " + serFiles.length + " heuristic(s) from " + heuristicsDir.getPath() + "/");
            List<Individual> loadedList = new ArrayList<>();
            for (int i = 0; i < serFiles.length; i++) {
                try (ObjectInputStream in = new ObjectInputStream(
                        new FileInputStream(serFiles[i]))) {
                    Object obj = in.readObject();
                    Individual ind;
                    if (obj instanceof Individual) {
                        ind = (Individual) obj;
                    } else {
                        ind = new Individual((Heuristic) obj);
                    }
                    loadedList.add(ind);
                    System.out.println("  [" + loadedList.size() + "] " + serFiles[i].getName() +
                                     " — size: " + ind.getHeuristic().getSize() +
                                     ", depth: " + ind.getHeuristic().getDepth());
                } catch (InvalidClassException e) {
                    System.err.println("  [skip] " + serFiles[i].getName() +
                                     " (serialized with older code version; ignored)");
                }
            }
            if (loadedList.isEmpty()) {
                System.err.println("Error: No compatible .ser files found in " + HEURISTICS_DIR);
                System.exit(1);
            }
            Individual[] individuals = loadedList.toArray(new Individual[0]);
            System.out.println("Ensemble size: " + individuals.length);

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

            Solution bestSolution = solver.solveWithOrder(items, individuals, capacity);
            int bestBinCount = bestSolution.getBinCount();

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
        System.out.println("    java GPHH20617232 --train [--seed N]");
        System.out.println();
        System.out.println("  Testing:");
        System.out.println("    java GPHH20617232 -s instance_file -o solution_file [-t max_time]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --seed N          Random seed for training (deterministic runs)");
        System.out.println("  -f file         Load a single heuristic file (instead of all .ser in heuristics/)");
        System.out.println("  -s instance_file  Path to the BPP instance file");
        System.out.println("  -o solution_file  Path to save the solution");
        System.out.println("  -t max_time       Maximum time in milliseconds (default: 9999)");
        System.out.println("  -h, --help        Show this help message");
    }
}
