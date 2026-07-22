[CmdletBinding()]
param(
    [string]$RootPath = $PSScriptRoot,
    [string]$OutputFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Tree {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [string]$Prefix = ''
    )

    $items = @(Get-ChildItem -LiteralPath $Path -Force | Sort-Object @{ Expression = { -not $_.PSIsContainer } }, Name)

    for ($index = 0; $index -lt $items.Count; $index++) {
        $item = $items[$index]
        $isLast = $index -eq ($items.Count - 1)
        $branch = if ($isLast) { '+-- ' } else { '|-- ' }
        $line = $Prefix + $branch + $item.Name

        if ($item.PSIsContainer) {
            $line += '/'
        }

        $line

        if ($item.PSIsContainer) {
            $childPrefix = if ($isLast) { '    ' } else { '|   ' }
            $nextPrefix = $Prefix + $childPrefix
            Write-Tree -Path $item.FullName -Prefix $nextPrefix
        }
    }
}

$resolvedRoot = (Resolve-Path -LiteralPath $RootPath).Path
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add($resolvedRoot)

if ((Get-ChildItem -LiteralPath $resolvedRoot -Force | Measure-Object).Count -gt 0) {
    $treeLines = Write-Tree -Path $resolvedRoot
    foreach ($line in $treeLines) {
        $lines.Add($line)
    }
}

if ($OutputFile) {
    $outputPath = if ([System.IO.Path]::IsPathRooted($OutputFile)) {
        $OutputFile
    } else {
        Join-Path -Path $resolvedRoot -ChildPath $OutputFile
    }

    $lines | Set-Content -LiteralPath $outputPath -Encoding utf8
    Write-Host "Projektstruktur geschrieben nach: $outputPath"
} else {
    $lines
}