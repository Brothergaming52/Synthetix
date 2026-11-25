<div align="center">

# ✦ Synthetix (synth) ✦

**Ultra-Modular Core Engine for Custom Items, Blocks, Entities & Mechanics on Paper & Folia 1.20.6+**

[![Synthetix Version 1.0](https://img.shields.io/badge/version-1.0-blueviolet?style=for-the-badge)](https://discord.gg/r5YXqgrGya)
[![Minecraft 1.20.6 to 1.21+](https://img.shields.io/badge/minecraft-1.20.6%20--%201.21+-green?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0id2hpdGUiIGQ9Ik0xMiAyQzYuNSAyIDIgNi41IDIgMTJzNC41IDEwIDEwIDEwLTQuNSAxMC0xMFMxNy41IDIgMTIgMnoiLz48L3N2Zz4=)](https://papermc.io/)
[![Java 21 LTS](https://img.shields.io/badge/java-21%20LTS-orange?style=for-the-badge)](https://www.oracle.com/java/)
[![Discord Community](https://img.shields.io/discord/1456137607569346739?label=Discord&style=for-the-badge&logo=discord&logoColor=white&color=5865F2)](https://discord.gg/r5YXqgrGya)
[![bStats Live Metrics](https://img.shields.io/badge/bStats-live-blue?style=for-the-badge)](https://bstats.org/plugin/bukkit/Synthetix/35485)
[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20Me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/brothergaming52)

**Custom Items · Data Components · 10x Recipe Engine · Custom Blocks · Bedrock Entities · Action Triggers · Particle Auras · Armor Sets**
<br>
Pure modern Paper & Folia API. Native multi-threaded regional ticking. Zero NMS reliance. Zero client mods required.

---

*Synthetix is an enterprise-grade core engine engineered for Minecraft Paper, Purpur, and Folia 1.20.6+ (Java 21 LTS). It unifies custom item creation with modern Data Components, virtual custom blocks with GPU SLERP interpolation, animated Bedrock entity rigs with keyframed animations, a 10-type universal recipe engine, interactive action triggers, and multi-driver database storage into one cohesive, high-performance ecosystem.*

</div>

> [!IMPORTANT]
> **Modrinth Status Notice:** Synthetix is currently **pending approval on Modrinth**. To download the latest builds, get setup guides, report bugs, or access community addons, please join our **[Discord Community](https://discord.gg/r5YXqgrGya)**!

---

## 🧩 About Synthetix

Synthetix (`synth`) provides a modular, high-performance foundation for server owners and developers to build deeply customized gameplay without fighting vanilla limitations or installing client mods.

Built from the ground up for modern Paper & Folia **Data Components (1.20.5+ / 1.20.6 / 1.21+)**, Synthetix eliminates the need to run separate, bloated plugins for recipes, mob drops, loot tables, resource pack hosting, custom blocks, and entity animations. Everything is editable in real-time through an intuitive in-game canvas GUI or through a clean, thread-safe developer API with native multi-threaded regional scheduling.

---

## ⚡ Features Overview

| | Feature | Description |
|:---:|---|---|
| ⚡ | **Folia Multi-Threading** | Native support for Folia multi-threaded regional ticking via universal PlatformScheduler. Entity, block, and visual mutations dispatch authoritatively to their owning regional threads with zero cross-region deadlocks. |
| 📦 | **Modern Data Components** | Native 1.20.5+ components: glint override, custom max durability, rarity, food & consumables with cooldowns, armor trims, leather/potion RGB hex colors, and equippable non-armor items. |
| 📜 | **Universal Recipe Engine** | 10 recipe types: Shaped, Shapeless, Furnace, Blast Furnace, Smoker, Campfire, Stonecutter, Smithing Transform, Anvil, and Brewing Stand with deterministic reload-safe IDs. |
| 📖 | **In-Game Recipe Viewer (JEI)** | Survival-friendly crafting guide (`/synth recipe <item>`) with animated ingredient grids and variant pagination. |
| 🧱 | **Dual-Layer Custom Blocks** | Vanilla physical proxy blocks combined with visual `ItemDisplay` entities, GPU SLERP interpolation, multi-part animated elements (gears, fans), authentic break particles, and natural ore generation. |
| 🐉 | **Tri-Layer Bedrock Entities** | Direct import of `.bbmodel` and `.geo.json` / `.animation.json` models with forward kinematics skeletal rigging, anatomical hitboxes, keyframed animations, and 1-click in-world live preview. |
| 🧠 | **Entity Behavior Chart** | Priority-based AI condition matrix (range, health %, cooldowns) with action nodes (melee strike, projectiles, leap dash, particle bursts, blink teleport, minion summoning). |
| ⚡ | **Action & Trigger Engine** | 15+ interaction triggers (`on-right-click`, `on-hit-entity`, `on-break-block`, `on-consume`, etc.) with customizable actions, sounds, particles, and cooldown groups. |
| ✨ | **Continuous Particle Auras** | Persistent particle trails and auras (Halo, Spiral, Footsteps, Orbiting Spheres, Wings, Bursts) with conditional triggers (mainhand, offhand, worn armor, sneaking, in combat). |
| 🛡️ | **Equipment Sets & Bonuses** | Group custom items into sets with tiered bonuses (2/4, 4/4) granting attribute modifiers, potion effects, and dynamic lore tooltips (`/synth sets`). |
| 🌍 | **World Distribution** | Configure custom mob drops (with looting multipliers), dungeon/structure loot table injections, and custom Villager/Wandering Trader merchant trades. |
| 📥 | **Universal Import / Export** | Organized auto-import drop folders (`import/items/`, `import/blocks/`, `import/entities/`) and one-click `.synthpack` / `.zip` archive exporting. |
| 🌐 | **Unified Resource Pack Pipeline** | Zero-port external injector bridges (Nexo, ItemsAdder, Oraxen, RSPM, CraftEngine) plus built-in HTTP server with public IP auto-detection and ZIP combiner. |
| 🗄️ | **Enterprise Storage & API** | Multi-driver data storage: SQLite, MySQL, MongoDB, and YAML with dedicated `getAddonDataStore()` for downstream plugins (e.g. CuriosPaper). |
| 🔄 | **Dynamic Item Modernizer** | Automatically modernizes legacy item stacks in player inventories when definitions are updated on the server. |
| 📱 | **Geyser & Bedrock Support** | Seamless compatibility with Bedrock players via Geyser/Floodgate, including custom 3-row chest GUIs for Anvil and Smithing operations. |

---

## 🛠️ In-Game Canvas & Editors

Synthetix puts item creation directly into your hands with a responsive, 6-row master canvas (`/synth create`, `/synth edit`, `/synth items`). The live preview in **slot 4** reflects changes to names, models, and components in real time—click it anytime to grant a working copy directly to your inventory.

Paired with `ChatPromptManager`, entering custom display names, lore lines, stack sizes, or hex colors is as natural as typing in chat, instantly returning you to your exact canvas upon completion. Downstream plugin integrations scale seamlessly across auto-paginated category rows, while `/synth items` provides players and administrators with a searchable, filterable catalog.

<div align="center">

![Synthetix In-Game Item Creation Canvas GUI](https://cdn.modrinth.com/data/gzvsgqmK/images/043105e87c71083c5d01e1d54eaf95bbfc5a4a16.png)

*The master 6-row item canvas with real-time live preview in slot 4*

</div>

---

## 🧩 Modern Data Components

Powered by Minecraft 1.20.5+ native **Data Components**, Synthetix unlocks deep item customization without NMS workarounds. Items natively support both 1.21 `item_model` identifiers and legacy CustomModelData for full backwards and forwards compatibility.

**Durability, Rarity & Visual Glint**  
Configure custom max durability to give a Netherite Dagger 3,500 strikes or limit a magical wand to 50 charges. Set native Minecraft rarities (`COMMON` through `EPIC`), or toggle the 3-state enchantment glint override (`Default`, `Forced Shiny`, `Forced Matte`) to make quest artifacts shine without enchants or keep stealth weapons clean.

**Food & Consumables Engine**  
Transform any item into an edible feast or potion draught. Define nutrition, saturation ratios, eat durations, and `can_always_eat` toggles. Built-in cooldown groups prevent potion spamming, while custom status effects apply instantly upon consumption with configurable amplifiers and probabilities.

**Cosmetics, Trims & Equippables**  
Equip custom backpacks, banners, and decorative headwear directly into armor slots (`HEAD`, `CHEST`, `LEGS`, `FEET`) complete with custom camera overlays and equip audio. Apply all 18 armor trim patterns across 10 materials, or dial in custom RGB hex codes for leather gear and potion bottles. Whenever an item definition is modified, `ItemUpdateManager` automatically synchronizes existing player inventory stacks in real time.

---

## 📜 Recipe Engine & JEI Viewer

Synthetix provides full in-game GUI builders and YAML configuration for all 10 Minecraft recipe types:

| Recipe Type | Station | Key Capabilities |
|---|---|---|
| `SHAPED` | Crafting Table | 3x3 crafting grid with automatic bounding box trimming |
| `SHAPELESS` | Crafting Table | 1 to 9 ingredient crafting in any arrangement |
| `FURNACE` | Furnace | Smelting recipes with custom cook time and experience yield |
| `BLAST_FURNACE` | Blast Furnace | High-temperature ore smelting |
| `SMOKER` | Smoker | High-speed food cooking |
| `CAMPFIRE` | Campfire | Outdoor slow-roast cooking |
| `STONECUTTER` | Stonecutter | 1-to-1 stone and block slicing |
| `SMITHING_TRANSFORM` | Smithing Table | Template + Base + Addition upgrades |
| `ANVIL` | Anvil | Left + Right ingredient combination with level cost rules |
| `BREWING` | Brewing Stand | Potion / ingredient combination recipes |

All recipes register with deterministic keys (`<itemId>_<type>`) that survive server restarts without console errors, and cleanly undiscover from player recipe books when removed.

Players can run `/synth recipe <item>` or Middle-Click any item in `/synth items` to open an interactive, read-only crafting guide with animated ingredient cycles and variant navigation.

<div align="center">

![Synthetix In-Game JEI Recipe Viewer GUI](https://cdn.modrinth.com/data/gzvsgqmK/images/d988ab7f953f5e71466511e9dbb35fb4ba7ffbd4.png)

*Interactive in-game JEI recipe viewer with animated ingredients and variant paging*

</div>

---

## 🧱 Custom Block Engine

Synthetix features a cutting-edge virtual custom block pipeline running entirely on vanilla clients with zero client-side mods or NMS.

**Dual-Layer Collision & Visuals**  
Solid collision, placement orientation, and player physics are handled by vanilla proxy blocks (`BARRIER`, `STRUCTURE_VOID`, `NOTE_BLOCK`, `SLAB`), while visuals are rendered by persistent `ItemDisplay` entities using custom models and CustomModelData. Large multiblocks ($2 \times 2$, $3 \times 3$, etc.) validate clearance before placement and safely clean up as a single cohesive unit when broken.

**60 FPS Mechanical Motion & World Gen**  
Composite machinery—such as the `GearBox` with multi-axis rotating shafts—executes smooth motion through server-side quaternion SLERP keyframes utilizing client GPU interpolation with zero network jitter. When mined, blocks sample their own textures (`Particle.ITEM`) for authentic break particles, respecting custom hardness, tool tiers, blast resistance, and fortune rules. Custom ores can even generate naturally across newly loaded chunks with configurable vein sizes, depths, and biome restrictions.

<div align="center">

![Synthetix Custom Block Creation and Editor GUI](https://cdn.modrinth.com/data/gzvsgqmK/images/0c91323f2bfaf06e0ce6f94a6afc98bebf6d46a6.png)

*Custom block editor for collision, hardness, and animated composite models*

<br>

![Custom 3D Animated Block Gameplay Demonstration](https://cdn.modrinth.com/data/gzvsgqmK/images/6b4c88a6e95d7ad21951c331b033fed21b674238.gif)

*In-game multi-axis animated blocks running at 60 FPS GPU SLERP*

</div>

---

## 🐉 Custom Entity Engine & Studio

Bring custom 3D mobs and bosses to life on vanilla clients with Synthetix's Tri-Layer Virtual Entity Engine.

**Tri-Layer Architecture & Skeletal Rigging**  
Server-side AI and pathfinding are driven by an invisible vanilla mob driver, while visual presentation is handled by virtual `ItemDisplay` bone hierarchies calculating forward kinematics ($M_{world} = M_{parent} \cdot T(pivot) \cdot M_{local}$) with GPU SLERP interpolation. Sub-voxel `Interaction` entities map precise anatomical hitboxes, enabling 2.0x headshots and localized damage zones.

**Bedrock Ingestion & In-World Animation Studio**  
Drop `.bbmodel` or Bedrock Geometry (`.geo.json`) and animation (`.animation.json`) files directly into `import/entities/`. Open `/synth entity` to inspect skeletal rigs, edit hitboxes, and preview animations (`idle`, `walk`, `attack`, `cast`) in real-time in the live world.

**Entity Behavior Chart AI**  
Replace rigid AI goals with a flexible condition-and-action matrix. Configure engagement ranges, health thresholds (for phase changes and rage states), attack cadences, and combat actions—including melee strikes, projectile barrages (fireballs, arrows, skulls), leap dashes, particle bursts, blink teleports, and minion summoning.

<div align="center">

![Synthetix Custom Entity Creation and Rigging GUI](https://cdn.modrinth.com/data/gzvsgqmK/images/323480ced095e1db89b98f2b6035e9d4ffce3012.png)

*Entity browser and skeletal rig inspector with anatomical hitboxes*

<br>

![Custom Animated Bedrock Entity Live In-World Preview](https://cdn.modrinth.com/data/gzvsgqmK/images/8f24791065d06c86c99c8f208ae1355d69ba7b11.gif)

*Real-time skeletal animation preview in the live world*

</div>

---

## ⚡ Actions, Triggers & Auras

Bring items to life with dynamic mechanics and visual effects without writing Java code.

**Rich Interaction Triggers**  
Bind custom abilities to over 15 built-in interaction events:
`Right/Left Click` · `Sneak Combos` · `Hit & Damage` · `Kill Entity` · `Block Break` · `Consume` · `Shoot` · `Jump` · `Drop & Swap` · `Equip/Unequip`

Abilities execute customizable sound effects, particle bursts, projectiles, potion effects, console commands, or target ignitions with dedicated cooldown groups.

**Continuous Particle Auras**  
Enchant items with persistent visual particle shapes:
`HALO` · `SPIRAL` · `FOOTSTEPS` · `ORBITING SPHERES` · `WINGS` · `BURST`

Auras activate based on conditions such as holding in mainhand or offhand, wearing as armor, sneaking, or during combat.

**Universal Keybinds**  
Configure sneak double-taps, single sneaks, and hold sneak durations via `/synth keybinds` for seamless ability activation across both Synthetix and downstream addons like CuriosPaper.

<div align="center">

![Synthetix Universal Keybind Creation GUI](https://cdn.modrinth.com/data/gzvsgqmK/images/93608b0879dfad4c309935f356e40934473bd70e.png)

*Universal Keybind configuration GUI for double-tap and hold-sneak triggers*

</div>

---

## 🛡️ Equipment Sets

Reward players for collecting complete gear sets (`/synth sets`). Group custom weapons, armor, and accessories into thematic collections with tiered bonus thresholds (such as 2-piece and 4-piece sets). Active set bonuses dynamically grant permanent potion effects, attribute boosts (+Max Health, +Speed, +Toughness), and formatted lore tooltips that update live as gear is swapped.

---

## 📦 Import, Export & Resource Packs

**Drop-and-Play Workflow**  
Drop community `.bbmodel`, `.json`, and `.png` files into dedicated auto-import folders:
- `plugins/Synthetix/import/items/`
- `plugins/Synthetix/import/blocks/`
- `plugins/Synthetix/import/entities/`

Run `/synth import` to validate geometry, normalize textures, assign CustomModelData, update registries, and regenerate the resource pack in one step. Ready to share your creation? Use `/synth export <id|all>` to bundle definitions, recipes, drop rules, and models into a `.synthpack` / `.zip` archive.

**Zero-Port Host Injection**  
Synthetix eliminates secondary HTTP ports and firewall hassle by injecting compiled assets directly into your server's active pack manager (**Nexo**, **ItemsAdder**, **Oraxen**, **ResourcePackManager**, or **CraftEngine**). If no external host is detected, the built-in HTTP server provides lightweight self-hosting with automatic public IP detection.

---

## 🎮 How It Works

```
1. /synth create item <name> → Opens visual item creator with live slot 4 preview
2. Configure properties       → Toggle Data Components, trims, food, aura, and abilities
3. Save & Add Recipe          → Attach any of the 10 recipe types directly in-game
4. Export or Distribute       → Ingest via drop folders or export complete .synthpack bundles
```

---

## 🔧 Compatibility

| | Requirements |
|---|---|
| **Minecraft** | 1.20.6 — 1.21+ |
| **Server Software** | Paper, Purpur, Folia *(Experimental Support)* |
| **Java** | 21 LTS+ |
| **External Pack Bridges** | Nexo, ItemsAdder, Oraxen, ResourcePackManager, CraftEngine |
| **Bedrock Support** | Geyser & Floodgate (with custom Anvil & Smithing GUIs) |
| **Downstream Ecosystem** | CuriosPaper and custom addon plugins |

> [!WARNING]
> **Experimental Folia Support:** Folia regional multi-threading support is experimental—many errors, desyncs, and unexpected bugs can happen when running on Folia servers.

---

## 📊 Live Statistics

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/Synthetix/35485">
    <img src="https://bstats.org/signatures/bukkit/Synthetix.svg" alt="Synthetix bStats Live Metrics Graph" width="500">
  </a>
</p>

---

## 💬 Community

<div align="center">

[![Join the Synthetix Discord Server](https://img.shields.io/discord/1456137607569346739?label=Join%20the%20Discord&style=for-the-badge&logo=discord&logoColor=white&color=5865F2)](https://discord.gg/r5YXqgrGya)

**[discord.gg/r5YXqgrGya](https://discord.gg/r5YXqgrGya)**

Get builds · Report bugs · Suggest features · Community addons

<br>

[![Buy Me a Coffee on Ko-fi](https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/brothergaming52)

**[ko-fi.com/brothergaming52](https://ko-fi.com/brothergaming52)**

</div>

---

<details>
<summary>📋 <b>Click to Expand: Commands & Permissions Reference Table</b></summary>

### Commands

| Command | Permission | Description |
|---|---|---|
| `/synth items` | `synthetix.command.gui` | Opens paginated visual Item Browser GUI |
| `/synth blocks` | `synthetix.command.gui` | Opens visual Custom Block Browser GUI |
| `/synth entity` | `synthetix.admin` | Opens Custom Entity Browser & Animation Studio |
| `/synth create item <id>` | `synthetix.command.gui` | Opens Canvas GUI to design a new custom item |
| `/synth create block <id>` | `synthetix.command.gui` | Opens Block Editor to configure custom blocks |
| `/synth edit <item_id>` | `synthetix.command.gui` | Edits an existing custom item definition |
| `/synth edit block <id>` | `synthetix.command.gui` | Edits an existing custom block definition |
| `/synth clone <source> <new_id>` | `synthetix.admin` | Clones an existing item or block |
| `/synth give <player> <id> [amt]` | `synthetix.command.give` | Gives a custom item or block to a player |
| `/synth delete [item\|block] <id>` | `synthetix.admin` | Deletes an item or block definition permanently |
| `/synth recipe [item_id]` | `synthetix.recipe` | Opens survival-friendly visual Recipe Lookup (JEI) |
| `/synth sets` | `synthetix.sets` | Opens Equipment Sets Manager GUI |
| `/synth keybinds` | `synthetix.admin` | Manages Universal Keybind API settings |
| `/synth export <id\|all>` | `synthetix.export` | Exports item/block bundles to `.synthpack` archives |
| `/synth import [items\|blocks\|entities\|all]` | `synthetix.import` | Scans and ingests packages from `import/` folders |
| `/synth pack <rebuild\|prompt\|info>` | `synthetix.admin` | Controls resource pack pipeline and recompilation |
| `/synth spawn <entity_id>` | `synthetix.admin` | Spawns a custom animated entity in the world |
| `/synth settings` | `synthetix.settings` | Manages global engine toggles and features |
| `/synth reload` | `synthetix.admin` | Reloads configurations, lang, items, and recipes |

### Permissions

| Permission | Default | Description |
|---|---|---|
| `synthetix.recipe` | `true` | Allows players to use `/synth recipe` to inspect crafting recipes |
| `synthetix.admin` | `op` | Full access to all administrative commands, entities, and pack controls |
| `synthetix.command.gui` | `op` | Allows opening the in-game item and block creation GUIs |
| `synthetix.command.give` | `op` | Allows giving custom items and blocks to players |
| `synthetix.sets` | `op` | Allows opening the equipment sets management GUI |
| `synthetix.export` | `op` | Allows exporting `.synthpack` bundles |
| `synthetix.import` | `op` | Allows importing items, blocks, and entity models |
| `synthetix.settings` | `op` | Access to global engine configuration toggles |

</details>

<details>
<summary>⚙️ <b>Click to Expand: Configuration Showcase (`config.yml`, Item YAML)</b></summary>

### 1. Main Plugin Config (`/plugins/Synthetix/config.yml`)

```yaml
language: 'en_US'

# Enterprise Storage Backend ('sqlite', 'mysql', 'mongodb', or 'yaml')
storage:
  type: 'sqlite'
  auto-save-interval-seconds: 300
  sqlite:
    file: 'synthetix_data.db'

# Resource Pack Pipeline ('auto', 'self', 'link', 'plugin', or 'none')
resource-pack:
  enabled: true
  hosting-mode: 'auto'
  prompt-on-join: true
  force-pack: false
  server:
    port: 8085
    external-ip: 'auto'

# Modular External Engine Bridges
bridges:
  auto-detect: true
  nexo: true
  oraxen: true
  itemsadder: true
  craftengine: true

# Custom Block Engine Settings
custom-blocks:
  default-brightness: 'ambient'
  minimum-ambient-light: 3
  culling:
    enabled: true
    max-rendered-per-player: 50
    max-distance: 48
```

### 2. Custom Item Definition Example (`/plugins/Synthetix/items/inferno_blade.yml`)

```yaml
id: inferno_blade
material: NETHERITE_SWORD
display-name: "<gradient:#FF4500:#FFD700>Inferno Blade</gradient>"
lore:
  - "<gray>Forged in the depths of the Nether.</gray>"
  - "<dark_gray>Strikes ignite foes with blazing embers.</dark_gray>"

rarity: RARE
unbreakable: false
max-damage: 2500
enchantment-glint-override: true

aura:
  shape: SPIRAL
  particle: FLAME
  speed: 0.05
  count: 3
  condition: while-holding-mainhand

abilities:
  on-shift-right-click:
    cooldown: 8.0
    cooldown-message: "<red>Inferno Slash cooling down! (%remaining%s)</red>"
    cost-durability: 5
    actions:
      - "sound: ENTITY_BLAZE_SHOOT 1.0 1.2"
      - "particle: LAVA 25 0.5 0.5 0.5"
      - "projectile: SMALL_FIREBALL speed:1.8"
      - "message: <gold>You unleashed Inferno Slash!</gold>"

  on-hit-entity:
    chance: 0.40
    actions:
      - "ignite: 4"
      - "sound: ITEM_FIRECHARGE_USE 1.0 1.0"
      - "particle: FLAME 15 0.3 0.3 0.3"
```

</details>

<details>
<summary>🤝 <b>Click to Expand: For Developers (Maven Dependency & API Reference)</b></summary>

### Maven
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.Brothergaming52</groupId>
    <artifactId>Synthetix</artifactId>
    <version>1.0</version>
    <scope>provided</scope>
</dependency>
```

### plugin.yml
```yaml
depend: [Synthetix]
```

### Java API Example
```java
SynthetixAPI api = SynthetixAPI.get();

// 1. Asynchronous Data Store for External Addons (e.g. CuriosPaper)
AddonDataStore store = api.getAddonDataStore(myPlugin, "player_data");
store.set("player_uuid", "key", myData);
store.getAsync("player_uuid").thenAccept(data -> { ... });

// 2. Extending the In-Game Canvas with Custom Addon Pages
api.getEditorRegistry().registerCategory(new MyAddonCategory(myPlugin));

// 3. Registering Custom Action Triggers
api.getTriggerManager().registerTrigger(new CustomItemTrigger("on-ability-cast") { ... });

// 4. Registering Resource Pack Assets from another Plugin
api.registerResourcePack(myPlugin, new File(myPlugin.getDataFolder(), "assets"));

// 5. Programmatic Custom Item Creation
ItemDefinition item = new ItemDefinition("mystic_sword");
item.setMaterial(Material.DIAMOND_SWORD);
item.setDisplayName("<gradient:#00ffff:#0088ff>Mystic Sword</gradient>");
item.setEnchantmentGlintOverride(true);
api.getItemRegistry().register(item);
```

</details>

---

<div align="center">

**Made with ❤️ by Brothergaming52**

[Discord](https://discord.gg/r5YXqgrGya) · [Ko-fi](https://ko-fi.com/brothergaming52) · [bStats](https://bstats.org/plugin/bukkit/Synthetix/35485)

</div>
