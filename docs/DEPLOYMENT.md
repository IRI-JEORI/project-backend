# NUNNUN backend disaster recovery and deployment

This runbook rebuilds the NUNNUN backend on a fresh Ubuntu server without relying on the current Gabia host. It intentionally contains placeholders only. Never commit a populated environment file, Firebase Admin JSON, database dump, signing key, or cloud credential.

## Recorded deployment baseline

- Repository: `https://github.com/IRI-JEORI/project-backend.git`
- Recorded local branch: `main`
- Recorded backend commit: `7948f38153f10fd153fd66e73c514882d2841de6`
- Application directory: `/opt/nunnun`
- OS baseline: Ubuntu 24.04 LTS (64-bit), at least 2 vCPU, 2 GB RAM, and sufficient encrypted disk for the database, logs, build, and backups
- Runtime: Java 21, Spring Boot 3.5, MySQL 8.x, Nginx
- Database: `nunnun`
- Flyway migrations present at this baseline: V1 through V13
- S3 region/bucket: `ap-northeast-2` / `nunnun-wake-storage-608841098309-ap-northeast-2-an`

Before restoring, compare the desired Git commit and the production server's deployed commit. Deploying a newer commit may run additional Flyway migrations.

## Critical frontend warning

The current Android production APK uses the literal API base URL `http://1.201.116.185`. If the replacement server receives a different public IP, the existing APK will not discover it automatically. Update the frontend API base URL, rebuild a release APK, sign it according to the compatibility rules below, and redistribute/install the new APK. Using a stable HTTPS domain in a future release prevents another IP-only cutover.

### Current Hackathon/Demo Android signing state

The currently distributed `com.nunnun` release APK is **not** signed with a separate production release keystore. The frontend's Android `release` build type uses `signingConfigs.debug`, whose keystore is `android/app/debug.keystore`. The installed APK certificate has subject `CN=Android Debug`, and its certificate SHA-256 fingerprint was verified to match that keystore. The keystore is currently tracked in the frontend Git repository.

Android permits an update installation for the same application ID only when the update is signed by a certificate accepted as the same signing identity. Therefore, while maintaining update compatibility with the current Hackathon/Demo installation, preserve the exact existing `android/app/debug.keystore` and build with the same signing configuration. Generating a new debug keystore with the same filename or alias does not reproduce the same signing identity.

Back up this existing demo keystore in access-controlled, encrypted storage in addition to preserving the reviewed frontend repository commit. Do not print or copy its password or private-key material into this document, logs, issue trackers, or deployment scripts.

### Recommended signing state for a future production service

A real production release should use a newly generated, dedicated production release keystore whose private key and passwords are never committed to Git. Configure release signing through protected CI/CD secrets or another controlled signing environment. If distributing through Google Play, define a deliberate Play App Signing/upload-key strategy before the first production publication.

Changing from the current debug certificate to a new production certificate is not a transparent in-place update for APKs already installed under `com.nunnun`. Plan the transition explicitly before publishing: depending on the distribution channel and signing facilities available, it may require an approved signing-key migration mechanism or uninstalling the demo build before installing the production build (which removes local app data). Do not simply replace the keystore and assume the existing demo APK can be updated normally.

### Signing security limitations

The tracked `debug.keystore` must not be treated as a trustworthy production signing key: anyone with repository access may be able to sign an APK with that demo identity. Adding the file to `.gitignore` later would prevent some future additions but would not remove the key from existing Git history or copies of the repository. Preserve it only as long as current demo APK update compatibility is required; use a separate, privately managed signing identity for the real production service.

## 1. Preserve the retiring server first

Do this before the server is terminated. These commands only read configuration or create private backup copies; they do not restart services or mutate application data.

```bash
sudo install -d -m 700 /root/NUNNUN-production-backup-$(date +%F)/database
sudo install -d -m 700 /root/NUNNUN-production-backup-$(date +%F)/infra
BACKUP_ROOT="/root/NUNNUN-production-backup-$(date +%F)"

sudo git -C /opt/nunnun rev-parse HEAD > "$BACKUP_ROOT/infra/deployed-git-commit.txt"
sudo systemctl cat nunnun > "$BACKUP_ROOT/infra/nunnun-service.txt"
sudo systemctl status nunnun --no-pager > "$BACKUP_ROOT/infra/nunnun-status.txt"
sudo nginx -T > "$BACKUP_ROOT/infra/nginx-config.txt" 2>&1
java -version > "$BACKUP_ROOT/infra/java-version.txt" 2>&1
mysql --version > "$BACKUP_ROOT/infra/mysql-version.txt" 2>&1
```

Nginx and systemd output can contain paths or environment details. Keep this directory private and inspect/redact it before sharing. Do not copy it into the Git repository.

For the database, first create a root-owned MySQL option file so the password is not exposed in shell history or process arguments:

```bash
sudo install -m 600 /dev/null /root/.nunnun-mysql.cnf
sudoedit /root/.nunnun-mysql.cnf
```

Its private contents should be:

```ini
[client]
user=<YOUR_BACKUP_DB_USER>
password=<YOUR_DB_PASSWORD>
host=127.0.0.1
```

Then use the repository script (or the equivalent command below):

```bash
cd /opt/nunnun
sudo env \
  MYSQL_DEFAULTS_FILE=/root/.nunnun-mysql.cnf \
  BACKUP_DIR="$BACKUP_ROOT/database" \
  bash scripts/backup-db.sh
sudo chmod -R go-rwx "$BACKUP_ROOT"
```

Verify without printing data:

```bash
sudo find "$BACKUP_ROOT" -type f -printf '%p %s bytes\n'
sudo test -s "$BACKUP_ROOT"/database/nunnun-*.sql
```

Copy the private directory to encrypted offline storage using the access method already approved for the server. Also preserve separately:

- `/etc/nunnun/nunnun.env` (or the actual systemd secret source)
- Firebase Admin JSON used by `GOOGLE_APPLICATION_CREDENTIALS`
- The frontend's exact tracked `android/app/debug.keystore` used by the current Hackathon/Demo release APK (store a private encrypted backup; do not expose its password or key contents)
- AWS access material only if no IAM role is used (prefer recording/recreating the IAM role policy instead)
- DNS/TLS configuration if later introduced

Check Flyway history read-only (do not use `repair` or `clean`):

```bash
mysql --defaults-extra-file=/root/.nunnun-mysql.cnf nunnun \
  -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

## 2. Provision a replacement Ubuntu server

Install Ubuntu 24.04 LTS, attach an IAM role with least-privilege access to the required S3 bucket if the hosting platform supports it, and allow inbound TCP 22 only from administrator IPs and TCP 80 from clients. Do not expose TCP 8080 or MySQL 3306 publicly.

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git mysql-server nginx ufw
java -version
git --version
mysql --version
```

Create a non-login service account and clone the recorded source:

```bash
sudo useradd --system --home /opt/nunnun --shell /usr/sbin/nologin nunnun
sudo git clone https://github.com/IRI-JEORI/project-backend.git /opt/nunnun
sudo git -C /opt/nunnun checkout 7948f38153f10fd153fd66e73c514882d2841de6
sudo chown -R nunnun:nunnun /opt/nunnun
```

If intentionally deploying a newer reviewed commit, replace the checkout SHA and review every migration newer than V13 first.

## 3. Install and restore MySQL

Secure MySQL according to the hosting policy, then create the database and a least-privilege application user. Substitute a strong password; do not paste it into Git-managed files.

```bash
sudo mysql
```

```sql
CREATE DATABASE nunnun CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'nunnun_app'@'127.0.0.1' IDENTIFIED BY '<YOUR_DB_PASSWORD>';
GRANT ALL PRIVILEGES ON nunnun.* TO 'nunnun_app'@'127.0.0.1';
FLUSH PRIVILEGES;
```

Restore the preserved dump before starting Spring Boot:

```bash
mysql --defaults-extra-file=/root/.nunnun-mysql.cnf nunnun \
  < /path/to/private-backup/database/nunnun-YYYYMMDD-HHMMSS.sql
```

Confirm that the restored `flyway_schema_history` has successful V1-V13 rows. On application startup Flyway validates the restored history and applies only migrations newer than the restored version.

## 4. Configure runtime secrets and integrations

The application has no separate production Spring profile in the repository. `application.yml` reads environment variables directly, and Flyway is enabled. Copy the placeholder template outside the repository:

```bash
sudo install -d -o root -g nunnun -m 750 /etc/nunnun
sudo install -o root -g nunnun -m 640 /opt/nunnun/.env.example /etc/nunnun/nunnun.env
sudoedit /etc/nunnun/nunnun.env
```

Required application variables are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`. Production features additionally require `OPENAI_API_KEY`, `AWS_S3_ENABLED=true`, `AWS_REGION`, `AWS_S3_BUCKET`, `FIREBASE_ENABLED=true`, `FIREBASE_PROJECT_ID`, and `GOOGLE_APPLICATION_CREDENTIALS`. Optional timeout/expiration/scheduler variables and their current defaults are documented in `.env.example`.

### Firebase Admin

The code calls `GoogleCredentials.getApplicationDefault()`. Restore the private service account JSON outside Git and restrict access:

```bash
sudo install -o root -g nunnun -m 640 /path/to/private/firebase-admin.json /etc/nunnun/firebase-admin.json
```

Set `GOOGLE_APPLICATION_CREDENTIALS=/etc/nunnun/firebase-admin.json` in `/etc/nunnun/nunnun.env`. Never display or commit the JSON.

### AWS S3

The AWS SDK default credential provider chain is used. Prefer an attached IAM role with only the actions and object prefixes the backend needs in the named bucket. If a role is unavailable, inject standard AWS credential variables through the protected runtime environment; never place static keys in the service file or repository. Confirm the server clock is synchronized because signed S3 requests are time-sensitive.

### OpenAI and JWT

Set `OPENAI_API_KEY` and a long, cryptographically random `JWT_SECRET` in the protected environment file. Preserve the existing JWT secret during a server move if existing tokens must remain valid; rotating it invalidates them. Never log either value.

## 5. Build and test the JAR

```bash
cd /opt/nunnun
sudo -u nunnun ./gradlew clean test build --no-daemon
ls -l build/libs/nunnun-0.0.1-SNAPSHOT.jar
```

The expected executable JAR follows `settings.gradle` (`nunnun`) and `build.gradle` (`0.0.1-SNAPSHOT`): `/opt/nunnun/build/libs/nunnun-0.0.1-SNAPSHOT.jar`.

For a one-off diagnostic launch, load the protected variables without echoing them and run the JAR as the service user. In normal operation use systemd instead.

## 6. Install systemd service

Review the example and adjust only server-specific paths if required:

```bash
sudo install -o root -g root -m 644 /opt/nunnun/deploy/nunnun.service.example /etc/systemd/system/nunnun.service
sudo systemctl daemon-reload
sudo systemctl enable --now nunnun
sudo systemctl status nunnun --no-pager
sudo journalctl -u nunnun -n 200 --no-pager
```

Do not proceed to Nginx until logs show successful Flyway validation/migration and Tomcat listening on 8080. The Java process binds to the application default port 8080; restrict public access at the firewall/security-group layer.

## 7. Install Nginx reverse proxy

```bash
sudo install -o root -g root -m 644 \
  /opt/nunnun/deploy/nginx-nunnun.conf.example \
  /etc/nginx/sites-available/nunnun
sudo ln -s /etc/nginx/sites-available/nunnun /etc/nginx/sites-enabled/nunnun
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

The template accepts HTTP on port 80 and proxies to `127.0.0.1:8080`. Add HTTPS only after obtaining a domain and certificate, then update the frontend API URL accordingly.

Example host firewall (coordinate this with the provider security group):

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx HTTP'
sudo ufw enable
sudo ufw status
```

## 8. Verify the deployment

Run safe GET checks locally and externally:

```bash
curl --fail --silent --show-error http://127.0.0.1:8080/v3/api-docs > /dev/null
curl --fail --silent --show-error http://<NEW_PUBLIC_IP>/v3/api-docs > /dev/null
sudo systemctl is-active nunnun
sudo systemctl is-active nginx
```

Verify Flyway read-only:

```bash
mysql --defaults-extra-file=/root/.nunnun-mysql.cnf nunnun \
  -e "SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Expected restored baseline: successful migrations V1 through V13. Never use `flyway clean`; do not use `repair` unless a failed migration has been investigated and a reviewed recovery plan explicitly requires it.

## 9. Troubleshooting order

1. `systemctl status nunnun` and `journalctl -u nunnun` for startup/root-cause errors.
2. Confirm Java 21 and the expected JAR exist and permissions allow user `nunnun` to read them.
3. Confirm all required environment variable names are populated without printing values.
4. Test MySQL locally and verify `flyway_schema_history`; check schema version/checksum errors before changing anything.
5. Confirm Firebase JSON path/permissions and `FIREBASE_PROJECT_ID`.
6. Confirm the instance IAM role or AWS credential chain and S3 region/bucket access without logging credentials or presigned URLs.
7. Confirm OpenAI outbound network access and key presence without printing the key.
8. Test `curl http://127.0.0.1:8080/v3/api-docs`; if this fails, the problem is behind Nginx.
9. Run `nginx -t`, inspect Nginx logs, and confirm port 80 routes to 127.0.0.1:8080.
10. Check provider firewall/security groups, UFW, DNS, and then rebuild the Android APK with the replacement API base URL.

## 10. Cutover checklist

1. Securely copy the DB dump, Firebase JSON, runtime environment values, infrastructure snapshots, IAM policy details, and the exact current demo Android `debug.keystore` off the old server/workstation into encrypted private storage.
2. Provision Ubuntu 24.04 and restrict network access.
3. Install Java 21, Git, MySQL, and Nginx.
4. Clone and check out the recorded/reviewed backend commit.
5. Create the DB/user and restore the dump.
6. Restore protected Firebase/runtime configuration and attach the S3 IAM role.
7. Build/test the JAR and install the systemd unit.
8. Verify Flyway, Tomcat 8080, and `/v3/api-docs`, then enable Nginx on port 80.
9. Change the frontend API base URL to the new server, then either rebuild the Hackathon/Demo APK with the exact existing debug signing identity for update compatibility or follow the separately planned production signing transition; smoke-test the chosen installation/update path.
10. Keep the old private backup encrypted according to the retention policy; never add it to Git.
