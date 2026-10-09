# 🌾 Vein Farming: Universal Crop Harvester

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub](https://img.shields.io/badge/GitHub-hungbbdzz%2Fvein--farming-blue.svg)](https://github.com/hungbbdzz/vein-farming)

The ultimate universal AOE farming mod for **NeoForge 1.21.1**.  
Built as a seamless companion for **[LiteMiner](https://modrinth.com/mod/liteminer)** and **[FTB Ultimine](https://modrinth.com/mod/ftb-ultimine)**, with full **Standalone Mode** support featuring its own client-side highlight outline preview!

Brings **Area Tilling**, **Smart Mass Planting with Advanced Spatial Algorithms**, **AOE Bone Meal**, **Root-Preserving Column Crop Harvesting**, **Stem-Protected Fruit Harvesting**, **Mass Destruction Mode**, **Batch Composting**, and **Auto-Replanting** to Minecraft!

---

## 🎮 Controls & Keybindings

All keybindings can be customized in the standard Minecraft **Options -> Controls -> Key Binds -> Vein Farming** menu:

| Keybind | Default Key | Description |
| :--- | :--- | :--- |
| **Farming Veinmine / Mass Action** | `Left Shift` | Hold to activate mass farming actions (tilling, planting, harvesting, bone meal, destroying). When LiteMiner or FTB Ultimine is installed, their activation keys also trigger mass actions. |
| **Toggle Smart Planting** | *Unbound* (None) | Instantly toggle between **Smart Planting** (spatial algorithms active) and **Uniform Carpet Planting** (plants all viable soils without spacing/layout filters). Displays an action bar message and immediately updates the client preview! |

---

## ✨ Features & Mechanics

### 🚜 1. Mass Tilling (Area Hoe)
* Hold any **Hoe** and press your **Vein Farming activation key** (or hold `Sneak/Shift`).
* **Right-click** on any tillable soil (`Grass Block`, `Dirt`, `Coarse Dirt`, `Rooted Dirt`, etc.).
* All blocks in your active shape (`3x3`, `Shapeless`, `Tunnel`...) are converted into **Farmland**!
* **Safe Foliage Clearing:** Automatically clears wild grass, ferns, and flowers above the soil without ever harming pre-existing crops.
* **Tool Breaking Protection:** Stops before your tool breaks when `prevent_tool_breaking` is enabled.

---

### 🌱 2. Smart Mass Planting (Seeds & Crops)
Right-click on Farmland, Sand, Soul Sand, End Stone, or Jungle Logs to carpet-plant seeds and crops!

* **Universal Crop Support:** Wheat, Carrot, Potato, Beetroot, Torchflower, Pitcher Pod, Nether Wart, Bamboo, Sugar Cane, Cactus, Kelp, Chorus Flower, Cocoa Beans, etc.
* **Modded Crop & Farmland Support:** Farmer's Delight (Tomato, Cabbage, Onion, Rice), Rich Soil Farmland, etc.
* **Smart Inventory Replenishment:** When your hand stack empties, seeds are automatically drawn from your inventory.

#### 📐 Advanced Spatial Planting Algorithms:
1. **🌳 Smart Sapling Groves (`smart_sapling_planting`):**
   * **2x2 Mega Tree Auto-Pairing:** Automatically identifies 2x2 clusters for **Dark Oak**, **Spruce**, and **Jungle** saplings, spacing clusters $\ge 2$ blocks apart.
   * **Strict 2x2 Enforcement:** Dark Oak saplings (which cannot grow as 1x1) will *never* be planted as isolated single saplings.
   * **Canopy & Overcrowding Detection:** Scans existing trees, logs, and leaves in the world to prevent planting under thick canopies or too close to grown trees.
2. **🍉 Optimal Fruit Stem Layout (`smart_melon_pumpkin_planting`):**
   * Uses an **Inverted Checkerboard 40/40 Layout** for Melon and Pumpkin seeds.
   * Automatically leaves empty adjacent dirt/farmland spots for fruit to spawn, guaranteeing **0% growth penalty** and maximum fruit spawn rates.
3. **🌵 Maximum Independent Set (MIS) Cactus Planting (`smart_cactus_planting`):**
   * Solves a bipartite maximum independent set grid graph in real time to ensure no two planted cacti are orthogonally adjacent (which would cause them to break and pop off).
4. **🌸 Organic Flora & Mushroom Meadow Distribution (`smart_flower_planting`):**
   * Employs procedural coherent value noise to mimic realistic wildflower meadows and mushroom groves.
   * Replaces rigid, repetitive 1-space-1-space chessboard grids with organic natural variation:
     - **Natural Clumps ("crowd a bit"):** Small companion clusters of 2-3 flowers/mushrooms.
     - **Sparse Scattering ("sparse away"):** Solitary blooms dotting the landscape with 2-4 block gaps.
     - **Natural Clearings ("no fixed shape"):** Open breathing glades without flowers.
     - **Anti-Overcrowding:** Prevents unnatural solid blobs (no 2x2 blocks or 3-way orthogonal clumping).
5. **🍫 Cocoa Bean Trunk Planting:**
   * Right-click on Jungle Logs with Cocoa Beans to plant them on all available horizontal bark faces!
6. **🔄 Context-Aware Intercropping (Xen Canh) (`smart_intercropping`):**
   * Hold one crop in your **Main Hand** (e.g., Carrot) and another in your **Off Hand** (e.g., Potato).
   * Plants strictly **alternating rows** (Carrot row, Potato row, Carrot row...) to grant the vanilla crop growth speed bonus!
   * Automatically synchronizes row parity with existing planted crops in the farm.
   * Fruit seeds (Melon/Pumpkin) are strictly excluded from being forced into intercropping.

---

### 🦴 3. AOE Bone Meal (Smart Fertilizing & Flower Propagation)
* Hold **Bone Meal** and **Right-click** on crops or flowers.
* **Smart Area Growth:** Even if the clicked crop is already mature, Bone Meal will automatically bypass it and continue fertilizing all other growing crops in the area until they reach 100% maturity!
* **🌸 Bedrock-Style Flower Propagation (`bedrock_flower_bonemeal`):**
  - Right-click small flowers (**Poppy, Dandelion, Tulips, Cornflower, Orchids, Allium, etc.**) with Bone Meal to propagate clones onto surrounding grass/dirt blocks, exactly like Minecraft Bedrock Edition!
  - **Single-Click Mode:** Spreads 1–4 duplicate blooms and companion short grass within a 7x7 meadow area.
  - **Vein Mass Mode (`Shift` / Trigger Key):** Cascades bone meal across entire connected wildflower patches with ascending audio pitches, instantly creating lush, vibrant flowering fields!
* In **Creative Mode**, bone meal is never consumed. In Survival, it draws safely from your hand and inventory.

---

### 🌾 4. Universal Mass Harvesting & Auto-Replanting
* **Right-click** on crops or farmland with an empty hand or tool.
* **Auto-Replant:** Automatically harvests mature crops and replants them at age 0 using dropped or inventory seeds!
* **Immature Crop Safety:** Immature crops are strictly preserved.
* **Root-Preserving Column Crops (Sugar Cane, Bamboo, Cactus, Kelp):** Automatically identifies the bottom root/anchor block and **strictly preserves the root**, only harvesting the stalks above it!
* **Fruit & Stem Protection (Melon, Pumpkin):** Harvests ripe melons and pumpkins while **strictly protecting and preserving stems (`StemBlock`)**!
* **Berry Picking:** Gathers **Sweet Berries** and **Cave Vines (Glow Berries)** and resets their age without breaking the vine/bush.
* **Chorus Tree Traversal:** Gathers Chorus Fruit and Flowers across 3D branches while replanting a Chorus Flower on the End Stone base.
* **🔪 Farmer's Delight Knife Compatibility:** Harvesting crops with a knife drops Straw with Fortune scaling!
* **Direct-to-Inventory & Drop Aggregation:** Harvested items can be placed directly into your inventory or neatly merged into compact stacks right at your feet.

---

### 🪓 5. Mass Destruction Mode (Farm Plot Clearing)
* Hold **Sneak (`Shift`)** (or your miner mod key) and **Left-click (Mine)** a crop while holding any tool (**Axe, Pickaxe, Shovel, Hoe, Knife, Shears**).
* Completely clears all connected agricultural crops, stems, and column roots **without replanting**, allowing you to completely redesign or wipe farm plots in seconds!
* Strictly restricted to agricultural plants—will never accidentally break stone, dirt, or ores.

---

### 🍂 6. Batch Composting
* Right-click a **Composter** while holding compostable items.
* Instantly processes up to **128 items** from your hand and inventory in a single click, popping Bone Meal directly into your inventory or above the composter!

---

### 🛡️ 7. Farmland Trample Prevention
* Built-in protection preventing Farmland (`#farmland`) from turning back to dirt when players or mobs jump or land on it! Configurable via `prevent_farmland_trample`.

---

### 👁️ 8. Standalone Real-Time 3D Wireframe Preview
When LiteMiner is not installed, the mod renders its own client-side **wireframe bounding box highlight** on target blocks when holding Sneak or the activation key:
* 🌾 **Golden Amber:** Mass Harvesting
* 🌿 **Sprout Green:** Mass Planting
* 🛑 **Crimson Red:** Mass Destruction
* 💎 **Emerald Jade:** Bone Meal Fertilizing
* 🟫 **Earth Brown:** Area Tilling (Hoe)

---

## 🏛️ Modular Code Architecture

The codebase has been refactored from a monolithic class into a decoupled, high-performance modular architecture:

| Class | Responsibility |
| :--- | :--- |
| **`PlantClassifier.java`** | Universal tags, block & item categorization, soil validation, and crop maturity logic. |
| **`PlantingAlgorithms.java`** | Spatial layout algorithms: 2x2 sapling groves, cactus bipartite MIS, fruit stem 40/40 layouts, organic flora meadow noise, and intercropping row parity. |
| **`PlantingManager.java`** | Mass planting execution, Cocoa Bean log wrapping, and BFS candidate search. |
| **`HarvestManager.java`** | Mass harvesting, auto-replanting, left-click destruction, and harvest BFS search. |
| **`TillingAndFertilizingManager.java`** | Mass hoe tilling, AOE bone meal fertilizing, and batch composting. |
| **`FarmingManager.java`** | High-level facade delegating to the domain managers while preserving 100% backward compatibility for external callers. |

---

## ⚙️ Configuration

The configuration file is generated automatically at `.minecraft/config/liteminer_farming_addon-common.toml`:

```toml
[general]
  # Maximum number of blocks to hoe, plant, fertilize, or harvest in a single action
  max_blocks = 64
  # Inherit block limit from LiteMiner's config
  use_liteminer_limit = true
  # Maximum horizontal radius from the clicked block
  farming_radius = 8
  # Prevent tools from breaking
  prevent_tool_breaking = true
  # Automatically pull seeds & bone meal from inventory when hand runs out
  pull_from_inventory = true
  # Clear wild grass/flowers above dirt when tilling
  clear_foliage = true
  # Food exhaustion per block
  exhaustion_per_block = 0.02
  # Require Shift/Sneak if LiteMiner is not installed
  require_sneak_fallback = true
  # Enable AOE mass harvesting of mature crops
  enable_mass_harvest = true
  # Automatically replant harvested crops at age 0 using dropped or inventory seeds
  replant_crops = true
  # If holding a hoe when mass harvesting, consume durability per crop
  damage_hoe_on_harvest = true
  # Continue fertilizing growing crops in the selected area until they reach maturity
  smart_bonemeal = true
  # Prevent farmland from being trampled into dirt when players or mobs jump or land on it
  prevent_farmland_trample = true
  # Gather all harvested item drops at the targeted block position
  collect_drops_at_target = true
  # Deposit harvested drops directly into the player's inventory
  harvest_to_inventory = false
  # Enable mass harvesting of column crops (Sugar Cane, Bamboo, Cactus, Kelp) preserving bottom root
  harvest_sugar_cane = true
  # Smart Sapling Planting with 2x2 mega pairing and anti-overcrowding spacing
  smart_sapling_planting = true
  # Minimum spacing between planted saplings
  sapling_min_spacing = 2
  # Auto-pair 2x2 saplings (Dark Oak, Spruce, Jungle)
  smart_sapling_2x2 = true
  # Bipartite Maximum Independent Set Cactus planting
  smart_cactus_planting = true
  # 40/40 Inverted Checkerboard fruit stem planting (Melon & Pumpkin)
  smart_melon_pumpkin_planting = true
  # Alternating row intercropping when holding different seeds in both hands
  smart_intercropping = true
  # Organic meadow distribution for flowers, mushrooms, and chorus flowers
  smart_flower_planting = true
  # Batch composter processing up to 128 items in one click
  batch_composter = true
  # Farmer's Delight knife straw compatibility
  farmers_delight_knife_compat = true
  # Satisfying rising pitch audio cascade
  satisfying_audio_cascade = true
```

---

## 🔨 Building from Source

Clone the repository and build using Gradle:

```bash
git clone https://github.com/hungbbdzz/vein-farming.git
cd vein-farming
./gradlew build --no-daemon
```

The compiled mod JAR will be located in `build/libs/`.

---

## 📜 License
Distributed under the **MIT License**. See [LICENSE](LICENSE) for more information.
