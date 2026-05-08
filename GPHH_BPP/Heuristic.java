import java.io.Serializable;

/**
 * Heuristic wrapper for a GP tree.
 * Evaluates the GP tree to decide which bin to place the current item in.
 */
public class Heuristic implements Serializable {
    
    private GPNode root;
    
    public Heuristic(GPNode root) {
        this.root = root;
    }
    
    /**
     * Evaluate the heuristic for placing current item in a bin.
     * @param state Current BPP state
     * @return Heuristic score (higher = better fit)
     */
    public double evaluate(BPPState state) {
        return root.evaluate(state);
    }
    
    /**
     * Get the root node of the GP tree.
     * @return Root node
     */
    public GPNode getRoot() {
        return root;
    }
    
    /**
     * Create a deep copy of this heuristic.
     * @return Copy
     */
    public Heuristic copy() {
        return new Heuristic(root.copy());
    }
    
    /**
     * Get the tree depth.
     * @return Depth
     */
    public int getDepth() {
        return root.getDepth();
    }
    
    /**
     * Get the number of nodes in the tree.
     * @return Size
     */
    public int getSize() {
        return root.getSize();
    }
    
    @Override
    public String toString() {
        return root.toString();
    }
}
