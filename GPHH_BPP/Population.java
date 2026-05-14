import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Population of GP individuals.
 */
public class Population implements Serializable {
    
    private List<Individual> individuals;
    
    public Population() {
        this.individuals = new ArrayList<>();
    }
    
    public Population(int initialSize) {
        this.individuals = new ArrayList<>(initialSize);
    }
    
    /**
     * Add an individual to the population.
     * @param individual Individual to add
     */
    public void add(Individual individual) {
        individuals.add(individual);
    }
    
    /**
     * Get the best individual (lowest fitness).
     * @return Best individual
     */
    public Individual getBest() {
        if (individuals.isEmpty()) {
            return null;
        }
        Individual best = individuals.get(0);
        for (Individual ind : individuals) {
            if (ind.compareToLexicographic(best) < 0) {
                best = ind;
            }
        }
        return best;
    }
    
    /**
     * Get the individual at the given index.
     * @param index Index
     * @return Individual
     */
    public Individual get(int index) {
        return individuals.get(index);
    }
    
    /**
     * Get the size of the population.
     * @return Size
     */
    public int size() {
        return individuals.size();
    }
    
    /**
     * Check if population is empty.
     * @return true if empty
     */
    public boolean isEmpty() {
        return individuals.isEmpty();
    }
    
    /**
     * Sort individuals by fitness (ascending).
     */
    public void sort() {
        Collections.sort(individuals, (a, b) -> a.compareToLexicographic(b));
    }
    
    /**
     * Get all individuals.
     * @return List of individuals
     */
    public List<Individual> getIndividuals() {
        return new ArrayList<>(individuals);
    }
    
    /**
     * Clear the population.
     */
    public void clear() {
        individuals.clear();
    }
}
