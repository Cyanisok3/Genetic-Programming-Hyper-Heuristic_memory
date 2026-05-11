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
