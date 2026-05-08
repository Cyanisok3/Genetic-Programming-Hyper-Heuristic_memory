import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single bin in the bin packing problem.
 */
public class Bin {
    
    private int capacity;
    private int fullness;
    private List<Integer> items;  // Item indices
    
    public Bin(int capacity) {
        this.capacity = capacity;
        this.fullness = 0;
        this.items = new ArrayList<>();
    }
    
    public Bin(Bin other) {
        this.capacity = other.capacity;
        this.fullness = other.fullness;
        this.items = new ArrayList<>(other.items);
    }
    
    /**
     * Add an item to this bin.
     * @param itemIndex Index of the item (0-based)
     * @param itemSize Size of the item
     * @return true if item was added successfully
     */
    public boolean addItem(int itemIndex, int itemSize) {
        if (!canFit(itemSize)) {
            return false;
        }
        items.add(itemIndex);
        fullness += itemSize;
        return true;
    }
    
    /**
     * Check if an item of given size can fit in this bin.
     * @param itemSize Size to check
     * @return true if item fits
     */
    public boolean canFit(int itemSize) {
        return fullness + itemSize <= capacity;
    }
    
    /**
     * Get remaining space in this bin.
     * @return Available space
     */
    public int getEmptiness() {
        return capacity - fullness;
    }
    
    /**
     * Get current fullness.
     * @return Fullness
     */
    public int getFullness() {
        return fullness;
    }
    
    /**
     * Get capacity.
     * @return Capacity
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Get items in this bin.
     * @return List of item indices
     */
    public List<Integer> getItems() {
        return new ArrayList<>(items);
    }
    
    /**
     * Get number of items in this bin.
     * @return Item count
     */
    public int getItemCount() {
        return items.size();
    }
    
    @Override
    public String toString() {
        return "Bin(" + fullness + "/" + capacity + ", " + items.size() + " items)";
    }
}
