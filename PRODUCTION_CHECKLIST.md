# Final Deployment Checklist

Verified against this repository and the live local Tomcat deployment on March 23, 2026.

## Deployment target

- Backend source: `D:\fantacy backend\fantasybackend`
- Backend artifact: `target\fantasybackend-0.0.1-SNAPSHOT.war`
- Live Tomcat WAR path: `C:\Program Files\Apache Software Foundation\Tomcat 10.1\webapps\fantasybackend.war`
- Live backend base URL: `http://localhost:8085/fantasybackend`
- Flutter app source: `D:\fantacy backend\fantasybackend\friends_fantasy_app`

## Verified baseline on March 23, 2026

- [x] Backend test suite passed.
- [x] Flutter static analysis passed: `dart analyze lib test`
- [x] Flutter smoke tests passed:
  - `flutter test test\minimal_smoke_test.dart`
  - `flutter test test\widget_test.dart`
- [x] Live backend WAR redeployed successfully to Tomcat 10.1.
- [x] Live API validation passed after redeploy:
  - `/api/v1/fixtures/1/player-pool` returned `50` players
  - same endpoint responded in about `677 ms`
  - invalid login returned `401`
  - missing fixture returned `404`
- [x] Backend production defaults are safe:
  - `spring.jpa.open-in-view=false`
  - `app.otp.fixed-enabled=false`
  - `fantasy.fixtures.sync-on-read=false`
  - `fantasy.fixtures.background-sync-enabled=true`
  - `server.shutdown=graceful`

## Required backend configuration

Set these in Tomcat service Java options, not in committed source files:

```text
-Dspring.profiles.active=prod
-DDB_URL=jdbc:mysql://HOST:3306/DATABASE?useSSL=true&requireSSL=true&serverTimezone=Asia/Kolkata
-DDB_USERNAME=your-db-user
-DDB_PASSWORD=your-db-password
-DJWT_SECRET=use-a-random-secret-that-is-at-least-32-characters-long
-DSPORTMONKS_TOKEN=your-sportmonks-token
-DMAIL_HOST=smtp.example.com
-DMAIL_PORT=587
-DMAIL_USERNAME=smtp-user
-DMAIL_PASSWORD=smtp-password
-DAPP_MAIL_FROM=noreply@example.com
-DAPP_CORS_ALLOWED_ORIGIN_PATTERNS=https://yourdomain.com,https://www.yourdomain.com
```

## Optional backend configuration

Only set these when you intentionally need them:

```text
-DSPORTMONKS_CRICKET_LEAGUE_IDS=all
-DFANTASY_FIXTURES_BACKGROUND_SYNC_ENABLED=true
-DFANTASY_FIXTURES_BACKGROUND_SYNC_FIXED_DELAY_MS=900000
-DFANTASY_FIXTURES_BACKGROUND_SYNC_INITIAL_DELAY_MS=30000
```

## One-time admin seed only if required

Enable these once, confirm the admin exists, then remove them:

```text
-DAPP_ADMIN_SEED_DEFAULT_ENABLED=true
-DAPP_ADMIN_SEED_DEFAULT_USERNAME=admin
-DAPP_ADMIN_SEED_DEFAULT_PASSWORD=choose-a-strong-password
```

## Pre-deploy checklist

- [ ] Confirm MySQL backup or snapshot exists and is restorable.
- [ ] Confirm the current production WAR is copied somewhere safe for rollback.
- [ ] Confirm Tomcat `Java Options` contain the real production secrets and not localhost placeholders.
- [ ] Confirm `SPORTMONKS_TOKEN` is valid in the live Tomcat service.
- [ ] Confirm `JWT_SECRET` is production-only and at least 32 bytes long.
- [ ] Confirm `APP_CORS_ALLOWED_ORIGIN_PATTERNS` contains only real frontend domains.
- [ ] Confirm SMTP settings are valid with a real mailbox.
- [ ] Confirm disk space is available for WAR expansion, logs, and MySQL growth.
- [ ] Confirm the frontend release target is decided:
  - Android APK/AAB
  - iOS build
  - web build only if the shipped Flutter app actually has web support enabled

## Suggested backup step

Example before deployment:

```powershell
mysqldump -u YOUR_DB_USER -p --databases YOUR_DATABASE > backup_before_deploy.sql
Copy-Item "C:\Program Files\Apache Software Foundation\Tomcat 10.1\webapps\fantasybackend.war" ".\fantasybackend-predeploy.war" -Force
```

## Build checklist

### Backend

```powershell
cd "D:\fantacy backend\fantasybackend"
.\mvnw.cmd -q -s .mvn\local-settings.xml test
.\mvnw.cmd -q -s .mvn\local-settings.xml -DskipTests package
```

### Flutter app

```powershell
cd "D:\fantacy backend\fantasybackend\friends_fantasy_app"
dart analyze lib test
flutter test test\minimal_smoke_test.dart
flutter test test\widget_test.dart
```

Build only the release artifact you actually ship:

```powershell
flutter build apk --release
```

If shipping web, make sure the production Flutter app has web support enabled before using:

```powershell
flutter build web --release
```

## Deploy checklist

### Backend deploy to Tomcat

```powershell
cd "D:\fantacy backend\fantasybackend"
Copy-Item .\target\fantasybackend-0.0.1-SNAPSHOT.war "C:\Program Files\Apache Software Foundation\Tomcat 10.1\webapps\fantasybackend.war" -Force
```

If hot redeploy is slow or stale, do a controlled Tomcat service restart:

```powershell
Restart-Service Tomcat10
```

### Frontend deploy

- [ ] Install the freshly built APK/AAB on the target device, or publish the selected release artifact.
- [ ] Confirm the frontend points to the correct backend base URL and production origin.

## Post-deploy smoke checks

### Browser or app flow

- [ ] Login with a real user account.
- [ ] Open community list.
- [ ] Open one fixture team builder flow.
- [ ] Confirm player pool loads.
- [ ] Create a team successfully.
- [ ] Join a contest or community successfully.
- [ ] Confirm wallet and notifications load.

### API spot checks

```powershell
curl.exe http://localhost:8085/fantasybackend/api/v1/fixtures/1/player-pool
curl.exe -X POST http://localhost:8085/fantasybackend/api/v1/auth/login/password -H "Content-Type: application/json" -d "{\"mobileOrUsername\":\"bad-user\",\"password\":\"bad-pass\"}"
curl.exe http://localhost:8085/fantasybackend/api/v1/fixtures/999999/player-pool
```

Expected results:

- valid player-pool request returns data
- invalid login returns `401`
- missing fixture returns `404`

### Logs and DB

- [ ] Check Tomcat logs for fresh stack traces after deployment.
- [ ] Confirm `players` and `fixture_player_pool` contain expected rows for the active fixture set.
- [ ] Confirm no repeated duplicate participant errors appear after scheduler runs.

## Rollback checklist

If the release fails:

1. Stop or restart Tomcat cleanly.
2. Restore the previously saved WAR.
3. Restart Tomcat and confirm the app comes back healthy.
4. Restore the DB backup only if the failure involved bad schema or bad data writes.

Example rollback:

```powershell
Copy-Item ".\fantasybackend-predeploy.war" "C:\Program Files\Apache Software Foundation\Tomcat 10.1\webapps\fantasybackend.war" -Force
Restart-Service Tomcat10
```

## Final go or no-go rule

Go live only if all are true:

- [ ] Backend tests pass.
- [ ] Flutter analyze passes.
- [ ] Flutter smoke tests pass.
- [ ] Production secrets are configured in Tomcat.
- [ ] Database backup is confirmed.
- [ ] Live login works.
- [ ] Live player-pool works.
- [ ] Team creation works.
- [ ] No new Tomcat startup or runtime errors appear after deploy.
