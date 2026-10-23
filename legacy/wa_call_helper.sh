#!/system/bin/sh
# WhatsApp call helper for the TV.
# When a WhatsApp call rings while WhatsApp is on screen, it selects the
# in-app call banner so the full call screen opens; Play/Pause then answers.
# Does nothing when WhatsApp is not on screen. Logs only timestamps.

LOG=/data/local/tmp/wa_call_helper.log
UI=/data/local/tmp/wa_ui.xml

echo "$(date '+%F %T') helper started" >> "$LOG"

while true; do
  focus=$(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)
  case "$focus" in
    *com.whatsapp/*VoipActivity*)
      ;;
    *com.whatsapp/*)
      if dumpsys audio 2>/dev/null | grep -m1 "Actual mode" | grep -q RINGTONE; then
        # Find the banner position; fall back to the top strip if the dump fails.
        x=960; y=39
        uiautomator dump --compressed "$UI" >/dev/null 2>&1
        b=$(grep -o 'incall_baner"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$UI" 2>/dev/null \
            | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
        rm -f "$UI"
        if [ -n "$b" ]; then
          set -- $(echo "$b" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\1 \2 \3 \4/')
          x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
        fi
        input tap "$x" "$y"
        echo "$(date '+%F %T') ringing in WhatsApp - opened call screen" >> "$LOG"
        sleep 4
      fi
      ;;
  esac
  sleep 1
done
