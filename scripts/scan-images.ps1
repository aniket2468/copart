$ErrorActionPreference = 'Stop'
$workspacePath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$reportsPath = Join-Path $workspacePath 'reports'
New-Item -ItemType Directory -Force -Path $reportsPath | Out-Null
foreach ($component in @('backend', 'frontend')) {
  $archivePath = Join-Path $reportsPath "$component-image.tar"
  try {
    docker image save -o $archivePath "copartverify-${component}:local"
    if ($LASTEXITCODE -ne 0) { throw "Could not export $component image" }
    # Windows PowerShell treats a native program's ordinary stderr logs as errors.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
      docker run --rm -v "${reportsPath}:/reports" -v copartverify_trivy_cache:/root/.cache/trivy `
        ghcr.io/aquasecurity/trivy:0.75.0 image --disable-telemetry --scanners vuln `
        --format json --output "/reports/$component-scan.json" --input "/reports/$component-image.tar"
      $scanExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($scanExitCode -ne 0) { throw "Could not scan $component image" }
    $report = Get-Content -Raw -LiteralPath (Join-Path $reportsPath "$component-scan.json") | ConvertFrom-Json
    $findings = @($report.Results | ForEach-Object { $_.Vulnerabilities } | Where-Object { $_ })
    $blocking = @($findings | Where-Object { $_.Severity -in @('HIGH', 'CRITICAL') })
    Write-Host "$component image: $($findings.Count) findings, $($blocking.Count) high/critical"
    if ($blocking.Count -gt 0) { throw "$component image has high/critical vulnerabilities; see reports/$component-scan.json" }
  } finally {
    if (Test-Path -LiteralPath $archivePath) { Remove-Item -LiteralPath $archivePath }
  }
}
