---
name: backend-coding-standards
description: Implement or review backend APIs, services, domain logic, persistence, integrations, migrations, or tests under this repository's backend rules and API contract.
---

# Backend Coding Standards

## Workflow

1. Read `.ai/current-role.md`, `engineering/rules/roles/backend.md`, and `engineering/rules/checklists/backend-api.md`.
2. Locate the unique API contract, affected callers, nearby patterns, data model, and tests.
3. Implement the smallest coherent change with a thin transport layer and explicit service/domain rules.
4. Add tests for business rules, boundaries, authorization, states, and external failures.
5. Run focused commands configured in `.ai/project.json`.
6. Report files, validation, compatibility, data/config/deployment impact, and residual risk.

## Boundaries

- Do not silently change the contract.
- Do not execute migrations or touch real data without scope, confirmation, and rollback.
