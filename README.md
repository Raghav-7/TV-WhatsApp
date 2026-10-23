# TV-WhatsApp

WhatsApp video calls on a Sony Google TV with a plain USB webcam, made simple enough for grandparents
to use with the TV remote. No extra hardware, ₹0.

Google Meet calling was removed from Android TV / Google TV in early 2025 (and from Samsung Tizen TVs in
2024), so this uses the normal **WhatsApp** Android app installed on the TV and linked to a phone's
WhatsApp account, plus two small apps of our own:

| App (home-screen tile) | Package | What it does |
|---|---|---|
| **Raghav's Whatsapp** (was "WhatsApp Beta" before 2.6) | `com.family.watile` (`wa-tile/`) | A TV-style contact grid (title, search bar, 6-across round avatars with name and number, blue focus outline). Select a person, then **Video call / Voice call / Cancel**; the call goes through WhatsApp and you come back to this screen when it ends. Also contains the **WhatsApp call helper** (an accessibility service, see below). |
| **WhatsApp** | `com.family.whatsapptile` (`wa-shortcut/`) | Opens normal WhatsApp. WhatsApp's phone app has no TV tile, so this gives it one. |

The **WhatsApp call helper** (inside Raghav's Whatsapp) only watches WhatsApp's screen:
1. **Incoming calls:** when a call rings while WhatsApp is open, WhatsApp shows only a small banner. The helper selects it so the full call screen opens; then the remote's **Play/Pause** button answers (WhatsApp treats it as a headset button).
2. **Opening a chat** from WhatsApp's chat list shows a big Video / Voice / Cancel box instead of the chat.
3. **Calls from Raghav's Whatsapp:** opens the person's chat (by number, or by name through WhatsApp's search), presses Video/Voice, and returns to Raghav's Whatsapp when the call is over, declined, cut or missed.

It survives TV restarts and app updates. It is switched **off** by Android if the app is **force-stopped**
(never use `am force-stop com.family.watile`), cleared or reinstalled from scratch.

Tested on: Sony KD-55X82L (2023, Google TV, Android 12, 32-bit ARM), Logitech C930e webcam,
WhatsApp 2.26.42.71.

## Setup on a new TV

1. **On the TV:** Settings → System → About → press *Build* 7 times; then Developer options → turn on
   **USB/ADB debugging**. Note the TV's IP (Settings → Network & Internet). Plug the webcam into the TV.
2. **On a Windows PC with a JDK:** `.\tools\setup-tools.ps1` (downloads Google's ADB, build-tools, platform).
3. `adb connect <TV-IP>:5555` and press **Allow** on the TV.
4. Install WhatsApp from https://www.whatsapp.com/android (`adb install WhatsApp.apk`), open it on the TV,
   accept the terms yourself, and **link it as a companion device** to the phone's account
   (phone: WhatsApp → Linked devices → Link a device). Don't register the number on the TV itself:
   that logs the phone out. Grant it camera/mic: `adb shell pm grant com.whatsapp android.permission.CAMERA`
   and `... RECORD_AUDIO`.
5. Get the WhatsApp logo for the tiles: `.\tools\get-whatsapp-logo.ps1 -Apk WhatsApp.apk`.
6. Build and install: `.\wa-tile\build.ps1`, `.\wa-shortcut\build.ps1`, then
   `adb install -r wa-tile\wa-tile.apk` and `adb install -r wa-shortcut\wa-shortcut.apk`.
   The build creates a local signing key `wa-tile/wa-tile.keystore` on first run. **Keep it**: updates
   must be signed with the same key, otherwise you have to uninstall (which switches the helper off).
7. **On the TV (the owner must do this):** Settings → System → Accessibility → **WhatsApp call helper** → On.
8. Family list: copy `family/family.example.txt` to `family/family.txt`, fill it in, run
   `.\family\update-family.ps1 -Tv <TV-IP>`. People can also be added on the TV with the *Add contact* tile.
9. Turn ADB debugging off again when done. Nothing here needs it to keep working.

See [docs/NOTES-FOR-AGENTS.md](docs/NOTES-FOR-AGENTS.md) for the investigation, what was tried, and the gotchas.

## Not in this repo (on purpose)
- `*.keystore`: the signing key. Anyone holding it could publish updates to the installed app.
- `family/family.txt` and photos: real names and phone numbers.
- `wa_logo.webp`: WhatsApp's logo; extract it locally with `tools/get-whatsapp-logo.ps1`.
- Built `.apk` files.
