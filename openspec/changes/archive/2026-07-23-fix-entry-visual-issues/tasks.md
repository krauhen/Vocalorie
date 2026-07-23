## 1. EXIF orientation for gallery images (B1)

- [x] 1.1 In `GalleryImageAttachment.kt`'s `toGalleryImageAttachment()`, read the source EXIF orientation via `ExifInterface` on the picked URI's input stream (confirm `androidx.exifinterface` is available; if not, flag for dependency approval before adding)
- [x] 1.2 Rotate the decoded bitmap to upright per the EXIF orientation before downsampling/compression
- [x] 1.3 Ensure both `previewImage` and the sent image are built from the rotated bitmap
- [x] 1.4 Verify a no-rotation image is passed through unchanged

## 2. Green-is-best score color (B2)

- [x] 2.1 In `MealStatsOverview.kt`, redefine `scoreToColor()` so the scale runs red (low) → yellow (mid) → green (best), removing the blue and magenta stops
- [x] 2.2 Update the palette consts (`:39–46`) accordingly, keeping the neutral "no data" color unchanged
- [x] 2.3 Confirm a score of 76 maps to the green range and 100 maps to green-max

## 3. Tests

- [x] 3.1 If color-mapping unit coverage exists, assert 100 → green-max, 76 → green-range, low → red; otherwise add a focused test for `scoreToColor`

## 4. Verification

- [x] 4.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and confirm all tests pass
- [x] 4.2 Manually verify on emulator/device: an EXIF-rotated gallery photo previews upright and is sent upright; the heatmap shows high scores (e.g. 76) as green
