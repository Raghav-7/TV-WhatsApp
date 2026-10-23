# Notes for whoever picks this up next

Goal from the owner: family video calls on the living-room TV with an existing USB webcam, for
grandparents who use only the TV remote, spending ₹0. Original devices: Samsung The Frame (Tizen),
Tata Play Binge+ box, Sony Google TV, Logitech C930e webcam. No laptop near the TV (a Windows PC on the
same Wi-Fi was used for setup over ADB).

## What was found (Oct 2026)

| Option | Result |
|---|---|
| Google Meet on Samsung Tizen | Removed March 2024. |
| Google Meet calling on Android TV / Google TV | Removed early 2025 (Google's help page; Sony says "Google Meet support ended in March 2025"). Server-side, so an old APK doesn't help. |
| Zoom "for Home TV" (`us.zoom.tv`) | Official on many 2022–2025 Sony BRAVIAs and worked with the C930e, but meeting/link based: too complicated for the grandparents. |
| Jami (`cx.ring`) | Has an Android TV app with ringing calls; installed but not pursued once WhatsApp worked. |
| WhatsApp phone APK on the TV, linked as a companion device | **Chosen.** Calls and video work with the USB webcam. Problems: phone UI, no TV tile, incoming calls don't take over the screen. All solved by the two small apps here. |
| Tata Play Binge+ | Android TV 9-ish box (Broadcom BCM72604, 2 GB). USB webcam support unverified; not needed in the end. |

Check first on a new TV: `adb shell pm list features | grep camera` should list
`android.hardware.camera.external`, and `dumpsys media.camera` should show 1 external camera. If not,
no app can use the webcam on that TV.

## How the pieces work

- **Answering:** WhatsApp's incoming call screen (`com.whatsapp.calling.ui.VoipActivityV2`) answers on
  `KEYCODE_HEADSETHOOK` (79) and the remote's **Play/Pause** (85). It does not answer from the small
  banner (`com.whatsapp:id/incall_baner`) shown when WhatsApp is already in front.
- **Why the call screen doesn't pop up by itself:** Android blocks WhatsApp from starting the activity
  over another app (the TV refuses it; WhatsApp doesn't declare `SYSTEM_ALERT_WINDOW`, so `appops` can't
  grant it). `settings put global heads_up_notifications_enabled 0` made no difference on this Sony.
  Fix: the accessibility service clicks the banner (`ACTION_CLICK`) while
  `AudioManager.getMode() == MODE_RINGTONE`.
- **Placing calls:** `whatsapp://send?phone=<digits>` opens the chat when a number is known; otherwise the
  helper types the name into WhatsApp's search (`search_bar_inner_layout` → `search_input`,
  `ACTION_SET_TEXT`) and clicks the result whose `conversations_row_contact_name` matches exactly. In the
  chat it clicks the header button whose content description is "Video call" / "Voice call".
- **Returning to WhatsApp Beta:** after Beta hands over to WhatsApp, the helper polls the audio mode;
  once a call happened (`MODE_IN_COMMUNICATION` or the call screen appeared) and audio is back to
  `MODE_NORMAL` for two checks, or ringing stops unanswered, it starts `FamilyActivity` again.
  It only does this if WhatsApp is still the app on screen.
- WhatsApp view IDs used (may change with WhatsApp updates; check with `uiautomator dump`):
  `incall_baner`, `conversation_contact_name`, `conversations_row_contact_name`,
  `contact_row_container`, `search_bar_inner_layout`, `search_input`. Activities:
  `com.whatsapp.home.ui.HomeActivity`, `com.whatsapp.Conversation`, `...VoipActivityV2`.

## Gotchas learned the hard way

- **`am force-stop com.family.watile` switches the accessibility helper off** (Android removes a
  force-stopped package's service from `enabled_accessibility_services`). Normal `adb install -r`
  updates keep it on. Only the TV owner should switch it back on (Settings → System → Accessibility).
- **The TV home screen shows one tile per package.** A second `LEANBACK_LAUNCHER` activity in the same
  app never appears, which is why the plain WhatsApp tile is a separate package.
- Display is 1920x1080 logical at 320 dpi (960x540 dp) on a 4K panel: size grids for 540 dp of height.
- WhatsApp contact names can contain commas and trailing dots (e.g. `lakshmi,ravi`, `Uncle Pune .`):
  the family list uses `|` as the separator and matches names exactly (ignoring case).
- **Don't tap a contact's profile photo in WhatsApp's chat list** to reach their number: if they have a
  Status posted, it opens and marks it as viewed. Opening the chat marks messages read. Ask the owner for
  numbers instead.
- An early version copied WhatsApp profile photos with `takeScreenshot`; the owner preferred plain
  coloured initials, so that was removed (and the screenshot capability with it).
- Windows: the session's scratch paths are too long for some native tools; everything is staged under
  `%TEMP%\tvadb` and `%TEMP%\tvbuild`. In Git Bash, set `MSYS_NO_PATHCONV=1` before `adb shell` paths.
- Privacy: avoid screenshots or UI dumps of WhatsApp chat lists; grep only for the specific names or IDs
  needed.

## Other things done on this TV
- Stremio has no Play Store listing for this TV: the official Android TV ARM APK from
  https://www.stremio.com/downloads (`dl.strem.io`, signed by Stremio) was installed with `adb install`.
- `legacy/wa_call_helper.sh` is the first version of the call helper: a shell loop started over ADB.
  It stopped on every reboot, so it was replaced by the accessibility service.

## Ideas not done yet
- A full TV restart test of the helper (expected to come back on its own; not yet observed).
- Optional self-repair: grant `WRITE_SECURE_SETTINGS` (`adb shell pm grant com.family.watile
  android.permission.WRITE_SECURE_SETTINGS`, run by the owner) and let the app re-add its own
  service to `enabled_accessibility_services` if it finds it off.
- Phone numbers for the family list (owner to provide).
