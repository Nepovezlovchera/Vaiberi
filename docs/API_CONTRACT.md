# API-контракт бэкенда (Java Blueprints)

Соответствует TS-типам из `docs/ARCHITECTURE_AND_SPEC.md`. База: `http://localhost:8080/api`. Swagger UI: `/swagger-ui.html`.
Файлы проекта везде передаются как `files: [{ "path": "Main.java", "source": "..." }]`.
Ошибки: `{ "code": "PARSE_ERROR|COMPILE_ERROR|BAD_REQUEST|NOT_FOUND|INTERNAL", "message": "...", "line": 12|null }`.
Коды HTTP: 422 — Java не разбирается (с номером строки), 400 — неверный запрос или правка ломает компиляцию, 404 — нет отчёта.

## Идентификаторы узлов
* метод: `methodId` = `[package.]Class[.Nested].method[#k]` (`#k` — для перегрузок);
* вход метода: `<methodId>/entry`;
* оператор: `<methodId>/s<путь>`, индексы с 1: `Main.f/s2.b.1` — первый оператор в теле второго оператора метода;
  слоты: `t`/`e` (then/else), `b` (тело цикла/try), `c0..` (catch), `f` (finally), `s0..` (ветки switch);
* контейнер для вставки может иметь суффикс слота: `Main.g/s1:e`.
* `index` во вставке/переносе — 0-based позиция среди операторов контейнера.

## POST /api/graph
Запрос `{ files, methodId? }` → `{ methods: MethodGraphResponse[], metrics: ComplexityMetric }`.
`GraphNode { id, parentId, type, data{label, code, lineStart, lineEnd, hasIssue, issueId, loopType, condition} }`,
`type`: method | var | call | op | return | condition | loop | try | switch | break | continue | throw | raw.
`GraphEdge { id, source, target, type, label }` (метки «Да», «Нет», «Тело цикла»).

## POST /api/graph/mutate
`{ files, action: UPDATE_CODE|DELETE_NODE|INSERT_NODE|REORDER, nodeId, parentId?, index?, payload?, template?, validate? }`
→ `{ files, graph, validated, compileOutput }`. После правки проект перекомпилируется (dry run);
если до правки компилировался, а после нет — `400 COMPILE_ERROR`, состояние на клиенте не меняется.
Вставка по `template` (заготовка из палитры) по умолчанию не валидируется (`validate:false`).

## POST /api/analysis
`{ files }` → `{ issues: CodeIssue[], metrics: ComplexityMetric }`.
Типы проблем: NESTED_LOOP, COSTLY_CALL_IN_LOOP, LONG_METHOD, HIGH_CYCLOMATIC, DUPLICATION.

## POST /api/optimize, /api/decompose
* `/optimize { files, issueId, allowLlm }` → `{ files, strategy: TEMPLATE|LLM|CACHED, explanation }`.
* `/decompose { files, plan?, extractClasses?, splitMethods? }` → `{ files, createdFiles, explanation }`:
  вложенные классы выносятся в свои файлы, статические методы раскладываются по `Parser` / `Analyzer` / `Optimizer`.

## POST /api/verify и /api/verify/stream (SSE)
`{ original, modified, mainClass?, tests:[{name, stdin, expectedStdout?}], mode: EXACT|SORTED_LINES|MASKED }`
→ `VerificationReport { allPassed, behaviorMatched, originalTimeMs, optimizedTimeMs, speedup, complexityBefore,
complexityAfter, testResults[{name, status: PASSED|FAILED|TIMEOUT|COMPILE_ERROR, durationMs, errorDetails}], logs, ... }`.
Стрим: события `log` (строка терминала), затем `complete` (JSON отчёта) или `error`.
Отчёт сохраняется в `verification_report.json` (`blueprints.report.path`), читается через `GET /api/verify/report`.
Лимит одного запуска — 3000 мс (`blueprints.runner.timeout-ms`), зависание → статус `TIMEOUT`.

## POST /api/run
`{ files, mainClass?, stdin? }` → `{ compiled, compileOutput, stdout, stderr, exitCode, timedOut, durationMs }`.

## Профиль заглушек
`--spring.profiles.active=stub` — все эндпоинты отдают моки для работы фронта без бэкенда.
