# image-attachment

## Purpose

TBD — capture the intent of attaching gallery images to a meal entry so the LLM can estimate nutrition from a photo, including correct handling of image orientation.

## Requirements

### Requirement: Gallery images respect EXIF orientation
The system SHALL read the EXIF orientation of a gallery image selected as a meal attachment and rotate the decoded bitmap to its upright orientation before downsampling and compressing it. The corrected orientation SHALL be applied consistently to both the on-screen preview image and the image sent to the LLM, so that an image shown upright in the OS gallery appears upright in the app preview and is transmitted upright.

#### Scenario: Rotated photo previews upright
- **WHEN** the user attaches a photo whose EXIF orientation marks it as rotated 90° and which appears upright in the OS gallery
- **THEN** the in-app preview shows the photo upright, not rotated 90°

#### Scenario: Sent image matches the corrected orientation
- **WHEN** an EXIF-rotated photo is attached and an estimate is requested
- **THEN** the image sent to the model is in the same upright orientation as the preview

#### Scenario: Already-upright photo is unchanged
- **WHEN** the user attaches a photo with normal (no-rotation) EXIF orientation
- **THEN** the preview and sent image are unrotated, identical in orientation to the source
