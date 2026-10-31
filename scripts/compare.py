# 키워드별 방식마다 결과 수, SQL 수 비교
import csv
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "http://localhost:8080"
ROOT = Path(__file__).resolve().parent.parent
ORDER = ["like", "es-n1", "es-my", "fulltext"]


def compare(keyword):
    # 방식마다 요청 분리
    result = {}
    for strategy in ORDER:
        url = f"{BASE}/bench/search/{strategy}?q={urllib.parse.quote(keyword)}"
        with urllib.request.urlopen(url, timeout=30) as res:
            result[strategy] = json.load(res)
    return result


def main(argv):
    with (ROOT / "jmeter" / "keywords-feature.csv").open(encoding="utf-8") as fh:
        keywords = list(csv.DictReader(fh))

    lines = ["## 키워드별 검색 결과 수 / 실행된 SQL 수", "",
             "| 키워드 | 유형 | " + " | ".join(ORDER) + " | 상위 결과 (es-my) |",
             "|---|---|" + "---:|" * len(ORDER) + "---|"]
    for row in keywords:
        try:
            result = compare(row["keyword"])
        except Exception as e:
            print(f"'{row['keyword']}' 요청 실패: {e}", file=sys.stderr)
            return 1
        cells = [f"{result[s]['total']:,}건 / SQL {result[s]['queryCount']}" for s in ORDER]
        top = ", ".join(result["es-my"]["names"][:3])
        lines.append(f"| {row['keyword']} | {row['kwtype']} | " + " | ".join(cells) + f" | {top} |")

    lines += ["", "> ES 결과 수는 기본 설정상 10,000건까지만 셈 (track_total_hits)",
              "> FULLTEXT ngram은 토큰 길이 2라 한 글자 검색은 0건"]
    text = "\n".join(lines)
    print(text)
    if argv:
        Path(argv[0]).parent.mkdir(parents=True, exist_ok=True)
        Path(argv[0]).write_text(text + "\n", encoding="utf-8")
        print(f"\n→ {argv[0]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
