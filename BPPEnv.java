import java.util.*;
import java.util.concurrent.ForkJoinPool;

// Represents a bin with a fixed capacity and tracks how full it is.
class Bin {
    int capacity, fullness;
    List<Integer> itemIndices = new ArrayList<>();
    public Bin(int capacity) { this.capacity = capacity; this.fullness = 0; }
    public int getEmptiness() { return capacity - fullness; }
    public boolean canFit(int item) { return fullness + item <= capacity; }
    public void addItem(int item, int origIndex) { fullness += item; itemIndices.add(origIndex); }
}

// Wraps a GP tree with its fitness value for the evolutionary algorithm.
class Individual implements Comparable<Individual> {
    GPNode tree;
    double fitness; // lower is better
    public Individual(GPNode tree) { this.tree = tree; }

    @Override public int compareTo(Individual o) {
        if (Math.abs(this.fitness - o.fitness) > 1e-6) return Double.compare(this.fitness, o.fitness);
        return Integer.compare(this.tree.getSize(), o.tree.getSize()); // tie-breaker: prefer smaller trees
    }
}

// Bin packing problem environment: fitness evaluation, ensemble solving, and evolution.
public class BPPEnv {
    // Evaluates a GP tree on a single instance using state-compressed bin packing.
    // Uses [Falkenauer fitness]: binCount - (sum of squared fullness ratios / binCount).
    public static double evaluateFitness(int[] items, int capacity, GPNode tree) {
        int binCount = 1;
        // empCounts maps remaining space to how many bins have that remaining space.
        int[] empCounts = new int[capacity + 1];
        empCounts[capacity] = 1; // start with one empty bin

        for (int S : items) {
            int bestEmptiness = -1;
            double bestScore = Double.NEGATIVE_INFINITY;

            // Only iterate over distinct emptiness states to reduce computation time.
            for (int e = S; e <= capacity; e++) {
                if (empCounts[e] > 0) {
                    double score = tree.evaluate(S, e, capacity, -1.0);
                    if (score > bestScore) {
                        bestScore = score;
                        bestEmptiness = e;
                    }
                }
            }

            // Score for opening a new bin (isNew = 1.0).
            double newBinScore = tree.evaluate(S, capacity, capacity, 1.0);

            if (newBinScore > bestScore || bestEmptiness == -1) {
                binCount++;
                empCounts[capacity - S]++;
            } else {
                empCounts[bestEmptiness]--;
                empCounts[bestEmptiness - S]++;
            }
        }

        double sumSquares = 0;
        for (int e = 0; e <= capacity; e++) {
            if (empCounts[e] > 0) {
                double fullnessRatio = (double) (capacity - e) / capacity;
                sumSquares += empCounts[e] * (fullnessRatio * fullnessRatio);
            }
        }
        return binCount - (sumSquares / binCount);
    }

    // Solves an instance by majority voting across a forest of trained GP trees.
    // Each tree independently evaluates all candidate bins and votes for one choice (including "open a new bin").
    public static List<Bin> solveEnsemble(int[] items, int capacity, List<GPNode> forest) {
        List<Bin> bins = new ArrayList<>();
        bins.add(new Bin(capacity));

        for (int i = 0; i < items.length; i++) {
            int S = items[i];
            int numBins = bins.size();
            int[] votes = new int[numBins + 1]; // last slot is "open a new bin"

            for (GPNode tree : forest) {
                int treePick = -1;
                double treeBestScore = Double.NEGATIVE_INFINITY;

                for (int j = 0; j < numBins; j++) {
                    if (bins.get(j).canFit(S)) {
                        double sc = tree.evaluate(S, bins.get(j).getEmptiness(), capacity, -1.0);
                        if (sc > treeBestScore) { treeBestScore = sc; treePick = j; }
                    }
                }
                double newSc = tree.evaluate(S, capacity, capacity, 1.0);
                if (newSc > treeBestScore || treePick == -1) treePick = numBins;
                votes[treePick]++;
            }

            // Pick the bin with the most votes; break ties by smallest remaining space (Best-Fit style).
            int winner = -1; int maxVotes = -1; int tieBreakerEmp = Integer.MAX_VALUE;
            for (int v = 0; v <= numBins; v++) {
                if (votes[v] > maxVotes) {
                    maxVotes = votes[v]; winner = v;
                    tieBreakerEmp = (v == numBins) ? capacity : bins.get(v).getEmptiness();
                } else if (votes[v] == maxVotes) {
                    int currentEmp = (v == numBins) ? capacity : bins.get(v).getEmptiness();
                    if (currentEmp < tieBreakerEmp) { winner = v; tieBreakerEmp = currentEmp; }
                }
            }

            if (winner == numBins) bins.add(new Bin(capacity));
            bins.get(winner).addItem(S, i);
        }
        return bins;
    }

    // Trains a single GP tree using generational evolution with tournament selection.
    public static GPNode trainForest(List<int[]> instances, int capacity, long seed, int maxGen) {
        Random rand = new Random(seed);
        int POP_SIZE = 500;
        int MAX_DEPTH = 6;

        List<Individual> pop = new ArrayList<>();
        for (int i=0; i<POP_SIZE; i++) pop.add(new Individual(GPTreeFactory.createRandomTree(MAX_DEPTH, rand)));
        Individual globalBest = null;

        ForkJoinPool pool = ForkJoinPool.commonPool();

        for (int gen = 0; gen <= maxGen; gen++) {
            final int currentGen = gen;
            final List<Individual> popRef = pop;
            // Evaluate fitness in parallel across all individuals and instances.
            pool.submit(() -> popRef.parallelStream().forEach(ind -> {
                double fitSum = 0;
                for (int[] items : instances) fitSum += evaluateFitness(items, capacity, ind.tree);
                ind.fitness = fitSum / instances.size();
            })).join();

            Collections.sort(pop);
            if (globalBest == null || pop.get(0).fitness < globalBest.fitness) {
                globalBest = new Individual(pop.get(0).tree.copy());
                globalBest.fitness = pop.get(0).fitness;
            }

            System.out.printf("Gen %d | Best Fitness: %.4f | Size: %d\n", gen, pop.get(0).fitness, pop.get(0).tree.getSize());
            if (gen == maxGen) break;

            List<Individual> nextPop = new ArrayList<>();
            // Elitism: copy top 2 individuals unchanged to the next generation.
            nextPop.add(new Individual(pop.get(0).tree.copy())); // Elitism: keep top 2
            nextPop.add(new Individual(pop.get(1).tree.copy()));

            while (nextPop.size() < POP_SIZE) {
                if (rand.nextDouble() < 0.85) { // Crossover
                    GPNode t1 = tournament(pop, rand).tree.copy();
                    GPNode t2 = tournament(pop, rand).tree.copy();
                    List<GPNode> n1 = new ArrayList<>(); t1.collectNodes(n1);
                    List<GPNode> n2 = new ArrayList<>(); t2.collectNodes(n2);
                    GPNode p1 = n1.get(rand.nextInt(n1.size()));
                    GPNode p2 = n2.get(rand.nextInt(n2.size()));

                    if (p1 == t1) { t1 = p2.copy(); }
                    else { t1.replaceChild(p1, p2.copy()); }

                    if (t1.getDepth() <= MAX_DEPTH) nextPop.add(new Individual(t1));
                    else nextPop.add(new Individual(tournament(pop, rand).tree.copy()));
                } else { // Mutation
                    GPNode t = tournament(pop, rand).tree.copy();
                    List<GPNode> n = new ArrayList<>(); t.collectNodes(n);
                    GPNode p = n.get(rand.nextInt(n.size()));
                    GPNode sub = GPTreeFactory.createRandomTree(3, rand);

                    if (p == t) t = sub;
                    else t.replaceChild(p, sub);

                    if (t.getDepth() <= MAX_DEPTH) nextPop.add(new Individual(t));
                    else nextPop.add(new Individual(tournament(pop, rand).tree.copy()));
                }
            }
            pop = nextPop;
        }
        return globalBest.tree;
    }

    // Tournament selection with size 7 (hardcoded).
    private static Individual tournament(List<Individual> pop, Random rand) {
        Individual best = pop.get(rand.nextInt(pop.size()));
        for (int i=1; i<7; i++) {
            Individual cand = pop.get(rand.nextInt(pop.size()));
            if (cand.compareTo(best) < 0) best = cand;
        }
        return best;
    }
}
