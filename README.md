# Java Blueprints — Визуальная среда программирования и анализа монолитов

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3+-brightgreen?style=for-the-badge&logo=springboot" alt="Spring Boot 3" />
  <img src="https://img.shields.io/badge/React-18-blue?style=for-the-badge&logo=react" alt="React 18" />
  <img src="https://img.shields.io/badge/React_Flow-12-red?style=for-the-badge" alt="React Flow" />
  <img src="https://img.shields.io/badge/Monaco_Editor-VS_Code_Core-blueviolet?style=for-the-badge" alt="Monaco" />
  <img src="https://img.shields.io/badge/Docker-Compose_Ready-2496ED?style=for-the-badge&logo=docker" alt="Docker" />
  <img src="https://img.shields.io/badge/Axenix_Hackathon-48_Hours-red?style=for-the-badge" alt="Axenix" />
</p>

> **Кейс хакатона Axenix (48 часов):** Разработка веб-инструмента для разбора монолитного Java-кода (~1000 строк) в интерактивный граф потока управления (CFG/Blueprints), двусторонней синхронизации правок, статического анализа с выявлением узких мест $O(n^2)$, автоматической оптимизации/декомпозиции и подтверждения сохранения поведения через Quality Gate.

---

## 🎯 Ключевой контракт кейса

> **«После любых преобразований автоматические тесты подтверждают, что поведение программы сохранилось.»**  
> *(Слайд 2 и 6 Технического задания Axenix)*

Система **Java Blueprints** не просто генерирует код, а математически и экспериментально доказывает эквивалентность поведения оптимизированной программы оригинальному монолиту с помощью встроенного дифференциального движка тестирования (**I/O Oracle**).

---

## 🚀 Ключевые возможности

- 🔄 **Полный двусторонний цикл (Java $\leftrightarrow$ Граф)**: Загрузка Java-кода $\rightarrow$ Построение структурного CFG-графа $\rightarrow$ Интерактивное редактирование на холсте $\rightarrow$ Обратная кодогенерация с сохранением форматирования (`LexicalPreservingPrinter`).
- 💎 **Интерактивный холст React Flow**: Кастомные специализированные узлы (ромб условия с портами Да/Нет, контейнер цикла с телом, точка входа метода, вызовы и возврат) с автолейаутом Dagre.
- ⚡ **Двусторонняя навигация и подсветка**: Клик по узлу на холсте или карточке дефекта мгновенно фокусирует Monaco Editor на соответствующей строке кода (`revealLineInCenter`), и наоборот.
- 🔍 **Детектор дефектов и Big-O анализатор**:
  - Детекция вложенных циклов и вызовов методов линейного поиска внутри циклов ($O(n^2)$).
  - Выявление длинных методов (>30 строк) и дублирования логики.
  - Оценка вычислительной сложности и прогноз ускорения.
- 🛠️ **Автоматическая оптимизация (1-Click Optimization)**:
  - Алгоритмическая трансформация $O(n^2) \rightarrow O(n)$ за счет предвыборки в `HashMap`/`HashSet`.
  - Замена дублирующихся проверок.
- 📦 **Автоматическая декомпозиция монолита (Слайд 6)**:
  - Разделение единого монолита `Main.java` на слабосвязанные модули: `Parser.java`, `Analyzer.java`, `Optimizer.java` и координатор `Main.java`.
- 🛡️ **Quality Gate & I/O Oracle**:
  - Изолированная компиляция в памяти (`javax.tools.JavaCompiler`).
  - Выполнение в песочнице с лимитом памяти `-Xmx256m` и сторожевым таймером 3000 мс против бесконечных циклов.
  - Нормализация вывода (**Smart Match**): посимвольный (`EXACT`), с учетом перестановки строк в хеш-таблицах (`SORTED_LINES`) и маскированием таймстемпов (`MASKED`).
  - Потоковый стриминг логов сборки и тестов через Server-Sent Events (SSE) в терминал.
  - Автоматическая генерация итогового артефакта `verification_report.json`.

---

## 🖥️ Концепт интерфейса веб-студии (согласно Слайду 4 ТЗ)

Интерфейс разделен на 4 согласованные зоны:

```
+----------------------------------------------------------------------------------------------------+
| [Java Blueprints Studio]    [Импорт Java]  [Экспорт Java]  [🚀 Оптимизировать]  [▶ Запуск] [🌙 Тема]|
+-----------------------+----------------------------------------------------+-----------------------+
| 📁 ПРОЕКТ             | 🔲 ХОЛСТ ГРАФА (React Flow + Dagre Layout)          | 📊 АНАЛИЗ & МЕТРИКИ   |
| ▾ src/com.example     |                                                    |                       |
|   📄 Main.java        |   [ Метод: processOrders(List<Order>) ]            | 🔴 Вложенность O(n²)  |
|   📄 OrderService.java|                 │ (next)                           |    Main.java:31       |
|   📄 Validator.java   |   [ Переменная: result = new ArrayList<>() ]       |    Сложность: O(n²)   |
| ───────────────────── |                 │ (next)                           |                       |
| 🧩 ПАЛИТРА БЛОКОВ     |         ◇ Условие: orders.isEmpty()?               | 🟡 Длинный метод      |
| 🔍 Поиск блоков...    |        ╱ Да            ╲ Нет                       |    Main.java:12       |
| [Переменная] [Условие]|  [Возврат result]   [Цикл for each: order]         |                       |
| [Цикл]       [Метод]  |                                                    | 📈 СЛОЖНОСТЬ:         |
| [Операция]   [Возврат]|                                                    |   Текущая: O(n²)      |
+-----------------------+----------------------------------------------------+   После:   O(n)       |
| 💻 ТАБЫ НИЖНЕЙ ПАНЕЛИ:                                                     |   Ускорение: до 5.2x  |
| [Код Java (Monaco)]         │ [📟 Консоль сборки (SSE)] │ [✅ Результаты тестов (Quality Gate)]     |
| 1 public List<Order> ...    │ [18:24:10] Компиляция...  │ ✔ testEmptyList       PASSED  12ms        |
| 2   List<Order> result = ...│ [18:24:11] Запуск тестов..│ ✔ testSingleOrder     PASSED   8ms        |
| 3   for (Order o : orders)  │ [18:24:12] Успешно (1.2x) │ ✔ testLargeDataset    PASSED 203ms        |
|                             │ Поведение совпадает ✓     │ ВСЕ ТЕСТЫ ПРОЙДЕНЫ: 12 из 12 (100%)       |
+----------------------------------------------------------------------------------------------------+
```

---

## ⚡ Быстрый старт (Запуск в одну команду)

### 🥇 Запуск всего проекта одной командой (Docker Compose)

Для сборки и запуска бэкенда (Spring Boot 3, порт 8080) и веб-студии (React Flow IDE, порт 3000) выполните **одну команду**:

```bash
docker compose up --build
```

> **Если репозиторий еще не склонирован (One-Liner для терминала):**
> ```bash
> git clone https://github.com/Nepovezlovchera/Vaiberi.git && cd Vaiberi && docker compose up --build
> ```
> *(В Windows PowerShell: `git clone https://github.com/Nepovezlovchera/Vaiberi.git; cd Vaiberi; docker compose up --build`)*

После завершения сборки перейдите в браузере:
- 🖥️ **Веб-интерфейс студии**: [http://localhost:3000](http://localhost:3000)
- ⚙️ **REST & SSE API бэкенда**: [http://localhost:8080](http://localhost:8080)
- 📑 **Swagger / OpenAPI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

### 🚀 Автономный запуск веб-студии без Docker (Мгновенное демо)

Студия оснащена **Dual-Mode**: если бэкенд не запущен, интерфейс автоматически переключается в режим **Demo Sandbox** (все графические функции, анализ $O(n^2)$, 1-Click оптимизация, Monaco Editor и Quality Gate работают локально без внешних зависимостей).

```bash
cd client
npm install
npm run dev
```
Интерфейс откроется по адресу: [http://localhost:3000](http://localhost:3000)

---

### 🛠️ Локальный запуск бэкенда без Docker (для разработки)

```bash
cd server
./mvnw clean spring-boot:run
```
*(В Windows: `.\mvnw.cmd clean spring-boot:run`)*

---

## 📁 Структура монорепозитория

```
Vaiberi/
├── client/                     # Фронтенд (React 18, React Flow, Monaco Editor)
│   ├── src/
│   │   ├── components/
│   │   │   ├── canvas/         # Холст React Flow, кастомные узлы и порты
│   │   │   ├── editor/         # Интеграция Monaco Editor и синхронизация курсора
│   │   │   ├── sidebar/        # Дерево файлов и палитра перетаскиваемых блоков
│   │   │   ├── analysis/       # Панель дефектов, Big-O карточки и метрики
│   │   │   └── terminal/       # SSE-терминал компиляции и таблица Quality Gate
│   │   ├── types/              # DTO-контракты графа, дефектов и отчетов
│   │   └── App.tsx             # Главный сплит-лейаут IDE
│   ├── package.json
│   └── vite.config.ts
├── server/                     # Бэкенд (Spring Boot 3, Java 21)
│   ├── src/main/java/com/axenix/blueprints/
│   │   ├── controller/         # REST и SSE контроллеры
│   │   ├── model/              # Модели AST Graph, Issues, VerificationReport
│   │   ├── parser/             # Движок JavaParser (Java -> Graph, Graph -> Java)
│   │   ├── analyzer/           # Статический анализатор сложности Big-O
│   │   ├── optimizer/          # Шаблоны оптимизации и авто-декомпозиции
│   │   └── sandbox/            # JavaCompiler в памяти, ProcessBuilder & I/O Oracle
│   ├── pom.xml
│   └── Dockerfile
├── samples/                    # Тестовые монолиты для валидации решения
│   ├── MonolithSample.java     # Демо-монолит ~1000 строк с вложенными O(n²) циклами
│   └── inputs/                 # Наборы тестовых данных для консольного stdin
├── docs/                       # Техническая документация хакатона
│   └── ARCHITECTURE_AND_SPEC.md# Полная архитектурная спецификация (C4, API, Big-O)
├── docker-compose.yml          # Оркестрация контейнеров
└── README.md                   # Главный документ проекта
```

---

## 📋 Матрица покрытия языка Java (Разделы 1–4 и 8 ТЗ)

| Раздел ТЗ | Синтаксическая конструкция | Узел графа | Разбор | Генерация Java |
| :--- | :--- | :---: | :---: | :---: |
| **1. Язык и java.lang** | Классы, интерфейсы, методы, static | `method` | ✅ | ✅ |
| **1. Язык и java.lang** | Примитивы, обёртки, массивы, присваивания | `var`, `op` | ✅ | ✅ |
| **1. Язык и java.lang** | Ветвления `if / else`, `switch` | `condition`, `switch` | ✅ | ✅ |
| **1. Язык и java.lang** | Циклы `for`, `foreach`, `while` | `loop` | ✅ | ✅ |
| **1. Язык и java.lang** | Управление: `break`, `continue`, `return` | `return`, `op` | ✅ | ✅ |
| **1. Язык и java.lang** | Исключения: `try / catch / finally`, `throw` | `try` | ✅ | ✅ |
| **2. Консольный ввод/вывод**| `System.in`, `Scanner`, `printf`, `Formatter` | `call`, `op` | ✅ | ✅ |
| **2. Консольный ввод/вывод**| `StandardCharsets.UTF_8` (Кириллица) | I/O Stream | ✅ | ✅ |
| **3. Коллекции java.util** | `List`, `Set`, `Map` (`ArrayList`, `HashMap`) | `var`, `call` | ✅ | ✅ |
| **3. Коллекции java.util** | `Iterator`, `Collections`, `Arrays`, `Comparator` | `call`, `op` | ✅ | ✅ |
| **3. Коллекции java.util** | Фабрики `List.of`, `Set.of`, `Map.of` | `op` | ✅ | ✅ |
| **4. Исключения** | Проверяемые и непроверяемые, `throws` | Сигнатура / `try`| ✅ | ✅ |
| **8. Бонусные фичи** | `record` и `enum` | Узел декларации | ✅ | ✅ |
| **8. Бонусные фичи** | Лямбды, `java.util.function`, `Stream API` | Функциональный `op` | ✅ | ✅ |

> **Границы кейса (Out of scope):** В соответствии со Слайдом 8 ТЗ, многопоточность (`java.util.concurrent`), рефлексия, файловые операции (`java.nio.file`), сетевые сокеты и `java.time` не требуются и не оцениваются.

---

## 🧪 Пример отчета Quality Gate (`verification_report.json`)

После запуска тестов верификации формируется структурированный артефакт:

```json
{
  "allPassed": true,
  "behaviorMatched": true,
  "originalTimeMs": 1240,
  "optimizedTimeMs": 238,
  "speedup": "5.2x",
  "complexityBefore": "O(n²)",
  "complexityAfter": "O(n)",
  "testResults": [
    { "name": "testEmptyList", "status": "PASSED", "durationMs": 12 },
    { "name": "testSingleOrder", "status": "PASSED", "durationMs": 8 },
    { "name": "testInvalidOrder", "status": "PASSED", "durationMs": 15 },
    { "name": "testLargeDataset_100k", "status": "PASSED", "durationMs": 203 }
  ],
  "timestamp": "2026-10-25T07:45:00Z"
}
```

---

## 👥 Команда проекта (Vaiberi)

- **D1 — Graph Lead**: Визуальный холст React Flow, кастомные узлы, порты связи, автолейаут Dagre, Drag-and-Drop палитра.
- **D2 — IDE & Experience Lead**: Каркас веб-студии (сплиты), Monaco Editor, двухсторонняя синхронизация курсора, панели диагностики и тестов.
- **D3 — AST & Compiler Engine**: Парсинг Java 21 в Graph JSON, стабильные AST-пути, round-trip кодогенерация, декомпозиция монолита.
- **D4 — Code Transformation & Analyzer**: Детектор $O(n^2)$, шаблоны мутаций AST в `HashMap`, локальный кэширующий ассистент.
- **D5 — Sandbox Runner & Quality Gate**: Изолированная компиляция `JavaCompiler`, запуск тестов, I/O Oracle, SSE-стриминг логов, Docker.

---

## 📑 Материалы и ссылки

- 📖 [Подробное архитектурное описание (ARCHITECTURE_AND_SPEC.md)](file:///c:/Users/gurzi/Desktop/Java%20Blueprints/docs/ARCHITECTURE_AND_SPEC.md)
- 📋 [Комплексный 48-часовой план реализации (code_artifact.md)](file:///c:/Users/gurzi/Desktop/Java%20Blueprints/code_artifact.md)
- 🌐 [Репозиторий проекта на GitHub](https://github.com/Nepovezlovchera/Vaiberi)
