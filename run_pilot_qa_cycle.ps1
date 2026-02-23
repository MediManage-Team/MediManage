param(
    [string]$MavenRepoLocal = ".m2/repository",
    [string]$OutputLogPath = "docs/pilot-logs/qa-cycle-1-latest.log"
)

$ErrorActionPreference = "Stop"

$tests = "AnalyticsReportDispatchDAOIntegrationTest,MedicineDAOInsightsIntegrationTest,DashboardKpiServiceTest,SubscriptionDAOIntegrationTest,ReportingWindowUtilsTest,FeatureFlagsTest,BillingServiceSubscriptionCheckoutTest,SubscriptionApprovalServiceTest,WeeklyAnomalyAlertEvaluatorTest,AnomalyActionTrackerDAOIntegrationTest"

$outputDir = Split-Path -Parent $OutputLogPath
if (-not [string]::IsNullOrWhiteSpace($outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

Write-Host "Running pilot QA cycle tests..."
Write-Host "Maven local repository: $MavenRepoLocal"
Write-Host "Output log: $OutputLogPath"

mvn "-Dmaven.repo.local=$MavenRepoLocal" "-Dtest=$tests" test | Tee-Object -FilePath $OutputLogPath
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Error "Pilot QA cycle failed with exit code $exitCode"
    exit $exitCode
}

Write-Host "Pilot QA cycle completed successfully."
