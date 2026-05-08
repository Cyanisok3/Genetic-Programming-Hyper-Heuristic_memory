#!/bin/bash
# Batch test evaluation script

HEURISTIC="best_heuristic.ser"
TEST_DIR="dualdistribution/test"
OUT_DIR="test_output"

mkdir -p "$OUT_DIR"

total_ratio=0
total_count=0

echo "=== Batch Test Evaluation ==="
echo "Heuristic: $HEURISTIC"
echo ""

for testdir in "$TEST_DIR"/testdual*; do
    testname=$(basename "$testdir")
    echo "--- $testname ---"

    set_total=0
    set_count=0

    for instance in "$testdir"/binpack*.txt; do
        instname=$(basename "$instance" .txt)
        outfile="$OUT_DIR/${testname}_${instname}.sol"

        # Run solver and capture output
        output=$(java -cp out Main -s "$instance" -o "$outfile" -t 10000 2>&1)
        bins=$(echo "$output" | grep "^Solution:" | sed 's/[^0-9]/ /g' | awk '{print $NF}')
        l2=$(echo "$output" | grep "^L2 lower bound:" | sed 's/[^0-9]/ /g' | awk '{print $NF}')
        ratio=$(echo "$output" | grep "^Ratio:" | awk '{print $2}')
        elapsed=$(echo "$output" | grep "^Solved in" | sed 's/[^0-9]/ /g' | awk '{print $1}')

        if [ -n "$ratio" ]; then
            echo "  $instname: bins=$bins L2=$l2 ratio=$ratio time=${elapsed}ms"
            set_total=$(awk "BEGIN {print $set_total + $ratio}")
            set_count=$((set_count + 1))
        else
            echo "  $instname: ERROR"
            echo "$output" | head -5
        fi
    done

    if [ $set_count -gt 0 ]; then
        set_avg=$(awk "BEGIN {printf \"%.6f\", $set_total / $set_count}")
        echo "  >> Average ratio: $set_avg ($set_count instances)"
        echo ""
        total_ratio=$(awk "BEGIN {print $total_ratio + $set_total}")
        total_count=$((total_count + set_count))
    fi
done

if [ $total_count -gt 0 ]; then
    overall_avg=$(awk "BEGIN {printf \"%.6f\", $total_ratio / $total_count}")
    echo "=== OVERALL ==="
    echo "Total instances: $total_count"
    echo "Average ratio: $overall_avg"
fi
