# Sillon — design

*Sillon* is French for the groove of a record. The identity is the record and
its sleeve: one continuous groove, a vinyl-black disc, a cream sleeve and the
amber of the label.

![Brand board](docs/brand/board.png)

## Idea

> **Ta musique, ton serveur, un seul sillon.**
> *Your music, your server, one groove.*

Sillon plays the music people keep on their own server. The design stays out
of the way of the covers (the colour on screen should come from the music),
and keeps a little warmth of the physical record: a serif on the titles, warm
blacks instead of grey, amber instead of a tech blue.

## Logo

One continuous groove — an Archimedean spiral of 2¾ turns, from the rim to the
label — spiralling to the amber label with its spindle hole.

| File | Use |
|---|---|
| [`docs/brand/logo.svg`](docs/brand/logo.svg) | Dark: cream groove on vinyl black (default) |
| [`docs/brand/logo-light.svg`](docs/brand/logo-light.svg) | Light: vinyl groove on sleeve cream |
| `res/drawable/ic_launcher_foreground.xml` | Launcher and splash icon (fits the 66 dp safe zone) |
| `res/drawable/ic_launcher_monochrome.xml` | Themed icons (Android 13+) |
| `ui/components/SillonMark.kt` | The same spiral in Compose: sign-in screen, cover placeholder |

**Animated logo** — [`docs/brand/logo-animation.gif`](docs/brand/logo-animation.gif),
[`logo-wordmark.mp4`](docs/brand/logo-wordmark.mp4) (1920 × 1080) and
[`logo-square.mp4`](docs/brand/logo-square.mp4) (1080 × 1080): the label
springs in, the groove is drawn from the rim to the label like a needle drop
while the record turns, then the name and the tagline arrive. Source in
[`brand/animation`](brand/animation) (Remotion): `npm install && npm run render`.

Don't stretch the spiral or change its number of turns; don't put the label in
another colour than amber except in monochrome uses.

## Colour

| Name | Hex | Role |
|---|---|---|
| **Vinyl** | `#15120F` | Dark background; text on light |
| **Groove** | `#2A241E` | Dark surfaces, cards, the icon's background family |
| **Sleeve** | `#F6EFE4` | Light background family; text on dark |
| **Label** | `#E8A33D` | Accent: primary in dark, the logo's label |
| **Rust** | `#C2552B` | Second accent (secondary) |

The Material 3 schemes in `ui/theme/Theme.kt` derive from these. In light, the
amber is darkened to `#8A5100` to stay readable on cream (WCAG AA). Material
You (wallpaper colours) is available in Settings → Appearance, off by default:
the brand's colours are the default.

The player borrows its colour from the cover: the cover's average colour washes
down from the top of the screen.

## Type

| Family | Weights | Use |
|---|---|---|
| **Fraunces** | SemiBold | Display, headlines and large titles: screen titles, album and track titles in the player |
| **DM Sans** | Regular, Medium, Bold | Everything else: lists, labels, buttons, body |

Both are SIL Open Font License fonts, bundled in `res/font/` (≈ 260 KB), with
their licences in [`docs/fonts/`](docs/fonts).

## Shape and motion

- Corners: 6 / 10 / 16 / 24 / 32 dp (extra small → extra large). Covers use 8–16 dp, circles for artists.
- The player's tint animates over 600 ms when the track changes.
- Splash screen: the spiral on vinyl black, then the app.

## Voice

Short, warm, direct, in the second person (*tu* in French). Say what happens
("Lecture aléatoire de mes 5 étoiles"), not how it works.
