# GPHH-BPP Implementation

Genetic Programming Hyper-Heuristic for the online Bin Packing Problem, based on:

> Burke, E. K., Hyde, M. R., Kendall, G., & Woodward, J. (2010). *A genetic programming-based hyper-heuristic for the online two-dimensional bin packing problem.* IEEE Symposium on Computational Intelligence in Scheduling, 86-93.

## Execution

```bash
# Compile
javac -d out *.java

# Train (island model, no time limit, saves ensemble.ser)
java -cp out Main --train

# Solve with memory switching (Strategy 2a, default)
java -cp out Main -s dualdistribution/test/testdual4/binpack0.txt -o solution.txt -t 10000

# Solve with weighted voting (Strategy 2b)
java -cp out Main -s dualdistribution/test/testdual4/binpack0.txt -o solution.txt -t 10000 --weighted-voting
```

---

## Algorithm Overview

### Standard GP with Memory (Burke et al. 2010)

The GP evolves a **tree-structured heuristic** that, at each step, scores every feasible bin and selects the highest-scored one. The novelty over plain GP is the **Memory** mechanism: the last 100 placed pieces are stored, and terminal nodes can read statistical summaries of that history (MIN, MAX, AVE, FE, FL, FXE, FXL). This lets the heuristic adapt its behavior based on the observed piece-size distribution.

### This Implementation: Architecture

1. **Island Model Training** — 4 islands evolve independently with different depth configs; best heuristic from each island is saved to `ensemble.ser`
2. **Memory-Based Heuristic Switching (Strategy 2a)** — warmup with island A → classify distribution via `memory.getAverage()` → select best heuristic for this instance type
3. **Weighted Voting (Strategy 2b, opt-in)** — each heuristic scores each bin; weights from warmup AVE
4. **K×M Parallel Shuffle Ensemble (Strategy 3b)** — parallel ForkJoin across shuffles, each using the selected/weighted heuristic

---

## Terminal Set (11 terminals)


| #   | Symbol | Description                                                                 |
| --- | ------ | --------------------------------------------------------------------------- |
| 0   | S      | Current piece size                                                          |
| 1   | E      | Bin emptiness (capacity − fullness)                                         |
| 2   | L      | Space left after placing (E − S)                                            |
| 3   | MIN    | Minimum piece size in memory                                                |
| 4   | MAX    | Maximum piece size in memory                                                |
| 5   | AVE    | Average piece size in memory                                                |
| 6   | FE     | Fraction of memory pieces fitting into E                                    |
| 7   | FL     | Fraction of memory pieces fitting into L                                    |
| 8   | FXE    | Fraction of memory pieces with gap ≤ 3 into E (gap = E − size, 0 < gap ≤ 3) |
| 9   | FXL    | Fraction of memory pieces with gap ≤ 3 into L (gap = L − size, 0 < gap ≤ 3) |
| 10  | C      | Ephemeral constant, one of {0.2, 0.4, 0.6, 0.8, 1.0, 1.5, 2.0}              |


> **Note:** FXE and FXL use a **hardcoded threshold of 3** for the gap. This value is not exposed as a parameter.

---

## Function Set (6 functions)


| Symbol | Arity | Description                                                |
| ------ | ----- | ---------------------------------------------------------- |
| +      | 2     | Addition                                                   |
| -      | 2     | Subtraction                                                |
| *      | 2     | Multiplication                                             |
| %      | 2     | Protected division (returns 1 if divisor = 0)              |
| FI     | 1     | Fraction of memory items below threshold (proportionBelow) |
| IFL    | 4     | If-Less-Than: if a < b then c else d                       |


---

## Memory Mechanism

Tracks the **last 100 placed items** (FIFO queue). Enables terminals MIN, MAX, AVE, FE, FL, FXE, FXL to compute statistics over recent pieces.

- **Per-solve isolation**: each call to `BPPSolver.solveWithOrder()` creates a fresh `Memory` instance. Memory does not persist across training instance evaluations.

---

## GP Parameters


| Parameter           | Value                               | Notes                                                                             |
| ------------------- | ----------------------------------- | --------------------------------------------------------------------------------- |
| Population Size     | 1000                                |                                                                                   |
| Max Generations     | 30                                  |                                                                                   |
| Crossover Rate      | 85%                                 | Probability of selecting crossover; if selected, mutation may still apply (10%)   |
| Mutation Rate       | 10%                                 | Applied after crossover with 10% probability, or standalone with 10% probability  |
| Reproduction Rate   | 5%                                  | Probability of direct copy (no crossover, no mutation)                            |
| Tournament Size     | 7                                   |                                                                                   |
| Elite Size          | 2                                   | Top-2 individuals copied unchanged into next generation                           |
| Min Tree Depth      | 4                                   | Ramped half-and-half lower bound                                                  |
| Max Tree Depth      | 6                                   | Hard upper bound; nodes at depth ≥ MAX_DEPTH are excluded from crossover/mutation |
| Terminal Count      | 11                                  |                                                                                   |
| Tree Penalty Alpha  | 0.0                                 | No additive size penalty; lexicographic selection handles bloat                   |
| Ephemeral Constants | {0.2, 0.4, 0.6, 0.8, 1.0, 1.5, 2.0} | Jin et al. (Memetic Computing 2024)                                               |


---

## Fitness Function

Average of `bins_used / L2_bound` across all training instances. Lower is better.

L2 bound = max(ceil(sum(items)/C), count(items > C/2)) — Martello & Toth (1990) lower bound.

---

## Phase 1 Changes

### 1.1 FI Depth Explosion Fix

- `crossover()` and `mutate()` now use `collectValidNodes()` instead of `getAllNodes()`
- A node is only eligible if its depth < MAX_DEPTH (depth 0 = root)
- `mutate()` additionally computes remaining depth budget and caps new subtree at `min(roomLeft-1, MAX_DEPTH/2)`

### 1.2 Tree Size Control (Lexicographic Selection)

- Standard GP adds `alpha * treeSize` to fitness (additive penalty) — alpha is sensitive and hard to tune
- Instead: lexicographic comparison in tournament selection, population sort, and best tracking
- Primary: raw fitness (lower = better). Tie-break: tree size (smaller = better)
- Tree size only matters when fitness is already equal — no alpha parameter needed
- Naturally prefers compact trees without aggressive penalty that causes premature convergence
- `TREE_PENALTY_ALPHA` is hardcoded to 0.0
- Applied to: `tournamentSelect()`, `Population.sort()`, `Population.getBest()`, best-overall comparison in `evolveFull()`

### 1.3 IFL Conditional Function

- Added `IfLessThanNode` (arity 4): `if a < b then c else d`
- Updated `createRandomFunction()` to include IFL (6 function types)
- Updated `createTree()` to handle IFL arity 4
- Added static `createRandomTreeStatic()` factory for use in node-level mutation

### 1.4 Depth Constraint in Mutation

- `mutate()` picks from depth-constrained node pool (`collectValidNodes`)
- New mutation subtree depth capped at `max(2, min(roomLeft-1, MAX_DEPTH/2))`
- Ephemeral constant terminals: when selected for mutation, replaced with another constant from the set

### 1.5 Training Instance Set

- Burke 2010: 10 instances (5 per class, class1 + class2)
- This implementation: 20 instances (5 per class, class1 + class2 + class3 + class4）
- All 4 dual-distribution classes used to increase training coverage

---

## Phase 2 Changes

### 2.1 Test-Time Shuffle Ensemble (Axis 1)

The core insight is that online BPP is **deterministic given the item order**: the GP heuristic always makes the same placement decisions. Therefore, finding a better item order is equivalent to finding a better solution.

**Implementation:**

- `BPPSolver.solveWithOrder(items, heuristic, capacity, seed)` — accepts an optional random seed. When non-null, items are shuffled using a Fisher-Yates shuffle seeded by the given value, then solved normally. When null, items are processed in original order.
- `Main.runTestMode()` — runs as many shuffles as possible within the `maxTime` budget, keeping the best (fewest bins) solution. Seeds are derived from `System.nanoTime() ^ shuffleIndex` for near-perfect diversity.

**Results on testdual4/binpack0.txt (L2 bound = 2123):**


| Configuration            | Bins Used | Abs Gap | Shuffles |
| ------------------------ | --------- | ------- | -------- |
| Baseline (before Axis 1) | ~2315     | ~192    | 1        |
| Axis 1 shuffle ensemble  | 2310      | 187     | 402      |


**Key observations:**

- 402 shuffles completed within the 10s budget (~23ms per shuffle)
- Best shuffle found at index 219, improving over the original order by 5 bins
- The gap remains large (~187) because the evolved heuristic was trained exclusively on unimodal distributions and lacks intrinsic awareness of the bimodal (33/50 peak mix) structure in the test data
- The shuffle ensemble extracts the maximum possible from the current heuristic, but a better heuristic trained on bimodal data would be needed for further gains

### 2.2 Tail Buffer Re-optimization (BFD)

After the GP heuristic processes the first (N - 100) items, the last up to 100 items are re-optimized using **Best Fit Decreasing (BFD)** — sorted descending by size and placed into the bin with the smallest gap that fits each item. This leverages the global bin state that accumulates during Phase 1.

**Rationale:** The GP heuristic is greedy and online; it cannot look ahead. The buffer collects items that arrive late in the sequence, giving BFD a chance to optimally pack the remaining space. This is a simple but effective hybrid.

**Implementation:** `BPPSolver.solveWithOrderRaw()` — Phase 1 runs GP on items 0..(N-bufferSize-1); Phase 2 runs BFD on items (N-bufferSize)..(N-1).

**Ablation test (testdual4/binpack0.txt, 5000 items, same heuristic tree):**


| Version     | Bins             | Ratio         | Gap     |
| ----------- | ---------------- | ------------- | ------- |
| With buffer | 2303, 2305, 2304 | 1.0848–1.0857 | 180–182 |
| No buffer   | 2306, 2309, 2311 | 1.0862–1.0886 | 183–188 |


Buffer improves ~1–2 bins on average. Stable improvement confirmed.

---

## Phase 3: Island Model GP (Training)

### 3.1 Motivation

Training a single GP population on bimodal test data risks premature convergence to one peak (e.g., always optimizing for 33-mean behavior). Island Model addresses this by running multiple independent GP populations ("islands") with different configurations, then collecting their best heuristics into an ensemble.

### 3.2 Island Configuration

| Island | Seed | Min Depth | Max Depth | Diversity Strategy |
|--------|------|-----------|-----------|-------------------|
| A      | 42   | 4         | 6         | Balanced (base config) |
| B      | 137  | 3         | 5         | Compact trees (shallower) |
| C      | 256  | 4         | 6         | Deep: minD=4 (deeper than A/B), explores complex interactions |
| D      | 999  | 5         | 6         | Wide-medium trees (deeper min than A) |

**Diversity from:** different random seeds + different depth constraints. Islands B/C/D are not "worse" than A — they explore different parts of the heuristic search space.

### 3.3 Implementation

- `IslandModelGP.trainAll(trainingSet)` — runs 4 islands in parallel via `ExecutorService`, each calling `GeneticProgramming.evolveIsland(seed, minDepth, maxDepth, trainingSet)`
- `GeneticProgramming.evolveIsland()` — static factory; creates a private-constructor `GP(minDepth, maxDepth, seed)` instance and calls `evolveFull()` with island-specific depth params
- `crossover(parent1, parent2, maxDepth)` and `mutate(individual, minDepth, maxDepth)` — island-aware variants; the old no-arg versions delegate with `MAX_DEPTH` for backward compat
- All 4 islands evolve on the **same training set** (20 instances, all 4 classes)

### 3.4 Output

`ensemble.ser` — a `List<Heuristic>` of 4 heuristics, one per island (A/B/C/D).

---

## Phase 4: Dynamic Heuristic Switching + Parallel Ensemble (Test-Time)

### 4.1 Memory-Based Hard-Switching (Strategy 2a, default)

The key insight is that `memory.getAverage()` depends only on item sizes, not on which heuristic placed them. After placing just the first 50 items, AVE is enough to classify the distribution:

| AVE range | Likely class | Selected heuristic |
|-----------|-------------|-------------------|
| AVE > 45  | High-mean dominant | hC (deeper min depth) |
| AVE < 36  | Low-mean dominant  | hB (compact trees) |
| otherwise | Mixed/bimodal       | hA (balanced) |

**Flow:**
1. Run warmup with island A's heuristic on first 50 items — collect `memory.getAverage()`, discard placements
2. Use AVE to select one heuristic
3. Re-shuffle items and solve with the selected heuristic

### 4.2 Weighted Voting (Strategy 2b, opt-in via `--weighted-voting`)

Each heuristic produces a score per bin. Final score = `sum(w_i * score_i)`, where weights are derived from warmup AVE:

```
w_bimodal = max(0, 1 - |AVE - 40.5| / 8.5)
if (w_bimodal < 0.3:
    # Unimodal zone: use closest specialist only
    w_closest = 1.0
else:
    # Bimodal zone: balanced (hA) + closest specialist
    wA = 0.5, w_specialist = 0.5
```

Note: the bimodal center shifts from 41.5 to 40.5 because theAVE=42.1 threshold change (41→45) changes the "high" zone interpretation. The weight formula centers on the bimodal gap midpoint (50+33)/2 ≈ 41.5 adjusted to 40.5 to better align with the revised hard-switch thresholds.

### 4.3 K×M Parallel Shuffle Ensemble (Strategy 3b)

The **top-level warmup** runs once per test run, producing a decision (which heuristic or which weights). Then:

- Multiple shuffle tasks run in parallel via `ForkJoinPool` (batch size = 32)
- Each `ShuffleTask` evaluates one shuffle with the pre-selected strategy
- Best solution across all shuffles is returned

Implementation: `ParallelShuffleEnsemble.run(items, timeLimitMs)`.

### 4.4 Execution

```bash
# Default: island ensemble + memory switching (2a) + parallel shuffles (3b)
java -cp out Main -s instance -o solution.txt -t 10000

# Alternative: weighted voting (2b)
java -cp out Main -s instance -o solution.txt -t 10000 --weighted-voting
```

---

## Training Data Statistics

Items per instance: 500. Distribution modality detected via KDE with Silverman bandwidth.


| File           | Items    | Mean      | S.D.     | Modality |
| -------------- | -------- | --------- | -------- | -------- |
| binpack0       | 500      | 49.98     | 5.07     | Unimodal |
| binpack1       | 500      | 50.30     | 4.98     | Unimodal |
| binpack2       | 500      | 49.81     | 4.82     | Unimodal |
| binpack3       | 500      | 50.09     | 5.03     | Unimodal |
| binpack4       | 500      | 50.16     | 5.20     | Unimodal |
| **class1 avg** | **2500** | **50.07** | **5.02** | —        |



| File           | Items    | Mean      | S.D.     | Modality |
| -------------- | -------- | --------- | -------- | -------- |
| binpack5       | 500      | 33.16     | 4.77     | Unimodal |
| binpack6       | 500      | 33.20     | 5.02     | Unimodal |
| binpack7       | 500      | 33.18     | 5.21     | Unimodal |
| binpack8       | 500      | 32.67     | 4.83     | Unimodal |
| binpack9       | 500      | 32.86     | 4.97     | Unimodal |
| **class2 avg** | **2500** | **33.01** | **4.96** | —        |



| File           | Items    | Mean      | S.D.      | Modality |
| -------------- | -------- | --------- | --------- | -------- |
| binpack10      | 500      | 49.98     | 9.84      | Unimodal |
| binpack11      | 500      | 50.38     | 9.85      | Unimodal |
| binpack12      | 500      | 49.72     | 10.39     | Unimodal |
| binpack13      | 500      | 51.03     | 10.08     | Unimodal |
| binpack14      | 500      | 50.49     | 9.84      | Unimodal |
| **class3 avg** | **2500** | **50.32** | **10.00** | —        |



| File           | Items    | Mean      | S.D.     | Modality |
| -------------- | -------- | --------- | -------- | -------- |
| binpack15      | 500      | 33.54     | 10.05    | Unimodal |
| binpack16      | 500      | 33.02     | 10.37    | Unimodal |
| binpack17      | 500      | 32.96     | 9.79     | Unimodal |
| binpack18      | 500      | 32.23     | 9.84     | Unimodal |
| binpack19      | 500      | 32.69     | 9.70     | Unimodal |
| **class4 avg** | **2500** | **32.89** | **9.95** | —        |


### Training Data Summary


| Class  | Distribution      | Mean  | S.D.  | Items |
| ------ | ----------------- | ----- | ----- | ----- |
| class1 | Unimodal Gaussian | 50.07 | 5.02  | 2500  |
| class2 | Unimodal Gaussian | 33.01 | 4.96  | 2500  |
| class3 | Unimodal Gaussian | 50.32 | 10.00 | 2500  |
| class4 | Unimodal Gaussian | 32.89 | 9.95  | 2500  |


Note: class1 and class3 share the same mean (~~50) but differ in variance (S.D. 5 vs 10); class2 and class4 share the same mean (~~33) but differ in variance (S.D. 5 vs 10). Two instances (binpack0, binpack9) showed marginal bimodality in the KDE — likely noise from finite sample size.

---

## Test Data Statistics

Items per instance: 5000. Each testdual contains 20 instances (binpack0–binpack19).


| Dataset   | Instances | Total Items | Mean  | S.D.  | Modality         |
| --------- | --------- | ----------- | ----- | ----- | ---------------- |
| testdual0 | 20        | 100000      | 50.01 | 5.01  | Bimodal Gaussian |
| testdual4 | 20        | 100000      | 42.47 | 9.01  | Bimodal Gaussian |
| testdual8 | 20        | 100000      | 42.50 | 10.90 | Bimodal Gaussian |


Note: All three remaining test sets are bimodal distributions, testing the heuristic's ability to handle mixed Gaussian distributions.

---

## File Structure

```
GPHH_BPP/
├── Main.java               # Entry point (--train or -s/-o/-t modes)
├── BPPInstance.java        # Problem instance loader
├── BPPSolver.java          # Online BPP solver + memory switching (Strategy 2a/2b)
├── BPPState.java           # State for heuristic evaluation
├── Bin.java                # Bin representation
├── Solution.java           # Solution representation
├── Memory.java             # Memory mechanism (last 100 items, FIFO)
├── L2BoundCalculator.java  # L2 lower bound (Martello & Toth 1990)
├── GeneticProgramming.java # GP evolution engine + island-aware crossover/mutation
├── GPNode.java             # Abstract GP tree node
├── FunctionNode.java       # Function nodes (+, -, *, %, FI, IFL)
├── TerminalNode.java       # Terminal nodes (11 types above)
├── Heuristic.java         # Heuristic wrapper for GP tree
├── Individual.java         # GP individual (tree + fitness + lexicographic comparison)
├── Population.java         # GP population
├── IslandModelGP.java     # Island Model: 4-thread parallel island training
├── ParallelShuffleEnsemble.java  # K×M matrix ForkJoin parallel ensemble
└── ensemble.ser           # Trained ensemble (4 heuristics, generated by --train)
```

