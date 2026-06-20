$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\.."
$outDir = Join-Path $root "artifacts\release"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$compose = Get-Content (Join-Path $root "deploy\docker-compose.prod.yml") -Raw
$appFiles = Get-ChildItem (Join-Path $root "lumira-backend") -Recurse -Include application.yml,application-prod.yml,application-production.yml -ErrorAction SilentlyContinue
$appText = ($appFiles | ForEach-Object { Get-Content $_.FullName -Raw }) -join "`n"
$checks = @(
  @{ name="DB_PASSWORD required"; pass=$compose.Contains('${DB_PASSWORD:?DB_PASSWORD is required}') },
  @{ name="JWT_SECRET required"; pass=$compose.Contains('${JWT_SECRET:?JWT_SECRET is required}') },
  @{ name="FIELD_SECRET required"; pass=$compose.Contains('${FIELD_SECRET:?FIELD_SECRET is required}') },
  @{ name="PLUGIN_SIGNATURE_SECRET required"; pass=$compose.Contains('${PLUGIN_SIGNATURE_SECRET:?PLUGIN_SIGNATURE_SECRET is required}') },
  @{ name="SAAS_JOB_INTERNAL_TOKEN required"; pass=$compose.Contains('${SAAS_JOB_INTERNAL_TOKEN:?SAAS_JOB_INTERNAL_TOKEN is required}') },
  @{ name="CORS required"; pass=$compose.Contains('CORS_ALLOWED_ORIGIN_PATTERNS:?CORS_ALLOWED_ORIGIN_PATTERNS') },
  @{ name="CORS no wildcard default"; pass=($compose -notmatch 'CORS_ALLOWED_ORIGIN_PATTERNS:-\*') },
  @{ name="CORS no localhost default"; pass=($compose -notmatch 'CORS_ALLOWED_ORIGIN_PATTERNS:-.*localhost') },
  @{ name="Swagger disabled or protected"; pass=($appText -match 'swagger-ui|api-docs' -and $appText -match 'enabled:\s*false|springdoc') },
  @{ name="Actuator not publicly bound by compose"; pass=($compose -notmatch '(^|\n)\s*-\s*(0\.0\.0\.0:)?(9090|12345|3100|3200):') },
  @{ name="Grafana password not change-me default"; pass=($compose -notmatch 'change-me') },
  @{ name="Upload root avoids sensitive host paths"; pass=($compose -notmatch '/etc:|/root:|/var/run/docker.sock:rw') },
  @{ name="Redis password required or local network accepted"; pass=($compose -match 'REDIS_PASSWORD' -and $compose -match '127\.0\.0\.1|default') }
)
$failed = @($checks | Where-Object { -not $_.pass } | ForEach-Object { $_.name })
$evidence = [ordered]@{
  generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
  gitCommitSha = (git -C $root rev-parse HEAD)
  status = $(if ($failed.Count -eq 0) { "PASS" } else { "FAIL" })
  checks = $checks
  failedSteps = $failed
}
$evidence | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 (Join-Path $outDir "production-readiness-evidence.json")
if ($failed.Count -gt 0) { exit 1 }
