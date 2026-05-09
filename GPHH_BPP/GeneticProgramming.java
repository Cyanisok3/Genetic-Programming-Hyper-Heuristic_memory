import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Genetic Programming evolution engine.
 * Evolves GP heuristics for the Bin Packing Problem.
 */
public class GeneticProgramming {
    
    // GP Parameters (from Memetic Computing 2024 paper)
    public static final int POPULATION_SIZE = 200;      // Increased from 80 (paper: 200)
    public static final int MAX_GENERATIONS = 20;      // Training mode
    public static final double CROSSOVER_RATE = 1.00;    // Paper: 100% (crossover is main search)
    public static final double MUTATION_RATE = 0.18;     // Paper: 2% (low mutation)
    public static final double REPRODUCTION_RATE = 0.00; // No pure reproduction
    public static final int TOURNAMENT_SIZE = 7;         // From paper
    public static final int ELITE_SIZE = 3;              // Time-limited mode
    public static final int MIN_DEPTH = 2;              // Increased from 2 (paper: 4-6)
    public static final int MAX_DEPTH = 6;             // Increased from 6 (paper: 8-12)
    public static final int FUNCTION_ARITY_2 = 4;         // +, -, *, %
    public static final int FUNCTION_ARITY_1 = 1;         // FI
    public static final int FUNCTION_ARITY_3 = 1;         // ITE (ternary)
    // Conditional functions
    public static final int FUNCTION_IFL = 1;             // IFL (binary)
    // 15 terminals: S, E, L, MIN, MAX, AVE, FE, FL, FXE, FXL (10 from Burke 2010)
    //             + BN, FR, P (3 short-term)
    //             + NB (bin lower bound — Quesada et al. 2025)
    //             + EphemeralConstant (Jin et al. 2024)
    public static final int TERMINAL_COUNT = 15;
    // Ephemeral constant values (Jin et al. 2024)
    public static final double[] EPHEMERAL_CONSTANTS = {0.2, 0.4, 0.6, 0.8, 1.0, 1.5, 2.0};
    // Parsimony pressure: penalize larger trees to encourage simpler heuristics
    // Value represents fitness penalty per node (e.g., 0.01 means 1% fitness penalty per 100 nodes)
    public static final double SIZE_PENALTY = 0.02;     // Moderate penalty to control bloat
    public static final double MAX_TREE_SIZE = 80;      // Soft limit - trees larger than this are penalized
    // Subsampling for faster fitness evaluation
    public static final int SAMPLE_SIZE = 8;            // Use subset for speed
    public static final double EARLY_STOP_THRESHOLD = 1.50; // Disabled, but kept for reference
    
    private Random rand;
    private int functionArity2Count;
    private int terminalCount;
    
    public GeneticProgramming() {
        this.rand = new Random();
        this.functionArity2Count = FUNCTION_ARITY_2;
        this.terminalCount = TERMINAL_COUNT;
    }
    
    public GeneticProgramming(long seed) {
        this.rand = new Random(seed);
        this.functionArity2Count = FUNCTION_ARITY_2;
        this.terminalCount = TERMINAL_COUNT;
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
                double fitness = evaluateFitnessRelative(ind.getHeuristic(), trainingSet);
                ind.setFitness(fitness);
            }
            
            // Track best individual
            Individual best = population.getBest();
            if (bestOverall == null || best.getFitness() < bestOverall.getFitness()) {
                bestOverall = best.copy();
            }
            
            System.out.println("Generation " + gen + ": best fitness = " +
                             String.format("%.6f", best.getFitness()) +
                             " (% above L2 bound)");
            
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
     * Full evolution without time limit, using paper-recommended parameters.
     * Class-aware training: evaluates heuristic on 2-3 instances from each of the 4 classes,
     * averaging class performances with optional class weights.
     * This follows the approach in Burke et al. 2010.
     * @param trainingByClass Training instances organized by class (4 classes)
     * @param classWeights Optional weights for each class (null for uniform weights)
     * @return Best evolved heuristic
     */
    @SuppressWarnings("unchecked")
    public Heuristic evolveFull(List<BPPInstance>[] trainingByClass, double[] classWeights) {
        final int FULL_POPULATION_SIZE = POPULATION_SIZE;  // 200
        final int FULL_MAX_GENERATIONS = MAX_GENERATIONS;  // 40
        final int FULL_ELITE_SIZE = ELITE_SIZE;            // 3
        final int INSTANCES_PER_CLASS = 5;                 // All 20 instances (5 per class)

        System.out.println("Starting class-aware evolution (pop=" + FULL_POPULATION_SIZE +
                         ", gen=" + FULL_MAX_GENERATIONS +
                         ", classes=4, per-class=" + INSTANCES_PER_CLASS + ")...");

        // Count total instances across all classes
        int totalClassInstances = 0;
        for (int c = 0; c < trainingByClass.length; c++) {
            totalClassInstances += trainingByClass[c].size();
        }
        System.out.println("Total training instances: " + totalClassInstances);

        if (totalClassInstances == 0) {
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

        // Pre-generate per-generation training instance indices ONCE per generation.
        // All individuals in the same generation evaluate the SAME instances.
        @SuppressWarnings("unchecked")
        List<Integer>[] genIndices = new List[trainingByClass.length];

        // Evolution loop
        for (int gen = 0; gen < FULL_MAX_GENERATIONS; gen++) {
            // Generate fixed shuffled indices for this generation (shared by all individuals)
            for (int c = 0; c < trainingByClass.length; c++) {
                if (trainingByClass[c].isEmpty()) {
                    genIndices[c] = new ArrayList<>();
                    continue;
                }
                List<Integer> idx = new ArrayList<>();
                for (int i = 0; i < trainingByClass[c].size(); i++) idx.add(i);
                Collections.shuffle(idx, rand);
                genIndices[c] = idx;
            }

            // Evaluate fitness for all individuals (same instances for all)
            for (Individual ind : population.getIndividuals()) {
                double fitness = evaluateClassAwareFitnessRelative(
                    ind.getHeuristic(), trainingByClass, INSTANCES_PER_CLASS, classWeights, genIndices);
                ind.setFitness(fitness);
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
     * Class-aware fitness evaluation.
     * Samples INSTANCES_PER_CLASS instances from each of the 4 classes,
     * computes average bins/L2 for each class, then averages across classes.
     * This ensures the heuristic performs well on ALL classes, not just one.
     */
    private double evaluateClassAwareFitness(Heuristic h, List<BPPInstance>[] trainingByClass,
                                           int instancesPerClass) {
        int numClasses = trainingByClass.length;
        double totalClassAvg = 0.0;
        int classesWithData = 0;

        for (int c = 0; c < numClasses; c++) {
            List<BPPInstance> classInstances = trainingByClass[c];
            if (classInstances.isEmpty()) continue;

            int sampleSize = Math.min(instancesPerClass, classInstances.size());
            double classSum = 0.0;
            int classEvaluated = 0;

            // Shuffle and sample from this class
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < classInstances.size(); i++) indices.add(i);
            Collections.shuffle(indices, rand);

            for (int i = 0; i < sampleSize; i++) {
                BPPInstance instance = classInstances.get(indices.get(i));
                BPPSolver solver = new BPPSolver();
                Solution solution = solver.solve(instance, h);
                double l2Bound = L2BoundCalculator.calculate(instance);
                double ratio = (double) solution.getBinCount() / l2Bound;
                classSum += ratio;
                classEvaluated++;
                // Early stopping disabled: evaluate all sampled instances for accurate fitness
            }

            totalClassAvg += classSum / sampleSize;
            classesWithData++;
        }

        double avgFitness = (classesWithData > 0) ? totalClassAvg / classesWithData : 999.0;
        int treeSize = h.getSize();
        return avgFitness + SIZE_PENALTY * treeSize;
    }

    /**
     * Full evolution without time limit, using paper-recommended parameters.
     * Larger population and more generations for better heuristics.
     * @param trainingSet List of BPP instances for training
     * @return Best evolved heuristic
     */
    @SuppressWarnings("unchecked")
    public Heuristic evolveFull(List<BPPInstance> trainingSet) {
        List<BPPInstance>[] byClass = new List[1];
        byClass[0] = trainingSet;
        return evolveFull(byClass, null);  // null = uniform weights
    }

    /**
     * Evaluate fitness with configurable sample size and early stopping.
     * For full evolution: uses all training instances.
     */
    private double evaluateFitnessFull(Heuristic h, List<BPPInstance> trainingSet,
                                      int sampleSize, double earlyStopThreshold) {
        int totalInstances = trainingSet.size();
        int actualSampleSize = Math.min(sampleSize, totalInstances);

        double sum = 0.0;
        int evaluated = 0;

        // Use all instances in shuffled order
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < totalInstances; i++) indices.add(i);
        Collections.shuffle(indices, rand);

        for (int i = 0; i < actualSampleSize; i++) {
            BPPInstance instance = trainingSet.get(indices.get(i));
            BPPSolver solver = new BPPSolver();
            Solution solution = solver.solve(instance, h);
            double l2Bound = L2BoundCalculator.calculate(instance);
            double ratio = (double) solution.getBinCount() / l2Bound;
            sum += ratio;
            evaluated++;

            // Early stopping
            if (ratio > earlyStopThreshold && evaluated >= 3) {
                int remaining = actualSampleSize - evaluated;
                sum += earlyStopThreshold * remaining;
                break;
            }
        }

        double avgBins = sum / actualSampleSize;
        int treeSize = h.getSize();
        return avgBins + SIZE_PENALTY * treeSize;
    }

    /**
     * Evaluate fitness using relative deviation from L2 bound (percent above L2).
     * Fitness = average((bins_used - l2_bound) / l2_bound * 100)
     * Lower is better.  Paper: Memetic Computing 2024 style relative fitness.
     */
    public double evaluateFitnessRelative(Heuristic h, List<BPPInstance> trainingSet) {
        int totalInstances = trainingSet.size();
        int sampleSize = Math.min(SAMPLE_SIZE, totalInstances);

        double sumDeviation = 0.0;

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < totalInstances; i++) indices.add(i);
        Collections.shuffle(indices, rand);

        for (int i = 0; i < sampleSize; i++) {
            BPPInstance instance = trainingSet.get(indices.get(i));
            BPPSolver solver = new BPPSolver();
            Solution solution = solver.solve(instance, h);
            double l2Bound = instance.getVerifiedL2Bound();
            double deviation = ((double) solution.getBinCount() - l2Bound) / l2Bound * 100.0;
            sumDeviation += deviation;
        }

        return sumDeviation / sampleSize;
    }

    /**
     * Class-aware fitness using relative deviation with class weights and parsimony pressure.
     * Evaluates instances per class, computes per-class avg deviation, then averages across classes.
     * High S.D. classes (2-3) get 1.5x weight to ensure better coverage of harder problem types.
     * Tree size is penalized to encourage simpler heuristics.
     *
     * FIX 2026-05-09: Shuffles once per generation-shared seed, then reuses those indices
     * for all individuals. Previously, each fitness evaluation advanced the shared Random
     * state, causing different individuals to see different shuffled subsets within the same
     * generation — making tournament selection unreliable and fitness landscapes unstable.
     */
    private double evaluateClassAwareFitnessRelative(Heuristic h, List<BPPInstance>[] trainingByClass,
                                        int instancesPerClass, double[] classWeights,
                                        List<Integer>[] fixedIndices) {
        int numClasses = trainingByClass.length;
        double totalWeightedAvg = 0.0;
        double totalWeight = 0.0;

        for (int c = 0; c < numClasses; c++) {
            List<BPPInstance> classInstances = trainingByClass[c];
            if (classInstances.isEmpty()) continue;

            int sampleSize = Math.min(instancesPerClass, classInstances.size());
            double classSum = 0.0;

            List<Integer> indices = fixedIndices != null ? fixedIndices[c] : null;
            for (int i = 0; i < sampleSize; i++) {
                int idx = (indices != null && i < indices.size()) ? indices.get(i) : (i % classInstances.size());
                BPPInstance instance = classInstances.get(idx);
                BPPSolver solver = new BPPSolver();
                Solution solution = solver.solve(instance, h);
                double l2Bound = instance.getVerifiedL2Bound();
                if (l2Bound <= 0) l2Bound = L2BoundCalculator.calculate(instance);
                double deviation = ((double) solution.getBinCount() - l2Bound) / l2Bound * 100.0;
                classSum += deviation;
            }

            double classAvg = classSum / sampleSize;
            double weight = (classWeights != null && c < classWeights.length) ? classWeights[c] : 1.0;
            totalWeightedAvg += classAvg * weight;
            totalWeight += weight;
        }

        double fitness = (totalWeight > 0) ? totalWeightedAvg / totalWeight : 999.0;

        // Apply parsimony pressure: penalize large trees
        int treeSize = h.getSize();
        if (treeSize > MAX_TREE_SIZE) {
            double excess = treeSize - MAX_TREE_SIZE;
            fitness += SIZE_PENALTY * excess;
        }

        return fitness;
    }

    /**
     * Overload for backward compatibility — generates fresh shuffled indices each call.
     * WARNING: This is the OLD buggy behavior; use the overload with fixedIndices instead.
     */
    private double evaluateClassAwareFitnessRelative(Heuristic h, List<BPPInstance>[] trainingByClass,
                                        int instancesPerClass, double[] classWeights) {
        // Compute indices once per call (expensive but correct)
        @SuppressWarnings("unchecked")
        List<Integer>[] fixed = new List[trainingByClass.length];
        for (int c = 0; c < trainingByClass.length; c++) {
            if (trainingByClass[c].isEmpty()) continue;
            int sz = trainingByClass[c].size();
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < sz; i++) idx.add(i);
            Collections.shuffle(idx, rand);
            fixed[c] = idx;
        }
        return evaluateClassAwareFitnessRelative(h, trainingByClass, instancesPerClass, classWeights, fixed);
    }

    /**
     * Evaluate fitness of a heuristic on training instances.
     * Uses subsampling and early stopping for faster evaluation.
     * Fitness = average(bins_used / L2_bound) + penalty
     * Lower is better.
     */
    public double evaluateFitness(Heuristic h, List<BPPInstance> trainingSet) {
        int totalInstances = trainingSet.size();
        int sampleSize = Math.min(SAMPLE_SIZE, totalInstances);
        
        double sum = 0.0;
        int evaluated = 0;
        
        // Random sampling of training instances
        List<BPPInstance> sampledInstances = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < totalInstances; i++) indices.add(i);
        Collections.shuffle(indices, rand);
        for (int i = 0; i < sampleSize; i++) {
            sampledInstances.add(trainingSet.get(indices.get(i)));
        }
        
        for (BPPInstance instance : sampledInstances) {
            BPPSolver solver = new BPPSolver();
            Solution solution = solver.solve(instance, h);
            double l2Bound = L2BoundCalculator.calculate(instance);
            double ratio = (double) solution.getBinCount() / l2Bound;
            sum += ratio;
            evaluated++;
            // Early stopping disabled: evaluate all sampled instances
        }

        double avgBins = sum / sampleSize;
        int treeSize = h.getSize();
        return avgBins + SIZE_PENALTY * treeSize;
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
        
        // If node is a terminal (leaf), replace with another random terminal
        if (node.getChildren().isEmpty()) {
            GPNode newTerminal = createRandomTerminal();
            tree.replaceNode(node, newTerminal);
        } else {
            // If node is a function, replace with a random subtree
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
            } else if (func instanceof ITENode) {
                // ITE has arity 3
                GPNode cond = createTree(targetDepth - 1, minDepth, maxDepth);
                GPNode thenBranch = createTree(targetDepth - 1, minDepth, maxDepth);
                GPNode elseBranch = createTree(targetDepth - 1, minDepth, maxDepth);
                func.addChild(cond);
                func.addChild(thenBranch);
                func.addChild(elseBranch);
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
        int type = rand.nextInt(7);  // 0-3: arity-2, 4: arity-1, 5: arity-2 (IFL), 6: arity-3 (ITE)
        switch (type) {
            case 0: return new AddNode();
            case 1: return new SubtractNode();
            case 2: return new MultiplyNode();
            case 3: return new DivideNode();
            case 4: return new FIFunction();
            case 5: return new IFLNode();
            case 6: return new ITENode();
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
            case 1: return new BinEmptinessTerminal();     // E
            case 2: return new SpaceLeftTerminal();        // L
            case 3: return new MemoryMinTerminal();        // MIN
            case 4: return new MemoryMaxTerminal();        // MAX
            case 5: return new MemoryAveTerminal();       // AVE
            case 6: return new MemoryFETerminal();         // FE
            case 7: return new MemoryFLTerminal();          // FL
            case 8: return new MemoryFXETerminal();        // FXE
            case 9: return new MemoryFXLTerminal();        // FXL
            case 10: return new BinCountTerminal();        // BN (short-term)
            case 11: return new FullnessRatioTerminal();   // FR (short-term)
            case 12: return new ProgressTerminal();        // P (short-term)
            case 13: return new BinLowerBoundTerminal();    // NB (Quesada 2025)
            case 14: {                                     // Ephemeral constant (Jin 2024)
                double val = EPHEMERAL_CONSTANTS[rand.nextInt(EPHEMERAL_CONSTANTS.length)];
                return new EphemeralConstantTerminal(val);
            }
            default: return new PieceSizeTerminal();
        }
    }
}
