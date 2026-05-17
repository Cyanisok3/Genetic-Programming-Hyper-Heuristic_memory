import java.io.Serializable;

/**
 * Heuristic –> a GP tree.
 * Evaluates the GP tree to decide which bin to place the current item in.
 */
public class Heuristic implements Serializable {

    private GPNode root;

    public Heuristic(GPNode root) {
        this.root = root;
    }

    public double evaluate(BPPState state) {
        return root.evaluate(state);
    }

    public GPNode getRoot() {
        return root;
    }

    public Heuristic copy() {
        return new Heuristic(root.copy());
    }

    public int getDepth() {
        return root.getDepth();
    }

    public int getSize() {
        return root.getSize();
    }

    @Override
    public String toString() {
        return root.toString();
    }
}
