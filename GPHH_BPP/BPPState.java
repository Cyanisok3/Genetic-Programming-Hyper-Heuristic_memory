/**
 * Represents the current state during BPP solving.
 * Used by terminal nodes to evaluate their values.
 */
public class BPPState {

    private final int[] items;
    private int currentPosition;
    private int binFullness;
    private final int binCapacity;
    private Memory memory;

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

    public void nextItem() {
        currentPosition++;
        if (currentPosition < items.length) {
            memory.addItem(items[currentPosition - 1]);
        }
    }

    public void placeItem(int itemSize) {
        binFullness += itemSize;
    }

    public void startNewBin(int itemSize) {
        binFullness = itemSize;
    }

    public void resetBin() {
        binFullness = 0;
    }

    public int getPieceSize() {
        if (currentPosition >= items.length) return 0;
        return items[currentPosition];
    }

    public int getEmptiness() {
        return binCapacity - binFullness;
    }

    public int getSpaceLeftAfterPlacing() {
        return getEmptiness() - getPieceSize();
    }

    public int getBinFullness() {
        return binFullness;
    }

    public int getBinCapacity() {
        return binCapacity;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public int getItemCount() {
        return items.length;
    }

    public int[] getItems() {
        return items;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public void setBinFullness(int fullness) {
        this.binFullness = fullness;
    }

    public boolean isFinished() {
        return currentPosition >= items.length;
    }

    public BPPState copy() {
        return new BPPState(this);
    }
}
