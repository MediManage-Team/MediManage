# Subscription AI Prompt Version Registry Baseline (v1)

Status date: 2026-02-23  
Scope: Central prompt template versioning for AI flows with change history and rollback capability.

## 1) Objective

Provide controlled prompt evolution with:

1. Per-prompt version tracking.
2. Active-version resolution at runtime.
3. Safe rollback to previously known-good templates.

## 2) Data Model

Table: `ai_prompt_registry`

Core fields:

1. `prompt_key`
2. `version_number`
3. `template_text`
4. `change_type` (`SEED`, `UPDATE`, `ROLLBACK`)
5. `change_note`
6. `rolled_back_from_version`
7. `is_active`
8. `changed_by_user_id`
9. `created_at`

Constraints:

1. Unique `(prompt_key, version_number)`.
2. Active prompt lookup indexed by `(prompt_key, is_active, version_number DESC)`.

## 3) Runtime Resolution

`AIPromptCatalog` resolves templates through `AIPromptRegistryService`:

1. Reads active version for the key.
2. If missing, seeds key with hard-coded default as `SEED` version `1`.
3. Falls back to hard-coded defaults when registry access is unavailable.

Tokenized placeholders (`{{TOKEN_NAME}}`) are replaced at runtime for dynamic prompt values.

## 4) Change Tracking and Rollback

Service APIs:

1. `registerPromptVersion(promptKey, templateText, changeNote, changedByUserId)`
2. `rollbackPromptVersion(promptKey, targetVersionNumber, changeNote, changedByUserId)`
3. `getPromptVersionHistory(promptKey, limit)`

Rollback behavior:

1. Target historical version is read.
2. A new `ROLLBACK` version is created with copied `template_text`.
3. New rollback version becomes active.
4. Full history is preserved (no destructive overwrite).

## 5) Safety and Governance

1. Prompt updates do not auto-change RBAC ownership or approval roles.
2. Prompt failures use deterministic defaults to preserve checkout continuity.
3. Change type and note are stored for audit-style review and post-incident analysis.

## 6) Validation Coverage

`AIPromptRegistryServiceTest` validates:

1. Auto-seeding of missing prompt keys.
2. Active version switching on update.
3. Rollback creation and activation from historical version.
