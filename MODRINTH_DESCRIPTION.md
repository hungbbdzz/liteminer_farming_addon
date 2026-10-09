# 🌾 LiteMiner Farming Addon

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A lightweight companion addon for **[LiteMiner](https://modrinth.com/mod/liteminer)** on **NeoForge 1.21.1**.
It seamlessly brings **Area Tilling (Hoe)**, **Mass Planting (Seeds & Crops)**, and **AOE Bone Meal Fertilizing** to LiteMiner, strictly following LiteMiner's shape system and client-side block highlight preview!

---

## ✨ Features

### 1. 🚜 Mass Tilling (Area Hoe)

* Hold any **Hoe** and press your **LiteMiner activation key** (or hold `Shift/Sneak`).
* **Right-click** on any tillable soil (`Grass Block`, `Dirt`, `Coarse Dirt`, `Rooted Dirt`, etc.).
* All blocks in your active LiteMiner shape (`3x3`, `Shapeless`, `Tunnel`...) are converted into **Farmland**!
* **Safe Foliage Clearing:** Automatically clears wild grass, ferns, and flowers above the soil without ever harming pre-existing crops.
* **Tool Protection:** Respects tool durability and stops before your tool breaks (`prevent_tool_breaking`).

### 2. 🌱 Mass Planting (Seeds & Crops)

* Hold any **Seeds or Crops** (`Wheat`, `Carrot`, `Potato`, `Beetroot`, `Melon`, `Pumpkin`, `Torchflower`, `Pitcher Pod`...).
* Fully compatible with modded crops like **Farmer's Delight** (Tomato, Cabbage, Onion, Rice...).
* Fully compatible with modded farmland like **Rich Soil Farmland**.
* **Right-click** on Farmland with your LiteMiner key active to carpet-plant all empty Farmland blocks in the selected shape!
* **Smart Inventory Replenishment:** If the stack in your hand runs out, the mod automatically consumes matching seeds from your inventory.

### 3. 🦴 AOE Bone Meal (Fertilizing)

* Hold **Bone Meal** and **Right-click** on crops with your LiteMiner key active.
* Automatically fertilizes all eligible crops in the selected area!
* Spawns standard green growth particles and sounds, and replenishes bone meal from your inventory when needed.

---

## 🎮 How It Works

1. Make sure **LiteMiner** is installed.
2. Select your desired shape in LiteMiner (e.g. `3x3`, `Shapeless`, etc.).
3. Hold your LiteMiner veinmine key (`~` by default). The blue/cyan highlight shows the exact target blocks!
4. **Right-click** with a Hoe, Seeds, or Bone Meal!

> **Note:** If LiteMiner is not installed, the mod gracefully falls back to holding `Sneak (Shift)`.

---

## ⚙️ Configuration

The config file is located at `.minecraft/config/liteminer_farming_addon-common.toml`:

```toml
[general]
  # Maximum number of blocks to hoe, plant, or fertilize in a single action
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
```

---

## 📜 License & Permissions

Distributed under the **MIT License**. Feel free to include this mod in any modpack!
