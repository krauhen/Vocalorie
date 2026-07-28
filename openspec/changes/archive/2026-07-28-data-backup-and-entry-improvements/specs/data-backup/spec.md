## ADDED Requirements

### Requirement: Export all entry data to a JSON file
The system SHALL provide an "Export data" action in Settings that writes a single JSON file, via the system file picker (SAF), containing all user data: meals, activities, and the reuse caches (`cached_meals`, `cached_items`). The file SHALL include a format marker and a `schemaVersion` equal to the Room database version, and SHALL include each row's primary key. The export SHALL NOT include any secret (OpenAI or Brave API keys or any other credential).

#### Scenario: Export produces a complete, secret-free file
- **WHEN** the user triggers "Export data" and chooses a destination
- **THEN** a JSON file is written containing every meal, activity, cached meal, and cached item row (each with its primary key) plus a `schemaVersion`, and containing no API keys or other secrets

#### Scenario: Empty database exports valid structure
- **WHEN** the user exports with no entries logged
- **THEN** a valid JSON file is written with empty arrays and the correct `schemaVersion`

### Requirement: Import merges by primary key without overwriting
The system SHALL provide an "Import data" action in Settings that reads a previously exported JSON file (via SAF) and inserts only those rows whose primary key is not already present in the database. Existing rows SHALL NOT be modified or deleted. Matching SHALL use the stable primary key: the `Long id` for meals and activities, `normalizedKey` for cached meals, and `normalizedName` for cached items. The system SHALL report how many rows were imported versus skipped.

#### Scenario: Restore into a fresh install
- **WHEN** the user imports an exported file into an app with no existing data
- **THEN** all rows from the file are inserted, preserving their original primary keys, and the reported skipped count is zero

#### Scenario: Re-importing the same file is a no-op
- **WHEN** the user imports a file whose rows are all already present by primary key
- **THEN** no rows are inserted or changed and every row is reported as skipped

#### Scenario: Existing rows are never overwritten
- **WHEN** the imported file contains a row whose primary key already exists in the database
- **THEN** the existing row is left unchanged and the incoming row is skipped

### Requirement: Import rejects unrecognized file versions
The system SHALL refuse to apply a file whose `schemaVersion` (or format marker) it does not recognize, surfacing a clear error instead of partially importing.

#### Scenario: Version mismatch is rejected cleanly
- **WHEN** the user selects a file whose `schemaVersion` is not understood, or a file that is not a valid backup envelope
- **THEN** the import is aborted with a clear error message and no rows are inserted or modified

### Requirement: Auto-backup remains enabled as a passive net
The system SHALL keep Android auto-backup enabled and SHALL NOT exclude the Room database (`vocalorie.db`) from backup rules, so device-transfer/cloud backup continues to cover entry data in addition to the manual export.

#### Scenario: Database stays included in backup rules
- **WHEN** the backup and data-extraction rules are inspected
- **THEN** `allowBackup` is true and neither `backup_rules.xml` nor `data_extraction_rules.xml` excludes `vocalorie.db`
