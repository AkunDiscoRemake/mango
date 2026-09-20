#!/bin/bash
gradle assembleDebug --no-daemon > /tmp/gradle_output.txt 2>&1
EXIT_CODE=$?
echo "EXIT_CODE=$EXIT_CODE"
if [ $EXIT_CODE -ne 0 ]; then
  # Find lines with * What went wrong: or errors
  grep -A 10 "\* What went wrong:" /tmp/gradle_output.txt | while IFS= read -r line; do
    echo "::error::$line"
  done
  exit $EXIT_CODE
fi
