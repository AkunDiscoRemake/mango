#!/bin/bash
gradle assembleDebug --no-daemon > /tmp/gradle_output.txt 2>&1
EXIT_CODE=$?
echo "EXIT_CODE=$EXIT_CODE"
if [ $EXIT_CODE -ne 0 ]; then
  while IFS= read -r line; do
    echo "::error::$line"
  done < <(tail -n 25 /tmp/gradle_output.txt)
  exit $EXIT_CODE
fi
