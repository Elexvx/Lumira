$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\.."
bash "$root\scripts\migration-check.sh"
