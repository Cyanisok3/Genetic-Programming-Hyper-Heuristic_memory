import java.io.Serializable;
import java.util.List;
import java.util.Random;

// Represents a mathematical expression tree used to score bin-packing decisions.
public abstract class GPNode implements Serializable {
    private static final long serialVersionUID = 1L;

    public abstract double evaluate(double S, double E, double C, double isNew);
    public abstract GPNode copy();
    public abstract int getDepth();
    public abstract int getSize();

    // Gathers all nodes in the tree for crossover and mutation operators.
    public void collectNodes(List<GPNode> list) {
        list.add(this);
        if (this instanceof GPFunction) {
            for (GPNode child : ((GPFunction) this).children) {
                child.collectNodes(list);
            }
        }
    }

    // Replaces a target node with a replacement node in the subtree.
    public boolean replaceChild(GPNode target, GPNode replacement) {
        if (this instanceof GPFunction) {
            GPNode[] children = ((GPFunction) this).children;
            for (int i = 0; i < children.length; i++) {
                if (children[i] == target) {
                    children[i] = replacement;
                    return true;
                }
                if (children[i].replaceChild(target, replacement)) return true;
            }
        }
        return false;
    }
}

// Base class for function nodes that have child nodes.
abstract class GPFunction extends GPNode {
    GPNode[] children;
    public GPFunction(GPNode... children) { this.children = children; }

    @Override public int getDepth() {
        int max = 0;
        for (GPNode c : children) max = Math.max(max, c.getDepth());
        return 1 + max;
    }

    @Override public int getSize() {
        int size = 1;
        for (GPNode c : children) size += c.getSize();
        return size;
    }
}

// +
class AddNode extends GPFunction {
    public AddNode(GPNode l, GPNode r) { super(l, r); }
    @Override public double evaluate(double S, double E, double C, double isNew) { return children[0].evaluate(S,E,C,isNew) + children[1].evaluate(S,E,C,isNew); }
    @Override public GPNode copy() { return new AddNode(children[0].copy(), children[1].copy()); }
    @Override public String toString() { return "+(" + children[0] + "," + children[1] + ")"; }
}

//-
class SubNode extends GPFunction {
    public SubNode(GPNode l, GPNode r) { super(l, r); }
    @Override public double evaluate(double S, double E, double C, double isNew) { return children[0].evaluate(S,E,C,isNew) - children[1].evaluate(S,E,C,isNew); }
    @Override public GPNode copy() { return new SubNode(children[0].copy(), children[1].copy()); }
    @Override public String toString() { return "-(" + children[0] + "," + children[1] + ")"; }
}

// *
class MulNode extends GPFunction {
    public MulNode(GPNode l, GPNode r) { super(l, r); }
    @Override public double evaluate(double S, double E, double C, double isNew) { return children[0].evaluate(S,E,C,isNew) * children[1].evaluate(S,E,C,isNew); }
    @Override public GPNode copy() { return new MulNode(children[0].copy(), children[1].copy()); }
    @Override public String toString() { return "*(" + children[0] + "," + children[1] + ")"; }
}

// /, protected against division by zero.
class DivNode extends GPFunction {
    public DivNode(GPNode l, GPNode r) { super(l, r); }
    @Override public double evaluate(double S, double E, double C, double isNew) {
        double denom = children[1].evaluate(S,E,C,isNew);
        return Math.abs(denom) < 1e-6 ? 1.0 : children[0].evaluate(S,E,C,isNew) / denom;
    }
    @Override public GPNode copy() { return new DivNode(children[0].copy(), children[1].copy()); }
    @Override public String toString() { return "/(" + children[0] + "," + children[1] + ")"; }
}

// Maximum of two expressions -- enables selecting the better of two scored alternatives.
// Not in the standard arithmetic GP function set; domain-specific to the bin-packing heuristic.
class MaxNode extends GPFunction {
    public MaxNode(GPNode l, GPNode r) { super(l, r); }
    @Override public double evaluate(double S, double E, double C, double isNew) { return Math.max(children[0].evaluate(S,E,C,isNew), children[1].evaluate(S,E,C,isNew)); }
    @Override public GPNode copy() { return new MaxNode(children[0].copy(), children[1].copy()); }
    @Override public String toString() { return "MAX(" + children[0] + "," + children[1] + ")"; }
}

// Conditional: if a <= b then c else d.
// Unlike standard GP which uses only arithmetic functions, this if-then-else operator allows the tree to encode context-dependent decision rules (e.g., "if bin is nearly full, prefer one strategy; otherwise prefer another").
class IfLteNode extends GPFunction {
    public IfLteNode(GPNode a, GPNode b, GPNode c, GPNode d) { super(a, b, c, d); }
    @Override public double evaluate(double S, double E, double C, double isNew) {
        return children[0].evaluate(S,E,C,isNew) <= children[1].evaluate(S,E,C,isNew) ? children[2].evaluate(S,E,C,isNew) : children[3].evaluate(S,E,C,isNew);
    }
    @Override public GPNode copy() { return new IfLteNode(children[0].copy(), children[1].copy(), children[2].copy(), children[3].copy()); }
    @Override public String toString() { return "IFLTE(" + children[0] + "," + children[1] + "," + children[2] + "," + children[3] + ")"; }
}

// S
class TerminalS extends GPNode {
    @Override public double evaluate(double S, double E, double C, double isNew) { return S; }
    @Override public GPNode copy() { return new TerminalS(); }
    @Override public int getDepth() { return 1; }
    @Override public int getSize() { return 1; }
    @Override public String toString() { return "S"; }
}

// E
class TerminalE extends GPNode {
    @Override public double evaluate(double S, double E, double C, double isNew) { return E; }
    @Override public GPNode copy() { return new TerminalE(); }
    @Override public int getDepth() { return 1; }
    @Override public int getSize() { return 1; }
    @Override public String toString() { return "E"; }
}

// IsNew: returns -1.0 for an existing bin, 1.0 for a new bin.
// This is a domain-specific signal terminal: it lets the GP tree distinguish between evaluating an existing bin (isNew=-1.0) and opening a fresh one (isNew=1.0)
class TerminalIsNew extends GPNode {
    @Override public double evaluate(double S, double E, double C, double isNew) { return isNew; }
    @Override public GPNode copy() { return new TerminalIsNew(); }
    @Override public int getDepth() { return 1; }
    @Override public int getSize() { return 1; }
    @Override public String toString() { return "IsNewBin"; }
}

// C
class TerminalConst extends GPNode {
    private double val;
    public TerminalConst(double val) { this.val = val; }
    @Override public double evaluate(double S, double E, double C, double isNew) { return val; }
    @Override public GPNode copy() { return new TerminalConst(val); }
    @Override public int getDepth() { return 1; }
    @Override public int getSize() { return 1; }
    @Override public String toString() { return String.format("%.2f", val); }
}

// Generates random GP trees for the initial population and mutation.
class GPTreeFactory {
    // Ephemeral random constant pool: only 4 values {0.1, 0.5, 1.0, 2.0}.
    // During subtree mutation, a new constant is drawn from this set to replace the selected node.
    private static final double[] CONSTANTS = {0.1, 0.5, 1.0, 2.0};

    // Creates a random tree up to the given depth limit.
    public static GPNode createRandomTree(int depthLimit, Random rand) {
        if (depthLimit <= 1 || (depthLimit < 6 && rand.nextDouble() < 0.2)) {
            int t = rand.nextInt(4);
            return switch(t) {
                case 0 -> new TerminalS();
                case 1 -> new TerminalE();
                case 2 -> new TerminalIsNew();
                default -> new TerminalConst(CONSTANTS[rand.nextInt(CONSTANTS.length)]);
            };
        }
        int f = rand.nextInt(6);
        return switch(f) {
            case 0 -> new AddNode(createRandomTree(depthLimit - 1, rand), createRandomTree(depthLimit - 1, rand));
            case 1 -> new SubNode(createRandomTree(depthLimit - 1, rand), createRandomTree(depthLimit - 1, rand));
            case 2 -> new MulNode(createRandomTree(depthLimit - 1, rand), createRandomTree(depthLimit - 1, rand));
            case 3 -> new DivNode(createRandomTree(depthLimit - 1, rand), createRandomTree(depthLimit - 1, rand));
            case 4 -> new MaxNode(createRandomTree(depthLimit - 1, rand), createRandomTree(depthLimit - 1, rand));
            default -> new IfLteNode(createRandomTree(depthLimit - 1, rand), createRandomTree(depthLimit - 1, rand),
                                     createRandomTree(depthLimit - 1, rand), createRandomTree(depthLimit - 1, rand));
        };
    }
}
