# Deployment secrets

Do not commit secret values to this directory.

For a fresh installation, provision the built-in administrator's one-time
password through the deployment secret manager and expose it as a host file.
Set its path in `deploy/.env`:

```dotenv
LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE=./secrets/bootstrap-admin-password
```

The file must contain 12-128 characters and include uppercase, lowercase,
numeric, and special characters. The migrator mounts it read-only, initializes
the administrator in one transaction, and records an immutable initialization
marker. Re-running migrations never replaces an initialized credential.

Remove or revoke the secret after the first successful deployment; later
upgrades safely no-op because the database marker already exists. The
administrator must change the temporary password on first login.
