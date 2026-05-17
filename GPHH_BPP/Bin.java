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

    public boolean addItem(int itemIndex, int itemSize) {
        if (!canFit(itemSize)) {
            return false;
        }
        items.add(itemIndex);
        fullness += itemSize;
        return true;
    }

    public boolean canFit(int itemSize) {
        return fullness + itemSize <= capacity;
    }

    public int getEmptiness() {
        return capacity - fullness;
    }

    public int getFullness() {
        return fullness;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<Integer> getItems() {
        return new ArrayList<>(items);
    }

    // String representation of the bin
    @Override
    public String toString() {
        return "Bin(" + fullness + "/" + capacity + ", " + items.size() + " items)";
    }
}
