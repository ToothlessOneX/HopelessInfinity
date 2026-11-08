#!/bin/bash
# HOPELESS LAB (Minecraft): the one tool suite for the Minecraft mods, with the one renderer (WorldView).
# Overlord's rule, 2026-11-08: "combine all the renderers into one that we can continuously update and all the tools needed to be added to a
# suite with it ... if the renderer or tools cant do something in minecraft you adapt them instead of making a new tool for the job".
#
#   tools/lab.sh list                    every tool in the suite, with what it does
#   tools/lab.sh build                   compile every tool (into /tmp/claude-0/st)
#   tools/lab.sh render <args>           WorldView: the world in 3D (generated or from a save), and every older view (render views)
#   tools/lab.sh test [Name ...]         the test suite (all), or the named tests
#   tools/lab.sh run <Tool> [args]       any other tool (RiverWalk, RoadsProbe, ...)
#   tools/lab.sh refcheck <jar>          the jar's references into the game jars
#
# Adding a tool: put it in tools/ with a javadoc first line that says what it does; it appears in "list". A new renderer is never added:
# WorldView gets the new view.
cd "$(dirname "$0")/.." || exit 1
CP="build/classes:$(cat /home/claude/mctool/cp.txt)"; OUT=/tmp/claude-0/st; mkdir -p "$OUT"
build() { javac -proc:none -nowarn -cp "$CP" -sourcepath tools -d "$OUT" tools/*.java 2>&1 | grep -v JAVA_TOOL; }
case "$1" in
  list)
    echo "Hopeless Lab (Minecraft) - tools/  [the renderer: WorldView]"
    for f in tools/*.java; do n=$(basename "$f" .java); d=$(grep -m1 -o '/\*\* [^.]*' "$f" | cut -c5- | cut -c1-110); kind=tool; case "$n" in *Test) kind=test;; WorldView) kind=RENDERER;; esac; printf "  %-18s %-8s %s\n" "$n" "$kind" "$d"; done ;;
  build) build ;;
  render) shift; [ -f "$OUT/WorldView.class" ] || build; java -Xmx6g -cp "$OUT:$CP" WorldView "$@" ;;
  test) shift; if [ $# -eq 0 ]; then ./tools/run_tests.sh; else build; for t in "$@"; do java -cp "$OUT:$CP" "$t" 2>&1 | grep -v JAVA_TOOL; done; fi ;;
  run) shift; t=$1; shift; [ -f "$OUT/$t.class" ] || build; java -Xmx6g -cp "$OUT:$CP" "$t" "$@" ;;
  refcheck) shift; /home/claude/mctool/refcheck.sh "$@" ;;
  *) sed -n '2,15p' "$0" ;;
esac
