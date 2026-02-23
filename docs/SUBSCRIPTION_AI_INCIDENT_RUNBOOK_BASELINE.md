# Subscription AI Incident Runbook Baseline (v1)

Status date: 2026-02-23  
Scope: Response playbook for AI misclassification, model drift, and AI service outage in subscription workflows.

## 1) Incident Types

1. **Misclassification**: AI output is repeatedly wrong against confirmed operational truth.
2. **Drift**: Precision/recall or acceptance metrics degrade over sustained windows.
3. **Service Outage**: AI inference path fails or times out and affects workflow continuity.

## 2) Severity Levels

1. `SEV-1`: Financial/compliance risk active, customer harm likely, or broad checkout/approval disruption.
2. `SEV-2`: Significant AI quality degradation but controlled via manual fallback.
3. `SEV-3`: Localized or low-impact issue with no active customer harm.

## 3) First 30-Minute Actions

1. Open incident channel and assign Incident Commander.
2. Confirm incident type and affected features (`PLAN_RECOMMENDATION`, `RENEWAL_PROPENSITY`, `ABUSE_DETECTION`, `DYNAMIC_OFFER`, `OVERRIDE_RISK`).
3. Snapshot evidence:
   - latest `subscription_ai_decision_log` reason codes
   - recent model monitoring metrics
   - sample wrong/failed decisions with timestamps
4. If risk is high, disable impacted AI feature flags and switch to fallback/manual path.

## 4) Containment by Scenario

### A) Misclassification

1. Freeze risky automation and keep human approval ownership active.
2. Extract recent decision logs and confirmed ground-truth rows.
3. Identify dominant reason codes and affected customer groups.
4. Apply temporary rules/guardrails while prompt/model fix is prepared.

### B) Drift

1. Compare current window vs prior baseline for precision/recall and acceptance rate.
2. Check data distribution shifts (input fields, enrollment mix, override volume patterns).
3. Roll back to last known-good prompt/model configuration where available.
4. Trigger offline benchmark rerun before restoring production AI behavior.

### C) Service Outage

1. Route to deterministic fallback rules engine path.
2. Verify critical operations (checkout and override approval) continue.
3. Track outage start/end, failure rates, and degraded decisions.
4. Re-enable AI only after health checks and smoke validation pass.

## 5) Recovery Validation Checklist

1. Incident trigger condition no longer present.
2. Offline benchmark gate is green.
3. No high-risk unresolved findings in post-fix validation sample.
4. Owner sign-off from engineering + operations approver.

## 6) Post-Incident (Within 48 Hours)

1. Publish timeline and root cause.
2. Document impacted decisions and remediation actions.
3. Record prevention actions (tests, thresholds, alerts, runbook updates).
4. Update relevant baseline docs and roadmap tracking if policy changed.

## 7) Data to Attach to Incident Record

1. Feature flags state before/after containment.
2. Extracted AI reason-code trend from `subscription_ai_decision_log`.
3. Monitoring snapshot before/after fix.
4. Offline benchmark report proving release gate pass.
