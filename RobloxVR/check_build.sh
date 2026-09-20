#!/bin/bash
gradle assembleDebug --no-daemon --stacktrace 2>&1 | tee /tmp/gradle_output.txt
EXIT_CODE=${PIPESTATUS[0]}
if [ $EXIT_CODE -ne 0 ]; then
  echo "===== GRADLE FAILED WITH EXIT CODE $EXIT_CODE ====="
  echo "===== LAST 200 LINES OF OUTPUT ====="
  tail -n 200 /tmp/gradle_output.txt
  exit $EXIT_CODE
fi
