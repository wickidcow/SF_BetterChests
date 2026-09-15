#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(path):
    p = root / path
    if not p.is_file():
        errors.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(ok, msg):
    if not ok:
        errors.append(msg)

def reject(ok, msg):
    if ok:
        errors.append(msg)

cache = read("src/main/java/me/mmmjjkx/betterChests/items/chests/ie/IEStorageCache.java")
unit = read("src/main/java/me/mmmjjkx/betterChests/items/chests/ie/IEStorageUnit.java")
inspector = read("src/main/java/me/mmmjjkx/betterChests/diagnostics/IEStorageDoctorInspector.java")
doctor = read("src/main/java/me/mmmjjkx/betterChests/diagnostics/BetterChestsDoctor.java")

require("boolean persistentStateSafe = true" in cache, "IE cache needs explicit persistent-state safety state")
require('markPersistentStateUnsafe("stored count is malformed' in cache, "malformed counts must freeze")
require('markPersistentStateUnsafe("stored count is negative' in cache, "negative counts must freeze")
require("exceeds capacity" in cache, "over-capacity counts must freeze")
require("if (!persistentStateSafe)" in cache, "unsafe cache operations must be guarded")
require("recoveredIdentity = output.clone()" in cache, "output identity recovery must clone evidence")
require("the output stack was left untouched" in cache, "output recovery must document non-consumption")
reject("menu.replaceExistingItem(OUTPUT_SLOT, null)" in cache, "identity recovery must not consume output items")
require("return persistentStateSafe && this.amount == 0" in cache, "unsafe caches must not masquerade as empty")
require("if (!cache.isPersistentStateSafe())" in unit, "block break must reject unsafe caches")
require("e.setCancelled(true)" in unit, "unsafe block break must be cancelled")

for token in ["MALFORMED_COUNT", "NEGATIVE_COUNT", "OVER_CAPACITY", "MISSING_DISPLAY_WITH_OUTPUT_EVIDENCE", "ZERO_COUNT_WITH_DISPLAY"]:
    require(token in inspector, f"Doctor inspector missing {token}")
reject("BlockStorage.addBlockInfo" in inspector, "IE Doctor inspector must remain read-only")
reject("replaceExistingItem" in inspector, "IE Doctor inspector must not mutate menus")
require("slimefunItem instanceof IEStorageUnit unit" in doctor, "Addon Doctor must inspect IE storage units")
require("IEStorageDoctorInspector.inspect(block, unit)" in doctor, "Addon Doctor must use read-only IE inspector")

if errors:
    print("BetterChests IE storage safety verification failed:")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)
print("BetterChests IE storage fail-closed invariants verified.")
