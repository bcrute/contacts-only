# Historical upgrade fixture

`contacts-only-1.0.apk` is the owner's original Contacts Only 1.0 APK, built on
August 24, 2026 and recovered from the owner's Samsung. Its source is preserved
in this repository's initial commit `f06a507`.

SHA-256:

```text
6bd9fed2ffa9b5e89e29659726062f288fbbb0477b41365c32208d41b14e751e
```

The integration job checks this identity, re-signs a copy with its disposable
debug key, and verifies every non-META-INF ZIP entry remains identical. It can
then install 1.0 and update to the candidate without a production key in the
job. The real Samsung update using the original certificate was separately
verified during development. The emulator does not establish carrier/OEM parity.

This fixture contains app code/resources and a public signing certificate. It
contains no contacts, call history, settings, account data or private signing key.
