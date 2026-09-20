#!/bin/bash
gradle assembleDebug --no-daemon 2>&1 | tee /tmp/gradle_output.txt
EXIT_CODE=${PIPESTATUS[0]}
if [ $EXIT_CODE -ne 0 ]; then
  MSG=$(tail -n 15 /tmp/gradle_output.txt | tr '\n' ' ' | tr '"' "'")
  gh issue create --title "Build Failure Log" --body "$MSG" || true
  exit $EXIT_CODE
fi
