# JMeter 결과 표로 정리
import csv
import math
import re
import sys
from collections import defaultdict
from pathlib import Path

ORDER = ["like", "es-n1", "es-my", "es-batch", "es-only", "fulltext"]
NAMES = {
    "like": "JPA LIKE (기존)",
    "es-n1": "ES + findById (현재 코드)",
    "es-my": "ES + findAllById",
    "es-batch": "ES + findAllById (개선)",
    "es-only": "ES 문서만 (DB 조회 없음)",
    "fulltext": "MySQL FULLTEXT ngram",
}
FILE_RE = re.compile(r"^(?P<strategy>[a-z0-9-]+)-t(?P<threads>\d+)\.jtl$")


def percentile(sorted_values, p):
    if not sorted_values:
        return float("nan")
    k = max(0, math.ceil(p / 100 * len(sorted_values)) - 1)
    return sorted_values[k]


def stats(rows):
    elapsed = sorted(int(r["elapsed"]) for r in rows)
    n = len(elapsed)
    if n == 0:
        return None
    errors = sum(1 for r in rows if r["success"].lower() != "true")
    start = min(int(r["timeStamp"]) for r in rows)
    end = max(int(r["timeStamp"]) + int(r["elapsed"]) for r in rows)
    span = max((end - start) / 1000, 0.001)
    return {
        "n": n,
        "err%": errors / n * 100,
        "avg": sum(elapsed) / n,
        "p50": percentile(elapsed, 50),
        "p90": percentile(elapsed, 90),
        "p95": percentile(elapsed, 95),
        "p99": percentile(elapsed, 99),
        "max": elapsed[-1],
        "tps": n / span,
    }


def load(result_dir):
    """{(strategy, threads): {kwtype: [rows], 'all': [rows]}}"""
    data = {}
    for f in sorted(Path(result_dir).glob("*.jtl")):
        m = FILE_RE.match(f.name)
        if not m:
            continue
        strategy, threads = m["strategy"], int(m["threads"])
        groups = defaultdict(list)
        with f.open(encoding="utf-8", newline="") as fh:
            for row in csv.DictReader(fh):
                label = row.get("label", "")
                kwtype = label[len(strategy) + 1:] if label.startswith(strategy + "-") else "?"
                groups[kwtype].append(row)
                groups["all"].append(row)
        data[(strategy, threads)] = groups
    return data


def sort_key(key):
    strategy, threads = key
    return (ORDER.index(strategy) if strategy in ORDER else 99, threads)


def fmt(v, digits=1):
    return "-" if v is None or (isinstance(v, float) and math.isnan(v)) else f"{v:,.{digits}f}"


def summarize_dir(result_dir):
    result_dir = Path(result_dir)
    data = load(result_dir)
    if not data:
        print(f"[{result_dir}] .jtl 파일 없음")
        return {}

    lines = [f"# 검색 벤치마크 결과 — {result_dir.name}", ""]
    stats_file = result_dir / "stats.json"
    if stats_file.exists():
        lines += [f"데이터: `{stats_file.read_text(encoding='utf-8').strip()}`", ""]

    lines += ["## 전체 (단위: ms, TPS = 초당 처리 요청 수)", "",
              "| 방식 | 동시 사용자 | 요청 수 | 평균 | p50 | p95 | p99 | 최대 | TPS | 에러율 |",
              "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    csv_rows = []
    overall = {}
    for key in sorted(data, key=sort_key):
        strategy, threads = key
        for kwtype, rows in sorted(data[key].items()):
            s = stats(rows)
            if s is None:
                continue
            csv_rows.append({"size": result_dir.name, "strategy": strategy, "threads": threads,
                             "kwtype": kwtype, **{k: round(v, 3) for k, v in s.items()}})
            if kwtype == "all":
                overall[key] = s
                lines.append(f"| {NAMES.get(strategy, strategy)} | {threads} | {s['n']:,} | {fmt(s['avg'])} | "
                             f"{fmt(s['p50'], 0)} | {fmt(s['p95'], 0)} | {fmt(s['p99'], 0)} | {fmt(s['max'], 0)} | "
                             f"{fmt(s['tps'])} | {fmt(s['err%'], 2)}% |")

    # 키워드 유형별 p95 (최대 동시 사용자 기준)
    max_threads = max(t for _, t in data)
    kw_order = ["frequent", "rare", "none"]
    kwtypes = sorted({k for g in data.values() for k in g if k != "all"},
                     key=lambda k: (kw_order.index(k) if k in kw_order else 99, k))
    lines += ["", f"## 키워드 유형별 p95 (동시 사용자 {max_threads}명, ms)", "",
              "| 방식 | " + " | ".join(kwtypes) + " |",
              "|---|" + "---:|" * len(kwtypes)]
    for key in sorted((k for k in data if k[1] == max_threads), key=sort_key):
        cells = []
        for kw in kwtypes:
            s = stats(data[key].get(kw, []))
            cells.append(fmt(s["p95"], 0) if s else "-")
        lines.append(f"| {NAMES.get(key[0], key[0])} | " + " | ".join(cells) + " |")

    lines += ["", "> frequent: 결과가 많은 키워드 / rare: 결과가 몇 건뿐인 키워드 / none: 결과 0건 키워드"]

    (result_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    with (result_dir / "summary.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(csv_rows[0].keys()))
        writer.writeheader()
        writer.writerows(csv_rows)
    print("\n".join(lines))
    print(f"\n→ {result_dir / 'summary.md'}, {result_dir / 'summary.csv'}")
    return overall


def summarize_sizes(dirs, overalls):
    common_threads = set.intersection(*({t for _, t in o} for o in overalls if o)) if all(overalls) else set()
    if not common_threads:
        return
    threads = max(common_threads)
    labels = [Path(d).name for d in dirs]
    lines = [f"# 데이터 규모별 비교 (동시 사용자 {threads}명)", "",
             "| 방식 | " + " | ".join(f"{l} 평균 / p95" for l in labels) + " |",
             "|---|" + "---:|" * len(labels)]
    strategies = sorted({s for o in overalls for s, _ in o}, key=lambda s: ORDER.index(s) if s in ORDER else 99)
    for strategy in strategies:
        cells = []
        for o in overalls:
            s = o.get((strategy, threads))
            cells.append(f"{fmt(s['avg'])} / {fmt(s['p95'], 0)}" if s else "-")
        lines.append(f"| {NAMES.get(strategy, strategy)} | " + " | ".join(cells) + " |")
    out = Path(dirs[0]).parent / "summary-by-size.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n" + "\n".join(lines))
    print(f"\n→ {out}")


def main(argv):
    if not argv:
        print(__doc__)
        return 1
    overalls = [summarize_dir(d) for d in argv]
    if len(argv) > 1:
        summarize_sizes(argv, overalls)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
