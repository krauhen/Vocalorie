# estimation-progress Specification

## Purpose
TBD - created by archiving change 2026-08-20-narrate-estimation-progress. Update Purpose after archive.
## Requirements
### Requirement: An in-flight estimate names its current step
While a meal estimate is running, the system SHALL show a single line of text naming what the estimate is currently doing, in place of a fixed loading message. The line SHALL update as the estimate moves from one step to the next, so that a progressing estimate is distinguishable from a stalled one without any further interaction.

#### Scenario: The line replaces the fixed loading message
- **WHEN** the user starts an estimate
- **THEN** the capture overlay shows a step line describing the current step, and not a message that stays identical for the whole run

#### Scenario: The line advances as the estimate proceeds
- **WHEN** an estimate moves from researching sources to computing the nutrition values
- **THEN** the step line changes to describe the later step, without the user interacting with the screen

#### Scenario: Only the current step is shown
- **WHEN** an estimate has passed through several steps
- **THEN** the step line shows only the step in progress, and no list of earlier steps accumulates on screen

### Requirement: Steps are named in user-facing terms
The step line SHALL describe steps in terms of what the app is doing for the user — preparing the request, looking for sources, reading a source, computing nutrition values. It SHALL NOT display internal identifiers such as tool names, function names, model names, or raw request payloads.

#### Scenario: A tool call is described, not named
- **WHEN** the estimate invokes its web-fetch tool
- **THEN** the step line describes reading a source, and does not display the tool's internal identifier

#### Scenario: No raw payload is shown
- **WHEN** any step is in progress
- **THEN** the step line contains no request body, prompt text, or model identifier

### Requirement: Reading a source names the source's host
When the estimate successfully retrieves a page while researching, the system SHALL name that page's host in the step line. It SHALL NOT display the full URL. A page that was requested but not successfully retrieved SHALL NOT be named.

#### Scenario: A fetched page is named by host
- **WHEN** the estimate successfully retrieves a nutrition page from `fddb.info`
- **THEN** the step line names `fddb.info` as the source being read

#### Scenario: The full URL is not shown
- **WHEN** the estimate retrieves a page whose URL carries a long path and query string
- **THEN** the step line still shows only the host, not the path or the query

#### Scenario: A failed retrieval names nothing
- **WHEN** the estimate requests a page that does not return successfully
- **THEN** no step line naming that host is shown for it

### Requirement: An estimate without research still reports progress
When research is not performed — because it is switched off or unavailable — the system SHALL still show the steps that do occur, rather than falling back to a static message.

#### Scenario: Ungrounded estimate still narrates
- **WHEN** the user runs an estimate with research disabled
- **THEN** the step line still reports that the request is being prepared and that nutrition values are being computed

### Requirement: The step line clears when the estimate ends
On completion of an estimate, successful or failed, the system SHALL remove the step line. No step text SHALL remain on screen once nothing is running.

#### Scenario: Success clears the line
- **WHEN** an estimate completes and its draft appears
- **THEN** the step line is gone and the draft editor is shown

#### Scenario: Failure clears the line
- **WHEN** an estimate fails and its error is shown
- **THEN** the step line is gone, so nothing on screen suggests work is still in progress

### Requirement: A failed estimate reports the step it failed on
When an estimate fails, the system SHALL include the step that was in progress at the time of failure in the diagnostic detail already offered with the error, so that a failure during research is distinguishable from a failure before any request was made.

#### Scenario: Failure while reading a source
- **WHEN** an estimate fails while reading a source from `fddb.info`
- **THEN** the error's diagnostic detail states that the failure occurred while reading that source

#### Scenario: Failure before any step ran
- **WHEN** an estimate fails before any step beyond preparing the request
- **THEN** the error's diagnostic detail reflects that, and does not name a source

