#!/bin/bash
gradle assembleDebug --no-daemon > build_log.txt 2>&1
EXIT_CODE=$?
echo "Exit code: $EXIT_CODE"
tail -n 30 build_log.txt > err_summary.txt
git config user.name "Build Bot"
git config user.email "bot@example.com"
git add err_summary.txt
git commit -m "Build log failure summary" || true
git push origin arena/01a0c08b-mango || true
exit $EXIT_CODE
