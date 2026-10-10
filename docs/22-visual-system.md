# Tally visual system

The Android app uses one cobalt accent (`#0066FF`) over a night-blue canvas. Blue carries actions and brand emphasis; green, amber and red are reserved for payment states. The shared tokens, glass rims, shadows and type scale live in `app/app/src/main/java/app/tally/ui/theme/Theme.kt`.

## Typography

- Display: [Playfair Display](https://fonts.google.com/specimen/Playfair+Display), 500–700. Used for the two-line editorial headlines and loading message.
- Interface: [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans), 400–800. Used for buttons, cards, navigation and body copy.
- Data: JetBrains Mono for addresses and transaction details.

The font files and OFL licenses are bundled in `app/app/src/main/res/font` and `app/third_party/fonts`. The app works offline.

## Artwork

`iassets/logo.png` is the source for all five Android launcher densities. `iassets/tally_mascot_transparent.png` supplies the high-resolution base pose and avatar. The older low-resolution reaction crops were removed from the Android resource directory.

Three matching reaction poses and one editorial hero render were generated with the built-in image generation tool using the transparent Tally mascot as an identity reference. The reaction renders were keyed from their white generation background to transparent lossless WebP. Final app assets are `mascot_thinking_hd.webp`, `mascot_success_hd.webp`, `mascot_blocked_hd.webp` and `mascot_editorial.webp` in `app/app/src/main/res/drawable-nodpi`.

Generation prompts, condensed only to remove repeated phrasing:

- **Thinking:** “One full-body high-fidelity 3D chibi Tally mascot, same cobalt beanie and white tally mark, mirrored blue visor, cobalt hoodie, black cargo pants, blue-white shoes and proportions as the reference; hand at chin, thoughtful expression; controlled soft upper-left studio light; isolated on pure white; no text, props or extra characters.”
- **Success:** “The same Tally mascot in a delighted thumbs-up pose, keeping identical face, outfit, proportions, material detail and studio lighting; full body on pure white; no text, props or extra characters.”
- **Blocked:** “The same Tally mascot with a calm concerned expression and a raised stop palm, keeping identical face, outfit, proportions, material detail and studio lighting; full body on pure white; no text, props or extra characters.”
- **Editorial hero:** “The same Tally mascot as a premium studio product render, detailed cobalt knit and clothing, controlled blue light, centered full body, no text or extra characters.”

The hero render intentionally keeps its blue studio backdrop and appears inside the masked welcome panel. The three transparent reactions are used in `Mascot.kt`; pose changes and idle movement are native Compose animations.

## Motion and loading

`Reveal` in `Components.kt` provides staggered 16 dp translation and opacity entry. Buttons and tabs use spring press feedback. `MainActivity.kt` gives busy work a solid, touch-blocking screen with an indeterminate cobalt progress bar. The implementation uses Compose's animation system because the product is a native Android app.
