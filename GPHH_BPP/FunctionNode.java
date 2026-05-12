import java.io.Serializable;
import java.util.List;
import java.util.Random;

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
    }

    @Override
    public String toString() {
        return "+(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

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
    }

    @Override
    public String toString() {
        return "-(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

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
    }

    @Override
    public String toString() {
        return "*(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

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
    }

    @Override
    public String toString() {
        return "%(" + children.get(0) + ", " + children.get(1) + ")";
    }
}

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
    }

    @Override
    public String toString() {
        return "FI(" + children.get(0) + ")";
    }
}

class IfLessThanNode extends FunctionNode {

    public IfLessThanNode() {
        super();
    }

    public IfLessThanNode(GPNode a, GPNode b, GPNode c, GPNode d) {
        super();
        addChild(a);
        addChild(b);
        addChild(c);
        addChild(d);
    }

    @Override
    public double evaluate(BPPState state) {
        double a = children.get(0).evaluate(state);
        double b = children.get(1).evaluate(state);
        double c = children.get(2).evaluate(state);
        double d = children.get(3).evaluate(state);
        return (a < b) ? c : d;
    }

    @Override
    public GPNode copy() {
        return new IfLessThanNode(
            children.get(0).copy(),
            children.get(1).copy(),
            children.get(2).copy(),
            children.get(3).copy()
        );
    }

    @Override
    public void mutate(Random rand) {
    }

    @Override
    public String toString() {
        return "IFL(" + children.get(0) + ", " + children.get(1) + ", " +
               children.get(2) + ", " + children.get(3) + ")";
    }
}
