---
name: contract-first
description: Align requirements, API contracts, frontend tasks, backend tasks, tests, permissions, and versioning before implementation. Use when fields, enums, paging, errors, states, or ownership must be checked.
---

# Contract First

## Workflow

1. Read `engineering/rules/checklists/contract.md` and the module requirement, design, API and task docs.
2. Inspect current consumers and implementation only to understand compatibility.
3. Compare path, method, auth, request, response, fields, enums, pagination, errors, permissions, idempotency, and versioning.
4. Classify status as `ready`, `missing`, `conflicting`, or `needs-confirmation`.
5. Record differences and required changes before implementation.

## Boundaries

- Do not create a competing contract when a project contract exists.
- Do not guess business-critical fields to make the contract look ready.
- Preview SQL, permission, migration, and destructive operations.
