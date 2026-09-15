# Customer request repair — integration required

The live Railway service is `bkfs-fresh`, not the prototype on `main`.
Its start command reconstructs the application from environment variables and
applies V72–V75 UI/staff patches. The connected Railway OAuth tools expose those
variable names but withhold their contents. Do not replace the live application
with the older recovered ZIP or deploy this repository as the existing backend.

`customer-requests.patch` is based on the recovered
`BKFS_RAILWAY_DEPLOY_READY.zip`. It is a reviewable patch, **not a verified live
deployment**. Apply to a copy of the current full backend source, resolve any
context differences, and preserve its staff/role protections and data schema.
Copy `server/request_intake.py` beside that application's `server.py`.

Changes:

- Normalize Indian mobile numbers and require complete submitted details.
- Accept unlinked enquiries into the owner's pending request inbox without
  disclosing customer registration, creating accounts, or granting loans.
- Require an owner to verify/link an unmatched request before approval.
- Return a receipt and persist an idempotency key to prevent retry duplicates.
- Escape submitted text in the request inbox.
- Use `/loan-request` and support the previous HTML URL.
- Serialize writes and reject stale state snapshots so an old admin screen
  cannot overwrite a newly received enquiry. Integrate this revision contract
  with **all current state writers**, including staff and offline clients.

Validation completed locally against the recovered backend: five unit tests
(including input variants), JavaScript syntax, Python compilation, HTTP intake,
retry deduplication, owner inbox visibility, unauthorized state read rejection,
and stale-write conflict handling. No test enquiries were sent to production.

Run unit tests: `python3 -m unittest discover -s tests -v`.

Before release, test the patch against the actual deployed source and synthetic
data, including staff permissions, pending request badge, linking, restart
persistence, and concurrent writes. Preserve the existing database and its
encryption key. This request repair does not implement offline synchronization,
complete the customer profile work, or verify the Android app end to end.
