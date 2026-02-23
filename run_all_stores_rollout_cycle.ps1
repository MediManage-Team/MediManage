param(
    [string]$DbPath = "medimanage.db",
    [string]$StartDate = "",
    [string]$EndDate = "",
    [string]$MavenRepoLocal = ".m2/repository",
    [string]$MonitoringOutputPath = "",
    [string]$QaOutputLogPath = "",
    [string]$RolloutOutputPath = "",
    [switch]$SkipRegression,
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

function Ensure-ParentDirectory {
    param([string]$PathValue)
    $dir = Split-Path -Parent $PathValue
    if (-not [string]::IsNullOrWhiteSpace($dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

function Read-SectionMetricValue {
    param(
        [string[]]$Lines,
        [string]$SectionHeader,
        [string]$MetricPrefix
    )
    $sectionStart = -1
    for ($i = 0; $i -lt $Lines.Length; $i++) {
        if ($Lines[$i].Trim() -eq $SectionHeader) {
            $sectionStart = $i
            break
        }
    }
    if ($sectionStart -lt 0) {
        return ""
    }

    for ($j = $sectionStart + 1; $j -lt $Lines.Length; $j++) {
        $line = $Lines[$j].Trim()
        if ($line.StartsWith("## ")) {
            break
        }
        if ($line.StartsWith($MetricPrefix)) {
            return $line.Substring($MetricPrefix.Length).Trim()
        }
    }
    return ""
}

$today = (Get-Date).Date
$start = Parse-DateOrDefault -Value $StartDate -DefaultValue $today.AddDays(-6)
$end = Parse-DateOrDefault -Value $EndDate -DefaultValue $today
if ($end -lt $start) {
    throw "EndDate must be on or after StartDate."
}

$rangeLabel = if ($start -eq $end) {
    $start.ToString("yyyy-MM-dd")
}
else {
    $start.ToString("yyyy-MM-dd") + "_to_" + $end.ToString("yyyy-MM-dd")
}

if ([string]::IsNullOrWhiteSpace($MonitoringOutputPath)) {
    $MonitoringOutputPath = "docs/pilot-logs/$rangeLabel-subscription-rollout-readiness-monitoring.md"
}
if ([string]::IsNullOrWhiteSpace($QaOutputLogPath)) {
    $QaOutputLogPath = "docs/pilot-logs/$rangeLabel-subscription-rollout-readiness-qa.log"
}
if ([string]::IsNullOrWhiteSpace($RolloutOutputPath)) {
    $RolloutOutputPath = "docs/pilot-logs/$rangeLabel-subscription-all-stores-rollout-cycle-1.md"
}

Ensure-ParentDirectory -PathValue $MonitoringOutputPath
Ensure-ParentDirectory -PathValue $QaOutputLogPath
Ensure-ParentDirectory -PathValue $RolloutOutputPath

Write-Host "Running all-stores rollout cycle checks..."
Write-Host "Window: $($start.ToString("yyyy-MM-dd")) to $($end.ToString("yyyy-MM-dd"))"

& .\run_pilot_monitoring_cycle.ps1 `
    -DbPath $DbPath `
    -StartDate $start.ToString("yyyy-MM-dd") `
    -EndDate $end.ToString("yyyy-MM-dd") `
    -OutputLogPath $MonitoringOutputPath `
    -FailOnHold:$FailOnHold
if ($LASTEXITCODE -ne 0) {
    throw "Pilot monitoring cycle failed with exit code $LASTEXITCODE"
}

$qaStatus = "PASS"
if ($SkipRegression) {
    $qaStatus = "SKIPPED"
} else {
    & .\run_pilot_qa_cycle.ps1 -MavenRepoLocal $MavenRepoLocal -OutputLogPath $QaOutputLogPath
    if ($LASTEXITCODE -ne 0) {
        throw "Pilot QA regression cycle failed with exit code $LASTEXITCODE"
    }
}

$monitorLines = Get-Content $MonitoringOutputPath
$monitorRaw = $monitorLines -join "`n"

$gateDecision = "UNKNOWN"
if ($monitorRaw -match "- Decision:\s*([A-Z]+)") {
    $gateDecision = $Matches[1].Trim()
}
$gateReason = "Not available."
if ($monitorRaw -match "- Reason:\s*(.+)") {
    $gateReason = $Matches[1].Trim()
}

$pricingHigh = Read-SectionMetricValue -Lines $monitorLines -SectionHeader "## 1. Pricing Integrity Alerts" -MetricPrefix "- High/Critical:"
$overrideHigh = Read-SectionMetricValue -Lines $monitorLines -SectionHeader "## 2. Override Abuse Signals" -MetricPrefix "- High/Critical:"
$feedbackHigh = Read-SectionMetricValue -Lines $monitorLines -SectionHeader "## 3. Pilot Feedback Tracker" -MetricPrefix "- Open High/Critical:"

$rolloutDecision = if ($gateDecision -eq "PASS" -and $qaStatus -eq "PASS") {
    "PROCEED"
}
elseif ($gateDecision -eq "PASS" -and $qaStatus -eq "SKIPPED") {
    "HOLD"
}
else {
    "HOLD"
}

$rolloutReason = if ($rolloutDecision -eq "PROCEED") {
    "Monitoring gate PASS and regression gate PASS."
}
elseif ($qaStatus -eq "SKIPPED") {
    "Regression gate was skipped; complete QA gate before full rollout."
}
else {
    "Monitoring gate and/or regression gate not satisfied."
}

$generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
$report = @"
# Subscription All-Stores Rollout Cycle 1 Report

Generated at: $generatedAt  
Window: $($start.ToString("yyyy-MM-dd")) to $($end.ToString("yyyy-MM-dd")) (inclusive)

## 1. Entry Gate Results

- Monitoring gate decision: $gateDecision
- Monitoring gate reason: $gateReason
- High/Critical pricing alerts: $(if ([string]::IsNullOrWhiteSpace($pricingHigh)) { "n/a" } else { $pricingHigh })
- High/Critical override abuse signals: $(if ([string]::IsNullOrWhiteSpace($overrideHigh)) { "n/a" } else { $overrideHigh })
- Open High/Critical pilot feedback items: $(if ([string]::IsNullOrWhiteSpace($feedbackHigh)) { "n/a" } else { $feedbackHigh })
- Regression QA gate: $qaStatus

## 2. Artifacts

- Monitoring report: $MonitoringOutputPath
- Regression QA log: $(if ($qaStatus -eq "SKIPPED") { "skipped" } else { $QaOutputLogPath })

## 3. Full Rollout Flag Posture

Set these for all-stores rollout:

- `-Dmedimanage.feature.subscription.release.enabled=true`
- `-Dmedimanage.feature.subscription.commerce.enabled=true`
- `-Dmedimanage.feature.subscription.approvals.enabled=true`
- `-Dmedimanage.feature.subscription.discount.overrides.enabled=true`
- `-Dmedimanage.feature.subscription.pilot.enabled=false`

## 4. Rollback Flag Posture

If rollback is required:

- `-Dmedimanage.feature.subscription.release.enabled=false`
- `-Dmedimanage.feature.subscription.commerce.enabled=false`
- `-Dmedimanage.feature.subscription.approvals.enabled=false`
- `-Dmedimanage.feature.subscription.discount.overrides.enabled=false`

## 5. Decision

- Rollout decision: $rolloutDecision
- Decision reason: $rolloutReason
"@

Set-Content -Path $RolloutOutputPath -Value $report -Encoding UTF8

Write-Host "All-stores rollout cycle completed."
Write-Host "Monitoring artifact: $MonitoringOutputPath"
if ($qaStatus -ne "SKIPPED") {
    Write-Host "QA artifact: $QaOutputLogPath"
}
Write-Host "Rollout report: $RolloutOutputPath"
Write-Host "Rollout decision: $rolloutDecision"

if ($FailOnHold -and $rolloutDecision -ne "PROCEED") {
    Write-Error "All-stores rollout decision is $rolloutDecision."
    exit 2
}
