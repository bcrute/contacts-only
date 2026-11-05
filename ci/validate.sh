#!/bin/sh
set -eu

# CI_ARTIFACTS_DIR is supplied by the fleet. This also prevents an accidental
# direct invocation from building on the development PC.
: "${CI_ARTIFACTS_DIR:?Submit this check through the Core/Heavy fleet}"
if [ -d /usr/lib/jvm/java-21-openjdk-amd64 ]; then
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    export JAVA_HOME
fi
: "${JAVA_HOME:?JDK 21 is required}"
: "${ANDROID_HOME:?Android SDK 36 is required}"
PATH="$JAVA_HOME/bin:$PATH"
export PATH
# The checks raise explicit errors, but never let an inherited optimisation
# setting change how the Python gates behave.
unset PYTHONOPTIMIZE

ci_source_dir="$PWD"
ci_wrapper_dir="$ci_source_dir/.ci-tooling"
ci_output_dir="$CI_ARTIFACTS_DIR/contacts-only"
mkdir -p "$ci_wrapper_dir" "$ci_output_dir"
cp gradlew "$ci_wrapper_dir/gradlew"
cp -R gradle "$ci_wrapper_dir/"

# Alter only the bootstrap download location in a job-private wrapper copy.
# The source tree and pinned distribution checksum are unchanged.
python3 - <<'PY'
import os
from pathlib import Path
url = os.environ.get('CONTACTS_ONLY_GRADLE_DISTRIBUTION_URL')
if url:
    if not url.startswith('https://') or '\n' in url or '\r' in url:
        raise ValueError('Expected an HTTPS Gradle distribution URL')
    p = Path('.ci-tooling/gradle/wrapper/gradle-wrapper.properties')
    text = p.read_text()
    lines = [f'distributionUrl={url}' if s.startswith('distributionUrl=') else s
             for s in text.splitlines()]
    p.write_text('\n'.join(lines) + '\n')
PY

ci_tasks=":app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lint :app:testDebugUnitTest"

# Regenerate gradle/verification-metadata.xml after a dependency change.
# The job runs the complete task set so every resolved artifact is recorded,
# retains the file and stops; commit the retained file after reviewing it.
if [ "${CONTACTS_ONLY_WRITE_VERIFICATION_METADATA:-}" = 1 ]; then
    # shellcheck disable=SC2086
    sh "$ci_wrapper_dir/gradlew" -p "$ci_source_dir" --no-daemon --console=plain \
        --write-verification-metadata sha256 $ci_tasks \
        > "$ci_output_dir/gradle.log" 2>&1 || { cat "$ci_output_dir/gradle.log"; exit 1; }
    cat "$ci_output_dir/gradle.log"
    cp gradle/verification-metadata.xml "$ci_output_dir/verification-metadata.xml"
    sha256sum gradle/verification-metadata.xml
    exit 0
fi

ci_status=0
# shellcheck disable=SC2086
sh "$ci_wrapper_dir/gradlew" -p "$ci_source_dir" --no-daemon --console=plain $ci_tasks \
    > "$ci_output_dir/gradle.log" 2>&1 || ci_status=$?
cat "$ci_output_dir/gradle.log"
for ci_result in app/build/reports app/build/test-results app/build/outputs/apk; do
    if [ -d "$ci_result" ]; then
        cp -R "$ci_result" "$ci_output_dir/"
    fi
done
if [ "$ci_status" -ne 0 ]; then
    exit "$ci_status"
fi
(cd "$ci_output_dir" && sha256sum apk/debug/app-debug.apk \
    apk/release/app-release-unsigned.apk \
    apk/androidTest/debug/app-debug-androidTest.apk > SHA256SUMS)
# Bind the passing build's outputs to its exact compiled inputs so a later job
# can reuse them only while every input is unchanged (ci/reuse_validation.py).
python3 ci/reuse_validation.py --write-binding "$CI_ARTIFACTS_DIR/reuse/binding.json"
sh ci/orchestration_tests.sh
sh ci/native_checks.sh
