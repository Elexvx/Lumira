$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\.."
bash "$root\scripts\staging-smoke.sh"
