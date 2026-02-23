# Subscription AI Multilingual Explanation Snippets Baseline (v1)

Status date: 2026-02-23  
Scope: Provide customer-facing subscription savings explanations in selected language on checkout confirmation and invoice.

## 1) Objective

Improve subscription savings transparency by showing a short, understandable explanation in the operator-selected language.

## 2) Inputs

1. Discount decision summary/talking points from subscription explanation assistant.
2. Selected communication language code at billing time.
3. AI availability (cloud/local) for translation enhancement.

## 3) Output Contract

Each snippet payload includes:

1. Language code and language name.
2. Localized explanation text.
3. Translation source marker (`aiTranslated` true/false).
4. Discount decision state and eligibility code snapshot.

## 4) Translation Behavior

1. English path: deterministic direct snippet.
2. Non-English path:
   - attempt AI translation first when local/cloud AI is available,
   - apply timeout guard,
   - fallback to deterministic localized templates if AI is unavailable/fails.
3. Preserve numeric values and currency context in translated text.

## 5) Integration

Billing service API:

- `BillingService.generateLocalizedSubscriptionExplanation(List<BillItem>, Customer, String languageCode)`

Checkout UX:

- Success dialog includes localized savings explanation snippet.

Invoice PDF:

- Invoice template renders `Savings Explanation (<Language>): <Snippet>` in summary section.

## 6) Safety and Continuity

1. Checkout does not fail if translation fails.
2. Deterministic fallback guarantees snippet availability without AI dependency.
3. Discount engine remains source-of-truth for financial application.

## 7) Validation Coverage

1. Unit tests for multilingual localization service (English and fallback non-English path).
2. Billing service tests for localized snippet generation fallback behavior.
