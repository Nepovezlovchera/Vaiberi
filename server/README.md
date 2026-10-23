# server — бэкенд Java Blueprints

Spring Boot 3.3 / Java 21, JavaParser. Пакеты `com.axenix.blueprints.*`: `controller`, `model`, `parser` (код → граф, мутации),
`analyzer` (Big-O, метрики), `optimizer` (автофиксы, LLM, декомпозиция), `sandbox` (компиляция, запуск, Quality Gate).

    mvn test                       # тесты
    mvn spring-boot:run            # сервер на :8080
    mvn spring-boot:run -Dspring-boot.run.profiles=stub   # заглушки для фронта

Контракт: `../docs/API_CONTRACT.md`. Демо-монолит и входные данные: `../samples`. Быстрая проверка: `../scripts/smoke.sh`.
Запуск нужен на JDK (не JRE): пользовательский код компилируется через `javax.tools`.
