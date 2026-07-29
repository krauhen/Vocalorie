# data-backup

## Purpose

User-controlled backup of all entry data so it can survive an uninstall/reinstall or move to another device, complementing Android's passive auto-backup. Export produces a portable JSON file; import merges a file back in without overwriting existing data.
## Requirements
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
The system SHALL refuse to apply a file whose format marker it does not recognize, or whose `schemaVersion` is outside the set of versions it knows how to import, surfacing a clear error instead of partially importing. The system SHALL accept every `schemaVersion` from which the current schema can be reached by additive migration alone, not only a version exactly equal to the current database version, so that a file exported by an earlier build of the app remains importable. The set of accepted versions SHALL be declared explicitly and SHALL be extended in the same change as any schema bump that remains additively compatible.

#### Scenario: Version mismatch is rejected cleanly
- **WHEN** the user selects a file whose `schemaVersion` is not understood, or a file that is not a valid backup envelope
- **THEN** the import is aborted with a clear error message and no rows are inserted or modified

#### Scenario: A file from an earlier additive schema still imports
- **WHEN** the user imports a file whose `schemaVersion` is older than the current database version but reachable by additive migration alone
- **THEN** the import proceeds and columns added since that version take their defaults

#### Scenario: A schema bump does not orphan existing exports
- **WHEN** the database schema is bumped additively and the export version is raised to match
- **THEN** files exported before the bump remain importable, because the accepted-version set was extended in the same change

### Requirement: Auto-backup remains enabled as a passive net
The system SHALL keep Android auto-backup enabled and SHALL NOT exclude the Room database (`vocalorie.db`) from backup rules, so device-transfer/cloud backup continues to cover entry data in addition to the manual export.

#### Scenario: Database stays included in backup rules
- **WHEN** the backup and data-extraction rules are inspected
- **THEN** `allowBackup` is true and neither `backup_rules.xml` nor `data_extraction_rules.xml` excludes `vocalorie.db`

### Requirement: Export captures a consistent snapshot
The system SHALL read all exported tables within a single transaction, so that a concurrent write cannot produce a file in which the exported tables disagree with one another.

#### Scenario: A concurrent save cannot tear the export
- **WHEN** a meal is saved while an export is reading the meal, activity and cache tables
- **THEN** the exported file reflects either the state before that save or the state after it, never a mixture in which a meal is present without its cache rows

### Requirement: The exported version matches the database version
The system SHALL stamp each export with a `schemaVersion` equal to the current Room database version at the time of export, and SHALL keep that value correct whenever the schema is bumped.

#### Scenario: Export stamps the current schema version
- **WHEN** the user exports data
- **THEN** the file's `schemaVersion` equals the current Room database version, not a stale earlier value

