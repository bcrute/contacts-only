# Contacts Only development

Builds, lint, automated suites and release builds run only on Core/Heavy through
the installed standalone fleet helper and private fleet configuration. Use
project `contacts-only`, keep shared slot limits, and run `sh ci/validate.sh`.
The combined checkpoint includes the JVM regression suite on API 35/36,
dependency verification, release artifact audit and native Android 16 (and,
when the image exists, Android 15) integration with synthetic modem calls. Keep all
automated interaction on the disposable emulator, never a personal phone.
Unavailable fleet access leaves checks pending; never fall back to local builds.
The full project-agent-workflow and its project channel remain disabled.

Use an isolated session branch/workspace, preserve existing work, and merge
explicitly after the required checks pass. Keep a durable handoff when blocked.

The call switch must allow calls when off, persist its choice, preserve 1.0's
behavior on upgrade, and request Android's role only when needed to enable.
Support Android 15 and later only: no tests, compatibility code or fixes for
older versions, though minSdk stays 29. Do not add READ_CONTACTS: granting it changes which calls Android delivers to
the service. Text messages stay in the existing messaging app by owner decision.

Never commit signing keys, passwords, private fleet configuration, recovery
transcripts or device data. Sign updates with the release key and the v3
rotation lineage from the original Android debug key; see docs/RELEASING.md.
Publish only a remotely validated APK with the matching signing certificate.
Record manual phone checks separately and never claim unrun checks passed.
ATD screenshots are not rendered visual evidence. Keep the pending physical,
TalkBack, visual and carrier/OEM checks explicit in docs/TESTING.md.
