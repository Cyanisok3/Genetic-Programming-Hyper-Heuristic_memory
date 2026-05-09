import java.io.Serializable;
import java.util.List;
import java.util.Random;

/**
 * Function nodes represent operators in the GP tree.
 * Functions have arity (number of children) and evaluate based on their children.
 */
public abstract class FunctionNode extends GPNode implements Serializable {
    
    public FunctionNode() {
        super();
    }
    
    public FunctionNode(List<GPNode> children) {
        super(children);
    }
    
    @Override
    public int getDepth() {
        if (children.isEmpty()) {
            return 1;
        }
        int maxChildDepth = 0;
        for (GPNode child : children) {
            maxChildDepth = Math.max(maxChildDepth, child.getDepth());
        }
        return 1 + maxChildDepth;
    }
}

/**
 * Addition: returns left + right
 */
class AddNode extends FunctionNode {
    
    public AddNode() {
        super();
    }
    
    public AddNode(GPNode left, GPNode right) {
        super();
        addChild(left);
        addChild(right);
    }
    
    @Override
    public double evaluate(BPPState state) {
        double left = children.get(0).evaluate(state);
        double right = children.get(1).evaluate(state);
        return left + right;
    }
    
    @Override
    public GPNode copy() {
        return new AddNode(children.get(0).copy(), children.get(1).copy());
    }
    
    @Override
    public void mutate(Random rand) {
        // No mutation for function nodes (they have fixed arity)
    }
    
    @Override
    public String toString() {
        return "+(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

/**
 * Subtraction: returns left - right
 */
class SubtractNode extends FunctionNode {
    
    public SubtractNode() {
        super();
    }
    
    public SubtractNode(GPNode left, GPNode right) {
        super();
        addChild(left);
        addChild(right);
    }
    
    @Override
    public double evaluate(BPPState state) {
        double left = children.get(0).evaluate(state);
        double right = children.get(1).evaluate(state);
        return left - right;
    }
    
    @Override
    public GPNode copy() {
        return new SubtractNode(children.get(0).copy(), children.get(1).copy());
    }
    
    @Override
    public void mutate(Random rand) {
        // No mutation for function nodes
    }
    
    @Override
    public String toString() {
        return "-(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

/**
 * Multiplication: returns left * right
 */
class MultiplyNode extends FunctionNode {
    
    public MultiplyNode() {
        super();
    }
    
    public MultiplyNode(GPNode left, GPNode right) {
        super();
        addChild(left);
        addChild(right);
    }
    
    @Override
    public double evaluate(BPPState state) {
        double left = children.get(0).evaluate(state);
        double right = children.get(1).evaluate(state);
        return left * right;
    }
    
    @Override
    public GPNode copy() {
        return new MultiplyNode(children.get(0).copy(), children.get(1).copy());
    }
    
    @Override
    public void mutate(Random rand) {
        // No mutation for function nodes
    }
    
    @Override
    public String toString() {
        return "*(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

/**
 * Protected division: returns left / right, or 1 if right is 0
 */
class DivideNode extends FunctionNode {
    
    private static final double PROTECTED_VALUE = 1.0;
    
    public DivideNode() {
        super();
    }
    
    public DivideNode(GPNode left, GPNode right) {
        super();
        addChild(left);
        addChild(right);
    }
    
    @Override
    public double evaluate(BPPState state) {
        double left = children.get(0).evaluate(state);
        double right = children.get(1).evaluate(state);
        if (Math.abs(right) < 1e-10) {
            return PROTECTED_VALUE;
        }
        return left / right;
    }
    
    @Override
    public GPNode copy() {
        return new DivideNode(children.get(0).copy(), children.get(1).copy());
    }
    
    @Override
    public void mutate(Random rand) {
        // No mutation for function nodes
    }
    
    @Override
    public String toString() {
        return "%(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

/**
 * FI Function: returns proportion of memory pieces less than threshold.
 * Takes one child as input (threshold value).
 */
class FIFunction extends FunctionNode {

    public FIFunction() {
        super();
    }

    public FIFunction(GPNode threshold) {
        super();
        addChild(threshold);
    }

    @Override
    public double evaluate(BPPState state) {
        double threshold = children.get(0).evaluate(state);
        return state.getMemory().getProportionBelow(threshold);
    }

    @Override
    public GPNode copy() {
        return new FIFunction(children.get(0).copy());
    }

    @Override
    public void mutate(Random rand) {
        // No mutation for function nodes
    }

    @Override
    public String toString() {
        return "FI(" + children.get(0) + ")";
    }
}

// =============================================================================
// CONDITIONAL FUNCTIONS (2026-05-09) — Literature: Jin et al. 2024; Quesada et al. 2025
// =============================================================================

/**
 * IFL (If Less Than): returns 1 if left < right, else 0.
 * Arity 2. Enables the GP to express conditional bin-selection preferences.
 *
 * Literature: Jin et al. (Memetic Computing 2024) used max(a,b) and max(a,0) as
 * the primary branching mechanisms. IFL is a more direct conditional operator that
 * returns a binary signal, enabling expressions like "IF item fits exactly THEN prefer".
 */
class IFLNode extends FunctionNode {

    public IFLNode() {
        super();
    }

    public IFLNode(GPNode left, GPNode right) {
        super();
        addChild(left);
        addChild(right);
    }

    @Override
    public double evaluate(BPPState state) {
        double left = children.get(0).evaluate(state);
        double right = children.get(1).evaluate(state);
        return (left < right) ? 1.0 : 0.0;
    }

    @Override
    public GPNode copy() {
        return new IFLNode(children.get(0).copy(), children.get(1).copy());
    }

    @Override
    public void mutate(Random rand) {
        // No mutation for function nodes
    }

    @Override
    public String toString() {
        return "IFL(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

/**
 * ITE (If-Then-Else): returns thenBranch if condition > 0, else elseBranch.
 * Arity 3. The fundamental building block for evolving rule-based heuristics.
 *
 * This is the most expressive function in the set — it allows the GP to create
 * multi-level conditional rules, e.g.:
 *   ITE(IFL(E, S), 1, ITE(IFL(E, %(S, 2)), 0.5, 0))
 * means: "if item fits (E>=S) give score 1; else if it fits at half size give 0.5; else 0"
 *
 * Literature: Standard in GP for decision problems; Jin et al. 2024 achieves similar
 * expressiveness through chained max(a,b) and max(a,0) operations.
 */
class ITENode extends FunctionNode {

    public ITENode() {
        super();
    }

    public ITENode(GPNode condition, GPNode thenBranch, GPNode elseBranch) {
        super();
        addChild(condition);
        addChild(thenBranch);
        addChild(elseBranch);
    }

    @Override
    public double evaluate(BPPState state) {
        double condition = children.get(0).evaluate(state);
        if (condition > 0) {
            return children.get(1).evaluate(state);
        } else {
            return children.get(2).evaluate(state);
        }
    }

    @Override
    public GPNode copy() {
        return new ITENode(
            children.get(0).copy(),
            children.get(1).copy(),
            children.get(2).copy()
        );
    }

    @Override
    public void mutate(Random rand) {
        // No mutation for function nodes
    }

    @Override
    public String toString() {
        return "ITE(" + children.get(0) + ", " + children.get(1) + ", " + children.get(2) + ")";
    }
}
