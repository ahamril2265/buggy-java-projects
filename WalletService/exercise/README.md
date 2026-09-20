# Wallet Service: Debugging Exercise (Production Grade)

A realistic Spring Boot 4 wallet/payments service (REST API, JPA/Hibernate, Flyway migrations,
idempotency keys, a double-entry ledger, concurrency, Docker, CI). The production code contains
**24 planted bugs**; the test suite is correct. Your job is to make all **141 tests** pass by fixing
the production code, without editing anything under `src/test`.

| File | What it is |
|---|---|
| [PRD.md](PRD.md) | The specification: API, business rules, invariants |
| [BUG_CHECKLIST.md](BUG_CHECKLIST.md) | The 24 bug categories (no locations) and a way to work |
| `src/main/java/com/example/wallet` | `domain` (entities, rules), `repository`, `service`, `web` (controllers, DTOs, error handling), `config` |
| `src/main/resources` | `application.yml`, Flyway migrations `db/migration` |
| `src/test/java` | Unit tests, MockMvc API tests, and real multi-threaded concurrency tests |

## Run it

Requires JDK 21+ (Maven is provided by the wrapper).

```bash
./mvnw test                          # all tests, in-memory H2   (Windows: mvnw.cmd test)
./mvnw test -Dtest=WalletApiTest     # one class
./mvnw test -Dtest.db=postgres       # same tests on a real embedded PostgreSQL (no Docker needed)
./mvnw spring-boot:run               # start the API on :8080
```

Reports: `target/surefire-reports/`.

## Ground rules
- Do **not** edit tests or `src/main/resources/db/migration` (the schema is correct).
- Currently 59 tests fail; the goal is 141 passing, on H2 **and** on PostgreSQL.
- Some bugs only show up once others are fixed, and some only under concurrency: run
  `ConcurrencyTest` several times before you call it done.
- If you get stuck, ask for a pointer per failing test; a `reference/` folder next to this one holds
  a clean implementation. Try not to open it until you are done.
