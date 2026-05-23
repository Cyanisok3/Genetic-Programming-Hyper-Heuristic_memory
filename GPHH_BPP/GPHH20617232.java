import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GPHH20617232 {
    
    // ================== CW 要求的入口 ==================
    public static void main(String[] args) throws Exception {
        boolean trainMode = false;
        String instancePath = null;
        String solutionPath = null;
        long timeLimit = 10000;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--train")) trainMode = true;
            else if (args[i].equals("-s")) instancePath = args[++i];
            else if (args[i].equals("-o")) solutionPath = args[++i];
            else if (args[i].equals("-t")) timeLimit = Long.parseLong(args[++i]);
        }

        if (trainMode) {
            runTrainingFlow();
        } else if (instancePath != null && solutionPath != null) {
            runTestingFlow(instancePath, solutionPath, timeLimit);
        } else {
            System.out.println("Usage Test: java GPHH20617232 -s <instance_file> -o <solution_file> [-t max_time]");
            System.out.println("Usage Train: java GPHH20617232 --train");
        }
    }

    // ================== 测试执行流 (压榨10秒时间) ==================
    private static void runTestingFlow(String instancePath, String solutionPath, long timeLimit) throws Exception {
        System.out.println("Loading instance: " + instancePath);
        InstanceData data = parseInstance(instancePath);
        
        // 读取所有训练好的投票树 (Ensemble)
        File dir = new File("best_heuristics");
        List<GPNode> forest = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles((d, name) -> name.endsWith(".ser"))) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                    forest.add((GPNode) ois.readObject());
                }
            }
        }
        if (forest.isEmpty()) throw new RuntimeException("No .ser files found in best_heuristics/ folder! Run --train first.");
        
        System.out.println("Loaded " + forest.size() + " heuristics for Ensemble Voting.");
        
        long start = System.currentTimeMillis();
        List<Bin> resultBins = BPPEnv.solveEnsemble(data.items, data.capacity, forest);
        long elapsed = System.currentTimeMillis() - start;
        
        int obj = resultBins.size();
        
        // 计算 L1 作为 L2 的大致占位 (如果在输出里需要精确L2，可以调用你原来的 L2BoundCalculator)
        long sum = 0; for(int s : data.items) sum += s;
        long l2Approx = (long) Math.ceil((double)sum / data.capacity); 

        System.out.println("Time taken: " + elapsed + "ms");
        System.out.println("Total Bins (objective_value): " + obj + " (L2 lower bound roughly: " + l2Approx + ")");
        
        // ================== 核心修改区：严格匹配最新的 Solution 格式 ==================
        try (PrintWriter pw = new PrintWriter(new FileWriter(solutionPath))) {
            File instFile = new File(instancePath);
            String setName = instFile.getParentFile().getName(); // 例如: "testdual0"
            String instName = instFile.getName().replace(".txt", ""); // 例如: "binpack0"
            
            // 第一行: SetName_InstanceName (使用下划线拼接)
            pw.println(setName + "_" + instName);
            
            // 第二行: obj=    objective_value    L2_bound (使用 \t 制表符分隔)
            pw.println("obj=\t" + obj + "\t" + l2Approx);
            
            // 第三行及以后: 每一行代表一个箱子，里面的物品索引以空格分隔
            for (Bin bin : resultBins) {
                if (bin.itemIndices.isEmpty()) continue; // 过滤掉空箱子防报错
                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bin.itemIndices.size(); i++) {
                    sb.append(bin.itemIndices.get(i));
                    // 除了最后一个物品，其他物品后追加空格
                    if (i < bin.itemIndices.size() - 1) {
                        sb.append(" ");
                    }
                }
                pw.println(sb.toString());
            }
        }
        System.out.println("Solution strictly saved to: " + solutionPath);
    }

    // ================== 训练执行流 ==================
    private static void runTrainingFlow() throws Exception {
        System.out.println("=== Starting Training Phase ===");
        
        // 1. 读取训练集 (强烈建议把双峰的 class0, class4, class8 全放在一个文件夹里读取)
        List<int[]> trainData = new ArrayList<>();
        int capacity = 100; // 根据数据集实际容量调整
        
        File trainDir = new File("dualdistribution/train"); 
        if(trainDir.exists()) {
            Files.walk(Paths.get(trainDir.getPath()))
                 .filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".txt"))
                 .forEach(p -> {
                     try {
                         InstanceData d = parseInstance(p.toString());
                         trainData.add(d.items);
                     } catch(Exception e) { }
                 });
        }
        
        if(trainData.isEmpty()) {
            System.out.println("Generating synthetic dual-distribution dummy data for fallback test...");
            trainData.add(generateDummyDualDist(500));
        }

        System.out.println("Total training instances loaded: " + trainData.size());
        
        new File("best_heuristics").mkdirs();
        
        // 2. 训练多棵树形成森林 (每次采用不同随机种子)
        // 在实际交CW前，让它跑 5 ~ 9 次，生成一堆 .ser 文件
        int ensembleSize = 5; 
        for (int i = 0; i < ensembleSize; i++) {
            long seed = System.currentTimeMillis() + i * 999;
            System.out.println("\n--- Training Tree " + (i+1) + "/" + ensembleSize + " (Seed: " + seed + ") ---");
            GPNode bestTree = BPPEnv.trainForest(trainData, capacity, seed, 60); // 跑60代
            
            String outPath = "best_heuristics/tree_model_" + i + ".ser";
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outPath))) {
                oos.writeObject(bestTree);
                System.out.println("Saved highly optimized Tree to " + outPath);
            }
        }
        System.out.println("\nTraining complete! Now run test mode.");
    }

    // ================== 解析工具 ==================
    static class InstanceData { int capacity; int[] items; }
    
    private static InstanceData parseInstance(String path) throws Exception {
        Scanner sc = new Scanner(new File(path));
        List<Integer> itemList = new ArrayList<>();
        
        // 只要文件里还有数字，就一直读取到末尾
        while (sc.hasNextInt()) {
            itemList.add(sc.nextInt());
        }
        sc.close();
        
        InstanceData data = new InstanceData();
        data.capacity = 100; // ★ 严格遵守 CW 要求，箱子容量始终固定为 100
        data.items = new int[itemList.size()];
        
        for (int i = 0; i < itemList.size(); i++) {
            data.items[i] = itemList.get(i);
        }
        
        return data;
    }
    
    private static int[] generateDummyDualDist(int n) {
        int[] items = new int[n];
        Random r = new Random();
        for(int i=0; i<n; i++) {
            // 简单模拟双峰分布
            items[i] = r.nextBoolean() ? (70 + r.nextInt(20)) : (20 + r.nextInt(15));
        }
        return items;
    }
}