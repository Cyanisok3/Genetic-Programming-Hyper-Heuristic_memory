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

    public static final int DEFAULT_CAPACITY = 100;

    private String name;
    private int[] items;
    private int capacity;
    private int problemClass;

    private static Map<String, Double> l2BoundCache;

    public BPPInstance(String name, int[] items, int capacity) {
        this(name, items, capacity, -1);
    }

    public BPPInstance(String name, int[] items, int capacity, int problemClass) {
        this.name = name;
        this.items = items;
        this.capacity = capacity;
        this.problemClass = problemClass;
    }

    public static BPPInstance load(String filePath) throws IOException {
        int detectedClass = detectClassFromPath(filePath);
        List<Integer> itemList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    itemList.add(Integer.parseInt(line));
                } catch (NumberFormatException e) {
                    // skip non-numeric lines
                }
                if (firstLine) firstLine = false;
            }
        }

        int[] items = new int[itemList.size()];
        for (int i = 0; i < itemList.size(); i++) {
            items[i] = itemList.get(i);
        }
        String name = extractName(filePath);
        return new BPPInstance(name, items, DEFAULT_CAPACITY, detectedClass);
    }

    private static int detectClassFromPath(String filePath) {
        String pathLower = filePath.toLowerCase();
        if (pathLower.contains("/class1/") || pathLower.contains("\\class1\\")) return 0;
        if (pathLower.contains("/class2/") || pathLower.contains("\\class2\\")) return 1;
        if (pathLower.contains("/class3/") || pathLower.contains("\\class3\\")) return 2;
        if (pathLower.contains("/class4/") || pathLower.contains("\\class4\\")) return 3;
        return -1;
    }

    private static String extractName(String filePath) {
        String[] parts = filePath.replace('\\', '/').split("/");
        if (parts.length >= 2) {
            String dir = parts[parts.length - 2];
            String file = parts[parts.length - 1];
            int dotIndex = file.lastIndexOf('.');
            if (dotIndex > 0) file = file.substring(0, dotIndex);
            return dir + "_" + file;
        }
        String fileName = parts[parts.length - 1];
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) fileName = fileName.substring(0, dotIndex);
        return fileName;
    }

    public String getName() {
        return name;
    }

    public int[] getItems() {
        return items;
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

    @Override
    public String toString() {
        return name + " (" + items.length + " items, capacity " + capacity + ")";
    }

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
                    try {
                        double l2 = Double.parseDouble(parts[2].trim());
                        String nameKey = parts[0].trim() + "_" + parts[1].trim().replace(".txt", "");
                        l2BoundCache.put(nameKey, l2);
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

    public double getVerifiedL2Bound() {
        if (l2BoundCache != null) {
            Double cached = l2BoundCache.get(name);
            if (cached != null) return cached;
        }
        return L2BoundCalculator.calculate(this);
    }

    public void setVerifiedL2Bound(double bound) {
        if (l2BoundCache == null) l2BoundCache = new HashMap<>();
        l2BoundCache.put(name, bound);
    }
}
