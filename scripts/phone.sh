#!/usr/bin/env bash
# Drives M3 on a device over adb for phone checks (Git Bash). Only acts while com.m3.app is the focused window,
# reads only com.m3.app nodes, and deletes its screen dump (/data/local/tmp/m3-ui.xml) every time.
# Needs M3_SERIAL (from `adb devices`). Usage:
#   phone.sh screen                 -> M3's texts
#   phone.sh pos                    -> the same with tap positions (x y|text)
#   phone.sh tap <regex> [n]        -> tap the n-th (default 1) M3 node whose text/desc matches
#   phone.sh tapxy <x> <y>          -> tap a point (M3 must be focused)
#   phone.sh type <text>            -> type into the focused field (spaces ok)
#   phone.sh key <keycode>          -> e.g. BACK, ENTER
#   phone.sh swipe up|down          -> scroll the middle of the screen
#   phone.sh wait <regex> [secs]    -> poll until a text matches
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
S="${M3_SERIAL:?set M3_SERIAL to the device serial (adb devices)}"
sh_() { timeout 40 "$ADB" -s "$S" shell "$@"; }
focused() { sh_ dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus' | grep -q 'com.m3.app'; }
guard() { focused || { echo "M3 is not in front; not touching the phone"; exit 2; }; }
raw() {
  sh_ 'timeout 20 uiautomator dump /data/local/tmp/m3-ui.xml >/dev/null 2>&1; cat /data/local/tmp/m3-ui.xml 2>/dev/null; rm -f /data/local/tmp/m3-ui.xml' \
    | tr '>' '\n' | grep 'package="com.m3.app"'
}
nodes() {
  raw | while IFS= read -r l; do
    t=$(echo "$l" | grep -o ' text="[^"]\+"' | sed 's/^ text="//;s/"$//')
    d=$(echo "$l" | grep -o 'content-desc="[^"]\+"' | sed 's/^content-desc="//;s/"$//')
    b=$(echo "$l" | grep -o 'bounds="[^"]\+"' | grep -oE '[0-9]+' | tr '\n' ' ')
    [ -z "$t$d" ] && continue
    set -- $b; cx=$(( ($1+$3)/2 )); cy=$(( ($2+$4)/2 ))
    echo "$cx $cy|${t}${d:+ [desc] $d}"
  done
}
case "$1" in
  screen) nodes | cut -d'|' -f2 ;;
  pos) nodes ;;
  tap)
    guard; n="${3:-1}"
    all=$(nodes)
    ln=$(echo "$all" | cut -d'|' -f2- | grep -nEi -- "$2" | sed -n "${n}p" | cut -d: -f1)
    hit=$([ -n "$ln" ] && echo "$all" | sed -n "${ln}p")
    [ -z "$hit" ] && { echo "no match for: $2"; exit 1; }
    xy=${hit%%|*}; sh_ input tap $xy; echo "tapped: ${hit#*|} at $xy" ;;
  tapxy) guard; sh_ input tap "$2" "$3"; echo "tapped $2,$3" ;;
  type) guard; shift; t=$(printf '%s' "$*" | sed 's/ /%s/g'); sh_ input text "$t"; echo "typed" ;;
  key) guard; sh_ input keyevent "KEYCODE_$2"; echo "key $2" ;;
  swipe) guard; if [ "$2" = up ]; then sh_ input swipe 540 1700 540 700 300; else sh_ input swipe 540 700 540 1700 300; fi; echo "swiped $2" ;;
  wait)
    LIMIT="${3:-120}"; START=$(date +%s)
    while :; do
      OUT=$(nodes | cut -d'|' -f2); NOW=$(( $(date +%s)-START ))
      if echo "$OUT" | grep -Eqi -- "$2"; then echo "MATCH after ${NOW}s"; echo "$OUT"; exit 0; fi
      [ "$NOW" -ge "$LIMIT" ] && { echo "TIMEOUT after ${NOW}s"; echo "$OUT"; exit 1; }
      sleep 2
    done ;;
esac
