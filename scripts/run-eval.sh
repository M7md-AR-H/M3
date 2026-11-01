#!/bin/bash
# Runs CoachPromptEval on the emulator with a model from models/. Takes about 40 minutes for 19 cases.
# Usage: bash scripts/run-eval.sh <model file> <output name> [vision: true|false]
# Writes build/eval/<output name>.txt. Read every answer, not just the score.
# It wipes the emulator's test data: the CPU weight cache needs about 3 GB free, and without a cache the
# low-memory killer stops the run. Don't run another Gradle build meanwhile, and start the phone out of the way:
# everything here is pinned to emulator-5554.
cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" MSYS_NO_PATHCONV=1 ANDROID_SERIAL=emulator-5554
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
OUT="build/eval/$2.txt"
mkdir -p build/eval
./gradlew --stop > /dev/null 2>&1
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_37.0 -wipe-data -no-snapshot-load -no-snapshot-save -no-audio -no-boot-anim -memory 6144 > /dev/null 2>&1 &
sleep 25
"$A" wait-for-device
until [ "$("$A" shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
sleep 15
"$A" shell svc power stayon true
"$A" shell mkdir -p /data/local/tmp/m3-eval
"$A" push "models/$1" /data/local/tmp/m3-eval/model.litertlm | tail -1
"$A" shell chmod 644 /data/local/tmp/m3-eval/model.litertlm
"$A" logcat -c
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.m3.app.CoachPromptEval \
    -Pandroid.testInstrumentationRunnerArguments.vision="${3:-true}" --console=plain -q 2>&1 | grep -E "FAILED|tests completed" | head -3
echo "exit=${PIPESTATUS[0]}"
"$A" logcat -d -s M3Eval:I | cut -c1-600 > "$OUT"
L=$(ls app/build/outputs/androidTest-results/connected/debug/*/logcat-com.m3.app.CoachPromptEval-run.txt 2>/dev/null | head -1)
[ -n "$L" ] && grep -E " E tflite| E litert| F libc |Abort message|Kill 'com.m3|Check failed" "$L" | cut -c1-220 | tail -6
"$A" shell svc power stayon false
"$A" emu kill
sleep 3
"$A" kill-server
./gradlew --stop > /dev/null 2>&1
echo finished
