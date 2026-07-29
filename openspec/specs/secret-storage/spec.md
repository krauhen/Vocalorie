# secret-storage Specification

## Purpose
TBD - created by archiving change improve-performance-and-code-quality. Update Purpose after archive.
## Requirements
### Requirement: A read failure never destroys a stored secret
The system SHALL NOT delete, clear, or overwrite a stored API key as a consequence of failing to decrypt or read it. When a stored secret cannot be read, the system SHALL retain the stored value, report the failure to the user as distinct from "no key configured", and allow the user to re-enter or explicitly clear the key.

#### Scenario: Decrypt failure keeps the stored value
- **WHEN** reading a stored API key fails, for example because the KeyStore entry is unavailable after a device restore
- **THEN** the stored ciphertext is left intact and is not deleted

#### Scenario: Read failure is distinguishable from no key
- **WHEN** a stored API key exists but cannot be read
- **THEN** the user is shown that the saved key could not be read, rather than the app reporting that no key is configured

#### Scenario: Clearing a key remains explicit
- **WHEN** the user chooses to clear a stored API key
- **THEN** the key is removed, because removal happens only on explicit user action

### Requirement: One shared secret-encryption implementation
The system SHALL implement KeyStore-backed secret encryption and decryption once and reuse it for every stored secret, parameterized by key alias. Each secret store SHALL delegate to that shared implementation rather than carrying its own copy of the key-generation, encryption, and decryption logic.

#### Scenario: Both stored keys use the same implementation
- **WHEN** the OpenAI key and the Brave key are encrypted or decrypted
- **THEN** both operations go through the same shared implementation, differing only by key alias

#### Scenario: A hardening change applies everywhere at once
- **WHEN** the shared encryption implementation is changed
- **THEN** every stored secret is covered by that change, with no second copy left un-updated

### Requirement: Secret masking is implemented once
The system SHALL derive the masked display label for a stored secret from one shared implementation, so that every stored key is masked identically.

#### Scenario: All stored keys mask consistently
- **WHEN** the saved-key labels for the OpenAI key and the Brave key are rendered
- **THEN** both are produced by the same masking implementation and reveal the same amount of the underlying value

