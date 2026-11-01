#!/usr/bin/env bash
# Runs the Coach prompt eval inside the debug build on the owner's phone (GPU first, like the real Coach).
# Usage: M3_SERIAL=<serial> bash scripts/phone-eval.sh <model-file> [vision true|false] [mtp true|false]
#   <model-file> is a file name in com.m3.app's model folder (noBackupFilesDir/models), e.g. gemma-4-E4B-it.litertlm
# Needs the debug build installed (:app:installDebug) and the model file already in place. Never use
# connectedAndroidTest on the phone: it uninstalls M3 and wipes the owner's data.
# Result: build/eval/<model name>.txt on the PC. Read every answer in it.
# Touches only com.m3.app: it removes files/m3-eval afterwards and force-stops only com.m3.app. Leaves nothing on the phone.
cd "$(dirname "$0")/.." || exit 1
export MSYS_NO_PATHCONV=1
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
S="${M3_SERIAL:?set M3_SERIAL to the device serial (adb devices)}"
MODEL="${1:?usage: phone-eval.sh <model-file> [vision true|false] [mtp true|false]}"
VISION="${2:-true}"
MTP="${3:-true}"
NAME="${MODEL%.litertlm}"
OUT="build/eval/$NAME.txt"
LIMIT_MINUTES=180
mkdir -p build/eval

a() { timeout 60 "$ADB" -s "$S" "$@"; }
result() { a exec-out run-as com.m3.app cat files/m3-eval/result.txt 2>/dev/null | tr -d '\r'; }

cleanup() {
  a shell run-as com.m3.app rm -rf files/m3-eval > /dev/null 2>&1
  a shell am force-stop com.m3.app > /dev/null 2>&1
}

# Start with no old report, so a previous run's DONE is never mistaken for this one.
a shell run-as com.m3.app rm -rf files/m3-eval > /dev/null 2>&1
a shell am start -n com.m3.app/.debug.EvalActivity --ez vision "$VISION" --ez mtp "$MTP" --es model "$MODEL" | tail -1

START=$(date +%s)
GONE=0
while :; do
  sleep 30
  R=$(result)
  CASES=$(echo "$R" | grep -c '^=== Case')
  if echo "$R" | tail -1 | grep -qx 'DONE'; then
    echo "$R" > "$OUT"
    echo "finished: $CASES cases, report at $OUT"
    break
  fi
  echo "$(( ($(date +%s)-START)/60 )) min: $CASES cases done"
  if [ -z "$(a shell pidof com.m3.app | tr -d '\r')" ]; then
    GONE=$((GONE+1))
  else
    GONE=0
  fi
  if [ "$GONE" -ge 2 ]; then
    echo "$R" > "$OUT"
    echo "M3 is no longer running and no DONE line: partial report at $OUT"
    break
  fi
  if [ $(( ($(date +%s)-START)/60 )) -ge "$LIMIT_MINUTES" ]; then
    echo "$R" > "$OUT"
    echo "gave up after $LIMIT_MINUTES min: partial report at $OUT"
    break
  fi
done
cleanup
echo "phone cleaned: files/m3-eval removed, com.m3.app stopped"
