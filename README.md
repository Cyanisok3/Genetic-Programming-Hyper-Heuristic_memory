# Genetic Programming Hyper-Heuristic for Online Bin Packing

A Java implementation of a streamlined GPHH framework that evolves constructive heuristics for the online bin packing problem (BPP), where items arrive sequentially with no lookahead.

## Overview

This project implements a genetic programming hyper-heuristic (GPHH) that evolves tree-structured scoring heuristics. Each GP tree evaluates candidate bins and selects the best placement for the current item. The system uses an ensemble of 5 independently-trained trees with per-decision majority voting at test time.  

## Key Features

- **Minimalist terminal set**: 4 terminals (S, E, IsNew, C) focusing on immediate bin-state information
- **Compact function set**: +, -, *, /, MAX, IFLTE
- **Falkenauer fitness**: Balances bin count and bin fullness uniformity
- **5-tree ensemble voting**: Aggregates decisions from multiple heuristics for robust packing
- **Parallel evaluation**: ForkJoinPool-based parallel fitness computation

## Quick Start

```bash
# Compile
javac -d out GPHH_BPP/*.java

# Train (5 trees saved to best_heuristics/)
java -cp out GPHH20617232 --train

# Test on a single instance
java -cp out GPHH20617232 -s dualdistribution/test/testdual4/binpack0.txt -o solution.txt

# Verify solution
java bpp_checker -s dualdistribution/test/testdual4/binpack0.txt -c solution.txt
```

## Test Results

Per-instance results on all three test sets. "Ratio" = bins / L2, "Gap" = bins − L2.

### testdual0


| Instance  | Bins     | L2       | Ratio      | Gap     |
| --------- | -------- | -------- | ---------- | ------- |
| binpack0  | 2524     | 2509     | 1.0060     | +15     |
| binpack1  | 2580     | 2536     | 1.0174     | +44     |
| binpack2  | 2533     | 2507     | 1.0104     | +26     |
| binpack3  | 2546     | 2529     | 1.0067     | +17     |
| binpack4  | 2609     | 2578     | 1.0120     | +31     |
| binpack5  | 2579     | 2549     | 1.0118     | +30     |
| binpack6  | 2556     | 2503     | 1.0212     | +53     |
| binpack7  | 2535     | 2517     | 1.0072     | +18     |
| binpack8  | 2545     | 2520     | 1.0099     | +25     |
| binpack9  | 2555     | 2524     | 1.0123     | +31     |
| binpack10 | 2522     | 2502     | 1.0080     | +20     |
| binpack11 | 2530     | 2502     | 1.0112     | +28     |
| binpack12 | 2551     | 2507     | 1.0176     | +44     |
| binpack13 | 2583     | 2561     | 1.0086     | +22     |
| binpack14 | 2558     | 2525     | 1.0131     | +33     |
| binpack15 | 2544     | 2520     | 1.0095     | +24     |
| binpack16 | 2551     | 2524     | 1.0107     | +27     |
| binpack17 | 2560     | 2528     | 1.0127     | +32     |
| binpack18 | 2574     | 2533     | 1.0162     | +41     |
| binpack19 | 2546     | 2507     | 1.0156     | +39     |
| **Avg**   | **2554** | **2524** | **1.0119** | **+30** |


### testdual4


| Instance  | Bins     | L2       | Ratio      | Gap      |
| --------- | -------- | -------- | ---------- | -------- |
| binpack0  | 2205     | 2123     | 1.0386     | +82      |
| binpack1  | 2206     | 2117     | 1.0420     | +89      |
| binpack2  | 2226     | 2130     | 1.0451     | +96      |
| binpack3  | 2222     | 2124     | 1.0461     | +98      |
| binpack4  | 2219     | 2133     | 1.0403     | +86      |
| binpack5  | 2202     | 2116     | 1.0406     | +86      |
| binpack6  | 2226     | 2126     | 1.0470     | +100     |
| binpack7  | 2238     | 2131     | 1.0502     | +107     |
| binpack8  | 2203     | 2117     | 1.0406     | +86      |
| binpack9  | 2223     | 2139     | 1.0393     | +84      |
| binpack10 | 2229     | 2122     | 1.0504     | +107     |
| binpack11 | 2197     | 2113     | 1.0398     | +84      |
| binpack12 | 2222     | 2122     | 1.0471     | +100     |
| binpack13 | 2216     | 2127     | 1.0418     | +89      |
| binpack14 | 2216     | 2121     | 1.0448     | +95      |
| binpack15 | 2221     | 2123     | 1.0462     | +98      |
| binpack16 | 2218     | 2126     | 1.0433     | +92      |
| binpack17 | 2221     | 2127     | 1.0442     | +94      |
| binpack18 | 2216     | 2126     | 1.0423     | +90      |
| binpack19 | 2207     | 2121     | 1.0405     | +86      |
| **Avg**   | **2217** | **2124** | **1.0435** | **+92** |


### testdual8


| Instance  | Bins     | L2       | Ratio      | Gap      |
| --------- | -------- | -------- | ---------- | -------- |
| binpack0  | 2217     | 2116     | 1.0477     | +101     |
| binpack1  | 2206     | 2115     | 1.0430     | +91      |
| binpack2  | 2222     | 2127     | 1.0447     | +95      |
| binpack3  | 2219     | 2126     | 1.0437     | +93      |
| binpack4  | 2208     | 2129     | 1.0371     | +79      |
| binpack5  | 2227     | 2128     | 1.0465     | +99      |
| binpack6  | 2220     | 2126     | 1.0442     | +94      |
| binpack7  | 2223     | 2126     | 1.0456     | +97      |
| binpack8  | 2211     | 2128     | 1.0390     | +83      |
| binpack9  | 2211     | 2114     | 1.0459     | +97      |
| binpack10 | 2221     | 2124     | 1.0457     | +97      |
| binpack11 | 2220     | 2131     | 1.0418     | +89      |
| binpack12 | 2216     | 2127     | 1.0418     | +89      |
| binpack13 | 2230     | 2123     | 1.0504     | +107     |
| binpack14 | 2251     | 2140     | 1.0519     | +111     |
| binpack15 | 2216     | 2118     | 1.0463     | +98      |
| binpack16 | 2204     | 2110     | 1.0445     | +94      |
| binpack17 | 2239     | 2141     | 1.0458     | +98      |
| binpack18 | 2230     | 2129     | 1.0474     | +101     |
| binpack19 | 2234     | 2134     | 1.0469     | +100     |
| **Avg**   | **2221** | **2126** | **1.0450** | **+96** |

