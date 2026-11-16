#!/bin/bash
# Тесты свидетельств bodega для qa/scripts/ebt-gate.sh: рабочая папка — корень клона,
# OUT_DIR — пустая папка гейта для JSONL. Аргумент — фильтр --tests ($EBT_TESTS гейта).
#
# Код по договору гейта: 0 — тесты прошли, 1 — тесты выполнились и упали, 2 — ошибка исполнения.
# Gradle возвращает 1 и на упавшие тесты, и на сломанную сборку, поэтому 1 — только если
# единственная причина отказа — «There were failing tests» и в свежих XML есть провалы;
# всё остальное, включая неоднозначный вывод, — 2.
set -uo pipefail
[ -n "${OUT_DIR:-}" ] || { echo "test.sh: нужен OUT_DIR (запуск через ebt-gate.sh)" >&2; exit 2; }

results=app/build/test-results/testDebugUnitTest
rm -rf app/build/ebt "$results"
log=$(mktemp) || exit 2
trap 'rm -f "$log"' EXIT

./gradlew -q :app:testDebugUnitTest --tests "${1:-*EbtTest}" --rerun 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}

# Свидетельства копируются при любом исходе: красный прогон тоже проверяется чекером.
mkdir -p "$OUT_DIR" || exit 2
shopt -s nullglob
evidence=(app/build/ebt/*.jsonl)
if [ ${#evidence[@]} -gt 0 ]; then
  cp "${evidence[@]}" "$OUT_DIR"/ || exit 2
fi

[ "$rc" -eq 0 ] && exit 0
if grep -q "There were failing tests" "$log" && ! grep -q "Build completed with" "$log" \
   && grep -lqE '<testsuite [^>]*(failures="[1-9]|errors="[1-9])' "$results"/TEST-*.xml 2>/dev/null; then
  exit 1
fi
exit 2
