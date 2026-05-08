import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete solution to a BPP instance.
 */
public class Solution {
    
    private String instanceName;
    private List<Bin> bins;
    private int objective;  // Number of bins used (or total fullness depending on format)
    private double l2Bound;
    
    public Solution(String instanceName, List<Bin> bins) {
        this.instanceName = instanceName;
        this.bins = bins;
        this.objective = calculateObjective();
        this.l2Bound = 0.0;
    }
    
    /**
     * Calculate the objective value (number of bins).
     */
    private int calculateObjective() {
        return bins.size();
    }
    
    /**
     * Save solution to a file in the required format.
     * Format:
     * instance_name
     * obj= objective_value L2_bound
     * item_index in bin0
     * item_index in bin1
     * ...
     */
    public void save(String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // First line: instance name
            writer.write(instanceName);
            writer.newLine();
            
            // Second line: objective and L2 bound
            writer.write("obj=\t" + objective + "\t" + (int) l2Bound);
            writer.newLine();
            
            // Each bin: items in that bin
            for (Bin bin : bins) {
                List<Integer> items = bin.getItems();
                if (items.isEmpty()) {
                    writer.newLine();
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < items.size(); i++) {
                        if (i > 0) {
                            sb.append(" ");
                        }
                        sb.append(items.get(i));
                    }
                    writer.write(sb.toString());
                    writer.newLine();
                }
            }
        }
    }
    
    /**
     * Save solution in the sample output format (tab-separated).
     */
    public void saveFormat2(String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // First line: instance name
            writer.write(instanceName);
            writer.newLine();
            
            // Second line: obj= value (no L2 bound)
            writer.write("obj=\t" + objective);
            writer.newLine();
            
            // Each bin: items in that bin
            for (Bin bin : bins) {
                List<Integer> items = bin.getItems();
                if (items.isEmpty()) {
                    writer.newLine();
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < items.size(); i++) {
                        if (i > 0) {
                            sb.append(" ");
                        }
                        sb.append(items.get(i));
                    }
                    writer.write(sb.toString());
                    writer.newLine();
                }
            }
        }
    }
    
    // Getters
    
    public String getInstanceName() {
        return instanceName;
    }
    
    public List<Bin> getBins() {
        return new ArrayList<>(bins);
    }
    
    public int getBinCount() {
        return bins.size();
    }
    
    public int getObjective() {
        return objective;
    }
    
    public double getL2Bound() {
        return l2Bound;
    }
    
    public void setL2Bound(double l2Bound) {
        this.l2Bound = l2Bound;
    }

    public void setInstanceName(String name) {
        this.instanceName = name;
    }
    
    /**
     * Get total fullness (sum of all items placed).
     */
    public int getTotalFullness() {
        int sum = 0;
        for (Bin bin : bins) {
            sum += bin.getFullness();
        }
        return sum;
    }
    
    @Override
    public String toString() {
        return "Solution(" + instanceName + ", " + bins.size() + " bins, objective=" + objective + ")";
    }
}
