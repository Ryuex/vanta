# Media assets

This directory holds the **real** media used to present Vanta: banners, screenshots, screen
recordings and the demo GIF.

**Nothing is published here yet.** The files below will be added when real Vanta captures exist.

## Planned files

| File | Purpose | Status |
|---|---|---|
| `vanta-demo.gif` | Short demo of Vanta running, referenced from the README | Pending — placeholder only |
| `banner.png` / `banner-dark.png` | Repository banner / social preview | Pending |
| `screenshots/*.png` | Screenshots of the Vanta interface and of games running | Pending |
| `logo-vanta.png` | Dedicated Vanta logo (until then the inherited `logo.png` at the repository root is used, credited to Winlator) | Pending |

## Where the demo GIF goes

`README.md` contains an **HTML comment placeholder** at the top explaining that the demo will live
at:

```
docs/media/vanta-demo.gif
```

The comment stays in place (so no broken image is rendered) until the real GIF is committed. When
adding it, uncomment the corresponding block in `README.md`.

## Rules

- **Only real captures of Vanta.** Do not add mockups, edited images of other applications or
  placeholder/stock images.
- No file may be referenced from documentation before it exists — avoid broken links.
- Keep images reasonably sized: GIFs ideally under ~10 MB (consider a short clip or a link to a
  video instead), screenshots compressed as PNG or optimized WebP.
- Sensitive information must be removed from captures: device identifiers, personal paths, account
  names, license keys.
- Original media authored for this project must be original or properly licensed, and third-party
  logos (including the upstream Winlator logo) must keep their attribution, see
  [CREDITS.md](../../CREDITS.md).
- Recommended sizes: README inline screenshots ~1280 px wide, banner ~1280×640, demo GIF ~760 px
  wide.

## Naming

```
docs/media/screenshots/<topic>-<detail>.png
  e.g. docs/media/screenshots/ui-containers.png
       docs/media/screenshots/game-example-dxvk-turnip.png
```
