# Contacts Only

A small Android app that rejects incoming calls from numbers outside your contacts.

Supported on Android 15 and later. The app installs on Android 10 and later, and
older versions may work, but they are not tested or supported. Support follows
the Android versions Google still updates.

Built around a narrow privacy boundary: Android performs the contacts lookup,
and the app controls whether to reject the screened calls. A saved switch lets
you temporarily accept unsaved callers without uninstalling the app.

[Architecture](docs/ARCHITECTURE.md) · [Testing and evidence](docs/TESTING.md) ·
[Physical-phone acceptance](docs/PHONE-ACCEPTANCE.md)

## Turn blocking on or off

1. Install and open **Contacts Only**.
2. Turn on **Block calls from unsaved numbers**.
3. If prompted, select **Contacts Only** as your caller ID and spam app.

Turn the switch off whenever you want unsaved callers to reach you. You can
leave the app installed and selected for call screening. The choice survives
closing the app and restarting your phone.

Coming from 1.0 or 1.1.0? Version 1.2.0 has a new application ID, so it
installs alongside the old app rather than over it. Install it, turn blocking on
and approve it as the caller ID and spam app (Android then stops using the old
app), and uninstall the old Contacts Only.

While blocking is on, saved contacts ring normally, unsaved visible numbers are
rejected, and blocked calls stay in the system call history. Android handles the
contacts lookup; this app does not request access to your contacts, call history,
messages or the internet. There is no account, advertising or analytics.

The switch controls this app only. Phone/carrier blocking settings still apply.
Hidden/withheld numbers are not sent to Android call-screening apps; use your
phone's settings to block those. A caller spoofing a number already in your
contacts can still get through.

Text messages continue to use your existing messaging app. SMS blocking would
require becoming the default SMS app and is outside this release.

## Development

The Gradle wrapper pins Gradle 8.14.3 and verifies its download checksum.
`gradle/verification-metadata.xml` pins the SHA-256 of every build and test
dependency, including the Android Gradle Plugin 8.13.2, so a changed artifact
fails the build. Compile/target SDK 36, minimum SDK 29 (see support above).
Use JDK 21 for the Robolectric regression checks on Android API 35 and 36.

After changing a dependency, regenerate the metadata on the fleet with
`CONTACTS_ONLY_WRITE_VERIFICATION_METADATA=1 sh ci/validate.sh`, review the
retained `contacts-only/verification-metadata.xml` and commit it.

This project's builds, lint and automated checks run on the owner's Core/Heavy
fleet, with shared bounded slots. Do not run them on the development PC. The
maintainer submits `sh ci/validate.sh` through the installed standalone
`ci-fleet.py` helper with private fleet configuration and project `contacts-only`.
Unavailable capacity or connectivity leaves validation pending.

The check builds the product and independent fixture APKs and the Google Play
app bundle, runs lint and sixteen regression scenarios on two simulated Android
versions (API 35 and 36), and audits the actual release manifest/DEX, including
the APK Play would generate from the bundle. Native Android integration exercises the
installed app, real roles and touch input, the role moving from 1.0, process/reboot persistence,
accessibility semantics, large text and synthetic incoming calls through Telecom,
including contacts saved in a different number format from the caller's.
It runs on Android 16 (API 36) and, when the fleet guest has the image, on
Android 15 (API 35); without that image the Android 15 result is recorded as
pending rather than passed.

The test-only dialer/provider has its own UID and permissions for synthetic
contacts and history. Those components and permissions are absent from the
product APK. Real carrier/OEM testing remains a separate, documented layer.

APKs, reports and native observations are retained in `CI_ARTIFACTS_DIR`.
The runtime must provide the Android SDK, JDK 21, KVM, a registered Android 36
ATD image (and the Android 35 ATD image for the Android 15 run) and access to
pinned Gradle/Maven inputs. The script supports a
`CONTACTS_ONLY_GRADLE_DISTRIBUTION_URL` mirror override; the pinned checksum is
still enforced. Set `ROBOLECTRIC_DEPENDENCY_REPO_URL` to the fleet's Maven
mirror for Robolectric's separately fetched Android runtime jars.

See [release preparation](docs/RELEASING.md) before distributing an APK.

## License

[MIT](LICENSE).
Test success does not authorize publication; this candidate remains unreleased.

## Platform references

- [Android call screening](https://developer.android.com/reference/android/telecom/CallScreeningService)
- [Android SMS app requirements](https://developer.android.com/reference/android/provider/Telephony)
