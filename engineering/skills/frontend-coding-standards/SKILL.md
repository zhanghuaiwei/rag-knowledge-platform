---
name: frontend-coding-standards
description: Implement or review frontend pages, components, API clients, types, state, routes, permissions, forms, tables, or visible UI under this repository's frontend rules and API contract.
---

# Frontend Coding Standards

## Workflow

1. Read `.ai/current-role.md`, `engineering/rules/roles/frontend.md`, and `engineering/rules/checklists/frontend-crud.md`.
2. Locate the target app, unique API contract, nearby patterns, shared components, request client, routes, and tests.
3. Keep protocol details in the API client and user behavior in the UI layer.
4. Cover loading, empty, error, permission, disabled, submit-in-progress, and dangerous-action states.
5. Run focused lint, typecheck, and tests configured in `.ai/project.json`.
6. Verify visible changes in a browser or provide an exact manual path.

## Boundaries

- Do not guess API fields or duplicate request abstractions.
- Do not treat zero static findings as complete UI verification.
