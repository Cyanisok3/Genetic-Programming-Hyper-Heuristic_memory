import java.io.FileInputStream;
import java.io.ObjectInputStream;

/**
 * Deserialize the heuristic from the file and print the structure. 
 * (I admit it's troublesome to use .ser file to store the heuristics..)
 */

public class DeserializeHeuristic {
    public static void main(String[] args) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream("best_heuristic.ser"))) {
            Object obj = in.readObject();
            System.out.println("=== Heuristic Structure ===");
            System.out.println("Type: " + obj.getClass().getSimpleName());
            if (obj instanceof Heuristic) {
                Heuristic h = (Heuristic) obj;
                System.out.println("Tree size: " + h.getSize() + " nodes");
                System.out.println("Tree depth: " + h.getDepth());
                System.out.println();
                System.out.println("=== Tree Structure ===");
                System.out.println(h);
            }
        }
    }
}
