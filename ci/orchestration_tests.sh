#!/bin/sh
# Unit tests for the CI orchestration itself (no Android or build needed).
# Shared by ci/validate.sh and ci/reuse_validation.py; output is retained.
set -eu
unset PYTHONOPTIMIZE
ci_log="$CI_ARTIFACTS_DIR/ci-unittest.txt"
python3 -m unittest discover -s ci -p 'test_*.py' -v > "$ci_log" 2>&1 || { cat "$ci_log"; exit 1; }
cat "$ci_log"
