#!/usr/bin/env bash
set -u

cd "${GITHUB_WORKSPACE:-.}"

APK="playback-demo/build/outputs/apk/debug/playback-demo-debug.apk"
adb install -r "$APK"
fail=0

run_case() {
  name="$1"
  url="$2"
  adb logcat -c
  adb shell am force-stop com.fongmi.player.demo || true
  adb shell am start -W -n com.fongmi.player.demo/.MainActivity --es url "$url"
  sleep 20
  adb logcat -d > "logcat-$name.txt"
  adb exec-out screencap -p > "screen-$name.png"
  echo "=== $name first frame ==="
  if grep -q "FIRST_FRAME" "logcat-$name.txt"; then
    echo "PASS $name video"
  else
    echo "FAIL $name video"
    grep -E "PlayerCore|PlaybackDemo|AndroidRuntime" "logcat-$name.txt" | tail -80 || true
    fail=1
  fi
}

run_case mp4 "http://10.0.2.2:8000/video.mp4"
run_case hls "http://10.0.2.2:8000/vod/index.m3u8"
run_case av "http://10.0.2.2:8000/av/index.m3u8"

echo "=== audio check ==="
if grep -q "AudioTrack play" "logcat-av.txt"; then
  echo "PASS audio track"
else
  echo "FAIL audio track"
  fail=1
fi

exit "$fail"
