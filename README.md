# 🌾 Vein Farming: Universal Crop Harvester

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub](https://img.shields.io/badge/GitHub-hungbbdzz%2Fvein--farming-blue.svg)](https://github.com/hungbbdzz/vein-farming)

The ultimate universal AOE farming mod for **NeoForge 1.21.1**.  
Built as a seamless companion for **[LiteMiner](https://modrinth.com/mod/liteminer)** and **[FTB Ultimine](https://modrinth.com/mod/ftb-ultimine)**, with full **Standalone Mode** support featuring its own client-side in-world highlight outline preview!

Brings **Area Tilling**, **Smart Water Bucket Irrigation (2-Pass Hybrid Grid)**, **Smart Mass Planting with Spatial Geometry**, **AOE Bone Meal**, **Bedrock-Style Flower Propagation**, **Root-Preserving Column Crop Harvesting**, **Stem-Protected Fruit Harvesting**, **Mass Destruction Mode with Drop Aggregation**, **Batch Composting**, **Custom Advancements**, and **Auto-Replanting** to Minecraft!

---

## 🎮 Controls & Keybindings

All keybindings can be customized in the standard Minecraft **Options -> Controls -> Key Binds -> Vein Farming** menu:

| Keybind | Default Key | Description |
| :--- | :--- | :--- |
| **Farming Veinmine / Mass Action** | `Left Shift` | Hold to activate mass farming actions (tilling, irrigation, planting, harvesting, bone meal, destroying). When LiteMiner or FTB Ultimine is installed, their activation keys also trigger mass actions. |
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

### 💧 2. Smart Water Bucket Irrigation (Civil Engineering)
Tired of manually digging water holes and placing buckets to hydrate massive farms? Vein Farming introduces an automated, mathematically optimal water irrigation system:

* **How to Trigger:** Hold any **Hoe in Main Hand** and a **Water Bucket** (or any fluid container filled with water) in **Off Hand**. Hold the activation key and **Right-click** tillable soil.
* **2-Pass Hybrid Optimization Grid:**
  1. **Pass 1 (Strict 8-Block Spacing Grid):** Places water source blocks strictly spaced $\ge 8$ blocks apart (Chebyshev distance). Automatically anchors to existing world water sources or centers over the field to ensure zero overlapping hydration zones and maximize plantable farmland surface.
  2. **Pass 2 (Full Coverage Rescue):** If irregular farm borders, narrow peninsulas, or tight pockets would otherwise leave dead unwatered zones, Pass 2 automatically places centered rescue water holes to **guarantee 100% of your farmland is hydrated** without dead dry dirt!
* **Resource Consumption:** Consumes 1 water source per placed water hole (from off-hand first, then player inventory). In Creative Mode, water is infinite.
* **Real-Time Preview:** When standalone preview is active, intended water holes are rendered in **Azure Cyan wireframe**!

---

### 🌱 3. Smart Mass Planting (Seeds & Crops)
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
6. **🔄 Context-Aware Intercropping (`smart_intercropping`):**
   * Hold one crop in your **Main Hand** (e.g., Carrot) and another in your **Off Hand** (e.g., Potato).
   * Plants strictly **alternating parallel rows** (Carrot row, Potato row, Carrot row...) to grant the vanilla crop growth speed bonus (up to 200% speed)!
   * Automatically synchronizes row parity with existing planted crops in the farm.
   * Fruit seeds (Melon/Pumpkin) are strictly excluded from being forced into intercropping.

---

### 🦴 4. AOE Bone Meal (Smart Fertilizing & Flower Propagation)
* Hold **Bone Meal** and **Right-click** on crops or flowers.
* **Smart Area Growth:** Even if the clicked crop is already mature, Bone Meal will automatically bypass it and continue fertilizing all other growing crops in the area until they reach 100% maturity!
* **🌸 Bedrock-Style Flower Propagation (`bedrock_flower_bonemeal`):**
  - Right-click small flowers (**Poppy, Dandelion, Tulips, Cornflower, Orchids, Allium, etc.**) with Bone Meal to propagate clones onto surrounding grass/dirt blocks, exactly like Minecraft Bedrock Edition!
  - **Single-Click Mode:** Spreads 1–4 duplicate blooms and companion short grass within a 7x7 meadow area.
  - **Vein Mass Mode (`Shift` / Trigger Key):** Cascades bone meal across entire connected wildflower patches with ascending audio pitches, instantly creating lush, vibrant flowering fields!
* In **Creative Mode**, bone meal is never consumed. In Survival, it draws safely from your hand and inventory.

---

### 🌾 5. Universal Mass Harvesting & Auto-Replanting
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

### 🪓 6. Mass Destruction Mode (Left-Click Farm Clearing)
* Hold **Sneak (`Shift`)** (or your miner mod key) and **Left-click (Mine)** a crop while holding any tool (**Axe, Pickaxe, Shovel, Hoe, Knife, Shears**).
* Completely clears all connected agricultural crops, stems, and column roots **without replanting**, allowing you to completely wipe farm plots in seconds!
* **Clean Drop Aggregation:**
  - If `harvest_to_inventory` is enabled, all drops from the broken vein are deposited directly into your inventory.
  - If `collect_drops_at_target` is enabled, all drops are merged and dropped neatly right at the block you hit, instead of scattering across the field!
* Strictly restricted to agricultural plants—will never accidentally break stone, dirt, or ores.

---

### 🍂 7. Batch Composting
* Right-click a **Composter** while holding compostable items.
* Instantly processes up to **128 items** from your hand and inventory in a single click, popping Bone Meal directly into your inventory or above the composter!

---

### 🛡️ 8. Farmland Trample Prevention
* Built-in protection preventing Farmland (`#farmland`) from turning back to dirt when players or mobs jump or land on it! Configurable via `prevent_farmland_trample`.

---

### 👁️ 9. Standalone Real-Time 3D Wireframe Preview
When LiteMiner is not installed, the mod renders its own client-side **wireframe bounding box highlight** on target blocks when holding Sneak or the activation key:
* 🌾 **Golden Amber:** Mass Harvesting
* 🌿 **Sprout Green:** Mass Planting
* 💧 **Azure Cyan:** Smart Water Irrigation Holes
* 🛑 **Crimson Red:** Mass Destruction
* 💎 **Emerald Jade:** Bone Meal Fertilizing
* 🟫 **Earth Brown:** Area Tilling (Hoe)

---

### 🏆 10. Custom Advancements Tree
Vein Farming includes a built-in custom Advancement tree to guide and reward players:

| Advancement | Icon | Description |
| :--- | :--- | :--- |
| **It Ain't Much, But It's Honest Work** | Diamond Hoe | Perform your first mass farming action with Vein Farming (Root). |
| **Lazy Farmer 3000** | Golden Hoe | Harvest 64 or more mature crops in a single instant click. |
| **Crop Rotation Genius** | Beetroot | Plant alternating parallel rows of crops by holding different seeds in each hand for 200% growth speed. |
| **Civil Engineer** | Water Bucket | Irrigate a field by holding a Hoe and Water Bucket, automatically digging spaced water wells. |
| **Is it Bedrock?** | Poppy | Duplicate a small flower with Bone Meal... wait, since when can we do this in Java?! |
| **The Lorax Approves** | Oak Sapling | Plant a spacious sapling orchard or a 2x2 mega tree grove using smart spacing. |
| **Speedrun Composting** | Composter | Shift-click a Composter with seeds or compostables to convert entire stacks into Bone Meal instantly. |
| **Living on the Edge** | Iron Hoe | Have your farming tool saved from breaking at exactly 1 durability. |

---

### ⚡ 11. Performance, Fast-Click & Re-Entrancy Protection
* **Tick Debouncing:** Protects against rapid spam-clicking, macro clickers, and dual-wield packet races within the same tick.
* **Re-entrancy Guard:** Employs thread-local execution locks preventing duplicate event cascades.
* **Eager JVM Class Preloading:** All core classes are initialized at mod startup, eliminating lazy classloader latency and preventing file-lock issues.
* **Top-level Crash Resilience:** All event handlers are safeguarded against unhandled exceptions, protecting server tick loops from crashes.

---

## 🏛️ Modular Code Architecture

The codebase follows a decoupled, high-performance modular architecture:

| Class | Responsibility |
| :--- | :--- |
| **`PlantClassifier.java`** | Universal tags, block & item categorization, soil validation, fluid container detection, and crop maturity logic. |
| **`PlantingAlgorithms.java`** | Spatial layout algorithms: 2x2 sapling groves, 2-pass hybrid water irrigation grid, cactus bipartite MIS, fruit stem 40/40 layouts, organic flora meadow noise, and intercropping row parity. |
| **`PlantingManager.java`** | Mass planting execution, Cocoa Bean log wrapping, and BFS candidate search. |
| **`HarvestManager.java`** | Mass harvesting, auto-replanting, left-click destruction with drop aggregation, and harvest BFS search. |
| **`TillingAndFertilizingManager.java`** | Mass hoe tilling, smart water bucket irrigation placement, AOE bone meal fertilizing, and batch composting. |
| **`FarmingManager.java`** | High-level facade delegating to the domain managers while preserving 100% backward compatibility for external callers. |
| **`ModAdvancements.java`** | Custom advancement criteria trigger and dispatching. |

---

## ⚙️ Configuration

### 🖥️ Native In-Game Configuration GUI
No third-party configuration mods (like *Configured* or *Cloth Config*) required!
* Go to Minecraft's **Mods** menu (`Esc` -> `Mods`).
* Select **Vein Farming: Universal Crop Harvester**.
* Click the **Config** button.
* An authentic, clean vanilla Minecraft style menu opens with interactive sliders and toggle switches.
* Frequently adjusted gameplay settings appear prominently at the top:
  - **Sapling Minimum Spacing** (Slider: 0-8)
  - **Flower & Flora Sparsity** (Slider: 0-5)
  - **Maximum Blocks Affected** (Slider: 1-1024, default: 128)
  - **Farming Radius** (Slider: 1-32, default: 8)
  - **Water Irrigation Hole Spacing** (Slider: 8-16, default: 8)
  - **Water Bucket Irrigation** (Toggle, default: ON)
  - **Harvest Direct to Inventory** (Toggle, default: OFF)
  - **Clear Replaceable Foliage when Hoeing** (Toggle, default: ON)
  - **Prevent Tool Breaking** (Toggle, default: ON)
* Features **Reset Defaults**, **Cancel**, and **Done** buttons with instant persistence.

### 📄 Configuration File
The configuration is saved automatically at `.minecraft/config/vein_farming-common.toml`:

```toml
[general]
  # Minimum spacing between planted saplings (0 = OFF / Carpet mode, 1-8 blocks, default: 4)
  sapling_min_spacing = 4
  # Flora & Flower sparsity level (0 = OFF / Dense carpet, 1-5 = Organic meadow noise, default: 3)
  flower_sparsity = 3
  # Maximum number of blocks to hoe, plant, fertilize, or harvest in a single action (1-1024, default: 128)
  max_blocks = 128
  # Maximum horizontal radius from the clicked block (1-32, default: 8)
  farming_radius = 8
  # Minimum spacing between automated water irrigation holes in blocks (8-16, default: 8)
  water_hole_spacing = 8
  # Automatically dig and place water holes when holding a Hoe in main hand and Water Bucket in off hand
  smart_water_bucket_irrigation = true
  # Send harvested items directly into the player's inventory instead of dropping them
  harvest_to_inventory = false
  # Automatically collect and merge drops at clicked position during harvesting/destroying
  collect_drops_at_target = true
  # Render real-time in-world preview highlight when mass farming key is held
  standalone_preview = true
  # Render transparent ghost farmland previews when holding a Hoe
  ghost_farmland_preview = true
  # Render transparent ghost crop previews when holding seeds
  ghost_plant_preview = true
  # Render water hole placement wireframe previews when holding Hoe and Water Bucket
  smart_irrigation_preview = true
  # 40/40 Inverted Checkerboard fruit stem planting (Melon & Pumpkin)
  smart_melon_pumpkin_planting = true
  # Allow bone meal on small flowers (Poppy, Dandelion, etc.) to propagate nearby clones (Bedrock Edition feature)
  bedrock_flower_bonemeal = true
  # Alternating parallel row intercropping when holding different seeds in both hands
  smart_intercropping = true
  # Automatically replant harvested crops at age 0 using dropped or inventory seeds
  replant_crops = true
  # Prevent tools from breaking by stopping at 1 durability
  prevent_tool_breaking = true
  # Clear wild grass/flowers above dirt when tilling
  clear_foliage = true
  # Batch composter processing up to 128 items in one click
  batch_composter = true
  # Continue fertilizing growing crops in the selected area until they reach maturity
  smart_bonemeal = true
  # Prevent farmland from being trampled into dirt when players or mobs jump or land on it
  prevent_farmland_trample = true
  # If holding a hoe when mass harvesting, consume durability per crop
  damage_hoe_on_harvest = true
  # Food exhaustion per block
  exhaustion_per_block = 0.02
```

---

## 🔨 Building from Source

Clone the repository and build using Gradle:

```bash
git clone https://github.com/hungbbdzz/vein-farming.git
cd vein-farming
./gradlew build --no-daemon
```

The compiled mod JAR will be located in `build/libs/vein_farming-1.21.1-neoforge-2.0.0.jar`.

---

## 📜 License
Distributed under the **MIT License**. See [LICENSE](LICENSE) for more information.
