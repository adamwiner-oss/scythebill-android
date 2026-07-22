# IOC / extended-taxonomy avoidance

The Android app supports only the built-in Clements taxonomy plus
whatever "extended" taxonomies (e.g. a mammal checklist) are embedded
directly inside the loaded `.bsxm` file — it does not support IOC or
other *mapped* taxonomies (see the master port plan). The classes below
must never be constructed from Android-side code. This doc records why
each one is currently safe to avoid, so future sub-plans can check new
call sites against it before wiring anything up.

## Classes that must never be constructed

- **`MappedTaxonomy`** (`model/.../taxa/MappedTaxonomy.kt`)
- **`MappedTaxonomyFactory`**, **`MappedTaxonomyImplParser`** — private
  nested classes inside `model/.../xml/XmlTaxonImport.kt`
- **`ClementsAndIocChecklist`** (`model/.../checklist/ClementsAndIocChecklist.java`)
- **`ExtendedTaxonomyChecklists`** (`model/.../checklist/ExtendedTaxonomyChecklists.java`)

## Classes that are now exercised (not avoided)

- **`ExtendedTaxonomyNodeParser`** (`model/.../xml/ExtendedTaxonomyNodeParser.java`)
- **`ExtendedTaxonomyParsing`** (`model/.../xml/ExtendedTaxonomyParsing.java`)

These parse the `<taxonomy>` elements embedded in a `.bsxm` file via
`XmlReportSetImport` (reused verbatim by Android), populating
`ReportSet.extendedTaxonomies()`. This always ran regardless of
Android-side wiring; the "extended taxonomy" sub-plan is what makes
Android actually *consume* the result — via `ActiveTaxonomyStore`,
letting the hamburger menu's "Select taxonomy" entry switch browsing/
search/reports to one of these taxonomies. Their `<checklists>` element
branch (location-status/checklist parsing, gated by
`ExtendedTaxonomyNodeParser.canParseChecklists(context)`) remains
unused — Android doesn't call `startChecklistParsing`/
`endChecklistParsing`, so checklist/location-status data on an embedded
extended taxonomy is parsed away but never surfaced.

## Why the mapped-taxonomy classes are still safe to avoid

`XmlTaxonImport` has two separate entry points:

- `importTaxa(...)` — parses a plain `Taxonomy`. This is the entry
  point Sub-plan 3 (taxonomy loading) uses. It never touches
  `MappedTaxonomy`/`MappedTaxonomyFactory`/`MappedTaxonomyImplParser`.
- `importMappedTaxa(in, base)` — the only entry point that reaches the
  mapped-taxonomy classes above. Nothing in the Android v1 feature set
  calls this. Do not add a call site for it.

`ClementsAndIocChecklist` and `ExtendedTaxonomyChecklists` are reached
from `Checklists`/`TransposedChecklists` (life-list/checklist
comparison) and from `:ui` report panels — not from
`XmlTaxonImport`/`Taxonomy`/`TaxonomyImpl`, and not from
`ExtendedTaxonomyNodeParser`'s unused `<checklists>` branch either. The
v1 query field subset (LOCATION/DATE/PHOTOGRAPHED, Sub-plan 5) does not
require checklist comparison, for either the base or an extended
taxonomy, so these should stay unreached.

## `TaxonomyMappings` — constructed, but with empty loader sets

`TaxonomyMappings` (`model/.../taxa/TaxonomyMappings.kt`) is a required
constructor argument of `XmlReportSetImport` (Sub-plan 4), used solely
to remap sightings tagged with an old taxonomy version
(`XmlReportSetImport`'s `mappings.getTracker(taxonomyId, ...)` branch,
only triggered when a sighting's stored `taxonomyId` differs from the
current taxonomy). Since Android requires every `.bsxm` file to already
be at the current taxonomy version (no upgrader support — see the
master plan's Decision 7), that branch is provably unreachable, so
`AppContainer.taxonomyMappings()` passes empty sets instead of porting
the ~15 real `TaxonomyMappingLoader`/`IocUpgradeLoader` instances that
live in the desktop-only `ApplicationModule` (`app/.../ApplicationModule.java`
in the `birdlist` repo).

## Other notes

- `TaxonImpl.equals()`/`hashCode()` (`model/.../taxa/TaxonImpl.kt`)
  compare only `type` + `name` (+ `parent` for `equals`) — not
  `id`/`accountId`/`conceptId`. Two distinct taxa with the same
  type/name/parent would be considered equal. Worth remembering before
  putting taxa in sets or map keys in later sub-plans.
