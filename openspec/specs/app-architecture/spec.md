# app-architecture Specification

## Purpose
TBD - created by archiving change improve-performance-and-code-quality. Update Purpose after archive.
## Requirements
### Requirement: Layered unidirectional data flow
The system SHALL organize application code into four layers with dependencies pointing in one direction only: composable UI, a screen-scoped state holder, repositories, and data sources (Room DAOs, preference stores, and the nutrition-estimation seam). Composables SHALL render an immutable state value and emit events, and SHALL NOT contain business logic, orchestration, or data-source access. Repositories SHALL own their own background dispatching, so that no caller must remember to wrap a repository call. An Android `Context` SHALL NOT be required below the repository boundary, and a DAO SHALL NOT be referenced above it.

#### Scenario: A composable does not reach a data source
- **WHEN** any screen-level composable is inspected
- **THEN** it holds no reference to a DAO, preference store, database, or nutrition-estimation client, and performs no persistence call

#### Scenario: Repository callers do not manage dispatchers
- **WHEN** a state holder calls a repository method that reads or writes persisted data
- **THEN** the caller does not wrap the call in a dispatcher switch, because the repository already dispatches its own work

#### Scenario: Business rules are testable without the UI toolkit
- **WHEN** a validation or planning rule extracted from the capture flow is exercised
- **THEN** it can be tested as a pure function without instantiating a composable or an Android framework component

### Requirement: In-progress capture work survives configuration change
The system SHALL retain in-progress meal capture state across an activity configuration change, including a running nutrition estimate, the reviewed draft awaiting confirmation, attached images, and the entered query. A configuration change SHALL NOT cancel a nutrition estimate that is already in flight.

#### Scenario: Rotation during an estimate keeps the request alive
- **WHEN** the user requests a nutrition estimate and rotates the device before the result arrives
- **THEN** the same request continues and its result is applied, without a second request being issued

#### Scenario: Rotation preserves the review draft and attachments
- **WHEN** the user has a reviewed draft on screen with attached images and rotates the device
- **THEN** the draft, its edits, and the attached images are all still present afterwards

### Requirement: A reviewed save is atomic
The system SHALL write the meal history row and its reuse-cache rows for a single reviewed save as one atomic unit, so that the meal and its cache entries either both persist or neither does. This SHALL hold even if the save is interrupted by a configuration change or process death.

#### Scenario: Interrupted save leaves no partial state
- **WHEN** a reviewed save is interrupted after the meal row is written but before the cache rows are written
- **THEN** neither the meal row nor the cache rows are present, rather than a meal without its cache entries

#### Scenario: Configuration change does not abort a save
- **WHEN** the user confirms a reviewed save and the device rotates while the write is in progress
- **THEN** the save completes and the meal appears in history

### Requirement: Persisted data changes propagate without manual refresh
The system SHALL treat the database as the single source of change notification for entry data. Reading code SHALL observe persisted entries as a stream, and the system SHALL NOT re-read whole tables after each write in order to refresh the UI.

#### Scenario: Saving an entry updates the list without a full reload
- **WHEN** the user saves a meal or activity
- **THEN** the entries list reflects the new row without the application re-reading the entire meal, activity and cache tables

#### Scenario: Deleting an entry updates the list without a full reload
- **WHEN** the user deletes an entry
- **THEN** the entries list and the day totals update from the observed stream, without an explicit follow-up query for all rows

