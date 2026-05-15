# GPHH-BPP Implementation

Genetic Programming Hyper-Heuristic for the online Bin Packing Problem, based on:

> Burke, E. K., Hyde, M. R., Kendall, G., & Woodward, J. (2010). *A genetic programming-based hyper-heuristic for the online two-dimensional bin packing problem.* IEEE Symposium on Computational Intelligence in Scheduling, 86-93.

## Execution

```bash
# Compile
javac -d out *.java

# Train (no time limit, saves best_heuristic.ser)
java -cp out Main --train

# Solve a test instance (runs multiple trials within 10s, keeps best)
java -cp out Main -s dualdistribution/test/testdual4/binpack0.txt -o solution.txt -t 10000
```

---

## Algorithm Overview

### Standard GP with Memory (Burke et al. 2010)

The GP evolves a **tree-structured heuristic** that, at each step, scores every feasible bin and selects the highest-scored one. The novelty over plain GP is the **Memory** mechanism: the last 100 placed pieces are stored, and terminal nodes can read statistical summaries of that history (MIN, MAX, AVE, FE, FL, FXE, FXL). This lets the heuristic adapt its behavior based on the observed piece-size distribution.

### This Implementation: Deviations from Burke 2010

This implementation differs from the original paper in several ways. These deviations are documented here so the gap between the standard approach and this code is transparent.


3. **Parallel fitness evaluation** — All individuals' fitnesses are evaluated in parallel using `ForkJoinPool.commonPool()`. Burke 2010's original evaluation is sequential.
4. **Tree size control** — This implementation uses **lexicographic tournament selection**: fitness is the primary comparison key; tree size only matters when fitness ties. `TREE_PENALTY_ALPHA` is hardcoded to 0.0.
5. **Test mode multiple trials** — Test mode runs the evolved heuristic across up to ~400 random shuffles within the 10s time budget and keeps the best (fewest bins) result. Random tie-breaking (same seed, independent `Random` stream) adds diversity when multiple bins have equal GP scores.

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

### 2.1 Test-Time Multiple Trials with Shuffle

The core insight is that online BPP is **deterministic given the item order**: the GP heuristic always makes the same placement decisions. Therefore, finding a better item order is equivalent to finding a better solution.

**Implementation — `BPPSolver.java`:**

- `solveWithOrder(items, heuristic, capacity)` — training path: items in original order, no randomness.
- `solveWithOrder(items, heuristic, capacity, seed)` — test path: when `seed != null`, items are Fisher-Yates shuffled using a dedicated `Random(seed)`. The same seed also produces a second `Random` instance passed into `solveWithOrderRaw` for tie-breaking.
- `solveWithOrderRaw()` — core solve loop. For each item, the GP heuristic is evaluated on every feasible bin. The item is placed in the highest-scoring bin. **Random tie-breaking**: when two bins are tied on both GP score and remaining space, `rng.nextBoolean()` randomly picks one. This adds diversity even across trials with identical item orders.
- `Main.runTestMode()` — runs as many trials as possible within `maxTime`, each with `seed = System.nanoTime() ^ trialIndex`. Keeps the best (fewest bins) solution.

**Key observations:**

- ~400 trials fit within a 10s budget (~23ms per trial)
- Random tie-breaking uses the same seed as the shuffle but is otherwise independent — the two `Random` instances derived from the same seed generate different sequences
- The large gap (~187 bins over L2) on testdual4 reflects that the evolved heuristic was trained exclusively on unimodal distributions and is unaware of the bimodal (33/50 peak mix) structure in test data

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
├── BPPSolver.java          # Online BPP solver using GP heuristic
├── BPPState.java           # State for heuristic evaluation
├── Bin.java                # Bin representation
├── Solution.java           # Solution representation
├── Memory.java             # Memory mechanism (last 100 items, FIFO)
├── L2BoundCalculator.java  # L2 lower bound (Martello & Toth 1990)
├── GeneticProgramming.java # GP evolution engine
├── GPNode.java             # Abstract GP tree node
├── FunctionNode.java       # Function nodes (+, -, *, %, FI, IFL)
├── TerminalNode.java       # Terminal nodes (11 types above)
├── Heuristic.java         # Heuristic wrapper for GP tree
├── Individual.java         # GP individual (tree + fitness + lexicographic comparison)
├── Population.java        # GP population
├── DeserializeHeuristic.java  # Utility to inspect saved heuristics
└── best_heuristic.ser     # Trained heuristic (generated by --train)
```

