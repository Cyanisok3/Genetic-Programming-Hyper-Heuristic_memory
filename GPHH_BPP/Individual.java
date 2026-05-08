import java.io.Serializable;

/**
 * Represents a single GP individual (heuristic).
 */
public class Individual implements Serializable {
    
    private Heuristic heuristic;
    private double fitness;
    
    public Individual(Heuristic heuristic) {
        this.heuristic = heuristic;
        this.fitness = Double.POSITIVE_INFINITY;  // Default worst fitness
    }
    
    public Individual(GPNode root) {
        this.heuristic = new Heuristic(root);
        this.fitness = Double.POSITIVE_INFINITY;
    }
    
    /**
     * Create a copy of this individual.
     * @return Deep copy
     */
    public Individual copy() {
        Individual copy = new Individual(heuristic.copy());
        copy.fitness = this.fitness;
        return copy;
    }
    
    // Getters and setters
    
    public Heuristic getHeuristic() {
        return heuristic;
    }
    
    public GPNode getTree() {
        return heuristic.getRoot();
    }
    
    public double getFitness() {
        return fitness;
    }
    
    public void setFitness(double fitness) {
        this.fitness = fitness;
    }
    
    @Override
    public String toString() {
        return "Individual(fitness=" + String.format("%.6f", fitness) + ", tree=" + heuristic + ")";
    }
}
