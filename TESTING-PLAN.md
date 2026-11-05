# Portfolio validation plan

Release is held. The owner's Samsung retains the already installed 1.1.0 test
build; automated work uses disposable Android guests on Core/Heavy only.

Completed checkpoint and its exact evidence/limitations are recorded in
[docs/TESTING.md](docs/TESTING.md). The plan below describes the implemented
scope. Physical-phone acceptance and rendered visual/TalkBack evidence remain
explicitly pending.

## Scope and evidence

1. Qualify the existing fleet Android 36 image with a bounded emulator probe:
   boot, telephony features, incoming synthetic modem event and Telecom state.
   This is runtime qualification, not application acceptance.
2. Expand regression scenarios across the supported Android versions (API 35
   and 36 since the November 4 review; previously 29, 33 and 36), then run one
   combined fleet build/lint/test checkpoint. Audit the actual release manifest.
3. Run native Android instrumentation and synthetic incoming-call checks on the
   qualified emulator, including real role management, contact filtering, call
   history, switch persistence across force-stop/reboot, and in-place upgrade.
   Missing platform capabilities leave corresponding checks unverified.
4. Publish repository documentation of the architecture, privacy boundary,
   reproducible commands, exact results/limits, and an unfilled physical-phone
   acceptance matrix. Do not publish a software release.

Tests must assert observable outcomes. The product must never gain READ_CONTACTS
or other permissions solely to make a fixture work. Only the separately installed
test fixture may have permissions for synthetic contacts/history.

The retained 1.0 APK is an owner-provided historical upgrade fixture. Any emulator
re-signing uses an ephemeral CI key and preserves its code/resources. No production
private signing key is sent in source snapshots. No real calls/texts are placed.

## Practices followed

The shared sdlc-lessons references for off-desktop builds, testing real adapter
boundaries/cold paths and durable handoffs apply. Use one own fleet job at a time,
keep exact source/job identities, inspect receipts before retrying and report
failed/unrun outcomes separately. Only observed failures justify extra checkpoints.
