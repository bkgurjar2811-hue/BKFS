BKFS 1.5 Sync — hosted management client

Package: com.bkfs.app
Host: https://bkfs-fresh-production.up.railway.app

This APK embeds the tested v2 offline state engine and service worker. After one authenticated online load of /app, it keeps the exact live Railway UI and device state available offline. Offline customer, loan and collection edits remain pending and retry on reconnect. Independent server/device changes are merged; same-field conflicts retain both values in syncConflicts for audit. Pending edits block logout.

First online login is required. Customer public requests submitted while their page is offline are delivered when that page reconnects. Server /api/state must support ETag and If-Match/409 for safe bidirectional sync.

Build validation: Gradle compile, Android lint, APK signature verification, package/version dump, JavaScript offline/reconnect tests.
