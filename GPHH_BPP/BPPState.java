/**
 * Represents the current state during BPP solving.
 * Used by terminal nodes to evaluate their values.
 */
public class BPPState {
    
    private int[] items;           // All items in the instance
    private int currentPosition;    // Index of current item being placed (0-based)
    private int binFullness;        // Current bin's used space
    private int binCapacity;        // Bin capacity (constant)
    private Memory memory;           // Memory of last 100 items
    
    public BPPState(int[] items, int binCapacity) {
        this.items = items;
        this.binCapacity = binCapacity;
        this.currentPosition = 0;
        this.binFullness = 0;
        this.memory = new Memory();
    }
    
    public BPPState(BPPState other) {
        this.items = other.items;
        this.binCapacity = other.binCapacity;
        this.currentPosition = other.currentPosition;
        this.binFullness = other.binFullness;
        this.memory = other.memory.copy();
    }
    
    /**
     * Move to the next item (after placing current one).
     */
    public void nextItem() {
        currentPosition++;
        if (currentPosition < items.length) {
            memory.addItem(items[currentPosition - 1]);
        }
    }
    
    /**
     * Place current item in bin, increasing fullness.
     * @param itemSize Size of the item being placed
     */
    public void placeItem(int itemSize) {
        binFullness += itemSize;
    }
    
    /**
     * Start a new bin with current item.
     * @param itemSize Size of the item being placed
     */
    public void startNewBin(int itemSize) {
        binFullness = itemSize;
    }
    
    /**
     * Reset state for a new bin (emptiness becomes full capacity minus fullness).
     */
    public void resetBin() {
        binFullness = 0;
    }
    
    // Getters for terminal evaluation
    
    /**
     * Get current piece size.
     * @return Size of current item
     */
    public int getPieceSize() {
        if (currentPosition >= items.length) {
            return 0;
        }
        return items[currentPosition];
    }
    
    /**
     * Get bin emptiness (space remaining in current bin).
     * @return Capacity minus fullness
     */
    public int getEmptiness() {
        return binCapacity - binFullness;
    }
    
    /**
     * Get space left after placing current item.
     * @return Emptiness minus piece size
     */
    public int getSpaceLeftAfterPlacing() {
        return getEmptiness() - getPieceSize();
    }
    
    /**
     * Get bin fullness (space already used).
     * @return Fullness
     */
    public int getBinFullness() {
        return binFullness;
    }
    
    /**
     * Get bin capacity.
     * @return Capacity
     */
    public int getBinCapacity() {
        return binCapacity;
    }
    
    /**
     * Get current position (item index).
     * @return Position
     */
    public int getCurrentPosition() {
        return currentPosition;
    }
    
    /**
     * Get total number of items.
     * @return Item count
     */
    public int getItemCount() {
        return items.length;
    }
    
    /**
     * Get all items.
     * @return Items array
     */
    public int[] getItems() {
        return items;
    }
    
    /**
     * Get memory.
     * @return Memory instance
     */
    public Memory getMemory() {
        return memory;
    }
    
    // Setters for BPPSolver
    
    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }
    
    public void setBinFullness(int fullness) {
        this.binFullness = fullness;
    }
    
    public void setMemory(Memory memory) {
        this.memory = memory;
    }
    
    /**
     * Check if all items have been processed.
     * @return true if currentPosition >= items.length
     */
    public boolean isFinished() {
        return currentPosition >= items.length;
    }
    
    /**
     * Create a copy of this state.
     * @return Deep copy
     */
    public BPPState copy() {
        return new BPPState(this);
    }
}
