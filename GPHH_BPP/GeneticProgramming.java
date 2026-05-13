import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

/**
 * Genetic Programming evolution engine.
 * Evolves GP heuristics for the Bin Packing Problem.
 * Based on Burke et al. (2010).
 */
public class GeneticProgramming {

    public static final int POPULATION_SIZE = 1000;
    public static final int MAX_GENERATIONS = 30;
    public static final double CROSSOVER_RATE = 0.85;
    public static final double MUTATION_RATE = 0.10;
    public static final double REPRODUCTION_RATE = 0.05;
    public static final int TOURNAMENT_SIZE = 7;
    public static final int ELITE_SIZE = 2;
    public static final int MIN_DEPTH = 4;
    public static final int MAX_DEPTH = 6;
    public static final int TERMINAL_COUNT = 11;
    private static final double TREE_PENALTY_ALPHA = 0.0;

    private final Random rand;
    private final ForkJoinPool forkJoinPool;

    public GeneticProgramming() {
        this.rand = new Random();
        this.forkJoinPool = ForkJoinPool.commonPool();
    }

    public GeneticProgramming(long seed) {
        this.rand = new Random(seed);
        this.forkJoinPool = ForkJoinPool.commonPool();
    }

    /**
     * Task that evaluates one individual on all training instances.
     */
    private class FitnessTask extends RecursiveTask<Double> {
        private final Individual ind;
        private final List<BPPInstance> trainingSet;

        FitnessTask(Individual ind, List<BPPInstance> trainingSet) {
            this.ind = ind;
            this.trainingSet = trainingSet;
        }

        @Override
        protected Double compute() {
            return evaluateFitness(ind.getHeuristic(), trainingSet);
        }
    }

    /**
     * Evolve a heuristic on the given training set.
     * All instances are evaluated in natural order each generation (no shuffling).
     * Fitness evaluation is parallelized across the population using ForkJoinPool.
     */
    public Heuristic evolve(List<BPPInstance> trainingSet) {
        System.out.println("Starting evolution (pop=" + POPULATION_SIZE +
                         ", gen=" + MAX_GENERATIONS +
                         ", instances=" + trainingSet.size() + ")...");

        if (trainingSet.isEmpty()) {
            System.err.println("Error: No training instances available.");
            return null;
        }

        Population population = new Population(POPULATION_SIZE);
        for (int i = 0; i < POPULATION_SIZE; i++) {
            GPNode tree = createRandomTree(MIN_DEPTH, MAX_DEPTH);
            population.add(new Individual(tree));
        }

        Individual bestOverall = null;

        for (int gen = 0; gen < MAX_GENERATIONS; gen++) {
            List<ForkJoinTask<Double>> futures = new ArrayList<>();
            for (Individual ind : population.getIndividuals()) {
                futures.add(forkJoinPool.submit(new FitnessTask(ind, trainingSet)));
            }
            for (int i = 0; i < population.getIndividuals().size(); i++) {
                try {
                    population.getIndividuals().get(i).setFitness(futures.get(i).get());
                } catch (Exception e) {
                    System.err.println("Error evaluating fitness: " + e.getMessage());
                    population.getIndividuals().get(i).setFitness(Double.MAX_VALUE);
                }
            }

            Individual best = population.getBest();
            if (bestOverall == null || best.compareToLexicographic(bestOverall) < 0) {
                bestOverall = best.copy();
            }

            System.out.println("Gen " + gen + ": best=" + String.format("%.6f", best.getFitness()));

            Population newPop = new Population();
            population.sort();
            for (int i = 0; i < Math.min(ELITE_SIZE, population.size()); i++) {
                newPop.add(population.get(i).copy());
            }

            while (newPop.size() < POPULATION_SIZE) {
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
     * Average of bins_used / L2_bound across all training instances.
     * TREE_PENALTY_ALPHA is 0.0; tree size only matters via lexicographic comparison.
     */
    private double evaluateFitness(Heuristic h, List<BPPInstance> trainingSet) {
        double sum = 0.0;
        for (BPPInstance instance : trainingSet) {
            BPPSolver solver = new BPPSolver();
            Solution solution = solver.solve(instance, h);
            double l2Bound = instance.getVerifiedL2Bound();
            if (l2Bound <= 0) l2Bound = L2BoundCalculator.calculate(instance);
            sum += (double) solution.getBinCount() / l2Bound;
        }
        double rawFitness = sum / trainingSet.size();
        double treeSizePenalty = TREE_PENALTY_ALPHA * h.getRoot().getSize();
        return rawFitness + treeSizePenalty;
    }

    /**
     * Tournament selection with lexicographic comparison:
     * primary key = raw fitness, secondary key = tree size.
     */
    public Individual tournamentSelect(Population population) {
        Individual best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Individual candidate = population.get(rand.nextInt(population.size()));
            if (best == null || candidate.compareToLexicographic(best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Subtree crossover using depth-constrained node pool.
     */
    public Individual crossover(Individual parent1, Individual parent2) {
        GPNode tree1 = parent1.getTree().copy();
        GPNode tree2 = parent2.getTree().copy();

        List<GPNode> nodes1 = collectValidNodes(tree1, MAX_DEPTH, 0);
        List<GPNode> nodes2 = collectValidNodes(tree2, MAX_DEPTH, 0);

        GPNode node1 = nodes1.get(rand.nextInt(nodes1.size()));
        GPNode node2 = nodes2.get(rand.nextInt(nodes2.size()));

        tree1.replaceNode(node1, node2.copy());
        return new Individual(tree1);
    }

    /**
     * Collect nodes at depth < maxDepth (eligible for crossover).
     */
    private List<GPNode> collectValidNodes(GPNode node, int maxDepth, int currentDepth) {
        List<GPNode> valid = new ArrayList<>();
        if (currentDepth >= maxDepth) return valid;
        valid.add(node);
        for (GPNode child : node.getChildren()) {
            valid.addAll(collectValidNodes(child, maxDepth, currentDepth + 1));
        }
        return valid;
    }

    /**
     * Subtree mutation with depth constraint.
     */
    public void mutate(Individual individual) {
        GPNode tree = individual.getTree();
        List<GPNode> nodes = collectValidNodes(tree, MAX_DEPTH, 0);
        GPNode node = nodes.get(rand.nextInt(nodes.size()));
        int currentDepth = getNodeDepth(tree, node, 0);

        if (node instanceof EphemeralConstantTerminal) {
            double val = EphemeralConstantTerminal.CONSTANTS[rand.nextInt(EphemeralConstantTerminal.CONSTANTS.length)];
            tree.replaceNode(node, new EphemeralConstantTerminal(val));
        } else if (node.getChildren().isEmpty()) {
            GPNode newTerminal = createRandomTerminal();
            tree.replaceNode(node, newTerminal);
        } else {
            int roomLeft = MAX_DEPTH - currentDepth;
            int maxNewDepth = Math.max(2, Math.min(roomLeft - 1, MAX_DEPTH / 2));
            GPNode newSubtree = createRandomTreeStatic(1, maxNewDepth, rand);
            tree.replaceNode(node, newSubtree);
        }
    }

    private int getNodeDepth(GPNode node, GPNode target, int depth) {
        if (node == target) return depth;
        for (GPNode child : node.getChildren()) {
            int found = getNodeDepth(child, target, depth + 1);
            if (found >= 0) return found;
        }
        return -1;
    }

    /**
     * Static factory for use in mutation contexts without a GP instance.
     */
    public static GPNode createRandomTreeStatic(int minDepth, int maxDepth, Random rand) {
        int depth = minDepth + rand.nextInt(maxDepth - minDepth + 1);
        return createTreeStatic(depth, minDepth, maxDepth, rand);
    }

    private static GPNode createTreeStatic(int targetDepth, int minDepth, int maxDepth, Random rand) {
        if (targetDepth <= 1) {
            return createRandomTerminalStatic(minDepth, maxDepth, rand);
        }
        int type = rand.nextInt(6);
        switch (type) {
            case 0: {
                GPNode left = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode right = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                return new AddNode(left, right);
            }
            case 1: {
                GPNode left = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode right = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                return new SubtractNode(left, right);
            }
            case 2: {
                GPNode left = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode right = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                return new MultiplyNode(left, right);
            }
            case 3: {
                GPNode left = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode right = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                return new DivideNode(left, right);
            }
            case 4: {
                GPNode child = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                return new FIFunction(child);
            }
            case 5: {
                GPNode a = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode b = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode c = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode d = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                return new IfLessThanNode(a, b, c, d);
            }
            default: {
                GPNode left = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                GPNode right = createTreeStatic(targetDepth - 1, minDepth, maxDepth, rand);
                return new AddNode(left, right);
            }
        }
    }

    private static GPNode createRandomTerminalStatic(int minDepth, int maxDepth, Random rand) {
        int type = rand.nextInt(TERMINAL_COUNT);
        switch (type) {
            case 0: return new PieceSizeTerminal();
            case 1: return new BinEmptinessTerminal();
            case 2: return new SpaceLeftTerminal();
            case 3: return new MemoryMinTerminal();
            case 4: return new MemoryMaxTerminal();
            case 5: return new MemoryAveTerminal();
            case 6: return new MemoryFETerminal();
            case 7: return new MemoryFLTerminal();
            case 8: return new MemoryFXETerminal();
            case 9: return new MemoryFXLTerminal();
            case 10: {
                double val = EphemeralConstantTerminal.CONSTANTS[rand.nextInt(EphemeralConstantTerminal.CONSTANTS.length)];
                return new EphemeralConstantTerminal(val);
            }
            default: return new PieceSizeTerminal();
        }
    }

    public GPNode createRandomTree(int minDepth, int maxDepth) {
        int depth = minDepth + rand.nextInt(maxDepth - minDepth + 1);
        return createTree(depth, minDepth, maxDepth);
    }

    private GPNode createTree(int targetDepth, int minDepth, int maxDepth) {
        if (targetDepth <= 1) {
            return createRandomTerminal();
        }
        GPNode func = createRandomFunction();

        if (func instanceof FIFunction) {
            GPNode child = createTree(targetDepth - 1, minDepth, maxDepth);
            func.addChild(child);
        } else if (func instanceof IfLessThanNode) {
            GPNode a = createTree(targetDepth - 1, minDepth, maxDepth);
            GPNode b = createTree(targetDepth - 1, minDepth, maxDepth);
            GPNode c = createTree(targetDepth - 1, minDepth, maxDepth);
            GPNode d = createTree(targetDepth - 1, minDepth, maxDepth);
            func.addChild(a);
            func.addChild(b);
            func.addChild(c);
            func.addChild(d);
        } else {
            GPNode left = createTree(targetDepth - 1, minDepth, maxDepth);
            GPNode right = createTree(targetDepth - 1, minDepth, maxDepth);
            func.addChild(left);
            func.addChild(right);
        }
        return func;
    }

    public GPNode createRandomFunction() {
        int type = rand.nextInt(6);
        switch (type) {
            case 0: return new AddNode();
            case 1: return new SubtractNode();
            case 2: return new MultiplyNode();
            case 3: return new DivideNode();
            case 4: return new FIFunction();
            case 5: return new IfLessThanNode();
            default: return new AddNode();
        }
    }

    public GPNode createRandomTerminal() {
        int type = rand.nextInt(TERMINAL_COUNT);
        switch (type) {
            case 0: return new PieceSizeTerminal();
            case 1: return new BinEmptinessTerminal();
            case 2: return new SpaceLeftTerminal();
            case 3: return new MemoryMinTerminal();
            case 4: return new MemoryMaxTerminal();
            case 5: return new MemoryAveTerminal();
            case 6: return new MemoryFETerminal();
            case 7: return new MemoryFLTerminal();
            case 8: return new MemoryFXETerminal();
            case 9: return new MemoryFXLTerminal();
            case 10: {
                double val = EphemeralConstantTerminal.CONSTANTS[rand.nextInt(EphemeralConstantTerminal.CONSTANTS.length)];
                return new EphemeralConstantTerminal(val);
            }
            default: return new PieceSizeTerminal();
        }
    }
}
