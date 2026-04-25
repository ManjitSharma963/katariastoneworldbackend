## Architecture Rules (Backend)

This project follows a strict layered architecture:

1. **Controller layer (`..controller..`)**
   - Accepts/returns **DTOs only**.
   - Handles HTTP concerns only (request parsing, auth context extraction, status codes).
   - Delegates all business behavior to services.
   - **Must not call repositories directly**.

2. **Service layer (`..service..`)**
   - Contains business logic only.
   - Coordinates repositories and other services.
   - Performs validation, calculations, workflow/state transitions.
   - **Must not contain HTTP/web concerns** (`@RestController`, `ResponseEntity`, request/response APIs).

3. **Repository layer (`..repository..`)**
   - Data access only (JPA queries, persistence concerns).
   - No business workflow logic.
   - **Must not depend on controllers/services/DTOs**.

4. **DTO layer (`..dto..`)**
   - API contracts only.
   - No persistence annotations or business services.

## Dependency Direction

Allowed direction:

`controller -> service -> repository`

`dto` is used at boundaries (controller input/output and service mapping), but repositories should map to entities, not DTOs.

## Guardrails

Automated architecture tests are added in:
- `src/test/java/com/katariastoneworld/apis/architecture/ArchitectureRulesTest.java`

Run checks:

```bash
mvn test
```

If a new class violates these boundaries, tests will fail.
