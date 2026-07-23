## Why

Two independent visual defects surfaced in daily use of the entries and capture screens. (B1) Gallery photos attached to a meal preview rotated 90° — the attachment pipeline decodes and downsamples the image but never reads its EXIF orientation, so portrait photos taken in certain orientations render sideways in the preview and are sent to the LLM sideways too, even though the OS gallery shows them upright. (B2) The heatmap/score color scale runs red→orange→yellow→green→blue→magenta with green sitting in the middle (~50–60) and magenta at the top (100), so a good score like 76 shows as blue instead of green; the intended reading is that green is the best/highest score. Both are small, low-risk presentation fixes bundled together.

## What Changes

- **B1 — respect EXIF orientation:** In the gallery-image attachment pipeline, read the source image's EXIF orientation and rotate the decoded bitmap accordingly before downsampling/compressing. The corrected orientation SHALL apply to **both** the on-screen preview and the image bytes sent to the LLM, so a photo that is upright in the OS gallery is upright everywhere.
- **B2 — green is the best score:** Rework the score→color mapping so the scale ends at green (best): low scores red, mid yellow, high/best green. The blue and magenta segments are removed. A score of 76 renders green, not blue.

Out of scope: any change to how the score itself is computed (the 0–100 nutrition score is unchanged); the neutral "no data" cell color is unchanged; broader theming/palette work.

## Approvals obtained

- Adding the `androidx.exifinterface` dependency for B1 (if not already available transitively) — approved.

## Capabilities

### New Capabilities
- `image-attachment`: correct orientation handling for gallery images attached to a meal, applied consistently to the preview and to the image sent to the model.

### Modified Capabilities
- `day-nutrition-score`: the score→color scale is redefined so green is the maximum/best color; the blue and magenta high-end segments are removed.

## Impact

- `app/src/main/java/com/example/vocalorie/ui/voice/GalleryImageAttachment.kt` — `toGalleryImageAttachment()`: read EXIF orientation (`ExifInterface`) and rotate the decoded bitmap before building `previewImage` and the sent image.
- `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsOverview.kt` — `scoreToColor()` and the palette consts (`:39–46`): redefine the gradient so it terminates at green; drop blue/magenta stops.
- Tests: `MealStatsOverview` color-mapping unit coverage if present (assert 76 → green-range, 100 → green-max); no prompt/DTO/schema changes.
- No dependency changes expected (Android's `androidx.exifinterface` may already be available transitively; if a new dependency is required for `ExifInterface`, that needs explicit approval before adding).
