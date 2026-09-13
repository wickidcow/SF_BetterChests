#!/usr/bin/env python3
"""Verify BetterChests Doctor reconciliation stays loaded-only and non-destructive."""

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

require("public static Inspection inspect(Block block)" in storage,
        "drawer storage must expose a read-only inspection path")
require("LEGACY_RECOVERABLE" in storage and "LEGACY_PARTIAL" in storage
        and "LEGACY_WAITING_FOR_ENTITIES" in storage,
        "legacy drawer recovery states are incomplete")
require("CURRENT_CORRUPT" in storage and "CURRENT_INCONSISTENT" in storage,
        "modern corrupt/inconsistent drawer states must be distinguished")
require("public static boolean migrateLegacyIfRecoverable" in storage,
        "positive-evidence legacy migration gate is missing")
require("inspection.status() != InspectionStatus.LEGACY_RECOVERABLE" in storage,
        "legacy migration must require an exact recoverable classification")
require("public static boolean markCurrentVersionIfSafe" in storage,
        "safe version-marker reconciliation is missing")

read_start = storage.find("public static DrawerData read(Block block)")
read_end = storage.find("public static Inspection inspect(Block block)")
read_body = storage[read_start:read_end] if read_start >= 0 and read_end > read_start else ""
require("case LEGACY_RECOVERABLE" in read_body,
        "normal drawer reads must retain positive-evidence lazy migration")
reject("write(block, DrawerData.empty())" in read_body,
       "normal drawer reads must never seal unresolved/corrupt state as an empty v2 record")
require("preserving the raw record" in read_body,
        "corrupt/inconsistent drawer reads must explicitly preserve raw evidence")

require("world.getLoadedChunks()" in doctor and "chunk.getTileEntities()" in doctor,
        "Doctor must restrict drawer discovery to already-loaded chunks/tile entities")
reject("loadChunk(" in doctor or "getChunkAt(" in doctor,
       "Doctor must not force-load chunks")
require("DrawerStorage.inspect(block)" in doctor,
        "Doctor scan must use the read-only drawer inspection path")
require("DrawerStorage.migrateLegacyIfRecoverable(block)" in doctor,
        "Doctor repair must use the positive-evidence legacy migration gate")
require("DrawerStorage.markCurrentVersionIfSafe(block)" in doctor,
        "Doctor repair must use the safe version-marker gate")
reject("DrawerStorage.clear(" in doctor or "DrawerStorage.write(" in doctor,
       "Doctor must not directly clear or rewrite drawer contents")
require("CURRENT_CORRUPT" in doctor and "preserved unchanged" in doctor,
        "Doctor must report corrupt drawer data without destructive repair")
require("LEGACY_PARTIAL" in doctor and "no migration attempted" in doctor,
        "partial legacy state must remain manual-only")
require("LEGACY_WAITING_FOR_ENTITIES" in doctor and "did not force-load" in doctor,
        "unloaded legacy display evidence must remain deferred")

require('Class.forName(DOCTOR_API, false, loader)' in bridge,
        "Slimefun Legacy Doctor integration must remain reflective/optional")
reject("import io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor" in bridge,
       "BetterChests must not gain a hard dependency on Legacy Doctor APIs")
require("BetterChestsDoctor.run(repair)" in bridge,
        "Doctor bridge must delegate scan/repair to BetterChests reconciliation")
require("unregisterAll(plugin)" in bridge,
        "Doctor services must be unregistered on plugin disable")

require("LegacyDoctorBridge.register(this);" in plugin,
        "BetterChests must register the optional Doctor bridge on enable")
require("LegacyDoctorBridge.unregister(this);" in plugin,
        "BetterChests must unregister Doctor services on disable")

if ERRORS:
    print("BetterChests Doctor reconciliation verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("BetterChests Doctor reconciliation verification passed.")
