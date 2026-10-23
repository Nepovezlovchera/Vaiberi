# Сводка текущего состояния проекта Java Blueprints
> **Дата и время фиксации**: 23.10.2026, 23:20 (Хакатон Axenix, рубеж 1)  
> **Текущая ветка**: `client`

---

## 1. Структура репозитория и веток

| Ветка | Назначение | Текущее состояние |
| :--- | :--- | :--- |
| **`client`** | Ветка фронтенда (рабочая) | Содержит полноценное веб-приложение студии (`/client`), React Flow, Monaco Editor, Zustand, кастомные ноды, стили `taste-skill`. |
| **`server`** | Ветка бэкенда (Неповезло) | Содержит Spring Boot 3, Java 21, JavaParser, AST-движок, детектор Big-O, декомпозитор и песочницу запуска с I/O Oracle. |
| **`main`** | Главная релизная ветка | Содержит только чистую документацию ([README.md](file:///c:/Users/gurzi/Desktop/Java%20Blueprints/README.md), [ARCHITECTURE_AND_SPEC.md](file:///c:/Users/gurzi/Desktop/Java%20Blueprints/docs/ARCHITECTURE_AND_SPEC.md), [API_CONTRACT.md](file:///c:/Users/gurzi/Desktop/Java%20Blueprints/docs/API_CONTRACT.md), [code_artifact.md](file:///c:/Users/gurzi/Desktop/Java%20Blueprints/code_artifact.md)). Код изолирован в ветках. |

---

## 2. Готовность фронтенда (`/client`) — 100% MVP со Слайда 4 ТЗ

- **Стек**: React 18, Vite 6, TypeScript, `@xyflow/react` (React Flow v12), `@monaco-editor/react`, Zustand, Tailwind CSS, `@dagrejs/dagre`.
- **Компоненты**:
  1. `TopBar`: кнопки «Импорт Java», «Экспорт Java», «🚀 Оптимизировать», «▶ Запуск», «Дебаг», переключатель темы.
  2. `LeftSidebar`: дерево файлов проекта (`Main.java`, `OrderService.java`...) и палитра перетаскиваемых AST-блоков (`var`, `condition`, `loop`, `method`, `op`, `return`).
  3. `FlowCanvas`: холст графа потока управления с автолейаутом Dagre, кастомными нодами (ромб condition с выходами Да/Нет, блок цикла с телом и бейджем $O(n^2)$).
  4. `MonacoEditorPane`: редактор Java 21 с двусторонней подсветкой при клике на граф или дефект.
  5. `BottomTabsPane`: переключение между кодом, консолью компиляции и матрицей автотестов Quality Gate (12 из 12).
  6. `AnalysisPanel`: карточки дефектов со Слайда 4 («Вложенность O(n²)», «Длинный метод», «Дублирование логики»), блок оценки сложности и ускорения (до 5.2x).
- **Сборка**: `npm run build` успешно компилирует production bundle за 9 сек.
- **Сервер разработки**: запущен на `http://localhost:3000/`.

---

## 3. Готовность бэкенда (`/server`)

- **Стек**: Spring Boot 3.3+, Java 21, JavaParser 3.26+, JUnit 5.
- **Реализованные эндпоинты**:
  - `POST /api/graph` — разбор Java в Graph JSON.
  - `POST /api/graph/mutate` — модификация AST с dry-run проверкой компиляции.
  - `POST /api/analysis` — статический анализ дефектов и Big-O.
  - `POST /api/optimize` — замена $O(n^2)$ на `HashMap`/`HashSet`.
  - `POST /api/decompose` — декомпозиция монолита на `Parser.java`, `Analyzer.java`, `Optimizer.java`, `Main.java`.
  - `POST /api/run` & `POST /api/verify` — изолированный запуск в песочнице с лимитом 3000 мс и Smart Match нормализацией.
  - `GET /api/verify/stream` — потоковый SSE-стриминг логов в терминал.
- **Инфраструктура**: подготовлен единый `docker-compose.yml`.

---

## 4. Как запустить проект

### Фронтенд:
```bash
cd client
npm install
npm run dev
# Открыть http://localhost:3000
```

### Бэкенд:
```bash
cd server
./mvnw clean spring-boot:run
# API доступно на http://localhost:8080
```

### Единая сборка через Docker:
```bash
docker compose up --build
```
