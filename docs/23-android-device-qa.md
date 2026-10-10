# Android device review — 2026-10-10

Device: Motorola moto g34 5G, 720 × 1600 display, Android density override 306 dpi.

The phone already had `app.tally` signed with a different key, so an in-place debug update was rejected by Android. The review used `app.tally.qa`, a separate debug package installed alongside the existing app. The original app and its data were left intact. The QA package was given a local debug demo owner with no funded capsule.

## Checked on device

- Fresh welcome screen: launcher artwork, typography, hero crop, card layers and first-screen wallet action.
- Connected empty dashboard and capsule rule form; text fields and route navigation render correctly.
- Receipts empty state and its capsule setup action.
- Red-team layout on the narrow screen; chips wrap without clipping and the action remains visible.
- Settings and merchant registration form.
- Scan route in the QA package: camera permission, live preview, scan frame, instruction layout and navigation back. This phone reports no NFC hardware.
- Busy state during a real read-only refresh: a screen recording showed the previous route fully masked by the opaque loading view.
- Android runtime log after navigation: no `AndroidRuntime` fatal exception recorded.

## Not exercised

Creating or funding a capsule, wallet signatures, merchant bonding, a real QR checkout, red-team transactions and receipts from completed payments require devnet funds, a registered merchant or a signed menu. No financial or on-chain transaction was initiated during this visual review.

Build commands:

```sh
cd app
./gradlew :app:assembleDebug -PtallyQaInstall --offline
./gradlew :app:assembleDebug --offline
```

The first command produces the side-by-side `app.tally.qa` package. The second restores the normal `app.tally` debug APK at the standard output path.
