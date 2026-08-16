# NUNNUN Backend Development Rules

## Project and source of truth

- Project: NUNNUN mobile-app backend.
- Stack: Java 21, Spring Boot, Gradle, Spring Data JPA, Spring Security, JWT, Bean Validation, MySQL, Swagger/OpenAPI, Firebase Cloud Messaging, AWS S3, and OpenAI API.
- Treat these files as the current specification source of truth:
  1. `docs/API_SPEC.pdf` for API URLs, HTTP methods, and API contracts.
  2. `docs/DB.pdf` for tables, columns, foreign keys, constraints, and deletion policy.
  3. This file for implementation conventions.
- If `docs/API_SPEC.pdf` and `docs/DB.pdf` conflict, do not resolve the conflict by assumption. Explain it and ask the user before implementing.
- Do not change an API URL, HTTP method, request format, or response format defined by `docs/API_SPEC.pdf`.
- Do not add features, columns, foreign keys, or tables not in the specifications. Explain the need and get user approval first if a schema change is required.

## Domain architecture

- Use package-by-domain. Java package names must be lowercase:
  - `auth`, `user`, `device`, `my`, `schedule`, `sleep`, `wake`, `roommate`.
- Within a domain, create only the packages needed by the implemented feature: `controller`, `service`, `repository`, `entity`, and `dto`.
- Do not create empty packages or placeholder application files.
- Reuse existing domain structures where possible. Do not refactor unrelated or existing domains without a task-specific reason.
- Keep one task scoped to the requested files and behavior; avoid unrelated edits.

## Layering and JPA

- Controllers handle HTTP concerns only; never place business logic in a controller.
- Services own business rules and transaction boundaries.
- Repositories own persistence queries; watch for N+1 queries and use appropriate fetch strategies or query methods when needed.
- Never return JPA entities directly from an API. Use response DTOs.
- Keep request DTOs and response DTOs separate.
- Use constructor injection.
- If Lombok is used, never use `@Data` on an entity.
- Avoid public setters and setter-heavy entity design. Prefer meaningful domain methods for state changes.
- Avoid unnecessary bidirectional associations. Prefer unidirectional mappings, especially for references to `User`.
- Model state values with enums whenever the value set is finite.
- Use `@Transactional` for writes with the smallest practical scope. Use `@Transactional(readOnly = true)` for read services where appropriate.

## Validation, security, and errors

- Apply Bean Validation to all user input DTOs.
- Combine service-level validation with DB-level UNIQUE and CHECK constraints.
- Store passwords only as BCrypt hashes. Never store or log plaintext passwords.
- Never hardcode JWT secrets, database passwords, OpenAI keys, AWS keys, or FCM credentials. Inject them through environment variables or separate configuration.
- For authenticated APIs, derive the acting user from Spring Security authentication. Never trust a client-provided `userId` as the acting user.
- Use a common exception-handling structure for API failures.
- Default success response:

  ```json
  { "success": true, "data": "..." }
  ```

- Default error response:

  ```json
  {
    "success": false,
    "error": { "code": "ERROR_CODE", "message": "message" }
  }
  ```

- A response explicitly defined by `docs/API_SPEC.pdf` takes precedence over this default.

## Database rules from the current specification

- Implement only the 16 specified tables: `users`, `users_devices`, `refresh_tokens`, `fixed_schedules`, `daily_routines`, `sleep_sessions`, `sleep_feedbacks`, `wake_groups`, `wake_group_members`, `wake_requests`, `wake_proofs`, `roommate_groups`, `roommate_group_members`, `roommate_complaints`, `roommate_behavior_manuals`, and `notifications`.
- Follow DB-defined FK relationships exactly. Do not add or alter them without approval.
- Preserve these key constraints in schema and service logic:
  - `users.email` is unique.
  - `users_devices.fcm_token` is unique and each device belongs to a user through `user_id`.
  - `daily_routines` is unique by `(user_id, routine_date)`.
  - `sleep_feedbacks` is unique by `(user_id, feedback_date)`.
  - `wake_group_members` is unique by `(wake_group_id, user_id)` and `(wake_group_id, slot_no)`; `slot_no` must be 1 through 12.
  - `roommate_group_members.user_id` is unique; membership slots are limited to 1 and 2, with unique group/member and group/slot pairs.
  - `wake_proofs.wake_request_id` is unique.
  - `roommate_behavior_manuals` is unique by `(roommate_group_id, target_user_id)`.
- Follow the specified deletion behavior: `users.deleted_at` is soft deletion; expired wake proofs require deletion of both the S3 image and the `wake_proofs` record after the specified expiry.
- Manage `created_at` and `updated_at` consistently across the project.

## Domain-specific contract reminders

- Wake request states are finite (`SENT`, `VERIFIED`, `NEEDS_HELP`) and must be represented by an enum.
- Roommate group states are finite (`WAITING`, `ACTIVE`) and must be represented by an enum.
- Sleep feedback values are finite (`VERY_BAD`, `BAD`, `NORMAL`, `GOOD`, `VERY_GOOD`) and must be represented by an enum.
- A wake proof verification creates a 30-minute wake cooldown calculated from `verified_at`; do not introduce an unapproved cooldown column.
- Expired wake-proof cleanup must remove the S3 object and its database record.
- Never expose `roommate_complaints.content` to the complaint target.
- On roommate complaint creation or modification, update the target user's current behavior manual through OpenAI and persist it in `roommate_behavior_manuals`.
- The specified roommate complaint modification API changes only `content`; do not allow its target user to be changed by that operation.

## Testing and completion

- For each implemented feature, consider: success, validation failure, missing resource, unauthorized user, duplicate data, and core business-rule violations.
- Write tests for important service business rules.
- Use mocks or fakes for OpenAI, FCM, and S3; tests must not call external services.
- Before completion, run relevant tests, the full test suite when practical, and `./gradlew build` when available.
- Do not report work as complete if tests or the build fail. Investigate and fix the relevant failure first.
- For each implementation request: inspect relevant docs and existing code first, make only scoped changes, then report created files, modified files, implementation details, tests run, build result, and remaining issues.
- If a large design change is needed, explain it and obtain direction before implementing.
