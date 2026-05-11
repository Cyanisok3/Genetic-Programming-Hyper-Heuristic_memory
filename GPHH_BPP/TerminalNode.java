import java.io.Serializable;
import java.util.Random;

/**
 * Terminal nodes represent constants or state variables in the GP tree.
 * Each terminal has arity 0 (no children).
 */
abstract class TerminalNode extends GPNode {
    
    public TerminalNode() {
        super();
    }
    
    @Override
    public int getDepth() {
        return 1;
    }
    
    @Override
    public void addChild(GPNode child) {
        throw new UnsupportedOperationException("Terminal nodes cannot have children");
    }
}

/**
 * S - Current piece size
 */
class PieceSizeTerminal extends TerminalNode {
    
    @Override
    public double evaluate(BPPState state) {
        return state.getPieceSize();
    }
    
    @Override
    public GPNode copy() {
        return new PieceSizeTerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated (no children to replace)
    }
    
    @Override
    public String toString() {
        return "S";
    }
}

/**
 * E - Bin emptiness (capacity - fullness)
 */
class BinEmptinessTerminal extends TerminalNode {
    
    @Override
    public double evaluate(BPPState state) {
        return state.getEmptiness();
    }
    
    @Override
    public GPNode copy() {
        return new BinEmptinessTerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "E";
    }
}

/**
 * L - Space left after placing (E - S)
 */
class SpaceLeftTerminal extends TerminalNode {
    
    @Override
    public double evaluate(BPPState state) {
        return state.getSpaceLeftAfterPlacing();
    }
    
    @Override
    public GPNode copy() {
        return new SpaceLeftTerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "L";
    }
}

/**
 * MIN - Minimum piece size in memory
 */
class MemoryMinTerminal extends TerminalNode {
    
    @Override
    public double evaluate(BPPState state) {
        return state.getMemory().getMin();
    }
    
    @Override
    public GPNode copy() {
        return new MemoryMinTerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "MIN";
    }
}

/**
 * MAX - Maximum piece size in memory
 */
class MemoryMaxTerminal extends TerminalNode {
    
    @Override
    public double evaluate(BPPState state) {
        return state.getMemory().getMax();
    }
    
    @Override
    public GPNode copy() {
        return new MemoryMaxTerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "MAX";
    }
}

/**
 * FE - Proportion of memory pieces that fit into space E
 */
class MemoryFETerminal extends TerminalNode {
    
    @Override
    public double evaluate(BPPState state) {
        return state.getMemory().getFittingRatio(state.getEmptiness());
    }
    
    @Override
    public GPNode copy() {
        return new MemoryFETerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "FE";
    }
}

/**
 * FL - Proportion of memory pieces that fit into space L
 */
class MemoryFLTerminal extends TerminalNode {
    
    @Override
    public double evaluate(BPPState state) {
        return state.getMemory().getFittingRatio(state.getSpaceLeftAfterPlacing());
    }
    
    @Override
    public GPNode copy() {
        return new MemoryFLTerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "FL";
    }
}

/**
 * AVE - Average piece size in memory
 */
class MemoryAveTerminal extends TerminalNode {

    @Override
    public double evaluate(BPPState state) {
        return state.getMemory().getAverage();
    }

    @Override
    public GPNode copy() {
        return new MemoryAveTerminal();
    }

    @Override
    public void mutate(Random rand) {
    }

    @Override
    public String toString() {
        return "AVE";
    }
}

/**
 * FXE - Proportion of memory pieces that almost exactly fit E (gap <= 3)
 */
class MemoryFXETerminal extends TerminalNode {
    
    private static final int GAP_THRESHOLD = 3;
    
    @Override
    public double evaluate(BPPState state) {
        return state.getMemory().getExactFittingRatio(state.getEmptiness(), GAP_THRESHOLD);
    }
    
    @Override
    public GPNode copy() {
        return new MemoryFXETerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "FXE";
    }
}

/**
 * FXL - Proportion of memory pieces that almost exactly fit L (gap <= 3)
 */
class MemoryFXLTerminal extends TerminalNode {
    
    private static final int GAP_THRESHOLD = 3;
    
    @Override
    public double evaluate(BPPState state) {
        return state.getMemory().getExactFittingRatio(state.getSpaceLeftAfterPlacing(), GAP_THRESHOLD);
    }
    
    @Override
    public GPNode copy() {
        return new MemoryFXLTerminal();
    }
    
    @Override
    public void mutate(Random rand) {
        // Terminals cannot be mutated
    }
    
    @Override
    public String toString() {
        return "FXL";
    }
}

/**
 * Ephemeral random constant — a floating-point literal sampled from a fixed set.
 * Literature: Jin et al. (Memetic Computing 2024) used ephemeral constants
 * {0.2, 0.4, 0.6, 0.8, 1.0, 1.5, 2.0} to allow heuristics to learn
 * adaptive thresholds (e.g., "if S > 0.5 then ...").
 *
 * Mutation is handled at the GeneticProgramming level.
 */
class EphemeralConstantTerminal extends TerminalNode {

    static final double[] CONSTANTS = {0.2, 0.4, 0.6, 0.8, 1.0, 1.5, 2.0};

    private final double value;

    public EphemeralConstantTerminal() {
        super();
        this.value = CONSTANTS[(int) (Math.random() * CONSTANTS.length)];
    }

    public EphemeralConstantTerminal(double value) {
        super();
        this.value = value;
    }

    @Override
    public double evaluate(BPPState state) {
        return value;
    }

    @Override
    public GPNode copy() {
        return new EphemeralConstantTerminal(value);
    }

    @Override
    public void mutate(Random rand) {
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "C" + (value == (int) value ? String.valueOf((int) value) : String.valueOf(value));
    }
}
