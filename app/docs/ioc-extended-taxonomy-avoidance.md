# IOC / extended-taxonomy avoidance

The Android app supports only the built-in Clements taxonomy (v1 scope
excludes IOC and other mapped/extended taxonomies — see the master
port plan). The classes below must never be constructed from
Android-side code. This doc records why each one is currently safe to
avoid, so future sub-plans can check new call sites against it before
wiring anything up.

## Classes that must never be constructed

- **`MappedTaxonomy`** (`model/.../taxa/MappedTaxonomy.kt`)
- **`MappedTaxonomyFactory`**, **`MappedTaxonomyImplParser`** — private
  nested classes inside `model/.../xml/XmlTaxonImport.kt`
- **`ClementsAndIocChecklist`** (`model/.../checklist/ClementsAndIocChecklist.java`)
- **`ExtendedTaxonomyChecklists`** (`model/.../checklist/ExtendedTaxonomyChecklists.java`)
- **`ExtendedTaxonomyNodeParser`** (`model/.../xml/ExtendedTaxonomyNodeParser.java`)
- **`ExtendedTaxonomyParsing`** (`model/.../xml/ExtendedTaxonomyParsing.java`)

## Why they're safe to avoid

`XmlTaxonImport` has two separate entry points:

- `importTaxa(...)` — parses a plain `Taxonomy`. This is the entry
  point Sub-plan 3 (taxonomy loading) uses. It never touches
  `MappedTaxonomy`/`MappedTaxonomyFactory`/`MappedTaxonomyImplParser`.
- `importMappedTaxa(in, base)` — the only entry point that reaches the
  mapped-taxonomy classes above. Nothing in the Android v1 feature set
  calls this. Do not add a call site for it.

Both entry points share `TaxonomyImplParser`/`SpeciesParser`, which has
a `<checklists>` element branch gated by
`ExtendedTaxonomyNodeParser.canParseChecklists(context)`. That flag is
only ever set true by `ExtendedTaxonomyNodeParser.startChecklistParsing(context)`
— and no caller of `startChecklistParsing`/`endChecklistParsing` exists
anywhere in the `birdlist` repo today. This branch is structurally dead
code repo-wide, not just avoided by Android-side convention; if a
future desktop change adds a caller, re-audit this doc.

`ClementsAndIocChecklist` and `ExtendedTaxonomyChecklists` are reached
from `Checklists`/`TransposedChecklists` (life-list/checklist
comparison) and from `:ui` report panels — not from
`XmlTaxonImport`/`Taxonomy`/`TaxonomyImpl`. The v1 query field subset
(LOCATION/DATE/PHOTOGRAPHED, Sub-plan 5) does not require checklist
comparison, so these should stay unreached.

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
