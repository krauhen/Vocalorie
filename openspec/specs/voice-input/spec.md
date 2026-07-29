# voice-input Specification

## Purpose
TBD - created by archiving change improve-performance-and-code-quality. Update Purpose after archive.
## Requirements
### Requirement: The microphone is released when the app leaves the foreground
The system SHALL stop any active speech-recognition session and release the microphone when the app is no longer in the foreground, and SHALL NOT resume listening until the user acts again. A continuous-listening session SHALL NOT keep the microphone open off-screen.

#### Scenario: Backgrounding the app stops listening
- **WHEN** a continuous listening session is active and the user backgrounds the app
- **THEN** recognition stops and the microphone is released

#### Scenario: Returning to the app does not silently resume listening
- **WHEN** the user returns to the app after a listening session was stopped by backgrounding
- **THEN** the microphone stays closed until the user starts listening again

### Requirement: An explicit stop is never overridden by a pending restart
The system SHALL honour a user request to stop listening even when a session restart is already scheduled. A scheduled restart SHALL re-check, at the moment it would begin, whether continuous listening is still requested and still permitted, and SHALL abort otherwise.

#### Scenario: Stopping during a scheduled restart keeps the microphone closed
- **WHEN** the user taps stop while a session restart is already scheduled
- **THEN** the restart does not open the microphone

#### Scenario: A restart aborts when listening is no longer permitted
- **WHEN** a session restart is scheduled and listening becomes disabled before it begins, for example because a save started
- **THEN** the restart aborts and no new recognition session starts

