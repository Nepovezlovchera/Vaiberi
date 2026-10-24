# server — бэкенд Java Blueprints

Spring Boot 3.3 / Java 21, JavaParser. Пакеты `com.axenix.blueprints.*`: `controller`, `model`, `parser` (код → граф, мутации),
`analyzer` (Big-O, метрики), `optimizer` (автофиксы, LLM, декомпозиция), `sandbox` (компиляция, запуск, Quality Gate).

    # Запуск через Maven Wrapper:
    ./mvnw test                    # тесты (Windows: .\mvnw.cmd test)
    ./mvnw spring-boot:run         # сервер на :8080 (Windows: .\mvnw.cmd spring-boot:run)
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=stub   # заглушки для фронта

    # Или через глобальный mvn:
    mvn test                       # тесты
    mvn spring-boot:run            # сервер на :8080

Контракт: `../docs/API_CONTRACT.md`. Демо-монолит и входные данные: `../samples`. Быстрая проверка: `../scripts/smoke.sh`.
Запуск нужен на JDK (не JRE): пользовательский код компилируется через `javax.tools`.
