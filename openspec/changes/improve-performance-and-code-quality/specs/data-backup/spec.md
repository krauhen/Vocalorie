## MODIFIED Requirements

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

## ADDED Requirements

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
