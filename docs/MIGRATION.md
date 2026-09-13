# Migration from Dev-16

1. Stop Paper completely.
2. Back up the entire server. At minimum preserve all worlds, `plugins/Slimefun/`, and `plugins/BetterChests/`.
3. Remove the old BetterChests JAR. Leave only one JAR whose plugin name is `BetterChests`.
4. Install the new JAR and start the server normally.
5. On Slimefun Legacy, run `/sf doctor addons scan`. This is read-only and reports BetterChests drawer/IE-storage state alongside any other registered addon Doctor providers.
6. Review every BetterChests `manual` or `deferred` entry before repair. These entries are intentionally not guessed or overwritten.
7. If the scan reports only repairs you are comfortable applying, stop the server and make another offline backup, restart, then run `/sf doctor addons repair confirm`. This command runs **all registered addon Doctor providers**, not BetterChests alone.
8. Visit representative old drawers. Recoverable Dev-16 drawers are persisted in the v2 block schema and their tagged display entities are recreated. A drawer whose legacy evidence is unavailable remains unresolved instead of being stamped empty.
9. Test breaking and replacing one filled low-value drawer, then repeat the read-only Doctor scan.
10. Keep the backup until all storage has survived at least one full restart.

## What Doctor will repair automatically

BetterChests Doctor repairs only states it can prove safe:

- a Dev-16 drawer when both stored-item identity and an exact positive count are recoverable;
- a valid v2 drawer item/count pair that is missing only the v2 schema marker.

It does **not** clear or rewrite partial/corrupt drawers, unknown drawer schema versions, or ambiguous data.

## IE-style storage units

IE-style storage retains its historical block schema (`stored` count plus display-slot item identity). There is no ID/schema conversion for these blocks.

Doctor checks loaded IE storage for malformed, negative, or over-capacity counts and for count/display inconsistencies. These findings are diagnostic-only. BetterChests does not clamp or zero them through Doctor.

When the live cache sees unsafe persisted IE state, the unit is frozen instead of normalizing it: ticking and transfers stop, and breaking the unit is blocked so the original BlockStorage/menu evidence can be investigated or restored from backup. If a positive count has lost its display identity but the output slot still proves the item type, BetterChests may rebuild only the display identity from a clone; the actual output stack is left untouched.

Legacy state can only be recovered when surviving metadata, BlockStorage, menu data, or legacy display entities still contain enough evidence. Data that was already erased before installing the fork cannot be reconstructed safely.
