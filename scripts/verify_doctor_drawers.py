#!/usr/bin/env python3
"""Verify BetterChests drawer migration remains loaded-only and non-destructive."""

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ERRORS: list[str] = []


def read(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        ERRORS.append(f"missing required file: {path}")
        return ""
    return file.read_text(encoding="utf-8")


def require(value: bool, message: str) -> None:
    if not value:
        ERRORS.append(message)


def reject(value: bool, message: str) -> None:
    if value:
        ERRORS.append(message)


storage = read("src/main/java/me/mmmjjkx/betterChests/storage/DrawerStorage.java")
doctor = read("src/main/java/me/mmmjjkx/betterChests/diagnostics/BetterChestsDoctor.java")
bridge = read("src/main/java/me/mmmjjkx/betterChests/diagnostics/LegacyDoctorBridge.java")
plugin = read("src/main/java/me/mmmjjkx/betterChests/BetterChests.java")

require('ITEM_KEY = "bc_drawer_item_v2"' in storage and 'COUNT_KEY = "bc_drawer_count_v2"' in storage,
        "v2 drawer persistence keys must remain stable")
require('Status.LEGACY_RECOVERABLE' in storage and 'Status.CORRUPT_MODERN' in storage,
        "drawer inspection must distinguish recoverable legacy and corrupt modern state")
require('block.getChunk().isEntitiesLoaded()' in storage,
        "legacy display recovery must not force entity/chunk loading")
require('migrateLegacyIfRecoverable' in storage,
        "Doctor-safe positive legacy migration entry point is missing")
require('inspection.status() != Status.LEGACY_RECOVERABLE' in storage,
        "legacy migration must fail closed unless recovery is positively proven")
require('leaving the stored evidence untouched' in storage,
        "corrupt v2 reads must explicitly preserve evidence")
reject('Could not decode drawer data' in storage and 'write(block, DrawerData.empty());' in storage[storage.find('Could not decode drawer data'):],
       "corrupt drawer decode must not clear persistent evidence")

require('world.getLoadedChunks()' in doctor,
        "BetterChests Doctor must scan only already-loaded chunks")
require('chunk.getTileEntities()' in doctor,
        "BetterChests Doctor must inspect already-loaded tile entities")
require('BlockStorage.check(block)' in doctor and 'slimefunItem instanceof SimpleDrawer' in doctor,
        "Doctor scan must remain scoped to registered BetterChests drawer blocks")
require('DrawerStorage.inspect(block)' in doctor,
        "Doctor scan must classify drawer state read-only before repair")
require('repair && DrawerStorage.migrateLegacyIfRecoverable(block)' in doctor,
        "Doctor repair must migrate only positively recoverable legacy drawers")
require('case CORRUPT_MODERN' in doctor and 'left untouched' in doctor,
        "corrupt v2 drawers must be reported and left untouched")
reject('loadChunk' in doctor or 'getChunkAtAsync' in doctor,
       "BetterChests Doctor must never force-load chunks")
reject('DrawerStorage.clear(' in doctor,
       "Doctor must never clear drawer storage as a repair action")

require('"io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor"' in bridge,
        "optional reflective Slimefun Legacy AddonDoctor bridge is missing")
require('reportClass.getConstructor(' in bridge and 'List.class' in bridge,
        "AddonDoctorReport reflective constructor contract is missing")
require('case "runDoctor"' in bridge and 'BetterChestsDoctor.run(repair)' in bridge,
        "Addon Doctor must delegate scan/repair to BetterChestsDoctor")
require('LegacyItemSchemaProbe' in bridge and 'LegacyItemSchemaMigrator' in bridge,
        "existing portable IE Storage schema migration must remain registered")
require('Bukkit.getServicesManager().unregisterAll(plugin)' in bridge,
        "Doctor services must unregister cleanly")
require('LegacyDoctorBridge.register(this)' in plugin and 'LegacyDoctorBridge.unregister(this)' in plugin,
        "BetterChests lifecycle must register and unregister the Doctor bridge")

if ERRORS:
    print("BetterChests Doctor verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("BetterChests Doctor verification passed.")
