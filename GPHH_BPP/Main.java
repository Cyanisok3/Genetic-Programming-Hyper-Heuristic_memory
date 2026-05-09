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
 * Two modes of operation:
 *
 * Training mode (local, no time limit):
 *   java -cp out Main --train
 *   Evolves a heuristic on training instances and saves it to best_heuristic.ser
 *
 * Test mode (submit, 10-second limit):
 *   java -cp out Main -s instance_file -o solution_file -t 10000
 *   Loads the saved heuristic and solves the given instance
 *
 * Compile: javac -d out *.java
 */
public class Main {

    private static final long DEFAULT_TIME_LIMIT = 10000; // 10 seconds
    private static final String SERIALIZED_HEURISTIC = "best_heuristic.ser";
    private static final int NUM_CLASSES = 4;

    // Class-aware training weights per Burke et al. 2010 (uniform weights)
    public static final double[] CLASS_WEIGHTS = {1.0, 1.0, 1.0, 1.0};

    public static void main(String[] args) {
        // Check for training mode first
        if (args.length > 0 && args[0].equals("--train")) {
            runTrainingMode();
        } else {
            runTestMode(args);
        }
    }

    /**
     * Training mode: evolve heuristic on training set and save to file.
     * Loads training instances from the dualdistribution/train/class{1-4} directories.
     * No time limit.
     */
    private static void runTrainingMode() {
        System.out.println("=== Training Mode ===");

        // Load verified L2 bounds from teacher's CSV
        String l2CSV = "l2_bounds_testdual_instances_0_4_8.csv";
        java.io.File csvFile = new java.io.File(l2CSV);
        if (csvFile.exists()) {
            BPPInstance.loadL2BoundsFromCSV(csvFile.getAbsolutePath());
            System.out.println("Loaded L2 bounds from: " + csvFile.getAbsolutePath());
        } else {
            System.out.println("Warning: L2 bounds CSV not found, will use computed L2 bounds.");
        }

        System.out.println("Loading training set by class...");
        List<BPPInstance>[] trainingByClass = loadTrainingSetByClass("dualdistribution/train");
        int totalInstances = 0;
        for (int c = 0; c < NUM_CLASSES; c++) {
            int count = trainingByClass[c].size();
            totalInstances += count;
            String className = getClassName(c);
            System.out.println("  Class " + c + " (" + className + "): " + count + " instances");
        }
        System.out.println("Total: " + totalInstances + " training instances");

        if (totalInstances == 0) {
            System.err.println("Error: No training instances found.");
            System.exit(1);
        }

        System.out.println("Evolving heuristic (no time limit)...");
        long startTime = System.currentTimeMillis();

        GeneticProgramming gp = new GeneticProgramming();
        Heuristic best = gp.evolveFull(trainingByClass, CLASS_WEIGHTS);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Evolution completed in " + elapsed + "ms (" + (elapsed / 1000.0) + "s)");
        System.out.println("Best heuristic: " + best);
        System.out.println("Tree size: " + best.getSize() + " nodes, depth: " + best.getDepth());

        // Save the heuristic
        System.out.println("Saving heuristic to: " + SERIALIZED_HEURISTIC);
        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(SERIALIZED_HEURISTIC))) {
            out.writeObject(best);
            System.out.println("Heuristic saved successfully.");
        } catch (IOException e) {
            System.err.println("Error saving heuristic: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Load training instances organized into class subdirectories.
     * Directory structure:
     *   train/
     *     class1/ -> class 0 (high-mean, low-SD, unimodal)
     *     class2/ -> class 1 (low-mean, low-SD, unimodal)
     *     class3/ -> class 2 (high-mean, high-SD; includes bimodal instances)
     *     class4/ -> class 3 (low-mean, high-SD; includes bimodal instances)
     *
     * Per Burke et al. 2010, Section 5.1: 5 instances per class, 4 classes, 20 original.
     * class3 and class4 also contain 5 generated bimodal instances each (2026-05-09),
     * bringing total training instances to 30 (10 per bimodal class).
     *
     * The bimodal instances (binpack_gen_bimodal_*.txt) model the dual-peak item-size
     * distributions found in testdual4 and testdual8, enabling the GP to learn heuristics
     * that perform well on both unimodal and bimodal test instances.
     */
    @SuppressWarnings("unchecked")
    private static List<BPPInstance>[] loadTrainingSetByClass(String baseDir) {
        List<BPPInstance>[] trainingByClass = new ArrayList[NUM_CLASSES];
        for (int c = 0; c < NUM_CLASSES; c++) {
            trainingByClass[c] = new ArrayList<>();
        }

        File base = new File(baseDir);
        if (!base.exists() || !base.isDirectory()) {
            System.err.println("Warning: Training directory not found: " + baseDir);
            return trainingByClass;
        }

        for (int c = 1; c <= NUM_CLASSES; c++) {
            File classDir = new File(base, "class" + c);
            if (classDir.exists() && classDir.isDirectory()) {
                File[] files = classDir.listFiles((d, name) -> name.endsWith(".txt"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            BPPInstance instance = BPPInstance.load(file.getPath());
                            trainingByClass[c - 1].add(instance);
                        } catch (IOException e) {
                            System.err.println("Warning: Could not load " + file.getName());
                        }
                    }
                }
            }
        }

        return trainingByClass;
    }

    /**
     * Get human-readable class name.
     */
    private static String getClassName(int cls) {
        switch (cls) {
            case 0: return "high-mean, low-SD";
            case 1: return "low-mean, low-SD";
            case 2: return "high-mean, high-SD";
            case 3: return "low-mean, high-SD";
            default: return "unknown";
        }
    }

    /**
     * Test mode: load saved heuristic and solve the given instance.
     */
    private static void runTestMode(String[] args) {
        String instancePath = null;
        String solutionPath = null;
        long maxTime = DEFAULT_TIME_LIMIT;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-s") && i + 1 < args.length) {
                instancePath = args[i + 1];
                i++;
            } else if (args[i].equals("-o") && i + 1 < args.length) {
                solutionPath = args[i + 1];
                i++;
            } else if (args[i].equals("-t") && i + 1 < args.length) {
                try {
                    maxTime = Long.parseLong(args[i + 1]);
                    i++;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid time value: " + args[i + 1]);
                    printUsage();
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
            // Load the saved heuristic
            File heuristicFile = new File(SERIALIZED_HEURISTIC);
            if (!heuristicFile.exists()) {
                System.err.println("Error: Heuristic file not found: " + SERIALIZED_HEURISTIC);
                System.err.println("Please run 'java -cp out Main --train' first.");
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

            // Load the test instance
            System.out.println("Loading instance: " + instancePath);
            BPPInstance instance = BPPInstance.load(instancePath);
            System.out.println("Instance: " + instance.getName() +
                             ", Items: " + instance.getItemCount() +
                             ", Capacity: " + instance.getCapacity());

            // Solve test instance with time limit
            System.out.println("Solving instance (time limit: " + maxTime + "ms)...");
            long startTime = System.currentTimeMillis();

            BPPSolver solver = new BPPSolver();
            Solution solution = solver.solve(instance, heuristic);

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("Solved in " + elapsed + "ms");

            // Calculate L2 bound for reference
            double l2Bound = L2BoundCalculator.calculate(instance);
            solution.setL2Bound(l2Bound);

            System.out.println("Solution: " + solution.getBinCount() + " bins used");
            System.out.println("L2 lower bound: " + (int) l2Bound);
            System.out.println("Ratio: " + String.format("%.4f", (double) solution.getBinCount() / l2Bound));

            // Save solution
            System.out.println("Saving solution to: " + solutionPath);
            solution.save(solutionPath);
            System.out.println("Done!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (ClassNotFoundException e) {
            System.err.println("Error: Heuristic class not found during deserialization.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Print usage information.
     */
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Training (local, no time limit):");
        System.out.println("    java -cp out Main --train");
        System.out.println();
        System.out.println("  Testing (submit, 10-second limit):");
        System.out.println("    java -cp out Main -s instance_file -o solution_file [-t max_time]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -s instance_file    Path to the BPP instance file");
        System.out.println("  -o solution_file    Path to save the solution");
        System.out.println("  -t max_time         Maximum time in milliseconds (default: 10000)");
        System.out.println("  -h, --help          Show this help message");
    }
}
