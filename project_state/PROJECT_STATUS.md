# Сводка текущего состояния проекта Java Blueprints Studio
> **Дата и время фиксации**: 24.10.2026, 12:15 (Хакатон Axenix, рубеж 2)  
> **Текущая ветка**: `main` (коммит `15fe818`)  
> **Статус синхронизации**: Полностью синхронизировано с `origin/main` (up-to-date)

---

## 1. Архитектура и структура репозитория

Вся кодовая база бэкенда и фронтенда **полностью объединена в ветке `main`** и связана через обратный прокси Vite (`:3000/api` → `:8080/api`):

```
Java Blueprints/
├── client/                     # Фронтенд-студия (React 18, Vite 6, React Flow v12, Monaco, Zustand)
│   ├── src/
│   │   ├── api/apiClient.ts    # Типизированный клиент ко всем 8 эндпоинтам бэкенда
│   │   ├── components/
│   │   │   ├── canvas/         # Канвас CFG-графа, Dagre автолейаут, drag-and-drop узлов, соединения ребер
│   │   │   ├── editor/         # Monaco Editor + EditorTabs (полоса вкладок файлов над кодом)
│   │   │   ├── terminal/       # 3-колоночная панель Слайда 4 (Код | Консоль с Ring Buffer | Тесты 12 шт.)
│   │   │   ├── analysis/       # Карточки дефектов, Diff Preview (Слайд 5) и Quick-Fix
│   │   │   └── layout/         # TopBar с пресетами, авто-демо на 60 сек и статусом Live API
│   │   └── store/              # Zustand (useStudioStore, graphMapper, graphEdits, monolithData)
│   └── package.json
├── server/                     # Бэкенд-сервер (Java 21, Spring Boot 3.3.5, JavaParser, JUnit 5)
│   ├── src/main/java/com/axenix/blueprints/
│   │   ├── controller/         # REST API контроллеры (/api/*) и SSE стриминг
│   │   ├── parser/             # AST, GraphBuilder (labelled loops, switch arrows, anonymous classes, records)
│   │   ├── analyzer/           # Детектор Big-O (O(n²)), вложенных циклов, дублирования
│   │   ├── optimizer/          # Шаблоны оптимизации O(n²) → O(n) (HashSet, HashMap)
│   │   ├── decomposer/         # Декомпозиция монолита на 4-8 специализированных классов
│   │   └── sandbox/            # Изолированная песочница JVM 21, Quality Gate, Smart Match, UTF-8 IO
│   ├── pom.xml
│   └── mvnw / mvnw.cmd         # Maven Wrapper с поддержкой сборки
├── samples/                    # Эталонный монолит (487 строк) и 12 тестовых датасетов (до 8000 строк)
└── docker-compose.yml          # Единый запуск всего стека в Docker
```

---

## 2. Статус реализации требований ТЗ (Axenix Case)

| Требование ТЗ / Слайд | Что реализовано | Статус |
| :--- | :--- | :---: |
| **Слайд 2 (Монолит)** | Монолит интернет-магазина (487 строк) [`samples/Main.java`](file:///c:/Users/gurzi/Desktop/Java%20Blueprints/samples/Main.java) встроен в пресеты фронтенда, 12 реальных тестов (`.in`). | **100% ГОТОВО** |
| **Слайд 3 (Двусторонняя реактивность)** | Monaco $\leftrightarrow$ Граф AST: правка в коде перестраивает граф (debounce 600мс); удаление/перетаскивание/вставка ноды на канвасе мутирует код через `LexicalPreservingPrinter`. | **100% ГОТОВО** |
| **Слайд 4 (Интерфейс студии)** | Точная 3-колоночная нижняя панель: **Код Java** (слева, 42%) + **Терминал JVM** (по центру, 28%) + **Результаты тестов** (справа, 30%). Вкладки файлов над Monaco: `[ Main.java x ] [ Order.java x ] [ + ]`. | **100% ГОТОВО** |
| **Слайд 5 (Диагностика и Diff)** | Анализ Big-O, определение $O(n^2)$, разворачивающийся блок **Diff Preview** (старый цикл vs `HashSet`/`HashMap`), кнопка «Исправить дефект» с ожидаемым ускорением 5.2x. | **100% ГОТОВО** |
| **Слайд 6 (Декомпозиция)** | Авто-декомпозиция монолита на 8 классов (`Order`, `PromoCode`, `Stats`, `Parser`, `Optimizer`...). | **100% ГОТОВО** |
| **Слайд 7 (Quality Gate)** | Потоковый запуск 12 тестов через SSE (`/api/verify/stream`), дифференциальное сравнение оригинального и оптимизированного вывода (100% совпадение). | **100% ГОТОВО** |
| **Слайд 10 (Сценарий защиты 60 сек)** | Кнопка **«▶ Демо ТЗ (60 сек)»** в шапке: автономный пошаговый тур через все шаги без риска ошибки докладчика. | **100% ГОТОВО** |
| **Клиентская безопасность** | **Ring Buffer (1000 строк)** в консоли для защиты от Client-side DoS на тесте `12_large_dataset.in` (8000 строк), лимиты файлов (2 МБ / 5 МБ), защита от XSS и Path Traversal. | **100% ГОТОВО** |

---

## 3. Сквозное тестирование API (100% работоспособность)

Все 8 эндпоинтов проверены через прокси фронтенда (`http://localhost:3000/api`):

```
✔ 1. Health (/api/health)                         -> Status 200 OK (status: ok)
✔ 2. Graph (/api/graph)                           -> Status 200 OK (методы, узлы CFG, ребра)
✔ 3. Analysis (/api/analysis)                     -> Status 200 OK (дефекты, сложность, LOC)
✔ 4. Mutate (/api/graph/mutate)                   -> Status 200 OK (dry-run javac валидация + откат)
✔ 5. Optimize (/api/optimize)                     -> Status 200 OK (устранение O(n²))
✔ 6. Decompose (/api/decompose)                   -> Status 200 OK (выделение классов)
✔ 7. Run in Sandbox (/api/run)                    -> Status 200 OK (JVM за 105-150 мс)
✔ 8. SSE Quality Gate (/api/verify/stream)        -> Status 200 OK (21 чанк стрима, 12 тестов)
```

---

## 4. Как запустить и работать со студией

### Серверы запущены локально прямо сейчас:
* **Интерфейс студии:** [http://localhost:3000](http://localhost:3000)
* **API бэкенда:** [http://localhost:8080/api/health](http://localhost:8080/api/health)
* **Swagger документация:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Команды ручного запуска при необходимости:
```bash
# 1. Запуск бэкенда (из корня репозитория)
cd server
cmd.exe /c "set JAVA_HOME=C:\Users\gurzi\.tools\jdk-21.0.12.1+1&& mvnw.cmd spring-boot:run"

# 2. Запуск фронтенда
cd client
npm run dev

# 3. Проверка сборки фронтенда
npm run build
```
