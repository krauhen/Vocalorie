---
description: Overlapping FABs, a clipped chart axis label and mixed-language date labels on the entries screen.
tags: [backlog, bugs, defect, ui, visual-baseline]
---

# B6: Visual clipping and mixed-language date labels

**Status:** investigated
**Source:** on-device observation during the 2026-08-20 backlog investigation (not a personal note)
**Likely capability:** `openspec/specs/visual-baseline/spec.md` plus
`openspec/specs/ui-responsiveness/spec.md` (guess, not a commitment)

## What was observed
Samsung Galaxy S23 (SM_S911B), installed debug build of 2026-07-31, entries screen:

- The settings gear FAB overlaps the meal card's energy line, hiding its leading digits
  (`…2 kJ / 500 kcal`). The Add FAB overlaps the macro line, cutting the Amount and Fat values.
  Content scrolls under both FABs with no bottom inset reserved for them.
- The "Calories over time" y-axis bottom label `0` is vertically clipped by the card edge.
- Heatmap range labels mix locales in one row: `18 Mai` (German) on the left, `20 Aug.` on the
  right.

## Open questions
- Is the FAB overlap a missing `contentPadding` on the list, or FAB placement outside the
  scaffold's inset handling?
- Are the date labels formatted through two different formatters, or one formatter with an
  inconsistent locale/pattern?
- Does the app intend a fixed German locale, or the system locale?

## Notes
Screenshots only; not reproduced on another device or window size, and no source investigation
done yet. Lowest severity of the captured defects — cosmetic, no data effect.
