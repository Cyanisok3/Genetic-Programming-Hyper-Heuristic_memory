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

    public Individual get(int index) {
        return individuals.get(index);
    }

    public int size() {
        return individuals.size();
    }

    public boolean isEmpty() {
        return individuals.isEmpty();
    }

    public void sort() {
        Collections.sort(individuals, (a, b) -> a.compareToLexicographic(b));
    }

    public List<Individual> getIndividuals() {
        return new ArrayList<>(individuals);
    }

    public void clear() {
        individuals.clear();
    }
}
