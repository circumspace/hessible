# Hessible icon

Two interwoven ribbons form an H, suggesting independent people connecting. Coral and amber cross deep purple on a midnight background. The layered color artwork and monochrome silhouette share the same paths.

## Source and regeneration

`source/icon.svg` is the editable desktop composition. `foreground.svg`, `background.svg`, and `monochrome.svg` separate the components. `store.svg` is the full-bleed store composition. All are self-contained SVG files with no external resources.

`generate-icons.mjs` contains the canonical geometry and export settings. Run it from the repository root with Node.js and the `sharp` package available:

```sh
node branding/icons/generate-icons.mjs .
```

Alternatively set `SHARP_MODULE` to the absolute path of an existing Sharp installation. The generator needs no network access and writes only the icon outputs. Edit its geometry when making a change that should propagate to every format; direct SVG edits are not read back by the generator.

## Android

- Separate opaque gradient background and transparent foreground at all five standard densities.
- Adaptive layers are 108dp. The essential artwork fits inside the centered 66dp safe circle.
- API 33+ resources supply a native monochrome vector with the same interwoven H silhouette. Earlier adaptive resources remain valid for API 26+.
- Launcher fallbacks include round and rounded-square images. The About screen and repository icon use the desktop composition.
- `android/play-store-512.png` is a 512px full-bleed opaque store asset.

Android owns the adaptive mask; do not use the desktop tile as the adaptive foreground. See [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

## macOS

`macos/Hessible.icns`, `Hessible.iconset`, and `AppIcon.appiconset` contain conventional macOS icons at standard and Retina resolutions. Add the asset catalog to the future native macOS target; no macOS application target is added here.

`macos/layers/` provides transparent foreground and full-bleed background PNGs for a future Icon Composer project. These are source layers, not a finished Liquid Glass `.icon` file. Import the layers and tune platform rendering in [Apple Icon Composer](https://developer.apple.com/icon-composer/) when the macOS codebase exists.

## Linux

`linux/hicolor/` includes 16–1024px PNGs and a scalable SVG using the existing application ID `com.circumspace.contactstr`. Install under the appropriate hicolor icon theme directory and use `Icon=com.circumspace.contactstr` in the native app's desktop entry. No Linux application or package is added here.

## Design specification

Four layered paths describe two interwoven ribbons. Coral and amber occupy the left side; dark purple occupies the right. The background runs from `#1D2C42` to `#0C1421`. Broad gradients give the folds depth while keeping the silhouette readable at small sizes. The mark itself forms the letter H; it contains no text or font dependency.

## Concept provenance

The built-in image-generation tool explored a sculptural H made from interwoven coral-orange and cyan-blue ribbons on midnight ink, suggesting private connections. Shipping artwork was drawn as editable vector paths, then the blue palette was changed to deep purple at the user’s request. Generated raster concepts are not runtime dependencies.
