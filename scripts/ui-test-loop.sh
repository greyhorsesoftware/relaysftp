#!/bin/bash
# Repeated-build flake finder for the UI test suite (docs/1.8.0/UI_TEST_HARDENING_PLAN.md §4).
#
# Runs `./gradlew clean build` N times (default 3), keeps every run's JUnit XML under
# ui-flake/run-N/ (outside build/, which `clean` wipes each run), and prints a class-by-run failure matrix plus the first line of
# each failure message, so intermittent failures can be bucketed by signature.
#
# Usage:  scripts/ui-test-loop.sh [N] [extra gradle args...]
#   e.g.  scripts/ui-test-loop.sh 3
#         scripts/ui-test-loop.sh 5 uiTest        # loop only the UI task, no clean build
#
# The UI tests drive the real desktop with a robot: leave the machine alone while it runs.
set -u

cd "$(dirname "$0")/.."

RUNS="${1:-3}"
shift || true
GRADLE_ARGS=("$@")
if [ ${#GRADLE_ARGS[@]} -eq 0 ]; then GRADLE_ARGS=(clean build); fi
# Without `clean`, an unchanged test task is UP-TO-DATE and would be skipped on runs 2..N.
case " ${GRADLE_ARGS[*]} " in *" clean "*) ;; *) GRADLE_ARGS+=(--rerun) ;; esac

OUT="ui-flake"
rm -rf "$OUT"
mkdir -p "$OUT"

for i in $(seq 1 "$RUNS"); do
    echo "=== run $i of $RUNS: ./gradlew ${GRADLE_ARGS[*]} ==="
    start=$(date +%s)
    touch "$OUT/run-$i.start"     # mtime marker: harvest only XML written after this
    ./gradlew "${GRADLE_ARGS[@]}" --continue > "$OUT/run-$i.log" 2>&1
    rc=$?
    echo "    exit $rc, $(( $(date +%s) - start ))s"
    echo "$rc" > "$OUT/run-$i.rc"
    mkdir -p "$OUT/run-$i"
    # only XML written during this run, so a no-clean loop never harvests a stale report left
    # over from an earlier task (the log's mtime is useless here: it is appended to until the end)
    for d in build/test-results/test build/test-results/uiTest build/test-results/uiTestRetry; do
        [ -d "$d" ] && find "$d" -name '*.xml' -newer "$OUT/run-$i.start" -exec cp {} "$OUT/run-$i/" \;
    done
done

python3 - "$OUT" "$RUNS" <<'EOF'
import sys, glob, os, collections
import xml.etree.ElementTree as ET

out, runs = sys.argv[1], int(sys.argv[2])
fails = collections.defaultdict(dict)      # class -> run -> [(test, first line)]
seen  = set()
for r in range(1, runs + 1):
    for f in glob.glob(os.path.join(out, f"run-{r}", "*.xml")):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        for tc in root.iter("testcase"):
            cls = tc.get("classname", "?").split(".")[-1]
            seen.add(cls)
            for kind in ("failure", "error"):
                node = tc.find(kind)
                if node is not None:
                    msg = (node.get("message") or node.text or "").strip().splitlines()
                    fails[cls].setdefault(r, []).append((tc.get("name"), msg[0] if msg else kind))

lines = []
rcs, counts = [], []
for r in range(1, runs + 1):
    try: rcs.append(open(os.path.join(out, f"run-{r}.rc")).read().strip())
    except OSError: rcs.append("?")
    counts.append(len(glob.glob(os.path.join(out, f"run-{r}", "*.xml"))))
lines.append("gradle exit codes: " + ", ".join(f"run{r}={rc}" for r, rc in enumerate(rcs, 1)))
lines.append("classes harvested: " + ", ".join(f"run{r}={c}" for r, c in enumerate(counts, 1)))
if any(c == 0 for c in counts):
    lines.append("WARNING: a run harvested no XML - the matrix below says nothing about it; trust the exit code only")
lines.append("")
lines.append(f"{'class':45s} " + " ".join(f"run{r}" for r in range(1, runs + 1)))
for cls in sorted(fails):
    cells = ["FAIL" if r in fails[cls] else "ok  " for r in range(1, runs + 1)]
    lines.append(f"{cls:45s} " + " ".join(cells))
if not fails:
    lines.append("(no failures in any run)")
lines.append("")
lines.append("signatures:")
for cls in sorted(fails):
    for r in sorted(fails[cls]):
        for test, msg in fails[cls][r]:
            lines.append(f"  run{r} {cls}.{test}: {msg[:160]}")
text = "\n".join(lines)
print(text)
with open(os.path.join(out, "summary.txt"), "w") as fh:
    fh.write(text + "\n")
print(f"\n{len(seen)} test classes seen; summary written to {out}/summary.txt")
EOF
