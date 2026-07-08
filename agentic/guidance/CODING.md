# Coding guidance

## Android style

- Prefer clear Kotlin and small Compose functions.
- Keep Android framework integration separate from future domain logic where practical.
- Do not introduce dependencies without explicit approval.
- Preserve the app identity unless explicitly asked to change it:
  - namespace: `com.example.vocalorie`
  - applicationId: `app.vocalorie.personal`
  - app label: `Vocalorie`

## Current boundaries

This starter intentionally contains no GPS, GPX, map, location, storage-sync, or background-service implementation.
