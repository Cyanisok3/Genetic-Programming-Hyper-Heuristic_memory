import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

/**
 * Genetic Programming evolution engine.
 * Evolves GP heuristics for the Bin Packing Problem.
 */
public class GeneticProgramming {
    
    // GP Parameters (Burke et al. 2010)
    public static final int POPULATION_SIZE = 1000;
    public static final int MAX_GENERATIONS = 50;
    public static final double CROSSOVER_RATE = 0.85;
    public static final double MUTATION_RATE = 0.10;
    public static final double REPRODUCTION_RATE = 0.05;
    public static final int TOURNAMENT_SIZE = 7;
    public static final int ELITE_SIZE = 2;
    public static final int MIN_DEPTH = 4;
    public static final int MAX_DEPTH = 6;
    public static final int TERMINAL_COUNT = 11;

    private final Random rand;
    private final int terminalCount;
    private final ForkJoinPool forkJoinPool;

    public GeneticProgramming() {
        this.rand = new Random();
        this.terminalCount = TERMINAL_COUNT;
        this.forkJoinPool = ForkJoinPool.commonPool();
    }

    public GeneticProgramming(long seed) {
        this.rand = new Random(seed);
        this.terminalCount = TERMINAL_COUNT;
        this.forkJoinPool = ForkJoinPool.commonPool();
    }

    /**
     * Task that evaluates one individual on all training instances.
     */
    private class FitnessTask extends RecursiveTask<Double> {
        private final Individual ind;
        private final List<BPPInstance> trainingSet;
        private final List<Integer> genIndices;

        FitnessTask(Individual ind, List<BPPInstance> trainingSet, List<Integer> genIndices) {
            this.ind = ind;
            this.trainingSet = trainingSet;
            this.genIndices = genIndices;
        }

        @Override
        protected Double compute() {
            return evaluateFitnessBinsOverL2Fixed(ind.getHeuristic(), trainingSet, genIndices);
        }
    }
    
    /**
     * Evolve a heuristic using the given training instances.
     * @param trainingSet List of BPP instances for training
     * @param timeLimitMs Maximum time in milliseconds
     * @return Best evolved heuristic
     */
    public Heuristic evolve(List<BPPInstance> trainingSet, long timeLimitMs) {
        long startTime = System.currentTimeMillis();
        
        // Initialize population
        Population population = new Population(POPULATION_SIZE);
        for (int i = 0; i < POPULATION_SIZE; i++) {
            GPNode tree = createRandomTree(MIN_DEPTH, MAX_DEPTH);
            population.add(new Individual(tree));
        }
        
        Individual bestOverall = null;
        
        // Evolution loop
        for (int gen = 0; gen < MAX_GENERATIONS; gen++) {
            // Check time limit
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeLimitMs) {
                System.out.println("Time limit reached at generation " + gen);
                break;
            }
            
            // Evaluate fitness for all individuals
            for (Individual ind : population.getIndividuals()) {
                double fitness = evaluateFitnessBinsOverL2(ind.getHeuristic(), trainingSet);
                ind.setFitness(fitness);
            }
            
            // Track best individual
            Individual best = population.getBest();
            if (bestOverall == null || best.getFitness() < bestOverall.getFitness()) {
                bestOverall = best.copy();
            }
            
            System.out.println("Generation " + gen + ": best fitness = " +
                             String.format("%.6f", best.getFitness()) +
                             " (avg bins/L2)");
            
            // Create next generation
            Population newPop = new Population();

            // Sort population and keep top ELITE_SIZE individuals
            population.sort();
            for (int i = 0; i < Math.min(ELITE_SIZE, population.size()); i++) {
                newPop.add(population.get(i).copy());
            }
            
            // Generate offspring
            while (newPop.size() < POPULATION_SIZE) {
                double r = rand.nextDouble();

                if (r < CROSSOVER_RATE) {
                    // Crossover
                    Individual parent1 = tournamentSelect(population);
                    Individual parent2 = tournamentSelect(population);
                    Individual child = crossover(parent1, parent2);

                    // Mutation
                    if (rand.nextDouble() < MUTATION_RATE) {
                        mutate(child);
                    }

                    newPop.add(child);
                } else if (r < CROSSOVER_RATE + MUTATION_RATE) {
                    // Mutation only
                    Individual parent = tournamentSelect(population);
                    Individual child = parent.copy();
                    mutate(child);
                    newPop.add(child);
                } else {
                    // Reproduction
                    Individual parent = tournamentSelect(population);
                    newPop.add(parent.copy());
                }
            }
            
            population = newPop;
        }
        
        return bestOverall.getHeuristic();
    }

    /**
     * Full evolution without time limit.
     * Evaluates each heuristic on all 10 training instances (class1 + class2),
     * computing average bins/L2 as fitness.
     * @param trainingSet List of BPP instances for training
     * @return Best evolved heuristic
     */
    public Heuristic evolveFull(List<BPPInstance> trainingSet) {
        final int FULL_POPULATION_SIZE = POPULATION_SIZE;
        final int FULL_MAX_GENERATIONS = MAX_GENERATIONS;
        final int FULL_ELITE_SIZE = ELITE_SIZE;

        System.out.println("Starting evolution (pop=" + FULL_POPULATION_SIZE +
                         ", gen=" + FULL_MAX_GENERATIONS +
                         ", instances=" + trainingSet.size() + ")...");

        if (trainingSet.isEmpty()) {
            System.err.println("Error: No training instances available.");
            return null;
        }

        // Initialize population
        Population population = new Population(FULL_POPULATION_SIZE);
        for (int i = 0; i < FULL_POPULATION_SIZE; i++) {
            GPNode tree = createRandomTree(MIN_DEPTH, MAX_DEPTH);
            population.add(new Individual(tree));
        }

        Individual bestOverall = null;

        // Pre-generate shuffled indices for this generation (shared by all individuals)
        List<Integer> genIndices = new ArrayList<>();
        for (int i = 0; i < trainingSet.size(); i++) genIndices.add(i);

        // Evolution loop
        for (int gen = 0; gen < FULL_MAX_GENERATIONS; gen++) {
            // Shuffle indices for this generation
            Collections.shuffle(genIndices, rand);

            // Evaluate fitness for all individuals in parallel
            List<ForkJoinTask<Double>> futures = new ArrayList<>();
            for (Individual ind : population.getIndividuals()) {
                futures.add(forkJoinPool.submit(new FitnessTask(ind, trainingSet, genIndices)));
            }
            for (int i = 0; i < population.getIndividuals().size(); i++) {
                try {
                    population.getIndividuals().get(i).setFitness(futures.get(i).get());
                } catch (Exception e) {
                    System.err.println("Error evaluating individual fitness: " + e.getMessage());
                    population.getIndividuals().get(i).setFitness(Double.MAX_VALUE);
                }
            }

            // Track best individual
            Individual best = population.getBest();
            if (bestOverall == null || best.getFitness() < bestOverall.getFitness()) {
                bestOverall = best.copy();
            }

            System.out.println("Gen " + gen + ": best=" + String.format("%.6f", best.getFitness()));

            // Create next generation
            Population newPop = new Population();

            population.sort();
            for (int i = 0; i < Math.min(FULL_ELITE_SIZE, population.size()); i++) {
                newPop.add(population.get(i).copy());
            }

            while (newPop.size() < FULL_POPULATION_SIZE) {
                double r = rand.nextDouble();

                if (r < CROSSOVER_RATE) {
                    Individual parent1 = tournamentSelect(population);
                    Individual parent2 = tournamentSelect(population);
                    Individual child = crossover(parent1, parent2);
                    if (rand.nextDouble() < MUTATION_RATE) {
                        mutate(child);
                    }
                    newPop.add(child);
                } else if (r < CROSSOVER_RATE + MUTATION_RATE) {
                    Individual parent = tournamentSelect(population);
                    Individual child = parent.copy();
                    mutate(child);
                    newPop.add(child);
                } else {
                    Individual parent = tournamentSelect(population);
                    newPop.add(parent.copy());
                }
            }

            population = newPop;
        }

        return bestOverall.getHeuristic();
    }

    /**
     * Evaluate fitness: average(bins_used / L2_bound) over all training instances.
     * Fitness = sum(ni / L2(i)) / |I|
     * Lower is better.
     * @param h Heuristic to evaluate
     * @param trainingSet Training instances
     * @param fixedIndices Pre-shuffled indices for this generation
     */
    private double evaluateFitnessBinsOverL2Fixed(Heuristic h, List<BPPInstance> trainingSet,
                                                  List<Integer> fixedIndices) {
        double sum = 0.0;
        for (int idx : fixedIndices) {
            BPPInstance instance = trainingSet.get(idx);
            BPPSolver solver = new BPPSolver();
            Solution solution = solver.solve(instance, h);
            double l2Bound = instance.getVerifiedL2Bound();
            if (l2Bound <= 0) l2Bound = L2BoundCalculator.calculate(instance);
            sum += (double) solution.getBinCount() / l2Bound;
        }
        return sum / trainingSet.size();
    }

    /**
     * Evaluate fitness: average(bins_used / L2_bound) over all training instances.
     * Shuffles indices each call.
     */
    public double evaluateFitnessBinsOverL2(Heuristic h, List<BPPInstance> trainingSet) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < trainingSet.size(); i++) indices.add(i);
        Collections.shuffle(indices, rand);
        return evaluateFitnessBinsOverL2Fixed(h, trainingSet, indices);
    }

    /**
     * Tournament selection.
     */
    public Individual tournamentSelect(Population population) {
        Individual best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Individual candidate = population.get(rand.nextInt(population.size()));
            if (best == null || candidate.getFitness() < best.getFitness()) {
                best = candidate;
            }
        }
        return best;
    }
    
    /**
     * Subtree crossover.
     */
    public Individual crossover(Individual parent1, Individual parent2) {
        GPNode tree1 = parent1.getTree().copy();
        GPNode tree2 = parent2.getTree().copy();
        
        List<GPNode> nodes1 = tree1.getAllNodes();
        List<GPNode> nodes2 = tree2.getAllNodes();
        
        GPNode node1 = nodes1.get(rand.nextInt(nodes1.size()));
        GPNode node2 = nodes2.get(rand.nextInt(nodes2.size()));
        
        tree1.replaceNode(node1, node2.copy());
        return new Individual(tree1);
    }
    
    /**
     * Subtree mutation with proper terminal mutation.
     * Replaces a random node with either:
     * - A random terminal (for leaf nodes)
     * - A random subtree (for function nodes)
     */
    public void mutate(Individual individual) {
        GPNode tree = individual.getTree();
        List<GPNode> nodes = tree.getAllNodes();
        GPNode node = nodes.get(rand.nextInt(nodes.size()));

        if (node instanceof EphemeralConstantTerminal) {
            double val = EphemeralConstantTerminal.CONSTANTS[rand.nextInt(EphemeralConstantTerminal.CONSTANTS.length)];
            tree.replaceNode(node, new EphemeralConstantTerminal(val));
        } else if (node.getChildren().isEmpty()) {
            GPNode newTerminal = createRandomTerminal();
            tree.replaceNode(node, newTerminal);
        } else {
            int newDepth = MIN_DEPTH + rand.nextInt(MAX_DEPTH - MIN_DEPTH + 1);
            GPNode newSubtree = createRandomTree(1, newDepth);
            tree.replaceNode(node, newSubtree);
        }
    }
    
    /**
     * Create a random GP tree using ramped half-and-half.
     */
    public GPNode createRandomTree(int minDepth, int maxDepth) {
        int depth = minDepth + rand.nextInt(maxDepth - minDepth + 1);
        return createTree(depth, minDepth, maxDepth);
    }
    
    private GPNode createTree(int targetDepth, int minDepth, int maxDepth) {
        if (targetDepth <= 1) {
            // Terminal
            return createRandomTerminal();
        } else {
            // Function
            GPNode func = createRandomFunction();

            if (func instanceof FIFunction) {
                // FI has arity 1
                GPNode child = createTree(targetDepth - 1, minDepth, maxDepth);
                func.addChild(child);
            } else {
                // Binary functions: +, -, *, /, IFL
                GPNode left = createTree(targetDepth - 1, minDepth, maxDepth);
                GPNode right = createTree(targetDepth - 1, minDepth, maxDepth);
                func.addChild(left);
                func.addChild(right);
            }
            return func;
        }
    }
    
    /**
     * Create a random function node.
     */
    public GPNode createRandomFunction() {
        int type = rand.nextInt(5);  // 0-3: arity-2, 4: arity-1 (FI)
        switch (type) {
            case 0: return new AddNode();
            case 1: return new SubtractNode();
            case 2: return new MultiplyNode();
            case 3: return new DivideNode();
            case 4: return new FIFunction();
            default: return new AddNode();
        }
    }
    
    /**
     * Create a random terminal node.
     * Includes both memory terminals, short-term terminals, and literature-based terminals.
     */
    public GPNode createRandomTerminal() {
        int type = rand.nextInt(terminalCount);
        switch (type) {
            case 0: return new PieceSizeTerminal();        // S
            case 1: return new BinEmptinessTerminal();    // E
            case 2: return new SpaceLeftTerminal();       // L
            case 3: return new MemoryMinTerminal();       // MIN
            case 4: return new MemoryMaxTerminal();       // MAX
            case 5: return new MemoryAveTerminal();       // AVE
            case 6: return new MemoryFETerminal();       // FE
            case 7: return new MemoryFLTerminal();        // FL
            case 8: return new MemoryFXETerminal();      // FXE
            case 9: return new MemoryFXLTerminal();       // FXL
            case 10: {                                    // Ephemeral constant (Jin 2024)
                double val = EphemeralConstantTerminal.CONSTANTS[rand.nextInt(EphemeralConstantTerminal.CONSTANTS.length)];
                return new EphemeralConstantTerminal(val);
            }
            default: return new PieceSizeTerminal();
        }
    }
}
