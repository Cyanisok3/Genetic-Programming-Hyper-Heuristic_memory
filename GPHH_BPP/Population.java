import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Population of GP individuals.
 */
public class Population implements Serializable {
    
    private List<Individual> individuals;
    private Individual elite;
    
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
            if (ind.getFitness() < best.getFitness()) {
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
     * Set the elite individual.
     * @param elite Elite individual
     */
    public void setElite(Individual elite) {
        this.elite = elite;
    }
    
    /**
     * Get the elite individual.
     * @return Elite
     */
    public Individual getElite() {
        return elite;
    }
    
    /**
     * Add the elite to this population.
     */
    public void addElite() {
        if (elite != null) {
            individuals.add(elite.copy());
        }
    }
    
    /**
     * Sort individuals by fitness (ascending).
     */
    public void sort() {
        Collections.sort(individuals, (a, b) -> Double.compare(a.getFitness(), b.getFitness()));
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
