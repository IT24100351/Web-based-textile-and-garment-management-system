# Contributing to TGMS

This document is the shared engineering and handoff contract for every ticket in the
Textile & Garment Management System.

## One verification process

Install the committed dependencies and run the complete local quality gate from the
repository root:

```bash
npm ci
npm run verify
```

`npm run verify` runs the same checks as pull-request CI, in this order:

1. `npm run lint` — ESLint for React, TypeScript, and repository scripts.
2. `npm run typecheck` — TypeScript type-check plus Spring Boot compilation.
3. `npm test` — Vitest/Testing Library and Maven/JUnit/MockMvc regression tests.
4. `npm run db:verify` — apply, roll back, and reapply every Liquibase changeset
   against the isolated H2 test database.
5. `npm run build` — Vite production assets and the executable Spring Boot JAR.

Run the complete command before handoff, even if narrower checks were used while
developing. The repository does not currently enforce a separate formatter. Preserve
the surrounding style and avoid mass-formatting unrelated files; ESLint is the
authoritative source check until a formatter is adopted in a dedicated team change.

## Application boundary

The working architecture is:

```text
React UI -> Axios API client -> Spring controller -> service/domain -> JDBC -> MySQL
```

- A module owns its source-of-truth records. Other modules store stable IDs and use
  documented services or APIs instead of copying master data.
- Preserve referenced business history with controlled lifecycle states such as
  inactive, discontinued, cancelled, or archived rather than hard deletion.
- Controllers remain thin: authenticate and authorize, validate input, call a service,
  then serialize a safe response.
- Multi-record operations that must succeed together use a Spring transaction.
- Prices, totals, ownership, roles, and status transitions are derived or verified on
  the server; client values are never authoritative.
- Secrets and environment-specific connection details belong in ignored environment
  files. Commit only `.env.example` placeholders.

## Input-validation convention

Spring Boot and Jakarta Bean Validation are the project standard. Do not introduce a
second validation stack for new endpoints.

- Request bodies use dedicated request DTOs or records and `@Valid @RequestBody`.
- Use constraints such as `@NotBlank`, `@Size`, `@Email`, `@Positive`, and controlled
  enum values as appropriate to the field.
- Path and query parameters must be parsed to their real types and constrained before
  service logic. IDs must be positive and must reference records visible to the
  authenticated user.
- Normalize values only when the domain contract defines normalization. Never silently
  change a controlled status or substitute a missing record.
- Authentication and `RoleGuards` authorization must run on the server. React route or
  menu visibility is defense-in-depth, not permission enforcement.
- Services enforce ownership, current state, valid state transitions, cross-record
  rules, and transaction boundaries.

Validation failures must be tested at the HTTP boundary and must not reach mutation
logic.

## API response and error convention

Successful endpoints return an explicitly typed response DTO and an appropriate HTTP
status. API errors use the existing `ApiErrorResponse` envelope:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Please correct the highlighted fields.",
    "fields": {
      "email": "Enter a valid email address."
    }
  }
}
```

- `code` is a stable uppercase `SNAKE_CASE` machine-readable value.
- `message` is safe user-facing text and must not contain SQL, stack traces, secrets,
  password hashes, tokens, or internal implementation details.
- `fields` is always an object. Use an empty object when the error is not field-specific.
- Use `400` for malformed or validation input, `401` for missing/invalid authentication,
  `403` for an authenticated but disallowed role, `404` for unavailable resources, and
  `409` for a state or uniqueness conflict.
- Unexpected failures are logged server-side and return the generic `500` code
  `INTERNAL_ERROR` with no implementation details.

New exceptions must be mapped centrally in `ApiExceptionHandler`; do not create a
different error shape in an individual controller.

## Test expectations

Every ticket adds coverage proportional to its risk:

- one main success path;
- validation and important business-rule failures;
- unauthenticated and wrong-role cases for protected actions;
- ownership and controlled-state cases where relevant;
- transaction/rollback behavior for atomic multi-record changes;
- React loading, empty, success, and error states for new data-driven UI;
- migration apply/rollback coverage for every schema change.

Tests must be deterministic, use isolated test data, and avoid real credentials or
external services.

## Definition of Done

A ticket is ready for Code Review only when all applicable items are true:

- [ ] Only the active ticket scope was implemented.
- [ ] Every acceptance criterion is manually mapped to the implementation.
- [ ] Existing public contracts are preserved or backward-compatible changes are documented.
- [ ] Backend authorization and ownership rules are enforced and tested.
- [ ] Inputs, IDs, numbers, and controlled statuses are validated before business logic.
- [ ] Services contain reusable business rules; controllers and UI components remain thin.
- [ ] Database changes use documented, reversible Liquibase changesets.
- [ ] Loading, empty, success, and error states exist where the UI requires them.
- [ ] Success, failure, and authorization tests appropriate to the ticket are included.
- [ ] `npm run verify` passes from the repository root.
- [ ] Manual QA steps and results are recorded in the ticket or pull request.
- [ ] Routes, services, migrations, contract changes, limitations, and documentation are reported.
- [ ] No secrets, generated build output, debug logging, or unrelated changes are committed.
- [ ] The pull-request checklist is completed and another team member reviews the change.

Merge to the shared branch only after review and required CI checks pass.
