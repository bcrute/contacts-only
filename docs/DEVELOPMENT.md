# Development

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

See [release preparation](RELEASING.md) before distributing an APK.
