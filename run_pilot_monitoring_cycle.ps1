param(
    [string]$DbPath = "medimanage.db",
    [string]$StartDate = "",
    [string]$EndDate = "",
    [int]$MinOverrideRequests = 3,
    [string]$OutputLogPath = "",
    [switch]$FailOnHold
)

$ErrorActionPreference = "Stop"

function Parse-DateOrDefault {
    param(
        [string]$Value,
        [datetime]$DefaultValue
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }
    return [datetime]::ParseExact($Value.Trim(), "yyyy-MM-dd", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Invoke-SqliteCsvRows {
    param(
        [string]$DatabasePath,
        [string]$Sql
    )
    $output = & sqlite3 -csv -header $DatabasePath $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "sqlite3 query failed with exit code $LASTEXITCODE"
    }
    if ($null -eq $output -or [string]::IsNullOrWhiteSpace(($output -join "`n"))) {
        return @()
    }
    return $output | ConvertFrom-Csv
}

function Is-HighOrCriticalSeverity {
    param([string]$Severity)
    $safeSeverity = if ($null -eq $Severity) { "" } else { $Severity.Trim().ToUpperInvariant() }
    return $safeSeverity -eq "HIGH" -or $safeSeverity -eq "CRITICAL"
}

$today = (Get-Date).Date
$start = Parse-DateOrDefault -Value $StartDate -DefaultValue $today
$end = Parse-DateOrDefault -Value $EndDate -DefaultValue $start
if ($end -lt $start) {
    throw "EndDate must be on or after StartDate."
}

$resolvedDbPath = [System.IO.Path]::GetFullPath($DbPath)
if (-not (Test-Path $resolvedDbPath)) {
    throw "Database file not found: $resolvedDbPath"
}

$rangeStartTs = $start.ToString("yyyy-MM-dd 00:00:00")
$rangeEndExclusiveTs = $end.AddDays(1).ToString("yyyy-MM-dd 00:00:00")

$rangeLabel = if ($start -eq $end) {
    $start.ToString("yyyy-MM-dd")
}
else {
    $start.ToString("yyyy-MM-dd") + "_to_" + $end.ToString("yyyy-MM-dd")
}

if ([string]::IsNullOrWhiteSpace($OutputLogPath)) {
    $OutputLogPath = "docs/pilot-logs/$rangeLabel-subscription-pilot-monitoring.md"
}

$outputDir = Split-Path -Parent $OutputLogPath
if (-not [string]::IsNullOrWhiteSpace($outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$pricingSql = @"
SELECT q.bill_id, q.bill_date, q.plan_id, q.plan_code, q.plan_name,
       ROUND(q.net_amount, 2) AS net_amount,
       ROUND(q.savings_amount, 2) AS savings_amount,
       ROUND(q.configured_discount_percent, 4) AS configured_discount_percent,
       ROUND(q.computed_discount_percent, 4) AS computed_discount_percent,
       q.alert_code,
       CASE
         WHEN q.alert_code IN ('NEGATIVE_SAVINGS','DISCOUNT_PERCENT_OUT_OF_RANGE','NEGATIVE_GROSS_BEFORE_DISCOUNT','SAVINGS_EXCEED_GROSS') THEN 'HIGH'
         WHEN q.alert_code = 'DISCOUNT_PERCENT_MISMATCH' THEN 'MEDIUM'
         ELSE 'LOW'
       END AS severity
FROM (
  SELECT b.bill_id,
         b.bill_date,
         b.subscription_plan_id AS plan_id,
         COALESCE(sp.plan_code, 'PLAN-' || b.subscription_plan_id) AS plan_code,
         COALESCE(sp.plan_name, 'Unknown Plan') AS plan_name,
         COALESCE(b.total_amount, 0) AS net_amount,
         COALESCE(b.subscription_savings_amount, 0) AS savings_amount,
         COALESCE(b.subscription_discount_percent, 0) AS configured_discount_percent,
         CASE
           WHEN (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) > 0
             THEN (COALESCE(b.subscription_savings_amount, 0) / (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0))) * 100.0
           ELSE 0
         END AS computed_discount_percent,
         CASE
           WHEN COALESCE(b.subscription_savings_amount, 0) < 0 THEN 'NEGATIVE_SAVINGS'
           WHEN COALESCE(b.subscription_discount_percent, 0) < 0 OR COALESCE(b.subscription_discount_percent, 0) > 100 THEN 'DISCOUNT_PERCENT_OUT_OF_RANGE'
           WHEN (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) < 0 THEN 'NEGATIVE_GROSS_BEFORE_DISCOUNT'
           WHEN COALESCE(b.subscription_savings_amount, 0) > (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) + 0.01 THEN 'SAVINGS_EXCEED_GROSS'
           WHEN (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0)) > 0
             AND ABS(((COALESCE(b.subscription_savings_amount, 0) / (COALESCE(b.total_amount, 0) + COALESCE(b.subscription_savings_amount, 0))) * 100.0) - COALESCE(b.subscription_discount_percent, 0)) > 1.0 THEN 'DISCOUNT_PERCENT_MISMATCH'
           ELSE 'NONE'
         END AS alert_code
  FROM bills b
  LEFT JOIN subscription_plans sp ON sp.plan_id = b.subscription_plan_id
  WHERE b.bill_date >= '$rangeStartTs'
    AND b.bill_date < '$rangeEndExclusiveTs'
    AND b.subscription_plan_id IS NOT NULL
) q
WHERE q.alert_code <> 'NONE'
ORDER BY q.bill_date DESC, q.bill_id DESC
LIMIT 200;
"@

$overrideSql = @"
SELECT o.requested_by_user_id,
       COALESCE(req.username, 'user-' || o.requested_by_user_id) AS requested_by_username,
       COUNT(*) AS total_requests,
       SUM(CASE WHEN o.status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count,
       SUM(CASE WHEN o.status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
       SUM(CASE WHEN o.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
       ROUND(COALESCE(AVG(o.requested_discount_percent), 0), 4) AS avg_requested_percent,
       ROUND(COALESCE(MAX(o.requested_discount_percent), 0), 4) AS max_requested_percent,
       MIN(o.created_at) AS first_request_at,
       MAX(o.created_at) AS latest_request_at,
       CASE
         WHEN COUNT(*) >= 5 AND (
           ((SUM(CASE WHEN o.status = 'REJECTED' THEN 1 ELSE 0 END) * 100.0) / COUNT(*)) >= 60.0
           OR COALESCE(AVG(o.requested_discount_percent), 0) >= 25.0
           OR COALESCE(MAX(o.requested_discount_percent), 0) >= 35.0
         ) THEN 'HIGH'
         WHEN COUNT(*) >= 3 AND (
           ((SUM(CASE WHEN o.status = 'REJECTED' THEN 1 ELSE 0 END) * 100.0) / COUNT(*)) >= 40.0
           OR COALESCE(AVG(o.requested_discount_percent), 0) >= 20.0
           OR COALESCE(MAX(o.requested_discount_percent), 0) >= 30.0
         ) THEN 'MEDIUM'
         ELSE 'LOW'
       END AS severity
FROM subscription_discount_overrides o
LEFT JOIN users req ON req.user_id = o.requested_by_user_id
WHERE o.created_at >= '$rangeStartTs'
  AND o.created_at < '$rangeEndExclusiveTs'
GROUP BY o.requested_by_user_id, req.username
HAVING COUNT(*) >= $MinOverrideRequests
ORDER BY COUNT(*) DESC, COALESCE(AVG(o.requested_discount_percent), 0) DESC, o.requested_by_user_id ASC;
"@

$feedbackSql = @"
SELECT feedback_id, severity, status, title, reported_at, updated_at, resolved_at, owner_user_id
FROM subscription_pilot_feedback
WHERE reported_at >= '$rangeStartTs'
  AND reported_at < '$rangeEndExclusiveTs'
ORDER BY reported_at DESC, feedback_id DESC
LIMIT 200;
"@

$pricingRows = Invoke-SqliteCsvRows -DatabasePath $resolvedDbPath -Sql $pricingSql
$overrideRows = Invoke-SqliteCsvRows -DatabasePath $resolvedDbPath -Sql $overrideSql
$feedbackRows = Invoke-SqliteCsvRows -DatabasePath $resolvedDbPath -Sql $feedbackSql

$pricingTotal = $pricingRows.Count
$highPricingCount = ($pricingRows | Where-Object { Is-HighOrCriticalSeverity $_.severity }).Count
$overrideSignalTotal = $overrideRows.Count
$highOverrideSignalCount = ($overrideRows | Where-Object { Is-HighOrCriticalSeverity $_.severity }).Count

$openFeedbackCount = ($feedbackRows | Where-Object { $_.status -ne "RESOLVED" }).Count
$inProgressFeedbackCount = ($feedbackRows | Where-Object { $_.status -eq "IN_PROGRESS" }).Count
$resolvedFeedbackCount = ($feedbackRows | Where-Object { $_.status -eq "RESOLVED" }).Count
$openHighCriticalFeedbackCount = ($feedbackRows | Where-Object {
    $_.status -ne "RESOLVED" -and (Is-HighOrCriticalSeverity $_.severity)
}).Count
$hasOpenCriticalFeedback = ($feedbackRows | Where-Object {
    $_.status -ne "RESOLVED" -and $_.severity -eq "CRITICAL"
}).Count -gt 0

$blockingSignalCount = $highPricingCount + $highOverrideSignalCount + $openHighCriticalFeedbackCount
$gateDecision = if ($blockingSignalCount -eq 0) { "PASS" } else { "HOLD" }
$gateReason = if ($gateDecision -eq "PASS") {
    "No high-severity blockers."
}
else {
    "High pricing alerts: $highPricingCount | High override signals: $highOverrideSignalCount | Open high/critical feedback: $openHighCriticalFeedbackCount"
}

$generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
$summaryLine = "Pilot gate: $gateDecision | $gateReason"

$pricingTopLines = @("None")
if ($pricingRows.Count -gt 0) {
    $pricingTopLines = $pricingRows | Select-Object -First 5 | ForEach-Object {
        "- Bill #$($_.bill_id) | $($_.bill_date) | $($_.plan_code) | Alert=$($_.alert_code) | Severity=$($_.severity)"
    }
}

$overrideTopLines = @("None")
if ($overrideRows.Count -gt 0) {
    $overrideTopLines = $overrideRows | Select-Object -First 5 | ForEach-Object {
        "- Requester $($_.requested_by_username) (#$($_.requested_by_user_id)) | Requests=$($_.total_requests) | Rejected=$($_.rejected_count) | AvgReq%=$($_.avg_requested_percent) | MaxReq%=$($_.max_requested_percent) | Severity=$($_.severity)"
    }
}

$feedbackTopLines = @("None")
if ($feedbackRows.Count -gt 0) {
    $feedbackTopLines = $feedbackRows | Where-Object { $_.status -ne "RESOLVED" } | Select-Object -First 5 | ForEach-Object {
        "- Feedback #$($_.feedback_id) | $($_.severity) | $($_.status) | $($_.title)"
    }
    if ($feedbackTopLines.Count -eq 0) {
        $feedbackTopLines = @("None")
    }
}

$report = @"
# Subscription Pilot Monitoring Report

Generated at: $generatedAt  
Database: $resolvedDbPath  
Window: $($start.ToString("yyyy-MM-dd")) to $($end.ToString("yyyy-MM-dd")) (inclusive)

## 1. Pricing Integrity Alerts

- Total: $pricingTotal
- High/Critical: $highPricingCount

Top rows:
$($pricingTopLines -join "`n")

## 2. Override Abuse Signals

- Total requesters flagged (>= $MinOverrideRequests requests): $overrideSignalTotal
- High/Critical: $highOverrideSignalCount

Top rows:
$($overrideTopLines -join "`n")

## 3. Pilot Feedback Tracker

- Open: $openFeedbackCount
- In Progress: $inProgressFeedbackCount
- Resolved: $resolvedFeedbackCount
- Open High/Critical: $openHighCriticalFeedbackCount

Open items (top 5):
$($feedbackTopLines -join "`n")

## 4. Pilot Gate

- Decision: $gateDecision
- Reason: $gateReason
- Open critical feedback present: $hasOpenCriticalFeedback

## 5. Next Action

- Recommended status: $(if ($gateDecision -eq "PASS") { "Proceed" } else { "Hold" })
"@

Set-Content -Path $OutputLogPath -Value $report -Encoding UTF8

Write-Host "Pilot monitoring completed."
Write-Host "Window: $($start.ToString("yyyy-MM-dd")) to $($end.ToString("yyyy-MM-dd"))"
Write-Host $summaryLine
Write-Host "Report: $OutputLogPath"

if ($FailOnHold -and $gateDecision -eq "HOLD") {
    Write-Error "Pilot gate is HOLD."
    exit 2
}
