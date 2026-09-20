#!/bin/bash
gradle assembleDebug --no-daemon > /tmp/gradle_output.txt 2>&1
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
  MSG=$(tail -n 20 /tmp/gradle_output.txt | sed 's/[^a-zA-Z0-9 ._:-]/ /g' | cut -c 1-200)
  git config user.name "Build Bot"
  git config user.email "bot@example.com"
  git commit --allow-empty -m "ERR: $MSG"
  git push origin arena/01a0c08b-mango || true
  exit $EXIT_CODE
fi
