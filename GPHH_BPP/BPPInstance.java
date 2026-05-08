import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a bin packing problem instance.
 */
public class BPPInstance {
    
    public static final int DEFAULT_CAPACITY = 100;  // Dual-distribution dataset uses capacity=100
    
    private String name;
    private int[] items;
    private int capacity;
    private int problemClass;  // 0-3 for 4-class training, -1 for unknown

    // Static cache of L2 bounds loaded from CSV (keyed by instance name)
    private static Map<String, Double> l2BoundCache = null;
    
    public BPPInstance(String name, int[] items, int capacity) {
        this(name, items, capacity, -1);
    }

    public BPPInstance(String name, int[] items, int capacity, int problemClass) {
        this.name = name;
        this.items = items;
        this.capacity = capacity;
        this.problemClass = problemClass;
    }
    
    /**
     * Load a BPP instance from a file.
     * Format: one item size per line (integers).
     * Automatically detects problem class from directory path:
     *   class1/ -> class 0 (high mean, low S.D.)
     *   class2/ -> class 1 (low mean, low S.D.)
     *   class3/ -> class 2 (high mean, high S.D.)
     *   class4/ -> class 3 (low mean, high S.D.)
     * @param filePath Path to the instance file
     * @return BPPInstance
     * @throws IOException if file cannot be read
     */
    public static BPPInstance load(String filePath) throws IOException {
        int detectedClass = detectClassFromPath(filePath);
        List<Integer> itemList = new ArrayList<>();
        String name = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (firstLine) {
                    name = extractName(filePath);
                    try {
                        int capacity = Integer.parseInt(line);
                        if (capacity > 100) {
                            // This is capacity
                        } else {
                            itemList.add(capacity);
                        }
                    } catch (NumberFormatException e) {
                        try {
                            itemList.add(Integer.parseInt(line));
                        } catch (NumberFormatException e2) {
                            // Skip non-numeric lines
                        }
                    }
                    firstLine = false;
                } else {
                    try {
                        itemList.add(Integer.parseInt(line));
                    } catch (NumberFormatException e) {
                        // Skip non-numeric lines
                    }
                }
            }
        }

        int[] items = new int[itemList.size()];
        for (int i = 0; i < itemList.size(); i++) {
            items[i] = itemList.get(i);
        }

        return new BPPInstance(name, items, DEFAULT_CAPACITY, detectedClass);
    }

    /**
     * Detect problem class from file path.
     * Maps directory names to class indices (0-3).
     */
    private static int detectClassFromPath(String filePath) {
        String pathLower = filePath.toLowerCase();
        if (pathLower.contains("/class1/") || pathLower.contains("\\class1\\")) return 0;
        if (pathLower.contains("/class2/") || pathLower.contains("\\class2\\")) return 1;
        if (pathLower.contains("/class3/") || pathLower.contains("\\class3\\")) return 2;
        if (pathLower.contains("/class4/") || pathLower.contains("\\class4\\")) return 3;
        return -1; // Unknown class
    }
    
    /**
     * Extract instance name from file path.
     * For paths like "dualdistribution/test/testdual0/binpack0.txt",
     * returns "testdual0_binpack0".
     */
    private static String extractName(String filePath) {
        String[] parts = filePath.replace('\\', '/').split("/");
        // Get the last two parts (directory and filename without extension)
        if (parts.length >= 2) {
            String dir = parts[parts.length - 2];
            String file = parts[parts.length - 1];
            int dotIndex = file.lastIndexOf('.');
            if (dotIndex > 0) {
                file = file.substring(0, dotIndex);
            }
            return dir + "_" + file;
        } else {
            // Fallback
            String fileName = parts[parts.length - 1];
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex);
            }
            return fileName;
        }
    }
    
    // Getters
    
    public String getName() {
        return name;
    }
    
    public int[] getItems() {
        return items;
    }
    
    public int getItem(int index) {
        return items[index];
    }
    
    public int getItemCount() {
        return items.length;
    }
    
    public int getCapacity() {
        return capacity;
    }

    public int getProblemClass() {
        return problemClass;
    }
    
    public int getTotalSize() {
        int sum = 0;
        for (int item : items) {
            sum += item;
        }
        return sum;
    }
    
    /**
     * Count items larger than half the bin capacity.
     * Used for L2 lower bound calculation.
     */
    public int countItemsLargerThan(int threshold) {
        int count = 0;
        for (int item : items) {
            if (item > threshold) {
                count++;
            }
        }
        return count;
    }
    
    @Override
    public String toString() {
        return name + " (" + items.length + " items, capacity " + capacity + ")";
    }

    /**
     * Load verified L2 lower bounds from the teacher's provided CSV file.
     * Caches results after first load.
     * @param csvPath absolute path to the L2 bounds CSV file
     * @return Map from instance name (e.g. "testdual0_binpack0") to L2 bound
     */
    public static Map<String, Double> loadL2BoundsFromCSV(String csvPath) {
        if (l2BoundCache != null) return l2BoundCache;
        l2BoundCache = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String testSet = parts[0].trim();
                    String instanceFile = parts[1].trim();
                    try {
                        double l2 = Double.parseDouble(parts[2].trim());
                        String name = testSet + "_" + instanceFile.replace(".txt", "");
                        l2BoundCache.put(name, l2);
                    } catch (NumberFormatException e) {
                        // skip malformed line
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load L2 bounds from " + csvPath + ": " + e.getMessage());
        }
        return l2BoundCache;
    }

    /**
     * Get the verified L2 lower bound for this instance from the loaded CSV.
     * Falls back to L2BoundCalculator.calculate() if CSV not loaded or instance not found.
     */
    public double getVerifiedL2Bound() {
        if (l2BoundCache != null) {
            Double cached = l2BoundCache.get(name);
            if (cached != null) return cached;
        }
        return L2BoundCalculator.calculate(this);
    }
}
