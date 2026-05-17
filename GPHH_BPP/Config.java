/**
 * Global configuration singleton for the GPHH BPP system.
 * All evolution, mutation, and ensemble parameters are centralized here.
 *
 * Parameters can be overridden via {@link #overrideParams(int, int, int)} before
 * starting evolution. Overrides are cleared by {@link #resetAdaptiveMutation()}.
 */
public final class Config {
    public static final Config INSTANCE = new Config();

    // Default evolution parameters
    public final int POPULATION_SIZE = 500;
    public final int MAX_GENERATIONS = 70;
    public final double CROSSOVER_RATE = 0.70;
    public final double MUTATION_RATE = 0.15;
    public final double REPRODUCTION_RATE = 0.10;
    public final int TOURNAMENT_SIZE = 20;
    public final int ELITE_SIZE = 2;
    public final int MIN_DEPTH = 2;
    public final int MAX_DEPTH = 6;
    public final int TERMINAL_COUNT = 11;

    // Adaptive mutation parameters
    private final double MUTATION_RATE_MIN = 0.15;
    private final double MUTATION_RATE_MAX = 0.50;
    private final double MUTATION_RATE_STEP = 0.04;
    private final int STAGNATION_THRESHOLD = 3;

    // Runtime state for adaptive mutation (reset at start of each evolve)
    private double adaptiveMutationRate = MUTATION_RATE;
    private int stagnationCounter = 0;
    private double lastGenBestFitness = Double.POSITIVE_INFINITY;

    // Parameter overrides (null means use default final field value)
    private Integer overrideMaxDepth = null;
    private Integer overrideEliteSize = null;
    private Integer overrideTournamentSize = null;

    private Config() {}

    // ---- Public getters ----

    public int getMAX_DEPTH() {
        return (overrideMaxDepth != null) ? overrideMaxDepth : MAX_DEPTH;
    }

    public int getELITE_SIZE() {
        return (overrideEliteSize != null) ? overrideEliteSize : ELITE_SIZE;
    }

    public int getTOURNAMENT_SIZE() {
        return (overrideTournamentSize != null) ? overrideTournamentSize : TOURNAMENT_SIZE;
    }

    public double getAdaptiveMutationRate() {
        return adaptiveMutationRate;
    }


    /**
     * Override one or more parameters for the next evolution run.
     * Pass -1 (or null for a specific arg) to leave a parameter unchanged.
     * Overrides are cleared by {@link #resetAdaptiveMutation()}.
     */
    public void overrideParams(int maxDepth, int eliteSize, int tournamentSize) {
        if (maxDepth > 0) this.overrideMaxDepth = maxDepth;
        if (eliteSize > 0) this.overrideEliteSize = eliteSize;
        if (tournamentSize > 0) this.overrideTournamentSize = tournamentSize;
    }

    public void resetAdaptiveMutation() {
        adaptiveMutationRate = MUTATION_RATE;
        stagnationCounter = 0;
        lastGenBestFitness = Double.POSITIVE_INFINITY;
        overrideMaxDepth = null;
        overrideEliteSize = null;
        overrideTournamentSize = null;
    }

    public void updateAdaptiveMutationRate(double currentBestFitness, int generation) {
        if (generation >= 10) {
            if (currentBestFitness > lastGenBestFitness) {
                adaptiveMutationRate = Math.max(MUTATION_RATE_MIN,
                    adaptiveMutationRate - MUTATION_RATE_STEP);
                stagnationCounter = 0;
            } else if (currentBestFitness < lastGenBestFitness) {
                stagnationCounter = 0;
            } else {
                stagnationCounter++;
                if (stagnationCounter >= STAGNATION_THRESHOLD) {
                    adaptiveMutationRate = Math.min(MUTATION_RATE_MAX,
                        adaptiveMutationRate + MUTATION_RATE_STEP);
                    stagnationCounter = 0;
                }
            }
        } else {
            stagnationCounter++;
            if (stagnationCounter >= STAGNATION_THRESHOLD) {
                stagnationCounter = 0;
            }
        }
        lastGenBestFitness = currentBestFitness;
    }
}
