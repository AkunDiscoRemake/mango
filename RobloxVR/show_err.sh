#!/bin/bash
gradle processDebugResources > /tmp/out.txt 2>&1
EXIT_CODE=$?
echo "--- OUT TAIL ---"
grep -C 3 -i "error" /tmp/out.txt | tail -n 25 | while IFS= read -r l; do
  echo "::error::$l"
done
exit 1
