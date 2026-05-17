import java.io.Serializable;

/**
 * Represents a single GP individual (heuristic).
 */
public class Individual implements Serializable {

    private Heuristic heuristic;
    private double fitness;

    public Individual(Heuristic heuristic) {
        this.heuristic = heuristic;
        this.fitness = Double.POSITIVE_INFINITY;
    }

    public Individual(Heuristic heuristic, double fitness) {
        this.heuristic = heuristic;
        this.fitness = fitness;
    }

    public Individual(GPNode root) {
        this.heuristic = new Heuristic(root);
        this.fitness = Double.POSITIVE_INFINITY;
    }

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

    public int compareToLexicographic(Individual other) {
        int fitnessCmp = Double.compare(this.fitness, other.fitness);
        if (fitnessCmp != 0) return fitnessCmp;
        return Integer.compare(this.getTree().getSize(), other.getTree().getSize());
    }

    @Override
    public String toString() {
        return "Individual(fitness=" + String.format("%.6f", fitness) + ", tree=" + heuristic + ")";
    }
}
