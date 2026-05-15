/**
 * Global configuration singleton for the GPHH BPP system.
 * All evolution, mutation, and ensemble parameters are centralized here.
 */
public final class Config {
    public static final Config INSTANCE = new Config();

    private Config() {}

    // ======================
    // Evolution parameters
    // ======================
    public final int POPULATION_SIZE = 300;
    public final int MAX_GENERATIONS = 50;
    public final double CROSSOVER_RATE = 0.70;
    public final double MUTATION_RATE = 0.10;
    public final double REPRODUCTION_RATE = 0.10;
    public final int TOURNAMENT_SIZE = 7;
    public final int ELITE_SIZE = 1;
    public final int MIN_DEPTH = 4;
    public final int MAX_DEPTH = 6;
    public final int TERMINAL_COUNT = 11;

    // ======================
    // Adaptive mutation parameters
    // ======================
    private final double MUTATION_RATE_MIN = 0.05;
    private final double MUTATION_RATE_MAX = 0.50;
    private final double MUTATION_RATE_STEP = 0.02;
    private final int STAGNATION_THRESHOLD = 3;
    private final double IMPROVEMENT_THRESHOLD_PCT = 0.001;

    // Runtime state for adaptive mutation (reset at start of each evolve(trainingSet, k))
    private double adaptiveMutationRate = MUTATION_RATE;
    private int stagnationCounter = 0;
    private double lastGenBestFitness = Double.POSITIVE_INFINITY;

    public double getAdaptiveMutationRate() {
        return adaptiveMutationRate;
    }

    public void resetAdaptiveMutation() {
        adaptiveMutationRate = MUTATION_RATE;
        stagnationCounter = 0;
        lastGenBestFitness = Double.POSITIVE_INFINITY;
    }

    public void updateAdaptiveMutationRate(double currentBestFitness) {
        // Relative improvement over the previous generation.
        double improvement = lastGenBestFitness - currentBestFitness;
        double relativeImprovement = improvement / lastGenBestFitness;
        if (relativeImprovement > IMPROVEMENT_THRESHOLD_PCT) {
            stagnationCounter = 0;
            // Fitness improving: gradually reduce mutation to fine-tune.
            adaptiveMutationRate = Math.max(MUTATION_RATE_MIN,
                adaptiveMutationRate - MUTATION_RATE_STEP);
        } else {
            stagnationCounter++;
            if (stagnationCounter >= STAGNATION_THRESHOLD) {
                // No improvement for STAGNATION_THRESHOLD consecutive generations:
                // increase mutation to escape local optima.
                adaptiveMutationRate = Math.min(MUTATION_RATE_MAX,
                    adaptiveMutationRate + MUTATION_RATE_STEP);
                stagnationCounter = 0;
            }
        }
        lastGenBestFitness = currentBestFitness;
    }

}
