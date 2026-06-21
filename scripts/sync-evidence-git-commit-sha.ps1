param(
  [string]$TargetSha = $env:LUMIRA_EVIDENCE_TARGET_SHA,
  [string]$ArtifactsRoot = "artifacts"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\.."
$target = if ([string]::IsNullOrWhiteSpace($TargetSha)) {
  (git -C $root rev-parse HEAD).Trim()
} else {
  $TargetSha.Trim()
}

if ($target -notmatch '^[0-9a-f]{40}$') {
  throw "Invalid evidence target commit SHA: $target"
}

$artifactsPath = Join-Path $root $ArtifactsRoot
if (-not (Test-Path $artifactsPath)) {
  throw "Artifacts path not found: $artifactsPath"
}

$updated = @()
Get-ChildItem -Path $artifactsPath -Recurse -Filter *.json | ForEach-Object {
  $path = $_.FullName
  $text = Get-Content -Raw -LiteralPath $path
  if ($text -notmatch '"gitCommitSha"\s*:') {
    return
  }

  $newText = [regex]::Replace(
    $text,
    '("gitCommitSha"\s*:\s*")([0-9a-f]{40})(")',
    "`${1}$target`${3}"
  )

  if ($newText -match '"gitCommitShaMeaning"\s*:') {
    $newText = [regex]::Replace(
      $newText,
      '("gitCommitShaMeaning"\s*:\s*")[^"]*(")',
      '`${1}verified-release-target`${2}'
    )
  } else {
    $newText = [regex]::Replace(
      $newText,
      '("gitCommitSha"\s*:\s*"[0-9a-f]{40}")',
      "`${1},`r`n  `"gitCommitShaMeaning`": `"verified-release-target`""
    )
  }

  if ($newText -ne $text) {
    Set-Content -LiteralPath $path -Value $newText -Encoding UTF8
    $updated += (Resolve-Path -Relative $path)
  }
}

[ordered]@{
  targetSha = $target
  gitCommitShaMeaning = "verified-release-target"
  updatedFiles = $updated
  updatedFileCount = $updated.Count
} | ConvertTo-Json -Depth 4
