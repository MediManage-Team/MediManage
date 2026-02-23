# Subscription AI Input Safety and PII Masking Baseline (v1)

Status date: 2026-02-23  
Scope: Approved-field filtering and PII masking/tokenization before AI inference for subscription-adjacent AI flows.

## 1) Safety Controls Added

Central guard:

- `AIInputSafetyGuard`

Capabilities:

1. Deterministic customer tokenization (`customer_<hash>`).
2. Free-text masking for:
   - emails,
   - phone numbers,
   - policy/id-like tokens,
   - labeled person references (`patient/customer <name>`).
3. Approved-field projection for checkout medicine prompts.

## 2) Approved Fields by Flow

### 2.1 Care Protocol Prompt (Checkout)

Allowed fields:

1. medicine name (sanitized),
2. quantity,
3. expiry (sanitized).

Blocked fields:

1. customer identifiers,
2. bill ids,
3. medicine ids,
4. price/gst/line totals,
5. payment details.

### 2.2 Customer History Prompt

Allowed fields:

1. tokenized customer reference,
2. sanitized known conditions.

Blocked fields:

1. raw customer name,
2. contact details,
3. policy/account identifiers.

### 2.3 Multilingual Savings Explanation

Allowed fields:

1. sanitized summary text,
2. sanitized talking points,
3. eligibility/discount decision context.

Masked before AI translation where present:

1. email,
2. phone,
3. policy/id tokens,
4. labeled person names.

## 3) Validation Coverage

1. `AIInputSafetyGuardTest` validates tokenization, masking, and approved medicine prompt projection.
2. `SubscriptionMultilingualExplanationServiceTest` validates masked PII in localized snippet output.

## 4) Operational Notes

1. This baseline focuses on prompt-input hygiene, not end-to-end token vaulting.
2. Additional masking rules can be extended centrally in `AIInputSafetyGuard` for future flows.
