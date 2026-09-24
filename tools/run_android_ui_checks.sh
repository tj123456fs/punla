#!/usr/bin/env bash
# AGP collects additionalTestOutputDir before removing the test app.
set -uo pipefail
gradle connectedDebugAndroidTest --no-daemon
test_status=$?
python3 - <<'PY'
from pathlib import Path
import shutil
source = Path('app/build/outputs/connected_android_test_additional_output')
target = Path('app/build/ui-qa')
target.mkdir(parents=True, exist_ok=True)
images = list(source.rglob('*.png'))
for image in images:
    shutil.copyfile(image, target / image.name)
print(f'Collected {len(images)} UI screenshots')
raise SystemExit(0 if images else 1)
PY
screenshot_status=$?
if [ "$test_status" -ne 0 ]; then
  exit "$test_status"
fi
exit "$screenshot_status"
