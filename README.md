# 🌾 Vein Farming: Universal Crop Harvester

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub](https://img.shields.io/badge/GitHub-hungbbdzz%2Fvein--farming-blue.svg)](https://github.com/hungbbdzz/vein-farming)

The ultimate universal AOE farming mod for **NeoForge 1.21.1**.  
Built as a seamless companion for **[LiteMiner](https://modrinth.com/mod/liteminer)** and **[FTB Ultimine](https://modrinth.com/mod/ftb-ultimine)**, with full **Standalone Mode** support featuring its own client-side highlight outline preview!

Brings **Area Tilling (Hoe)**, **Mass Planting (Seeds & Crops)**, **Smart AOE Bone Meal**, **Root-Preserving Column Crop Harvesting**, and **Stem-Protected Fruit Harvesting with Auto-Replant** to Minecraft!

---

## ✨ Universal Features

### 🚜 1. Mass Tilling (Area Hoe)
* Hold any **Hoe** and press your **LiteMiner / FTB Ultimine activation key** (or hold `Shift/Sneak`).
* **Right-click** on any tillable soil (`Grass Block`, `Dirt`, `Coarse Dirt`, `Rooted Dirt`, etc.).
* All blocks in your active shape (`3x3`, `Shapeless`, `Tunnel`...) are converted into **Farmland**!
* **Safe Foliage Clearing:** Automatically clears wild grass, ferns, and flowers above the soil without ever harming pre-existing crops.
* **Tool Protection:** Respects tool durability and stops before your tool breaks (`prevent_tool_breaking`).

### 🌱 2. Mass Planting (Seeds & Crops)
* Hold any **Seeds or Crops** (`Wheat`, `Carrot`, `Potato`, `Beetroot`, `Melon`, `Pumpkin`, `Torchflower`, `Pitcher Pod`...).
* Fully compatible with modded crops like **Farmer's Delight** (Tomato, Cabbage, Onion, Rice...).
* Fully compatible with modded farmland like **Rich Soil Farmland**.
* **Right-click** on Farmland to carpet-plant all empty Farmland blocks in the selected shape!
* **Smart Inventory Replenishment:** If the stack in your hand runs out, the mod automatically consumes matching seeds from your inventory.

### 🦴 3. AOE Bone Meal (Smart Fertilizing)
* Hold **Bone Meal** and **Right-click** on crops.
* **Smart Area Growth:** Even if the block you are looking at is already fully mature, Bone Meal will automatically bypass it and continue fertilizing all other growing crops in the selected area until they reach 100% maturity!
* In **Creative Mode**, bone meal is never consumed. In Survival, it safely draws from your hand and inventory as needed.

### 🌾 4. Universal Mass Harvesting
* **Right-click** on any crop or farmland to trigger harvest.
* Works with an **empty hand**, holding a **Hoe**, or any tool.
* **Auto-Replant:** Automatically harvests all mature crops and **replants** them at age 0 using dropped or inventory seeds!
* **Immature Crop Safety:** Immature crops in the selected area are safely preserved while harvesting all mature ones!
* **Vertical Column Crops (Sugar Cane, Cactus, Bamboo, Kelp):** Automatically identifies the bottom root/anchor block and **strictly preserves the root**, only harvesting the stalks above it!
* **Fruit & Stem Protection (Melon, Pumpkin):** Harvests ripe melons and pumpkins while **strictly protecting and preserving stems (`StemBlock`)**!
* **Berry Picking:** Gathers **Sweet Berries** and **Cave Vines (Glow Berries)** and resets their age without breaking the vine/bush.
* **Item Drop Aggregation:** All harvested drops are automatically merged into compact stacks and spawned right at the targeted block location, identical to LiteMiner!
* Supports **Fortune** enchantments if holding an enchanted tool.

### 🛡️ 5. Farmland Trample Prevention
* Built-in protection preventing Farmland (`#farmland`) from turning back to dirt when players or mobs jump or land on it! Configurable via `prevent_farmland_trample`.

### 👁️ 6. Standalone Client Highlight Preview
* When LiteMiner is not installed, the mod renders its own real-time **wireframe bounding box highlight** on target blocks when holding Sneak!
* Color-coded preview:
  - 🌾 **Golden Amber:** Harvesting
  - 🌿 **Sprout Green:** Planting
  - 💎 **Emerald Jade:** Bone Meal fertilizing
  - 🟫 **Earth Brown:** Tilling (Hoe)

---

## 🎮 How It Works

### With LiteMiner or FTB Ultimine
1. Select your desired shape (e.g. `3x3`, `Shapeless`, etc.).
2. Hold your veinmine key (`~` by default). The highlight shows the exact target blocks!
3. **Right-click** with a Hoe, Seeds, Bone Meal, or empty hand on crops!

### Standalone Mode (No Miner Mod Required)
LiteMiner and FTB Ultimine are **completely optional**:
* Simply hold **Sneak (`Shift`)** while right-clicking!
* Real-time client wireframe outline shows you exactly which blocks will be affected.

## ⚖️ LiteMiner Addon vs. Standalone Mode

| Feature / Aspect | With LiteMiner (Addon Mode) | Without LiteMiner (Standalone Mode) |
| :--- | :--- | :--- |
| **Activation Key** | LiteMiner keybind (`~` by default) | Hold **Sneak (`Shift`)** *(configurable)* |
| **Area Shapes** | Switchable shapes (`3x3`, `Tunnel`, `Staircase`, `Shapeless`...) | Natural clustered radius search (`farming_radius` & `max_blocks`) |
| **Target Highlight Preview** | Real-time client outline preview | None (actions execute directly upon click) |
| **Block Limits** | Inherited from LiteMiner config | Configured via `farming_radius` & `max_blocks` |
| **Core Mechanics** | Full (Till, Plant, Fertilize, Harvest & Replant) | Full (Till, Plant, Fertilize, Harvest & Replant) |

---

## ⚙️ Configuration

The config file is generated automatically at `.minecraft/config/liteminer_farming_addon-common.toml`:

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
  # Enable mass harvesting of sugar cane, preserving the bottom root block
  harvest_sugar_cane = true
```

---

## 🔨 Building from Source

Clone the repository and build using Gradle:

```bash
git clone https://github.com/hungbbdzz/vein-farming.git
cd vein-farming
./gradlew build
```

The compiled mod JAR will be located in `build/libs/`.

---

## 🤝 Credits & Disclaimer

* This is an **unofficial** companion addon for [LiteMiner](https://modrinth.com/mod/liteminer) created by **iamkaf** (MIT License).
* Logo is a derivative work of LiteMiner's original icon.

---

## 📜 License
Distributed under the **MIT License**. See [LICENSE](LICENSE) for more information.
