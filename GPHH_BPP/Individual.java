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

    /**
     * Epsilon for grouping similar fitness values into the same bucket.
     * Two individuals with fitness difference <= EPSILON are considered equally fit,
     * allowing tree size to act as the true tiebreaker.
     */
    private static final double FITNESS_EPSILON = 1e-7;

    /**
     * Lexicographic comparison: first by raw fitness bucket, then by tree size (smaller wins).
     * Fitness is bucketed to EPSILON granularity so that tree size meaningfully breaks ties
     * between individuals with nearly identical fitness.
     * @return negative if this is better, positive if other is better
     */
    public int compareToLexicographic(Individual other) {
        double thisBucket = Math.floor(this.fitness / FITNESS_EPSILON);
        double otherBucket = Math.floor(other.fitness / FITNESS_EPSILON);
        int bucketCmp = Double.compare(thisBucket, otherBucket);
        if (bucketCmp != 0) return bucketCmp;
        return Integer.compare(this.getTree().getSize(), other.getTree().getSize());
    }

    @Override
    public String toString() {
        return "Individual(fitness=" + String.format("%.6f", fitness) + ", tree=" + heuristic + ")";
    }
}
