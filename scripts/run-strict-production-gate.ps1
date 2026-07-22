param(
    [string]$ArelleCmd = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ArelleCmd)) {
    $ArelleCmd = $env:ARELLE_CMD
}
if ([string]::IsNullOrWhiteSpace($ArelleCmd)) {
    $ArelleCmd = "arelleCmdLine"
}

function Test-ArelleCommand {
    param([string]$Cmd)

    if (Test-Path $Cmd) {
        return $true
    }

    $cmdInfo = Get-Command $Cmd -ErrorAction SilentlyContinue
    return $null -ne $cmdInfo
}

if (-not (Test-ArelleCommand -Cmd $ArelleCmd)) {
    Write-Error "Arelle command not found: $ArelleCmd"
    exit 2
}

$env:ARELLE_CMD = $ArelleCmd
$env:SKIP_ARELLE = "false"
$env:FAIL_ON_VALIDATION_ISSUES = "true"
$env:REQUIRE_VIEWER_PLUGIN = "true"

Write-Host "Running strict production gate with ARELLE_CMD=$ArelleCmd"
mvn -B test
mvn -B exec:java

Write-Host "Strict production gate finished successfully."
