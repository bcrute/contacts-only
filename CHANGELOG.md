# Changes

## 1.1.0 — November 4, 2026

- Switch call blocking on or off inside the app without uninstalling it.
- Save the switch setting and apply it to every new call.
- Preserve blocking when upgrading from 1.0.
- Show the effective status when call-screening access is unavailable or revoked.
- Leave texting in the existing messaging app.
- Show a message instead of closing when no app can open the phone or app
  settings screen, and use device-neutral wording for the phone-app button.
- Support Android 15 and later; older versions may work but are not supported.
- Sign with a dedicated release key. The key rotation lets 1.1.0 install as an
  update over 1.0 without uninstalling.

## 1.0 — August 24, 2026

- Reject incoming calls from visible phone numbers outside Contacts.
- Use Android's contacts filtering without requesting contacts access.
- Keep blocked calls in the system history.
