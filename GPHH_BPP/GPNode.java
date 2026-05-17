import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Abstract base class for all nodes in the GP tree.
 */
public abstract class GPNode implements Serializable {

    protected GPNode parent;
    protected List<GPNode> children;

    public GPNode() {
        this.children = new ArrayList<>();
    }

    public GPNode(List<GPNode> children) {
        this.children = children;
    }
    
    /**
     * Evaluate this node given the current BPP state.
     * @param state Current state of the bin packing problem
     * @return The evaluated value
     */
    public abstract double evaluate(BPPState state);
    
    /**
     * Create a deep copy of this node.
     * @return A deep copy of this node
     */
    public abstract GPNode copy();

    public abstract int getDepth();

    public int getSize() {
        int size = 1;
        for (GPNode child : children) {
            size += child.getSize();
        }
        return size;
    }
    
    /**
     * Mutate this node.
     * @param rand Random number generator
     */
    public abstract void mutate(Random rand);
    
    public boolean replaceNode(GPNode target, GPNode replacement) {
        if (this == target) {
            return false;
        }
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == target) {
                replacement.parent = this;
                children.set(i, replacement);
                return true;
            }
            if (children.get(i).replaceNode(target, replacement)) {
                return true;
            }
        }
        return false;
    }
    
    // Getters and setters
    public GPNode getParent() {
        return parent;
    }

    public void setParent(GPNode parent) {
        this.parent = parent;
    }

    public List<GPNode> getChildren() {
        return new ArrayList<>(children);
    }

    public void addChild(GPNode child) {
        child.setParent(this);
        children.add(child);
    }
}
