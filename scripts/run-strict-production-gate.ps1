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

if ([string]::IsNullOrWhiteSpace($env:IXBRL_VIEWER_PLUGIN)) {
    $localViewerPlugin = Resolve-Path "./arelle/plugin/iXBRLViewerPlugin/__init__.py" -ErrorAction SilentlyContinue
    if ($null -ne $localViewerPlugin) {
        $env:IXBRL_VIEWER_PLUGIN = $localViewerPlugin.Path
    }
}

Write-Host "Running strict production gate with ARELLE_CMD=$ArelleCmd"
mvn -B test
if ($LASTEXITCODE -ne 0) {
    Write-Error "Strict production gate failed during tests."
    exit $LASTEXITCODE
}

mvn -B exec:java
if ($LASTEXITCODE -ne 0) {
    Write-Error "Strict production gate failed during pipeline execution."
    exit $LASTEXITCODE
}

Write-Host "Strict production gate finished successfully."
