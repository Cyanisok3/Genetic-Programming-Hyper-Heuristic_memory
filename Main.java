import java.io.*;
import java.nio.file.*;
import java.util.*;

// Main entry point for training and testing GP-based hyper-heuristics on bin packing.
public class Main {

    // Fixed seeds for reproducible multi-tree ensemble training.
    // Each tree is trained with a distinct seed, ensuring deterministic diversity
    // ...and making experimental results reproducible.
    private static final long[] ENSEMBLE_SEEDS = {
        1716000000000L,
        171600000999L,
        171600001998L,
        171600002997L,
        171600003996L
    };

    // Parses command-line arguments and dispatches to training or testing mode.
    public static void main(String[] args) throws Exception {
        boolean trainMode = false;
        String instancePath = null;
        String solutionPath = null;
        long timeLimit = 10000;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--train")) trainMode = true;
            else if (args[i].equals("-s")) instancePath = args[++i];
            else if (args[i].equals("-o")) solutionPath = args[++i];
            else if (args[i].equals("-t")) timeLimit = Long.parseLong(args[++i]);
        }

        if (trainMode) {
            runTrainingFlow();
        } else if (instancePath != null && solutionPath != null) {
            runTestingFlow(instancePath, solutionPath, timeLimit);
        } else {
            System.out.println("Usage Test: java GPHH20617232 -s <instance_file> -o <solution_file> [-t max_time]");
            System.out.println("Usage Train: java GPHH20617232 --train");
        }
    }

    // Loads an instance, runs the ensemble of trained trees, and writes the solution file.
    private static void runTestingFlow(String instancePath, String solutionPath, long timeLimit) throws Exception {
        System.out.println("Loading instance: " + instancePath);
        InstanceData data = parseInstance(instancePath);

        // Load all serialized GP trees for ensemble voting.
        File dir = new File("best_heuristics");
        List<GPNode> forest = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles((d, name) -> name.endsWith(".ser"))) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                    forest.add((GPNode) ois.readObject());
                }
            }
        }
        if (forest.isEmpty()) throw new RuntimeException("No .ser files found in best_heuristics/ folder! Run --train first.");

        System.out.println("Loaded " + forest.size() + " heuristics for Ensemble Voting.");

        long start = System.currentTimeMillis();
        List<Bin> resultBins = BPPEnv.solveEnsemble(data.items, data.capacity, forest);
        long elapsed = System.currentTimeMillis() - start;

        int obj = resultBins.size();

        long sum = 0; for(int s : data.items) sum += s;
        long l2Approx = (long) Math.ceil((double)sum / data.capacity);

        System.out.println("Time taken: " + elapsed + "ms");
        System.out.println("Total Bins (objective_value): " + obj + " (L2 lower bound roughly: " + l2Approx + ")");

        // Write solution in the required format.
        try (PrintWriter pw = new PrintWriter(new FileWriter(solutionPath))) {
            File instFile = new File(instancePath);
            String setName = instFile.getParentFile().getName();
            String instName = instFile.getName().replace(".txt", "");

            // Line 1: SetName_InstanceName
            pw.println(setName + "_" + instName);

            // Line 2: obj= objective_value L2_bound (tab-separated)
            pw.println("obj=\t" + obj + "\t" + l2Approx);

            // Line 3+: each line lists item indices (space-separated) in one bin
            for (Bin bin : resultBins) {
                if (bin.itemIndices.isEmpty()) continue;

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bin.itemIndices.size(); i++) {
                    sb.append(bin.itemIndices.get(i));
                    if (i < bin.itemIndices.size() - 1) sb.append(" ");
                }
                pw.println(sb.toString());
            }
        }
        System.out.println("Solution strictly saved to: " + solutionPath);
    }

    // Trains multiple GP trees and serializes them for later use in testing.
    private static void runTrainingFlow() throws Exception {
        System.out.println("=== Starting Training Phase ===");

        // Load all training instances from the dualdistribution/train directory.
        List<int[]> trainData = new ArrayList<>();
        int capacity = 100;

        File trainDir = new File("dualdistribution/train");
        if(trainDir.exists()) {
            Files.walk(Paths.get(trainDir.getPath()))
                 .filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".txt"))
                 .forEach(p -> {
                     try {
                         InstanceData d = parseInstance(p.toString());
                         trainData.add(d.items);
                     } catch(Exception e) { }
                 });
        }

        if(trainData.isEmpty()) {
            System.out.println("Generating synthetic dual-distribution dummy data for fallback test...");
            trainData.add(generateDummyDualDist(500));
        }

        System.out.println("Total training instances loaded: " + trainData.size());

        new File("best_heuristics").mkdirs();

        // Train multiple trees (ensemble) with controlled fixed seeds.
        // Each tree explores a different region of the search space, producing diverse
        // heuristics that complement each other at test time via majority voting.
        int ensembleSize = 5;
        for (int i = 0; i < ensembleSize; i++) {
            long seed = ENSEMBLE_SEEDS[i];
            System.out.println("\n--- Training Tree " + (i+1) + "/" + ensembleSize + " (Seed: " + seed + ") ---");
            GPNode bestTree = BPPEnv.trainForest(trainData, capacity, seed, 60); // 60 generations

            String outPath = "best_heuristics/tree_model_" + i + ".ser";
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outPath))) {
                oos.writeObject(bestTree);
                System.out.println("Saved highly optimized Tree to " + outPath);
            }
        }
        System.out.println("\nTraining complete! Now run test mode.");
    }

    // Holds parsed instance data: bin capacity and the list of item sizes.
    static class InstanceData { int capacity; int[] items; }

    // Reads an instance file and returns capacity (fixed at 100) and item sizes.
    private static InstanceData parseInstance(String path) throws Exception {
        Scanner sc = new Scanner(new File(path));
        List<Integer> itemList = new ArrayList<>();

        while (sc.hasNextInt()) {
            itemList.add(sc.nextInt());
        }
        sc.close();

        InstanceData data = new InstanceData();
        data.capacity = 100; // bin capacity is always 100 as per competition rules
        data.items = new int[itemList.size()];

        for (int i = 0; i < itemList.size(); i++) {
            data.items[i] = itemList.get(i);
        }

        return data;
    }

    // Generates synthetic dual-distribution data as a fallback for testing.
    private static int[] generateDummyDualDist(int n) {
        int[] items = new int[n];
        Random r = new Random();
        for(int i=0; i<n; i++) {
            // Roughly simulates a bimodal distribution: large or small items.
            items[i] = r.nextBoolean() ? (70 + r.nextInt(20)) : (20 + r.nextInt(15));
        }
        return items;
    }
}
