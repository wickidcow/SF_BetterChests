<div align="center">

# 📦 BetterChests — Slimefun Legacy

**High-capacity drawers and portable storage, preserved for modern Slimefun servers.**

![Slimefun Legacy](https://img.shields.io/badge/Slimefun-Legacy-6bd425?style=for-the-badge)
![Paper 26.2](https://img.shields.io/badge/Paper-26.2-blue?style=for-the-badge)
![License: MIT](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)
![Maintained for AlbionMC.com](https://img.shields.io/badge/Maintained%20for-albionmc.com-7b68ee?style=for-the-badge)

</div>

> [!IMPORTANT]
> BetterChests is an **unofficial Slimefun Legacy maintenance fork**. It is developed and maintained for use on **albionmc.com** and is not intended to replace or erase the original project or its author.

## 🧰 What does BetterChests do?

BetterChests provides **large-capacity Slimefun drawer/storage blocks** designed to hold very large quantities of one item while remaining usable by players and Slimefun Cargo.

Player controls remain simple:

- **Right-click while holding an item** — deposit matching items.
- **Right-click with an empty main hand** — withdraw up to one normal stack.
- **Slimefun Cargo Input/Output Nodes** — automate drawer insertion and extraction.
- **Break and replace a drawer** — portable drawer data is preserved when possible.

The drawer inventory itself does not open, and vanilla hoppers do not directly access drawers; those behaviors are intentional parts of the original design.

## 🛡️ Slimefun Legacy maintenance

This fork modernizes the old SfBetterChests code for current Paper and Slimefun Legacy while protecting existing `BC_*` item IDs and stored data wherever possible.

Notable maintenance work includes:

- persistent drawer contents through Slimefun block data;
- migration/recovery support for older Dev-16 drawer metadata and display entities;
- portable drawer contents stored safely in item persistent data;
- safe handling of extremely large capacities with `long` arithmetic;
- protection against illegal oversized Bukkit item stacks;
- safer vanilla/custom item-name handling;
- display-entity repair and ownership tagging;
- withdrawal accounting that only removes items actually accepted by the player;
- safer IE-style storage withdrawal and serialization;
- Cargo delivery rollback instead of silently voiding failed deliveries;
- removal of the obsolete upstream self-updater so it cannot overwrite this maintenance build.

See `BUILDING.md`, `docs/MIGRATION.md`, and `docs/VALIDATION.md` for the detailed build, upgrade, and validation notes.

## ⚠️ Upgrade safety

1. Stop the server completely.
2. Back up the world, Slimefun data, and affected plugin data.
3. Remove the previous BetterChests JAR; never run two copies together.
4. Install the new JAR and perform a clean startup.
5. Visit several old drawers and verify item/count displays and contents.
6. Test breaking and replacing a low-value filled drawer before trusting production storage.

Legacy data that was already destroyed before this fork is installed cannot be recreated from nothing.

## ❤️ Credits & project lineage

- **lijinhong11** — creator/maintainer of **SfBetterChests**, the project this repository directly forks.
- **lijinhong11/SfBetterChests** — original and immediate upstream repository for this fork.
- **Slimefun developers and contributors** — for the storage, item, Cargo, and addon APIs used by BetterChests.
- **wickidcow / Slimefun Legacy** — current compatibility, migration, safety, and maintenance work for modern servers and albionmc.com.

The original code and project history remain credited to their respective authors. This repository is a maintenance fork, not a claim of original authorship.

## 📜 License

BetterChests is distributed under the **MIT License** contained in `LICENSE`, including the existing **2024 lijinhong11** copyright notice.

Copies or substantial portions of the software must retain the copyright and permission notice required by the MIT License. The software is provided **“AS IS”**, without warranty, as stated in that license.

> [!NOTE]
> BetterChests is **not GNU GPLv3**. Its repository license is MIT, so this README intentionally preserves the actual upstream license rather than applying the GPL wording used by many other Slimefun addons.

## ⚖️ Independence & trademark notice

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

BetterChests, Slimefun Legacy, and this repository are independent community projects. They are not sponsored, endorsed, approved, or operated by Mojang Studios or Microsoft. Minecraft-related names, brands, and assets remain the property of their respective rights holders.

This fork is also not represented as an official release of lijinhong11, the original Slimefun developers, or any other upstream project unless those parties explicitly say otherwise.

---

<div align="center">

**📦 More storage. Less chaos. Keep the legacy intact.**

</div>
