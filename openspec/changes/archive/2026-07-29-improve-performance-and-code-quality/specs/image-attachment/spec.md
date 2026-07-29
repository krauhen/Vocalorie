## ADDED Requirements

### Requirement: Attachment processing runs off the main thread with visible progress
The system SHALL perform gallery-image reading, EXIF orientation correction, downsampling and compression on a background dispatcher, and SHALL show a progress indication while attachments are being prepared. Selecting the maximum permitted number of images SHALL NOT block the user interface.

#### Scenario: Selecting several images keeps the UI responsive
- **WHEN** the user selects the maximum permitted number of gallery images at once
- **THEN** the interface stays responsive while the images are prepared, and no frame is blocked waiting on decoding or compression

#### Scenario: Progress is visible while attachments are prepared
- **WHEN** attachment preparation is in progress
- **THEN** the user sees that attachments are being prepared, rather than an unexplained pause

#### Scenario: Prepared attachments appear when ready
- **WHEN** attachment preparation completes
- **THEN** the prepared attachments appear as previews with their corrected orientation, unchanged from the existing orientation behaviour

#### Scenario: Failing to prepare one image is reported
- **WHEN** one selected image cannot be read or decoded
- **THEN** the user is told that image could not be attached, and the remaining selected images are still attached
