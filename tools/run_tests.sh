#!/bin/bash
# run_tests.sh: builds and runs every off-game test of Hopeless Infinity, with each one's time, and appends the times to tools/test_times.log
# (test maintenance: a slow, silent or skipped test shows in the log). Saved tool.
cd "$(dirname "$0")/.." || exit 1; CP="build/classes:$(cat /home/claude/mctool/cp.txt)"; T=/tmp/claude-0/st; rm -rf $T; mkdir -p $T
javac -nowarn -cp "$CP" -d $T tools/*.java 2>&1 | grep -v Picked | head -5; [ -f build/classes/com/hopeless/infinity/HopelessInfinity.class ] || { echo "no build/classes: build first"; exit 99; }; fails=0; stamp=$(date -u +%FT%TZ)
for f in tools/*Test.java; do t=$(basename $f .java); args=""; [ "$t" = SpawnTest ] && args="1 2 3"
  s=$(date +%s.%N); r=$(timeout 600 java -cp "$T:$CP" $t $args 2>&1 | grep -v Picked | tail -1); e=$(date +%s.%N); d=$(echo "$e - $s" | bc)
  printf "%-16s %6.1fs  %s\n" $t $d "$r"; echo "$stamp $t $d $r" >> tools/test_times.log; if echo "$r" | grep -qE "(^| )[1-9][0-9]* (fail|failed)|FAIL" || ! echo "$r" | grep -qE "pass|PASS|fail|failed"; then fails=$((fails+1)); echo "  ^ counted as a failure (failed, or no result line)"; fi; done
echo "tests with failures: $fails"; exit $fails
