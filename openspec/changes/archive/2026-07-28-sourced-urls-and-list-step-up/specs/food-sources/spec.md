## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Research tools default on when a Brave key is present
The system SHALL enable real Brave Search + WebFetch research by default when a Brave API key is configured, so that grounding is attempted without the user having to opt in per parse. When no Brave API key is configured, the system SHALL NOT make real network research calls and SHALL fall back silently to an LLM estimate without surfacing an error.

#### Scenario: Key present enables grounding automatically
- **WHEN** a Brave API key is stored and the user parses a meal
- **THEN** the flow performs real search + fetch grounding without requiring a manual per-parse toggle

#### Scenario: No key falls back silently
- **WHEN** no Brave API key is configured and the user parses a meal
- **THEN** no real research network calls are made and the result is an LLM estimate with no error shown

### Requirement: Per-item sourced-versus-estimate indicator
The system SHALL visually distinguish sourced items from estimated items wherever a food item's source is surfaced. An item whose `source` is a real fetched URL SHALL show a "sourced" indicator identifying the source domain; an item with a blank `source` SHALL show a subtle "estimate" indicator.

#### Scenario: Sourced item shows its domain
- **WHEN** an item has a non-blank fetched `source` URL
- **THEN** the UI shows a sourced indicator naming the source domain and remains tappable to open the page

#### Scenario: Estimated item is marked as an estimate
- **WHEN** an item has a blank `source`
- **THEN** the UI shows a subtle "estimate" indicator and no clickable link
