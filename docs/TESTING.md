# Testing and evidence

Current checkpoint: **passed on November 4, 2026**, after the security and
testing review.

Validated source: `d4ab7a70b58a7971b5e57ce14655161dd50bba99`.
Fleet job: `803677d34678d5939547d76fda179086`, Heavy, completed exit 0 in
304.86 seconds. Source snapshot:
`c0e5353b63a6652bdc7748634212c210ef2eb849d5d29d8a08e86a7ad76dee96`.
Later commits change only documentation and CI orchestration, not the
application, fixtures or compiled inputs. Reuse job
`3a8ab146f292994c913f1b57019d47ae` (source `671f6c3`, Core, exit 0 in
157.46 seconds) verified that build's retained download, ran the orchestration
tests and repeated the native Android 16 checks with identical results.

1.2.0 checkpoint (application ID `com.contactsonly.app`): job
`35d0f8952a4a3bc1b2609e5fb8c1d861` (source `ad40cd8`, Heavy, exit 0 in 269.47
seconds). Dependency verification, 32 JVM executions, lint, 15 orchestration
tests and the release audit of the APK and the bundle's universal APK passed
(version 1.2.0, code 3, no permissions). Native Android 16 passed all nine
phases from a fresh install-on phase and seven Telecom cases; before them, 1.0
(`com.ben.contactsonly`) was installed with the call-screening role, the
candidate was installed beside it, and the role moved to the candidate. 1.0
stayed installed, and unsaved callers still rang while paused and after
revocation. Android 15 pending (no image). Release APK SHA-256
`f8325ff2800defa950c892f58935c5cde0e30d91c310929955bf0889eee3e2c0`; bundle
`8760b4f6eb9ce62caccc228f0f5ae00927dd3ea0d157cc555c957699bdd97445`.

App bundle checkpoint: job `82b4a40c9ebb6ab66f306df5418ac6e5` (source `7d0d4b1`,
Heavy, exit 0 in 269.9 seconds) ran the full check with the Google Play bundle
added. Dependency verification passed with no new entries; 32 JVM executions,
15 orchestration tests and the native Android 16 phases passed (Android 15
pending, no image). The release audit passed for both the release APK and the
universal APK generated from the bundle (version 1.1.0, code 2, no permissions).
Bundle SHA-256 `55523f283e77d22e5646de54f38ddc4d9e133c84a823801cdd98452277cd174b`.

[Sanitized machine-readable evidence](evidence/validation-2026-11-04-review.json)
is derived from that job's retained JUnit XML, APK audit, reuse binding and
native result records. It contains source/artifact identities, cases and
observations, without private configuration, credentials or device data.

| Check | Observed result |
| --- | --- |
| Builds | Debug app, unsigned release app and independent instrumentation APK passed |
| Dependencies | Every Gradle-resolved artifact verified against `gradle/verification-metadata.xml` |
| JVM | 16 scenarios × API 35/36 = 32 executions; zero failures/errors/skips |
| Reuse guard | 15 orchestration tests passed (`Ran 15 tests … OK`, retained in the reuse job); a real reuse of the build above succeeded |
| Lint | Zero errors; one OldTargetApi warning for the deliberately retained target SDK 36 |
| Release artifact | No requested permissions, no debuggable flag, one launcher/one guarded service, no test fixture/libraries in DEX |
| Native Android 16 (API 36) | All nine phases passed: upgrade-on, pause, off after force-stop, off after reinstall, off after reboot, resume, role revocation/cancel, restored role, large-text interaction |
| Telecom, Android 16 | Seven cases passed (below) |
| Work-profile contact, Android 16 | Observed, not gated: a caller saved only in a managed work profile **rang** |
| Native Android 15 (API 35) | **Pending.** The fleet guest has no API 35 image; nothing was run |
| Physical phone / rendered pixels / TalkBack | Pending; never inferred from the checks above |

Telecom cases on Android 16, each held past the six-second screening window:

| Case | Expected | Observed |
| --- | --- | --- |
| Saved contact calls | Rings | RINGING |
| Contact saved as `202-555-0106`, caller presents `+12025550106` | Rings | RINGING |
| Contact saved as `+12025550107`, caller presents `2025550107` | Rings | RINGING |
| Unsaved caller, blocking on | Rejected | Blocked history (type 6, screening reason 1); dialer never rang |
| Unsaved caller, blocking paused | Rings | RINGING |
| Unsaved caller after resuming | Rejected | Blocked history; dialer never rang |
| Unsaved caller after the role is revoked (switch still on) | Rings | RINGING |

Actual system image fingerprint:
`Android/sdk_slim_x86_64/emu64x:16/BE2A.250530.027/13847098:userdebug/test-keys`.
The checked unsigned release artifact's SHA-256 is:

```text
5e9ee7cebbae6462088877d354f09d525810f442dd7362b982e9678bb5062464
```

This is an unsigned validation artifact, not a published or phone-installed
release. The owner's Samsung keeps the earlier signed test build from `daf060b`.

### Supporting jobs

- `626a87f9dd529a371665e68d45a141d4`: generated the dependency verification
  metadata (328 components) from the complete task set. Five artifacts, the
  junit, mockito-core and robolectric jars plus the Android Gradle Plugin and
  lint jars, were checked against Maven Central and Google Maven and matched.
- `a741f12695748e1033270a92a1e7ca85`: listed the fleet guest's system images;
  only `system-images;android-36;aosp_atd;x86_64` is installed.

### Earlier checkpoint

Job `972645b1435bec2dc674100ba0147f80` (source `a7ff154`) passed the same
layers with 12 scenarios on simulated API 29/33/36 and four Telecom cases. See
[validation-2026-11-04.json](evidence/validation-2026-11-04.json). API 29 and
33 are no longer tested, because Android 15 is now the oldest supported version.

## Supported versions

Android 15 (API 35) and later. `minSdk` stays 29, so older phones may install
and run the app, but they get no tests or fixes. JVM checks simulate API 35 and
36; native checks run on API 36 and, once the fleet has the image, API 35.

## Layers and what they prove

| Layer | Boundary exercised | Limits |
| --- | --- | --- |
| JVM regression | Sixteen scenarios on APIs 35 and 36: toggle/callback flags, default-on migration, recreation, role grant/refusal/revocation, paused-state retention, status text, and buttons or role requests that no app can open | Simulated roles and call details; not a telecom integration test |
| Release APK audit | Actual manifest, requested permissions, export surface, backup policy, debuggable flag, and absence of fixture/test libraries in DEX | Static artifact inspection; not a penetration test |
| Native Android | Installed app, real touch input and role system, permission-picker cancellation, saved state after force-stop/reboot/reinstall, call-screening role moving from 1.0 to the new application ID, primary switch accessibility and large text | Disposable emulator images; no physical-device claim |
| Synthetic modem/Telecom | Saved and differently formatted contacts ring; unknown caller rejected and never rings when on; rings when paused or when the role is revoked; rejection resumes when on | Emulator calls and a test dialer; not a real carrier network |
| Work-profile observation | Whether Android treats a work-profile-only contact as a contact | Recorded, not gated: it is platform behaviour the app cannot change |
| Physical acceptance | Samsung call behavior, Secure Folder contacts, OEM controls and human accessibility experience | Pending the owner's observations; never inferred from a suite pass |

The call test observes actual Telecom RINGING delivery in an independent fixture
InCallService, whether a call ever rang, and actual BLOCKED history with the
CALL_SCREENING_SERVICE reason. Allowed calls must remain ringing beyond the
screening window; rejected calls must never have rung. Recorded observation
duration includes polling and is not a callback-latency metric.

The historical APK is checked, re-signed only for disposable testing, installed
and updated without uninstalling. The test records preserved first-install time
and role, then verifies default-on status through the actual app. Pausing is
tested across separate instrumentation processes, force-stop, reinstall and an
emulator reboot. The owner's phone is never rebooted or automated by this suite.

The work-profile observation creates a managed profile with a test-only profile
owner, seeds a contact there and places a call. If the image lacks managed-user
support or setup fails, it records `unverified` with the reason instead of a
result. It does not cover Samsung Secure Folder.

## Reproduce on the fleet

Use the installed standalone `ci-fleet.py` helper with private configuration,
project `contacts-only`, and command `sh ci/validate.sh`. Set:

- CONTACTS_ONLY_GRADLE_DISTRIBUTION_URL: the fleet HTTPS mirror of the pinned
  Gradle 8.14.3 distribution; checksum validation stays enabled.
- ROBOLECTRIC_DEPENDENCY_REPO_URL: the fleet Maven mirror for runtime jars.

The guest needs JDK 21, Android platform 36/build-tools 36.0.0, KVM, and the
registered `system-images;android-36;aosp_atd;x86_64` image. Registering
`system-images;android-35;aosp_atd;x86_64` on the guest enables the Android 15
run without any repository change. The script builds all APKs and the app
bundle, verifies dependencies, runs regression/lint, audits the release artifact and runs Android
integration in the same bounded guest. Never run on the PC.

After a dependency change, the build fails verification until the metadata is
regenerated: submit with `CONTACTS_ONLY_WRITE_VERIFICATION_METADATA=1`, review
the retained `contacts-only/verification-metadata.xml` and commit it. That file
records what the mirror served; spot-check new entries against the upstream
repository before committing. Robolectric's Android runtime jars are fetched by
Robolectric outside Gradle and are not covered; they are test-only.

For external reviewers, an equivalent isolated Android CI runner can reproduce
the scripts with JAVA_HOME, ANDROID_HOME and CI_ARTIFACTS_DIR configured, Android
tools on PATH, and the registered image above. Public Gradle/Maven repositories
are the defaults; mirror overrides are optional outside the owner's fleet.
Automatic pull-request triggers have not been deployed.

## Reusing a completed build

When only validation orchestration or documentation changes,
`ci/reuse_validation.py` continues the native checks from a passing build
instead of rebuilding. The build job writes `reuse/binding.json` itself: the
complete compiled-input inventory with each SHA-256, the three APKs, its JUnit
reports and their test total. Nobody assembles a binding by hand.

To reuse, download the build job with `ci-fleet.py --download-artifacts`, place
the whole download unchanged at `.validation-inputs/` (never commit it), and
submit `python3 ci/reuse_validation.py`. It refuses reuse when:

- any downloaded file differs from the fleet's readback manifest, or a file was
  added, or a manifest path is unsafe;
- the build retained no binding, or the binding names anything other than
  exactly the three APKs and the JUnit reports;
- an artifact differs from the binding, a JUnit report has failures, errors or
  skips, or the test total differs from the binding;
- any compiled input was added, deleted or modified.

The checked-in tests cover each of those cases, plus a gate run under
`python -O`. All gates raise explicit errors, so Python optimisation cannot
remove them, and the runner also unsets `PYTHONOPTIMIZE`. The reuse path then
runs `ci/native_checks.sh`, the same steps as a full run. Native fixture changes
require rebuilding, because the fixture APK is a compiled input.

The emulator re-signs copies with one newly generated disposable fixture key,
checking every executable/resource ZIP entry against its source APK. The key
stays in the disposable guest's ignored tooling directory and is not retained.

Retained evidence is under CI_ARTIFACTS_DIR:

- contacts-only/: APKs, Gradle log, JUnit XML, lint/test reports and SHA256SUMS
  for all three APKs.
- reuse/: the build's binding.
- apk-audit/: manifest/permission dumps and result.json.
- android-integration-36/ and android-integration-35/: candidate/fixture
  identities, phase transcripts, upgrade and call observations, work-profile
  observation, system image fingerprint, emulator and app logs; or a pending
  record when the image is absent.

The native runner requires explicit passed result codes. Missing modem support,
role eligibility, fixture permissions, contact lookup or expected call history
fails the job. Only the work-profile observation and an absent optional Android
15 image are recorded rather than gated, and both are reported as such here.

No coverage percentage or mutation score has been measured. Passing cases are
reported by scenario and layer, not presented as proof of exhaustive correctness.
No public release is authorized by test success.

## Screenshot and accessibility limits

The retained PNGs from ATD runs are uniformly black. They are diagnostics, not
visual-regression evidence or portfolio screenshots. ATDs disable hardware
rendering, and Android does not support screenshot tests that depend on it in
this configuration. See [Android's ATD documentation](https://developer.android.com/studio/test/managed-devices).

Native View measurements, semantics, actual touch handling and Telecom outcomes
were exercised. Color contrast, final pixel appearance, clipped content beyond
the checked label, spoken TalkBack navigation, other OEMs and carrier voicemail
routing remain unverified. The physical acceptance matrix keeps those limits
visible.
