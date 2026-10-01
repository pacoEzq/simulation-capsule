#!/usr/bin/env bash
# sweep_driver.sh -- piece 2 of the T7 chain (spec v4 section 3.2).
#
# Launches the four macros of this repository, one `starccm+ -batch` session per macro and per
# point. It holds no STAR-CCM+ state and creates nothing inside a simulation. Run it from the run
# directory, which holds the template, the four macros, sweep.json and LLM_point.properties.
#
#   bash sweep_driver.sh [--points 100,300] [--dry-run]
#
# Per point: LLM_point.properties, copy of the template, session A (run_macro), window check W on
# work/cube_re<NNNN>, session B (output_exporter), C (manifest_writer), D (object_audit). The gates
# read the logs, not only the exit code: `starccm+ -batch` exits 0 even when the macro dies.
#
# Layout under the run directory: work/cube_re<NNNN>/ is everything a point writes, work/_driver/ the
# session logs and sweep_log.txt, work/_failed/ the points that failed. The driver writes nothing
# under capsules/ and launches nothing after the last point: build_capsule.py is the one command
# after the sweep.
#
# Exit: 0 sweep done, 1 a point failed (sweep stopped), 2 usage or setup error, 130 interrupted.

set -euo pipefail

if [[ -z "${BASH_VERSINFO[0]:-}" || "${BASH_VERSINFO[0]}" -lt 4 ]]; then
    echo "sweep_driver: bash 4 or later is required" >&2
    exit 2
fi

readonly CONFIG="sweep.json"
readonly PROPS="LLM_point.properties"
# The four macros resolve their outputs under "work" relative to the session directory; the name is
# a literal in each of them.
readonly WORK="work"
readonly DRV="$WORK/_driver"
readonly SWEEP_LOG="$DRV/sweep_log.txt"
readonly TAGS=(A B C D)
declare -A MACRO=(
    [A]="run_macro.java"
    [B]="output_exporter.java"
    [C]="manifest_writer.java"
    [D]="object_audit.java"
)

DRY=0
POINTS_ARG=""
CURRENT=""          # NNNN of the point in progress, for the stop path

usage() {
    echo "usage: bash sweep_driver.sh [--points 100,300] [--dry-run]"
}

die() {
    echo "sweep_driver: $*" >&2
    exit 2
}

utc() {
    date -u +%Y-%m-%dT%H:%M:%SZ
}

# One line per event in sweep_log.txt, echoed to stdout. A dry run writes nothing.
slog() {
    local line
    line="$(utc) $*"
    if [[ $DRY -eq 1 ]]; then
        echo "dry-run: log: $line"
    else
        printf '%s\n' "$line" >> "$SWEEP_LOG"
        echo "$line"
    fi
}

# A setup check: fatal in a real run, reported and skipped in a dry run.
check() {
    local ok=$1
    shift
    if [[ $ok -ne 0 ]]; then
        if [[ $DRY -eq 1 ]]; then
            echo "dry-run: check failed: $*"
        else
            die "$*"
        fi
    fi
}

show() {
    local out="" a
    for a in "$@"; do
        out+="$(printf '%q' "$a") "
    done
    printf '%s' "${out% }"
}

# ------------------------------------------------------------------ command line
while [[ $# -gt 0 ]]; do
    case "$1" in
        --points)
            [[ $# -ge 2 ]] || die "--points needs a value, e.g. --points 100,300"
            POINTS_ARG=$2
            shift 2
            ;;
        --points=*)
            POINTS_ARG=${1#--points=}
            shift
            ;;
        --dry-run)
            DRY=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            die "unknown argument '$1'"
            ;;
    esac
done

# ------------------------------------------------------------------ sweep.json
[[ -f "$CONFIG" ]] || die "no $CONFIG in the current directory (run from the run directory; see sweep.example.json)"
command -v python3 >/dev/null 2>&1 || die "python3 not found (needed to read $CONFIG)"

# python3 reads and validates the JSON; the shell gets key=value lines.
cfg_status=0
cfg=$(python3 - "$CONFIG" <<'PY'
import json, sys
path = sys.argv[1]
try:
    with open(path, encoding="utf-8") as f:
        c = json.load(f)
except Exception as e:
    sys.exit("cannot parse %s: %s" % (path, e))
if not isinstance(c, dict):
    sys.exit("%s: top level is not an object" % path)
keys = ["template", "points", "baseline", "np", "starccm", "python", "window_check"]
extra = sorted(set(c) - set(keys))
missing = [k for k in keys if k not in c]
if extra:
    sys.exit("%s: unknown keys %s" % (path, ", ".join(extra)))
if missing:
    sys.exit("%s: missing keys %s" % (path, ", ".join(missing)))
for k in ["template", "starccm", "python", "window_check"]:
    v = c[k]
    if not isinstance(v, str) or not v.strip() or "\n" in v or "\r" in v:
        sys.exit("%s: '%s' must be a non-empty one-line string" % (path, k))
def is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)
p = c["points"]
if not isinstance(p, list) or not p or not all(is_int(x) for x in p):
    sys.exit("%s: 'points' must be a non-empty list of integers" % path)
# baseline (spec v16, 2): read by manifest_writer.java and build_capsule.py, not by the driver.
if not is_int(c["baseline"]) or c["baseline"] not in p:
    sys.exit("%s: 'baseline' must be one of 'points'" % path)
if not is_int(c["np"]) or c["np"] < 1:
    sys.exit("%s: 'np' must be a positive integer" % path)
print("template=" + c["template"])
print("points=" + ",".join(str(x) for x in p))
print("np=%d" % c["np"])
print("starccm=" + c["starccm"])
print("python=" + c["python"])
print("window_check=" + c["window_check"])
PY
) || cfg_status=$?
[[ $cfg_status -eq 0 ]] || die "could not read $CONFIG (see the message above)"

declare -A CFG=()
while IFS='=' read -r k v; do
    CFG[$k]=$v
done <<< "$cfg"

TEMPLATE=${CFG[template]}
NP=${CFG[np]}
STARCCM=${CFG[starccm]}
PY=${CFG[python]}
WINDOW_CHECK=${CFG[window_check]}

# ------------------------------------------------------------------ points
points_csv=${POINTS_ARG:-${CFG[points]}}
POINTS=()
declare -A SEEN=()
IFS=',' read -r -a raw_points <<< "$points_csv"
for p in "${raw_points[@]}"; do
    p=${p//[[:space:]]/}
    [[ $p =~ ^[0-9]+$ ]] || die "point '$p' is not a positive integer"
    p=$((10#$p))
    (( p >= 1 && p <= 9999 )) || die "point $p does not fit the four-digit NNNN of the point name"
    [[ -z "${SEEN[$p]:-}" ]] || die "point $p is listed twice"
    SEEN[$p]=1
    POINTS+=("$p")
done
[[ ${#POINTS[@]} -gt 0 ]] || die "no points to run"

# ------------------------------------------------------------------ setup checks
rc=0; [[ "$STARCCM" != \<* ]] || rc=1
check $rc "starccm in $CONFIG is still the placeholder; set the launcher path in the local copy"
rc=0; [[ -x "$STARCCM" ]] || command -v -- "$STARCCM" >/dev/null 2>&1 || rc=1
check $rc "starccm launcher '$STARCCM' is not executable"
rc=0; command -v -- "$PY" >/dev/null 2>&1 || rc=1
check $rc "python command '$PY' not found"
rc=0; [[ -f "$WINDOW_CHECK" ]] || rc=1
check $rc "window check '$WINDOW_CHECK' not found"
rc=0; [[ -f "$TEMPLATE" ]] || rc=1
check $rc "template '$TEMPLATE' not found"
for t in "${TAGS[@]}"; do
    rc=0; [[ -f "${MACRO[$t]}" ]] || rc=1
    check $rc "macro '${MACRO[$t]}' not found"
done
# A point directory already present would mix with this run's outputs (run_log.txt is appended to by
# B, C and D). It is not this run's to move: stop and let the operator decide.
for p in "${POINTS[@]}"; do
    nnnn=$(printf '%04d' "$p")
    rc=0; [[ ! -e "$WORK/cube_re$nnnn" ]] || rc=1
    check $rc "'$WORK/cube_re$nnnn' already exists; move it away before running this point"
done

# ------------------------------------------------------------------ gates
count_lines() {
    if [[ -f "$1" ]]; then
        wc -l < "$1" | tr -d '[:space:]'
    else
        echo 0
    fi
}

# Lines appended to a file after the first $2 lines, CR stripped.
new_lines() {
    if [[ -f "$1" ]]; then
        tail -n +"$(( $2 + 1 ))" -- "$1" | tr -d '\r'
    fi
}

# Prints the first failure reason of a session, nothing if it passed.
#   $1 tag  $2 exit code  $3 session log  $4 point run_log.txt  $5 its line count before the session
gate() {
    local tag=$1 rc=$2 slog_file=$3 flog=$4 before=$5 hit body
    if [[ $rc -ne 0 ]]; then
        echo "exit code $rc"
        return 0
    fi
    hit=$(tr -d '\r' < "$slog_file" | grep -n -m1 -E '^error:|Exception' || true)
    if [[ -n "$hit" ]]; then
        hit=${hit:0:200}
        echo "session log line ${hit%%:*}: ${hit#*:}"
        return 0
    fi
    if [[ ! -f "$flog" ]]; then
        echo "closing line missing: no $flog"
        return 0
    fi
    body=$(new_lines "$flog" "$before")
    case "$tag" in
        A)
            # run_macro.saveUnderOwnName logs it; runRunLogBody writes it in the TRACE list as
            # "  - simulation saved under its own name". The failure variant reads "NOT saved".
            grep -q -F 'simulation saved under its own name' <<< "$body" \
                || { echo "closing line missing: 'simulation saved under its own name' in $flog"; return 0; }
            ;;
        B)
            hit=$(grep -m1 -E '^exporter: (staleness: FAIL|FAIL)' <<< "$body" || true)
            [[ -z "$hit" ]] || { echo "${hit:0:200}"; return 0; }
            grep -q -x -F 'exporter: done' <<< "$body" \
                || { echo "closing line missing: 'exporter: done' in $flog"; return 0; }
            ;;
        C)
            hit=$(grep -m1 -E '^manifest: FAIL' <<< "$body" || true)
            [[ -z "$hit" ]] || { echo "${hit:0:200}"; return 0; }
            grep -q -x -F 'manifest: done' <<< "$body" \
                || { echo "closing line missing: 'manifest: done' in $flog"; return 0; }
            ;;
        D)
            hit=$(grep -m1 -E '^audit: (RESULT FAIL|FAIL)' <<< "$body" || true)
            [[ -z "$hit" ]] || { echo "${hit:0:200}"; return 0; }
            grep -q -x -F 'audit: RESULT PASS' <<< "$body" \
                || { echo "closing line missing: 'audit: RESULT PASS' in $flog"; return 0; }
            grep -q -x -F 'audit: done' <<< "$body" \
                || { echo "closing line missing: 'audit: done' in $flog"; return 0; }
            ;;
    esac
}

# ------------------------------------------------------------------ stop path
stop_sweep() {
    local nnnn=$1 pt stamp dest
    pt="$WORK/cube_re$nnnn"
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    dest="$WORK/_failed/cube_re${nnnn}_$stamp"
    if [[ -d "$pt" ]]; then
        if mkdir -p -- "$WORK/_failed" && mv -- "$pt" "$dest"; then
            slog "re$nnnn point moved to $dest"
        else
            slog "re$nnnn point could NOT be moved to $dest; $pt is partial"
        fi
    else
        slog "re$nnnn no point directory to move"
    fi
    slog "sweep stopped at re$nnnn"
}

on_signal() {
    trap - INT TERM
    if [[ $DRY -eq 0 && -n "$CURRENT" ]]; then
        slog "re$CURRENT interrupted"
        stop_sweep "$CURRENT"
    fi
    exit 130
}
trap on_signal INT TERM

# ------------------------------------------------------------------ steps
# Runs one step; returns 1 if its gate fails.
#   $1 tag (A B C D W)  $2 NNNN  then the command
step() {
    local tag=$1 nnnn=$2
    shift 2
    local flog="$WORK/cube_re$nnnn/run_log.txt"
    local slog_file="$DRV/re${nnnn}_${tag}.log"
    local before t0 t1 rc reason

    if [[ $DRY -eq 1 ]]; then
        echo "dry-run: $(show "$@") > $(show "$slog_file") 2>&1"
        return 0
    fi

    before=$(count_lines "$flog")
    slog "re$nnnn $tag start"
    t0=$(date -u +%s)
    rc=0
    "$@" > "$slog_file" 2>&1 < /dev/null || rc=$?
    t1=$(date -u +%s)

    if [[ "$tag" == W ]]; then
        reason=""
        [[ $rc -eq 0 ]] || reason="exit code $rc"
    else
        reason=$(gate "$tag" "$rc" "$slog_file" "$flog" "$before")
    fi
    if [[ -z "$reason" ]]; then
        slog "re$nnnn $tag end $(( t1 - t0 )) pass"
        return 0
    fi
    slog "re$nnnn $tag end $(( t1 - t0 )) fail: $reason"
    return 1
}

session() {
    local tag=$1 nnnn=$2
    step "$tag" "$nnnn" "$STARCCM" -batch "${MACRO[$tag]}" -np "$NP" "cube_re$nnnn.sim"
}

run_point() {
    local re=$1 nnnn=$2
    local sim="cube_re$nnnn.sim"

    if [[ $DRY -eq 1 ]]; then
        echo "dry-run: write $PROPS: mode=run re_target=$re"
        echo "dry-run: $(show cp -- "$TEMPLATE" "$sim")"
    else
        printf 'mode=run\nre_target=%s\n' "$re" > "$PROPS"
        cp -- "$TEMPLATE" "$sim"
    fi

    session A "$nnnn" || return 1
    step W "$nnnn" "$PY" "$WINDOW_CHECK" "$WORK/cube_re$nnnn" || return 1
    session B "$nnnn" || return 1
    session C "$nnnn" || return 1
    session D "$nnnn" || return 1
}

# ------------------------------------------------------------------ sweep
if [[ $DRY -eq 1 ]]; then
    echo "dry-run: $(show mkdir -p -- "$DRV")"
    [[ ! -f "$PROPS" ]] || echo "dry-run: $(show cp -- "$PROPS" "$PROPS.prev")"
else
    mkdir -p -- "$DRV"
    if [[ -f "$PROPS" ]]; then
        cp -- "$PROPS" "$PROPS.prev"
    fi
fi

points_str=$(IFS=','; echo "${POINTS[*]}")
slog "sweep start points=$points_str np=$NP"

for re in "${POINTS[@]}"; do
    nnnn=$(printf '%04d' "$re")
    CURRENT=$nnnn
    if ! run_point "$re" "$nnnn"; then
        stop_sweep "$nnnn"
        exit 1
    fi
    CURRENT=""
done

# Nothing is launched after the last point: the capsules are built by build_capsule.py.
slog "sweep done ${#POINTS[@]} points"
exit 0
