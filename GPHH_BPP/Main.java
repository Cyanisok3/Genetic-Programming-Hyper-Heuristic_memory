import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    // Training configuration: weight classes by problem difficulty (high S.D. instances are harder)
    // Class 0-1: low S.D.=5, Class 2-3: high S.D.=10
    // Weights ensure high-S.D. classes get more evaluation attention during training
    public static final double[] CLASS_WEIGHTS = {1.0, 1.0, 1.5, 1.5};
    // Generate training instances from Gaussian distributions (per Burke et al. 2010, Algorithm 2)
    public static final boolean GENERATE_TRAINING_DATA = true;
    // Increased training data for better generalization
    public static final int TRAINING_INSTANCES_PER_CLASS = 10;  // Increased from 5
    public static final int TRAINING_ITEMS_PER_INSTANCE = 1000; // Increased from 500
    // Bimodal training: simulate test sets 5-8 with 50% mix of two distributions
    public static final boolean ADD_BIMODAL_TRAINING = false; // Disabled for academic compliance
    public static final int BIMODAL_INSTANCES_PER_MIX = 5; // 5 instances per bimodal mix
    public static final int BIMODAL_ITEMS_PER_INSTANCE = 1000;
    // Bimodal configurations: mix of two Gaussian distributions (per test sets 5-8)
    // Format: {dist1_mean, dist1_sd, dist2_mean, dist2_sd}
    public static final double[][] BIMODAL_PARAMS = {
        {50.0, 5.0, 35.0, 5.0},   // Bimodal 0: mean 50+35 (like test set 5)
        {50.0, 5.0, 30.0, 5.0},   // Bimodal 1: mean 50+30 (like test set 6)
        {50.0, 5.0, 25.0, 5.0},   // Bimodal 2: mean 50+25 (like test set 7)
        {50.0, 10.0, 35.0, 5.0},  // Bimodal 3: mean 50+35 high-low S.D. (like test set 9)
    };
    // Class parameters: (mean, standardDeviation) per Burke et al. 2010 Table II
    public static final double[][] CLASS_PARAMS = {
        {50.0, 5.0},   // Class 0: high mean, low S.D.
        {33.0, 5.0},   // Class 1: low mean, low S.D.
        {50.0, 10.0},  // Class 2: high mean, high S.D.
        {33.0, 10.0}   // Class 3: low mean, high S.D.
    };
    public static final long TRAINING_SEED = 20617232; // Fixed seed for reproducibility

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
     * Uses class-aware training strategy from Burke et al. 2010.
     * No time limit.
     */
    private static void runTrainingMode() {
        System.out.println("=== Training Mode (Class-Aware) ===");

        // Load verified L2 bounds from teacher's CSV for relative fitness
        String l2CSV = "l2_bounds_testdual_instances_0_4_8.csv";
        java.io.File csvFile = new java.io.File(l2CSV);
        if (csvFile.exists()) {
            BPPInstance.loadL2BoundsFromCSV(csvFile.getAbsolutePath());
            System.out.println("Loaded L2 bounds from: " + csvFile.getAbsolutePath());
        } else {
            System.out.println("Warning: L2 bounds CSV not found at " + csvFile.getAbsolutePath() + ", will use computed L2 bounds.");
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
        System.out.println("Total: " + totalInstances + " instances (" +
            TRAINING_INSTANCES_PER_CLASS + " unimodal + " +
            (ADD_BIMODAL_TRAINING ? BIMODAL_PARAMS.length * BIMODAL_INSTANCES_PER_MIX : 0) + " bimodal per class 2)");

        if (totalInstances == 0) {
            System.err.println("Error: No training instances found.");
            System.exit(1);
        }

        System.out.println("Evolving heuristic (no time limit)...");
        long startTime = System.currentTimeMillis();

        // Use a fixed seed for reproducibility
        GeneticProgramming gp = new GeneticProgramming(TRAINING_SEED);
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
     *     class1/ -> class 0 (high mean, low S.D.)
     *     class2/ -> class 1 (low mean, low S.D.)
     *     class3/ -> class 2 (high mean, high S.D.)
     *     class4/ -> class 3 (low mean, high S.D.)
     *
     * IMPORTANT: Training data is generated from Gaussian distributions (Burke et al. 2010, Algorithm 2).
     * This ensures coverage of all 4 problem classes including high S.D. distributions.
     */
    @SuppressWarnings("unchecked")
    private static List<BPPInstance>[] loadTrainingSetByClass(String baseDir) {
        List<BPPInstance>[] trainingByClass = new ArrayList[NUM_CLASSES];
        for (int c = 0; c < NUM_CLASSES; c++) {
            trainingByClass[c] = new ArrayList<>();
        }

        if (GENERATE_TRAINING_DATA) {
            // Generate training instances from Gaussian distributions (per Burke et al. 2010)
            generateTrainingInstances(trainingByClass);
        } else {
            // Fallback: load from files
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
        }

        return trainingByClass;
    }

    /**
     * Generate training instances from Gaussian distributions.
     * Per Burke et al. 2010, Algorithm 2: piece sizes are random integers from Gaussian distribution.
     * Class 0-1: S.D.=5 (low variance), Class 2-3: S.D.=10 (high variance)
     */
    private static void generateTrainingInstances(List<BPPInstance>[] trainingByClass) {
        Random rand = new Random(TRAINING_SEED);

        // Generate unimodal instances (per Burke 2010)
        for (int c = 0; c < NUM_CLASSES; c++) {
            double mean = CLASS_PARAMS[c][0];
            double sd = CLASS_PARAMS[c][1];
            String className = getClassName(c);

            System.out.println("Generating class " + c + " (" + className + "): mean=" + mean + ", S.D.=" + sd);

            for (int i = 0; i < TRAINING_INSTANCES_PER_CLASS; i++) {
                int[] items = generateGaussianItems(rand, mean, sd, TRAINING_ITEMS_PER_INSTANCE);
                String name = "gen_class" + c + "_binpack" + i;
                BPPInstance instance = new BPPInstance(name, items, 100, c);
                trainingByClass[c].add(instance);
            }
        }

        // Generate bimodal instances for better generalization (simulate test sets 5-8, 9-12)
        if (ADD_BIMODAL_TRAINING) {
            System.out.println("\nGenerating bimodal instances for generalization...");
            for (int m = 0; m < BIMODAL_PARAMS.length; m++) {
                double[] params = BIMODAL_PARAMS[m];
                double mean1 = params[0], sd1 = params[1];
                double mean2 = params[2], sd2 = params[3];

                System.out.println("  Bimodal mix " + m + ": (" + mean1 + "," + sd1 + ") + (" + mean2 + "," + sd2 + ")");

                for (int i = 0; i < BIMODAL_INSTANCES_PER_MIX; i++) {
                    int[] items = generateBimodalItems(rand, mean1, sd1, mean2, sd2, BIMODAL_ITEMS_PER_INSTANCE);
                    String name = "gen_bimodal" + m + "_binpack" + i;
                    // Assign to class 2 (high S.D.) for class weight purposes
                    BPPInstance instance = new BPPInstance(name, items, 100, 2);
                    trainingByClass[2].add(instance);
                }
            }
        }
    }

    /**
     * Generate items from a bimodal distribution (mix of two Gaussians).
     * Each item has 50% chance of coming from each distribution.
     */
    private static int[] generateBimodalItems(Random rand, double mean1, double sd1,
                                              double mean2, double sd2, int count) {
        int[] items = new int[count];
        for (int i = 0; i < count; i++) {
            double value;
            if (rand.nextDouble() < 0.5) {
                value = mean1 + sd1 * rand.nextGaussian();
            } else {
                value = mean2 + sd2 * rand.nextGaussian();
            }
            // Truncate to valid range [1, 99]
            value = Math.max(1, Math.min(99, value));
            items[i] = (int) Math.round(value);
        }
        return items;
    }

    /**
     * Generate items from a truncated Gaussian distribution.
     * Values are clamped to [1, 99] to ensure valid item sizes.
     */
    private static int[] generateGaussianItems(Random rand, double mean, double sd, int count) {
        int[] items = new int[count];
        for (int i = 0; i < count; i++) {
            double value = mean + sd * rand.nextGaussian();
            // Truncate to valid range [1, capacity-1]
            value = Math.max(1, Math.min(99, value));
            items[i] = (int) Math.round(value);
        }
        return items;
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
