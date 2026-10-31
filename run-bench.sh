#!/usr/bin/env bash

set -euo pipefail

LABEL=${1:?"라벨 필요. ex) ./run-bench.sh 100k"}
STRATEGIES=${STRATEGIES:-"like es-n1 fulltext es-my"}
THREADS=${THREADS:-"1 10 50"}
DURATION=${DURATION:-60}
WARMUP=${WARMUP:-20}
JMETER=${JMETER:-jmeter}
HOST=${HOST:-localhost}
PORT=${PORT:-8080}

ROOT=$(cd "$(dirname "$0")" && pwd)
PLAN=${PLAN:-"$ROOT/jmeter/search-bench.jmx"}
KEYWORDS=${KEYWORDS:-"$ROOT/jmeter/keywords-speed.csv"}
OUT="$ROOT/results/$LABEL"
mkdir -p "$OUT"

if ! curl -fs "http://$HOST:$PORT/bench/stats" > "$OUT/stats.json"; then
  echo "벤치마크 서버(http://$HOST:$PORT) 연결 실패. ./gradlew bootRun 먼저 실행" >&2
  exit 1
fi
echo "데이터 현황: $(cat "$OUT/stats.json")"

JMETER_OPTS=(
  -n -t "$PLAN"
  -Jhost="$HOST" -Jport="$PORT" -Jkeywords="$KEYWORDS"
  -Jjmeter.save.saveservice.output_format=csv
  -Jjmeter.save.saveservice.print_field_names=true
)

for strategy in $STRATEGIES; do
  for threads in $THREADS; do
    echo "▶ ${strategy} / 동시 사용자 ${threads}명 — 워밍업 ${WARMUP}s"
    "$JMETER" "${JMETER_OPTS[@]}" -Jstrategy="$strategy" -Jthreads="$threads" \
      -Jrampup=0 -Jduration="$WARMUP" -l "$OUT/.warmup.jtl" -j "$OUT/.warmup.log" > /dev/null
    rm -f "$OUT/.warmup.jtl"

    echo "  측정 ${DURATION}s"
    rm -f "$OUT/$strategy-t$threads.jtl"
    "$JMETER" "${JMETER_OPTS[@]}" -Jstrategy="$strategy" -Jthreads="$threads" \
      -Jrampup=0 -Jduration="$DURATION" \
      -l "$OUT/$strategy-t$threads.jtl" -j "$OUT/$strategy-t$threads.log" > /dev/null
  done
done

python3 "$ROOT/scripts/summarize.py" "$OUT"
