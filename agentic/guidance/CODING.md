# Coding guidance

## Android style

- Prefer clear Kotlin and small Compose functions.
- Keep Android framework integration separate from future domain logic where practical.
- Do not introduce dependencies without explicit approval.
- Preserve the app identity unless explicitly asked to change it:
  - namespace: `com.example.vocalorie`
  - applicationId: `app.vocalorie.personal`
  - app label: `Vocalorie`

## Architecture rules

These are the rules the `improve-performance-and-code-quality` change established. They are enforced by review, not by tooling. A rule change here must update the matching ADR in `docs/arc42.md` in the same commit.

1. **Layering is one-directional: UI → state holder → repository → DAO.** No `Context` above the repository boundary, and no DAO reference above it either. `AppContainer` builds the graph; `MealCaptureViewModel` holds capture-flow state; `data/repository/` owns dispatching, mapping and transactions. The repository — not the DAO — is the seam everything above depends on.
2. **Composables render state and emit events.** No business logic inside an argument lambda. A rule that decides something (`NutritionGoals.parse`, `EditableActivityDraft.validate`, `planEstimate`) is a pure function outside the composable and outside the state holder, so it is testable without either.
3. **Nothing expensive in a composable body without `remember`.** Formatters, searches, derived lists and score computations are keyed on their inputs. Clocks are hoisted to parameters — a composable never reads `Instant.now()` inline, because that defeats memoization and makes the result untestable.
4. **No bitmap, crypto, preference or JSON work on the main thread.** Decoding, encrypting, `SharedPreferences` reads and `itemsJson` parsing run on a repository dispatcher or an explicit `withContext`.
5. **Long-lived clients are created once, injected, and closed.** HTTP engines, prompt executors and LLM clients belong in `AppContainer` and are handed to their users. Never construct one per call.
6. **No failure path may convert an error into plausible data.** Malformed input, an unreadable stored key or an unknown enum value is reported or mapped to a neutral value — never to a real-looking 0-kcal meal, "no key configured", or a default that renders as a real choice. Classify a failure by walking the **cause chain**, not the outermost `Throwable.message`; Koog and Ktor wrap HTTP errors.
7. **Prefer a value type over three or more same-typed positional parameters.** Eight `String`s in a row compile happily when two are transposed. Wrap them (see `EditableNutrition`) so the compiler catches it.
8. **Room migrations are additive only, and the backup version moves in the same commit.** Never `fallbackToDestructiveMigration`. A schema bump means: a new `Migration`, registered; `BACKUP_SCHEMA_VERSION` raised; `SUPPORTED_BACKUP_SCHEMA_VERSIONS` widened so files already exported still import — all together, or existing backups are orphaned.
