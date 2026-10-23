#!/usr/bin/env bash
# Быстрая проверка живого бэкенда на демо-монолите. Нужны curl и python3.
# Использование: scripts/smoke.sh [http://localhost:8080]
set -euo pipefail
API="${1:-http://localhost:8080}/api"
DIR="$(cd "$(dirname "$0")/.." && pwd)"

files() { python3 - "$DIR/samples/Main.java" <<'PY'
import json, sys
print(json.dumps([{"path": "Main.java", "source": open(sys.argv[1], encoding="utf-8").read()}]))
PY
}
F="$(files)"

echo "== health";  curl -fsS "$API/health"; echo
echo "== analyze (проблемы)"
curl -fsS -X POST "$API/analysis" -H 'Content-Type: application/json' -d "{\"files\":$F}" \
  | python3 -c 'import json,sys; [print(i["id"], i["complexityBefore"], "->", i["complexityAfter"]) for i in json.load(sys.stdin)["issues"]]'

echo "== optimize (автофикс первой проблемы COSTLY_CALL_IN_LOOP)"
ISSUE=$(curl -fsS -X POST "$API/analysis" -H 'Content-Type: application/json' -d "{\"files\":$F}" \
  | python3 -c 'import json,sys; print(next(i["id"] for i in json.load(sys.stdin)["issues"] if i["type"]=="COSTLY_CALL_IN_LOOP"))')
OPT=$(curl -fsS -X POST "$API/optimize" -H 'Content-Type: application/json' -d "{\"files\":$F,\"issueId\":\"$ISSUE\",\"allowLlm\":false}")
echo "$OPT" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["strategy"], "-", d["explanation"])'

echo "== verify (Quality Gate)"
MOD=$(echo "$OPT" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)["files"]))')
TESTS=$(python3 - "$DIR/samples/inputs" <<'PY'
import json, os, sys
d = sys.argv[1]
print(json.dumps([{"name": f[:-3], "stdin": open(os.path.join(d, f), encoding="utf-8").read()} for f in sorted(os.listdir(d)) if f.endswith(".in")]))
PY
)
curl -fsS -X POST "$API/verify" -H 'Content-Type: application/json' \
  -d "{\"original\":$F,\"modified\":$MOD,\"mainClass\":\"Main\",\"tests\":$TESTS,\"mode\":\"EXACT\"}" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print("поведение сохранено:", d["behaviorMatched"], f"(ускорение: {d[\"speedup\"]})")'
