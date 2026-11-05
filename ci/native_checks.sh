#!/bin/sh
# Checks that run against built APKs. Shared by ci/validate.sh and by
# ci/reuse_validation.py, so a reused build runs exactly the same steps.
set -eu
unset PYTHONOPTIMIZE
python3 ci/audit_apk.py
# Android 16 (API 36) is required. Android 15 (API 35) runs when the fleet guest
# has its image; otherwise android-integration-35/result.json records "pending".
python3 ci/android_integration.py --api 36
python3 ci/android_integration.py --api 35 --if-installed
