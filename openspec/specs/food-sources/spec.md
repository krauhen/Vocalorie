# food-sources

## Purpose

TBD — capture the intent of item-level food source attribution and nutrition-prompt output rules.
## Requirements
### Requirement: Every meal item always attempts a real source URL
The system SHALL ground each generated food item on a real, fetched web page whenever possible, rather than letting the model author a source URL from memory. The nutrition-estimation flow SHALL, when research tools are active, use Brave Search + WebFetch to retrieve candidate pages and SHALL derive the item's nutrition values from a page it actually fetched during the run. An item's `source` SHALL be populated ONLY with a URL that was actually fetched during that run; any URL not matching a fetched URL SHALL be discarded and treated as blank. When no page is fetched for an item (tools disabled, no key, or no confident match), the values MAY be an LLM estimate and the `source` SHALL be left blank rather than filled with a guessed or fabricated URL.

#### Scenario: Sourced item stores a fetched URL
- **WHEN** the flow fetches a real page for an item and derives its values from that page
- **THEN** the item's `source` is set to exactly that fetched URL

#### Scenario: Guessed URL is rejected
- **WHEN** the model emits a `source` URL that was not among the URLs actually fetched during the run
- **THEN** that URL is discarded and the item's `source` is left blank

#### Scenario: Unsourced item leaves source blank
- **WHEN** no page was fetched for an item (research disabled, no Brave key, or no confident match)
- **THEN** the item keeps an LLM-estimated value and a blank `source`, never a fabricated URL

### Requirement: Preference for German and listed national food-composition databases
The system SHALL, when selecting which fetched page to cite as an item's `source`, prefer results from a defined list of national/international food-composition databases, preferring German sources (the Bundeslebensmittelschlüssel, https://www.blsdb.de/) when a German-appropriate fetched page is available, and falling back to other listed databases (USDA FoodData Central, McCance & Widdowson's/CoFID, Ciqual, Frida, AFCD, Swiss Food Composition Database, NEVO, Livsmedelsverket, Canadian Nutrient File, Open Food Facts, FAO/INFOODS) otherwise. This preference SHALL only rank among pages actually fetched during the run; it SHALL NOT authorize citing a preferred database by name without a fetched URL.

#### Scenario: German food item prefers a fetched German source
- **WHEN** multiple pages are fetched for a common German food item, including a BLS page
- **THEN** the item's `source` preferentially cites the fetched BLS URL over other fetched database URLs

#### Scenario: Preference never invents an unfetched URL
- **WHEN** a preferred database would be relevant but no page from it was fetched
- **THEN** the item does not cite that database and its `source` reflects an actually fetched URL or is left blank

### Requirement: Multi-food queries split into maximal separate items
The system SHALL instruct the model to split a query describing multiple distinct foods into the maximum reasonable number of separate items, rather than merging them into one combined item.

#### Scenario: Combined food-and-drink query splits into two items
- **WHEN** the user's query is "coffee with milk"
- **THEN** the generated result contains two separate items — one for coffee and one for milk — rather than a single combined item

### Requirement: Generated text is unconditionally German
The system SHALL instruct the model to always generate item titles and descriptions in German, regardless of the language of the user's query, replacing the prior bilingual "reply in the query's language" behavior.

#### Scenario: English query still produces German output
- **WHEN** the user's query is written in English
- **THEN** the generated item titles and descriptions are in German

### Requirement: Source is an item-level-only concept
The system SHALL represent food-item source exclusively at the item level. The meal-level `source` field SHALL be removed from `NutritionAgentResult` (`app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt`), from the persisted `MealEntity` (`app/src/main/java/com/example/vocalorie/data/MealEntity.kt`, requiring Room migration `MIGRATION_4_5` to drop the column, bumping schema to version 5), and from the meal editor UI (`app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt`, removing the standalone meal-level source text field). Existing meal-level source values are discarded on migration; item-level `source` on `FoodItemEstimate` remains the sole source field and continues to be shown per item in the editor.

#### Scenario: Meal editor no longer shows a meal-level source field
- **WHEN** the user opens the meal editor for any meal
- **THEN** no standalone meal-level source input is present; each item still shows its own source

#### Scenario: Existing meals migrate without a meal-level source
- **WHEN** the app upgrades a database from schema version 4 to version 5
- **THEN** the `meals` table no longer has a `source` column, all other meal data is preserved, and any previously stored meal-level source values are gone (not migrated into any item)

#### Scenario: Item-level source remains visible and editable
- **WHEN** the user views or edits a meal with items that have source URLs
- **THEN** each item still displays and allows editing its own source, unaffected by the meal-level field's removal

### Requirement: Research tools default on when a Brave key is present
The system SHALL enable real Brave Search + WebFetch research by default when a Brave API key is configured, so that grounding is attempted without the user having to opt in per parse. When no Brave API key is configured, the system SHALL NOT make real network research calls and SHALL fall back silently to an LLM estimate without surfacing an error. Silent fallback SHALL apply only to the absence of a key; when a key is configured and grounding is attempted but fails, the system SHALL NOT treat that failure as an ordinary unsourced result and SHALL surface it (see "Grounding failures are reported, not silently absorbed").

#### Scenario: Key present enables grounding automatically
- **WHEN** a Brave API key is stored and the user parses a meal
- **THEN** the flow performs real search + fetch grounding without requiring a manual per-parse toggle

#### Scenario: No key falls back silently
- **WHEN** no Brave API key is configured and the user parses a meal
- **THEN** no real research network calls are made and the result is an LLM estimate with no error shown

#### Scenario: A configured but rejected key is not treated as absence
- **WHEN** a Brave API key is stored but the search service rejects it
- **THEN** the outcome is reported as a grounding failure rather than as the silent no-key fallback

### Requirement: Per-item sourced-versus-estimate indicator
The system SHALL visually distinguish sourced items from estimated items wherever a food item's source is surfaced. An item whose `source` is a real fetched URL SHALL show a "sourced" indicator identifying the source domain; an item with a blank `source` SHALL show a subtle "estimate" indicator.

#### Scenario: Sourced item shows its domain
- **WHEN** an item has a non-blank fetched `source` URL
- **THEN** the UI shows a sourced indicator naming the source domain and remains tappable to open the page

#### Scenario: Estimated item is marked as an estimate
- **WHEN** an item has a blank `source`
- **THEN** the UI shows a subtle "estimate" indicator and no clickable link

### Requirement: Grounding failures are reported, not silently absorbed
The system SHALL, when grounding is attempted and fails, retain diagnostic information about the failure and surface to the user that the estimate is ungrounded because grounding failed. A grounding failure SHALL NOT be discarded, and SHALL NOT be presented as an estimate that simply found no sources.

#### Scenario: A failed grounding pass is visible on the result
- **WHEN** grounding is attempted and fails for any reason
- **THEN** the resulting estimate carries a warning that grounding failed, distinguishing it from an estimate that ran grounding successfully and found nothing

#### Scenario: Failure detail is retained for diagnosis
- **WHEN** a grounding failure occurs
- **THEN** the underlying error is retained in diagnostic output rather than discarded

### Requirement: A research response is validated before its content is used
The system SHALL check the transport-level outcome of every research request before interpreting its body. A response that does not indicate success SHALL be reported as a failure and SHALL NOT be converted into an empty or absent result, so that an authentication, authorization, or rate-limit response is never presented to the model as a legitimate "no results found".

#### Scenario: A rejected search request is a failure, not an empty result
- **WHEN** a search request returns an unsuccessful status such as unauthorized or rate-limited
- **THEN** the tool reports a failure and does not report that the search returned no snippets

#### Scenario: A genuinely empty result is still reported as empty
- **WHEN** a search request succeeds and legitimately contains no usable snippets
- **THEN** the tool reports that no snippets were found, distinct from a failure

#### Scenario: A malformed successful response is a failure
- **WHEN** a search request succeeds but its body cannot be parsed into the expected structure
- **THEN** the tool reports a failure rather than silently reporting no results

### Requirement: Fetched content is bounded before it is read
The system SHALL bound the amount of fetched content it reads into memory, and SHALL reject a fetch target whose content type is not text-like, before reading the body. Because the fetch target is chosen by the model rather than the user, the system SHALL NOT read an unbounded response body into memory in order to retain only a truncated portion of it.

#### Scenario: An oversized page does not get fully read
- **WHEN** the model requests a fetch of a page far larger than the retained excerpt limit
- **THEN** only up to the bounded amount is read, and the remainder is never loaded into memory

#### Scenario: A non-text target is rejected before download
- **WHEN** the model requests a fetch of a target whose content type is not text-like
- **THEN** the fetch is rejected without downloading the body

### Requirement: Fetch targets are validated on every hop
The system SHALL apply its fetch-safety validation to each target it actually retrieves, including every redirect hop, and SHALL fail closed when it cannot determine whether a target is permitted. A target whose address cannot be resolved SHALL be rejected rather than allowed.

#### Scenario: A redirect to a disallowed address is rejected
- **WHEN** a permitted public URL redirects to a loopback, private, or link-local address
- **THEN** the fetch is rejected rather than following the redirect

#### Scenario: An unresolvable host is rejected
- **WHEN** the safety check cannot resolve the target host's addresses
- **THEN** the fetch is rejected, rather than the check passing because no disallowed address was observed

### Requirement: Fetched source URLs are recorded without loss
The system SHALL record the set of genuinely fetched URLs in a way that remains correct when fetches complete concurrently, so that a successfully fetched page is never omitted from the fetched-URL set and a legitimate item source is never discarded as unverified because of concurrent recording.

#### Scenario: Concurrent fetches all register their URLs
- **WHEN** several fetches complete concurrently during one grounding pass
- **THEN** every successfully fetched URL is present in the fetched-URL set

#### Scenario: A legitimate source survives concurrent grounding
- **WHEN** an item cites a URL that was genuinely fetched during a pass in which fetches overlapped
- **THEN** that item retains its source rather than having it blanked as unverified

### Requirement: Estimate requests are bounded in time and retried on transient failure
The system SHALL apply a request timeout to the nutrition-estimation call and SHALL retry a bounded number of times on transient failures such as rate-limiting or server errors. An unresponsive estimation request SHALL NOT leave the capture flow in a loading state indefinitely.

#### Scenario: An unresponsive request terminates
- **WHEN** the estimation request receives no response within the configured timeout
- **THEN** the request fails with a reported error and the capture flow leaves its loading state

#### Scenario: A transient failure is retried
- **WHEN** the estimation request fails with a rate-limit or server error
- **THEN** it is retried up to the configured bound before being reported as a failure

### Requirement: Failure classification inspects the whole failure chain
The system SHALL classify an estimation failure into its user-facing message by examining the entire chain of underlying causes, not only the outermost failure. A recognizable condition such as a rejected API key SHALL produce its specific message even when it is wrapped by intermediate framework errors.

#### Scenario: A wrapped authentication failure is classified correctly
- **WHEN** an estimation request fails because the API key was rejected, and that failure is wrapped by intermediate errors
- **THEN** the user sees the specific rejected-key message rather than a generic wrapper message

