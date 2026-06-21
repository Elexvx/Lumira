# Evidence SHA Policy

`gitCommitSha` in release evidence is the commit that was verified by the
evidence command. It is not the commit that stores the evidence file.

This avoids an impossible self-reference loop: committing an evidence file
changes the repository HEAD, which would immediately make a SHA stored inside
that same commit stale.

Rules:

1. Evidence generators must write `gitCommitShaMeaning:
   verified-release-target`.
2. Release commands may set `LUMIRA_EVIDENCE_TARGET_SHA` to pin evidence to
   the release candidate being verified.
3. If the variable is not set, scripts use the current `HEAD` as the verified
   target.
4. Use `scripts/sync-evidence-git-commit-sha.ps1` or
   `scripts/sync-evidence-git-commit-sha.sh` to update existing evidence files.
5. Do not require an evidence file commit to contain its own commit SHA.
