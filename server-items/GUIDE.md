# ServerItems Content Creation Guide

Complete guide for creating custom items, furniture, blocks, and wearables using the ServerItems plugin.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Directory Structure](#directory-structure)
3. [Creating Custom Items](#creating-custom-items)
4. [Creating Furniture](#creating-furniture)
5. [Creating Custom Blocks](#creating-custom-blocks)
6. [Creating Wearable Clothes & Cosmetics](#creating-wearable-clothes--cosmetics)
7. [Resource Pack Setup](#resource-pack-setup)
8. [3D Models for Furniture](#3d-models-for-furniture)
9. [Mechanics System](#mechanics-system)
10. [Selling Items in the Shop](#selling-items-in-the-shop)
11. [Commands Reference](#commands-reference)
12. [Bedrock (Geyser) Support](#bedrock-geyser-support)
13. [Troubleshooting](#troubleshooting)

---

## Quick Start

1. Drop `ServerItems.jar` into your server's `plugins/` folder
2. Start the server - it creates `plugins/ServerItems/items/` with example files
3. Create a `.yml` file in the `items/` folder with your item definitions
4. Drop matching textures into `plugins/ServerItems/pack/assets/serverplugins/textures/item/`
5. Run `/witems reload` to load new items
6. Run `/witems pack generate` to rebuild the resource pack
7. Give items with `/witems give <player> <item_id>`

---

## Directory Structure

After first startup, the plugin creates this layout:

```
plugins/ServerItems/
  config.yml                           # Plugin configuration
  items/                               # All item definitions go here
    example_items.yml                  # Shipped example items
    furniture_living.yml               # Default furniture (living room)
    furniture_decorations.yml          # Default furniture (decorations)
    furniture_electronics.yml          # Default furniture (electronics)
    furniture_misc.yml                 # Default furniture (miscellaneous)
    my_weapons.yml                     # (your custom file - any name works)
    clothes/                           # (subdirectories are supported)
      hats.yml
      outfits.yml
  pack/
    assets/
      serverplugins/
        textures/item/                 # Put your .png textures here
          flame_sword.png
          wooden_chair.png
        models/item/                   # Put custom .json models here (optional)
          wooden_chair.json
    generated/
      ServerPlugins-Items.zip              # Auto-generated resource pack
      geyser_mappings.json             # Auto-generated Bedrock mappings
```

**Key points:**
- The `items/` folder is scanned recursively - use any file names and subdirectories you want
- Every `.yml` and `.yaml` file in `items/` is loaded as item definitions
- Textures go in `pack/assets/serverplugins/textures/item/` named `<item_id>.png`
- Custom 3D models go in `pack/assets/serverplugins/models/item/` named `<item_id>.json`
- If no custom model JSON exists, a flat 2D item model is auto-generated

---

## Creating Custom Items

Every item is defined as a YAML block inside any `.yml` file in the `items/` folder. The top-level key is the **item ID** (lowercase, underscores, no spaces).

### Minimal Item

```yaml
ruby:
  material: EMERALD
  display_name: "<red>Ruby"
  custom_model_data: 10010
```

This creates an item that looks like an emerald in vanilla but shows your custom texture (if you provide `ruby.png`) when the resource pack is loaded.

### Full Item Reference

```yaml
flame_sword:
  # REQUIRED - Base Minecraft material
  material: DIAMOND_SWORD

  # REQUIRED - Display name (supports MiniMessage formatting)
  display_name: "<gradient:#FF4500:#FFD700>Flame Sword</gradient>"

  # Optional - Lore lines (supports MiniMessage)
  lore:
    - "<gray>A blade forged in dragonfire"
    - ""
    - "<gold>Special Item"

  # Optional but recommended - Links to your custom texture/model
  custom_model_data: 10001

  # Optional - Enchantments (use Minecraft enchantment names, lowercase)
  enchants:
    fire_aspect: 2
    sharpness: 5
    unbreaking: 3

  # Optional - Hide specific item properties
  item_flags:
    - HIDE_ENCHANTS
    - HIDE_ATTRIBUTES
    - HIDE_UNBREAKABLE

  # Optional - Makes the item unbreakable
  unbreakable: true

  # Optional - Adds enchantment glint without enchantments
  glow: true

  # Optional - Pluggable behaviors (see Mechanics section)
  mechanics:
    durability:
      max: 500
    cooldown:
      ticks: 40
```

### Material Choices

The `material` field determines the base Minecraft item. Common choices:

| Use Case | Recommended Material | Why |
|----------|---------------------|-----|
| Swords/weapons | `DIAMOND_SWORD`, `NETHERITE_SWORD` | Held in hand correctly, has attack animation |
| Tools | `DIAMOND_PICKAXE`, `DIAMOND_AXE` | Has mining properties |
| Food/consumables | `GOLDEN_APPLE`, `COOKED_BEEF` | Can be eaten |
| Decorative items | `STICK`, `PAPER` | Neutral base, no special behavior |
| Furniture/cosmetics | `LEATHER_HORSE_ARMOR` | Has 2D item texture, no vanilla appearance to conflict |
| Wearable head items | `CARVED_PUMPKIN` | Equips to head slot |
| Blocks | `NOTE_BLOCK` | Has 800 available states for custom block textures |

### custom_model_data Ranges

Keep your CMD values organized:

| Range | Category |
|-------|----------|
| 10001-19999 | General custom items (weapons, tools, misc) |
| 20001-29999 | Custom blocks |
| 30001-39999 | Furniture |
| 40001-49999 | Wearable cosmetics / clothes |
| 50001-59999 | Reserved for future use |

### Display Name Formatting (MiniMessage)

```yaml
# Solid colors
display_name: "<red>Fire Blade"
display_name: "<#FF5733>Custom Hex Color"

# Gradients
display_name: "<gradient:#FF4500:#FFD700>Flame Sword</gradient>"
display_name: "<gradient:red:gold:yellow>Rainbow Item</gradient>"

# Styles
display_name: "<bold><gold>Epic Item</gold></bold>"
display_name: "<italic><gray>Mysterious Artifact</gray></italic>"

# Combined
display_name: "<bold><gradient:#00CED1:#20B2AA>Ocean's Edge</gradient></bold>"
```

---

## Creating Furniture

Furniture items are regular custom items with an additional `furniture:` section. When a player uses `/wfurniture place <id>`, the plugin spawns a Display Entity in the world.

### Basic Furniture

```yaml
wooden_chair:
  material: LEATHER_HORSE_ARMOR
  display_name: "<#8B4513>Wooden Chair"
  lore:
    - "<gray>A simple oak chair"
  custom_model_data: 30100

  furniture:
    hitbox:
      width: 0.6           # Interaction entity width (blocks)
      height: 0.8           # Interaction entity height (blocks)
    transform:
      scale: [1.0, 1.0, 1.0]         # Display size [x, y, z]
      translation: [0.0, -0.5, 0.0]  # Offset from placement point [x, y, z]
    rotation: PLAYER_YAW    # How it rotates when placed
    barrier: false           # Place invisible barrier block for collision?
    sittable: false          # Can players sit on it?
```

### Sittable Furniture

```yaml
cozy_sofa:
  material: LEATHER_HORSE_ARMOR
  display_name: "<white>Cozy Sofa"
  lore:
    - "<gray>Sink into comfort"
  custom_model_data: 30101

  furniture:
    hitbox:
      width: 1.5
      height: 0.8
    transform:
      scale: [1.0, 1.0, 1.0]
      translation: [0.0, -0.5, 0.0]
    rotation: PLAYER_YAW
    barrier: false
    sittable: true           # Players can right-click to sit
    sit_height: 0.3          # How high the player sits (blocks above placement)
```

### Large Furniture with Collision

```yaml
stone_fireplace:
  material: LEATHER_HORSE_ARMOR
  display_name: "<gold>Stone Fireplace"
  lore:
    - "<gray>Warm and inviting"
    - "<dark_gray>Decorative only"
  custom_model_data: 30102

  furniture:
    hitbox:
      width: 2.0
      height: 1.5
    transform:
      scale: [1.5, 1.5, 1.5]         # 1.5x bigger than normal
      translation: [0.0, -0.3, 0.0]
    rotation: PLAYER_YAW
    barrier: true            # Places BARRIER block so players can't walk through
    sittable: false
```

### Furniture Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `hitbox.width` | float | 1.0 | Width of the clickable interaction zone |
| `hitbox.height` | float | 1.0 | Height of the clickable interaction zone |
| `transform.scale` | [x,y,z] | [1,1,1] | Visual size multiplier |
| `transform.translation` | [x,y,z] | [0,0,0] | Position offset from placement block |
| `rotation` | enum | PLAYER_YAW | `PLAYER_YAW` (faces player direction), `NONE` (north), `FIXED` (no rotation) |
| `barrier` | boolean | false | Place invisible collision block |
| `sittable` | boolean | false | Allow right-click sitting |
| `sit_height` | float | 0.5 | Seat height offset (only if sittable) |

### Translation Tips

The `translation` value `[x, y, z]` offsets the display entity from where it's spawned:

- Furniture spawns **on top** of the block you look at
- `[0.0, -0.5, 0.0]` - good default, model center is 0.5 blocks lower (floor level)
- `[0.0, 0.0, 0.0]` - model center is at the top surface of the target block
- `[0.0, -1.0, 0.0]` - model center is at the bottom of the target block
- Adjust per-model until it looks right in-game

### Scale Tips

- `[1.0, 1.0, 1.0]` - normal size (1 block = 1 unit in your model)
- `[0.5, 0.5, 0.5]` - half size
- `[2.0, 2.0, 2.0]` - double size
- `[1.0, 0.5, 1.0]` - normal width/depth but half height (for flat items)

---

## Creating Custom Blocks

Custom blocks use NoteBlock state hijacking. The server has 800 NoteBlock states available (16 instruments x 25 notes x 2 powered). Each custom block gets a unique state automatically.

### Basic Custom Block

```yaml
marble_block:
  material: NOTE_BLOCK
  display_name: "<white>Marble Block"
  lore:
    - "<gray>Smooth polished marble"
  custom_model_data: 20001

  block:
    hardness: 1.5               # Break time multiplier (stone = 1.5)
    drop: marble_block           # Item ID to drop when broken (itself)
    break_tool: PICKAXE          # Required tool type (or omit for any tool)
    sounds:
      place: block.stone.place   # Sound when placed
      break: block.stone.break   # Sound when broken
```

### Block Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `hardness` | float | 1.0 | Break time multiplier |
| `drop` | string | (self) | Item ID dropped when broken |
| `break_tool` | string | (any) | `PICKAXE`, `AXE`, `SHOVEL`, `HOE` |
| `sounds.place` | string | - | Minecraft sound key for placement |
| `sounds.break` | string | - | Minecraft sound key for breaking |

### Server Requirement

Custom blocks require this setting in `config/paper-global.yml`:

```yaml
block-updates:
  disable-noteblock-updates: true
```

Without this, NoteBlock states reset when adjacent blocks update.

---

## Creating Wearable Clothes & Cosmetics

Wearable items are standard custom items using specific materials that Minecraft renders on the player model. The custom appearance comes entirely from the resource pack model/texture.

### Head Slot Items (Hats, Helmets, Masks)

```yaml
top_hat:
  material: CARVED_PUMPKIN
  display_name: "<gray>Top Hat"
  lore:
    - "<gray>A gentleman's choice"
  custom_model_data: 40001
  item_flags:
    - HIDE_ATTRIBUTES
```

**Why `CARVED_PUMPKIN`?** It renders on the head when equipped and has no vanilla armor value to conflict. Your resource pack model replaces its appearance at the given CMD.

### Chest Armor (Jackets, Shirts, Capes)

```yaml
leather_jacket:
  material: LEATHER_CHESTPLATE
  display_name: "<#8B4513>Leather Jacket"
  lore:
    - "<gray>Rugged and stylish"
  custom_model_data: 40050
  item_flags:
    - HIDE_ATTRIBUTES
    - HIDE_DYE
```

### Leg Armor (Pants, Skirts)

```yaml
jeans:
  material: LEATHER_LEGGINGS
  display_name: "<blue>Blue Jeans"
  lore:
    - "<gray>Classic denim"
  custom_model_data: 40100
  item_flags:
    - HIDE_ATTRIBUTES
    - HIDE_DYE
```

### Boots (Shoes, Sneakers)

```yaml
sneakers:
  material: LEATHER_BOOTS
  display_name: "<white>Sneakers"
  lore:
    - "<gray>Lightweight and fast"
  custom_model_data: 40150
  mechanics:
    speed:
      amplifier: 0
      duration: 100
  item_flags:
    - HIDE_ATTRIBUTES
    - HIDE_DYE
```

### Material Choices for Wearables

| Slot | Material | Notes |
|------|----------|-------|
| Head | `CARVED_PUMPKIN` | No overlay, renders on head |
| Head (alt) | `PLAYER_HEAD` | Requires skull texture, not CMD |
| Chest | `LEATHER_CHESTPLATE` | Dyeable, use `HIDE_DYE` flag |
| Legs | `LEATHER_LEGGINGS` | Dyeable, use `HIDE_DYE` flag |
| Feet | `LEATHER_BOOTS` | Dyeable, use `HIDE_DYE` flag |
| Handheld cosmetic | `LEATHER_HORSE_ARMOR` | Good for parasols, canes, etc. |

**Tip:** Always add `HIDE_ATTRIBUTES` and `HIDE_DYE` to cosmetic armor so vanilla stats and dye info don't clutter the tooltip.

### Cosmetics with Effects

You can attach mechanics to wearables, but note that mechanics currently trigger on **interact/use events**, not passively while worn. For passive effects while wearing armor, you'd need a custom mechanic implementation.

---

## Resource Pack Setup

### Texture Files

For every item with a `custom_model_data`, place a matching texture:

```
plugins/ServerItems/pack/assets/serverplugins/textures/item/<item_id>.png
```

**Requirements:**
- PNG format
- Square dimensions recommended: 16x16, 32x32, 64x64, or 128x128
- File name must exactly match the item ID (e.g., `flame_sword.png` for item `flame_sword`)
- Transparent backgrounds work fine

### What Gets Auto-Generated

When you run `/witems pack generate`, the plugin creates:

1. **`pack.mcmeta`** - Pack metadata with format version
2. **Vanilla model overrides** - For each base material (e.g., `diamond_sword.json`), adds CMD predicates pointing to your custom models
3. **Item models** - For items without a custom `.json` model, generates a simple flat model referencing your texture
4. **Atlas JSON** - Texture atlas for the `serverplugins` namespace
5. **Geyser mappings** - `geyser_mappings.json` for Bedrock support

### Custom Model JSON (for 3D items)

If you want a 3D model instead of a flat texture, place a model JSON:

```
plugins/ServerItems/pack/assets/serverplugins/models/item/<item_id>.json
```

If this file exists, the auto-generator skips creating a flat model for that item.

### Example: Simple Flat Item Model (auto-generated)

This is what the plugin generates automatically when you only provide a texture:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "serverplugins:item/flame_sword"
  }
}
```

### Example: Custom 3D Model (you provide this)

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "serverplugins:item/marble_block"
  },
  "display": {
    "thirdperson_righthand": {
      "rotation": [10, -45, 170],
      "translation": [0, 1.5, -2.75],
      "scale": [0.375, 0.375, 0.375]
    },
    "firstperson_righthand": {
      "rotation": [0, -135, 25],
      "translation": [0, 4, 2],
      "scale": [0.4, 0.4, 0.4]
    }
  }
}
```

### Vanilla Override File (auto-generated)

The plugin auto-generates overrides for each base material. For example, if you have items using `DIAMOND_SWORD` with CMD 10001 and 10002:

```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "minecraft:item/diamond_sword"
  },
  "overrides": [
    { "predicate": { "custom_model_data": 10001 }, "model": "serverplugins:item/flame_sword" },
    { "predicate": { "custom_model_data": 10002 }, "model": "serverplugins:item/ice_blade" }
  ]
}
```

### Applying the Resource Pack

After generating, the pack is at:
```
plugins/ServerItems/pack/generated/ServerPlugins-Items.zip
```

Options for distribution:
- Upload to your web host and set the server resource pack URL in `server.properties`
- Merge with your existing resource pack if you have one
- Use a plugin like `ItemsAdder` or your own hosting to serve it

---

## 3D Models for Furniture

Furniture looks best with proper 3D models. Here's how to create them.

### Tools for Making Models

- **[Blockbench](https://www.blockbench.net/)** (free, recommended) - Visual model editor with MC format support
- **[Cubik Studio](https://cubik.studio/)** - Alternative model editor
- **[MCreator](https://mcreator.net/)** - Has a built-in model maker

### Blockbench Workflow

1. Open Blockbench and create a new **Java Block/Item** project
2. Set the texture size (32x32 or 64x64 for furniture)
3. Build your model using cubes and planes
4. Paint textures directly on the model
5. Set up **display transforms** for how it looks in hand, on ground, etc.
6. **Export** as `.json` (Java Edition model format)

### Display Transforms for Furniture

Furniture models need specific display settings since they're rendered as ItemDisplay entities:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "0": "serverplugins:item/wooden_chair"
  },
  "elements": [
    // ... your model cubes ...
  ],
  "display": {
    "head": {
      "translation": [0, 0, 0],
      "scale": [1, 1, 1]
    },
    "fixed": {
      "translation": [0, 0, 0],
      "scale": [1, 1, 1]
    },
    "ground": {
      "translation": [0, 3, 0],
      "scale": [0.25, 0.25, 0.25]
    }
  }
}
```

The `fixed` display is what ItemDisplay entities use. Keep it at `[1,1,1]` scale and `[0,0,0]` translation, then control size/position through the YAML `transform` section instead.

### Importing Existing Models

If you have models from another plugin (e.g., converting from Oraxen, ItemsAdder):

1. The `.json` model format is standard Minecraft - it works directly
2. Copy the model `.json` to `pack/assets/serverplugins/models/item/`
3. Copy the texture `.png` to `pack/assets/serverplugins/textures/item/`
4. Make sure the texture paths in the JSON reference `serverplugins:item/<name>` instead of the old namespace
5. Create a matching YAML definition with the same item ID

### Texture Path Fix When Importing

If your imported model has:
```json
"textures": {
    "0": "oraxen:item/wooden_chair"
}
```

Change it to:
```json
"textures": {
    "0": "serverplugins:item/wooden_chair"
}
```

---

## Mechanics System

Mechanics add interactive behavior to items. Add them under the `mechanics:` section of any item.

### Available Mechanics

#### durability - Custom Durability

Tracks durability separately from vanilla, stored in the item's PersistentDataContainer. The vanilla durability bar updates proportionally.

```yaml
mechanics:
  durability:
    max: 500        # Maximum durability points
```

When durability reaches 0, the item breaks.

#### cooldown - Use Cooldown

Prevents spamming item abilities.

```yaml
mechanics:
  cooldown:
    ticks: 40       # Cooldown in ticks (20 ticks = 1 second)
```

#### command - Execute Commands

Runs commands when the item is right-clicked.

```yaml
mechanics:
  command:
    player:                    # Commands run as the player
      - "msg {player} You used the wand!"
    console:                   # Commands run from console
      - "give {player} diamond 1"
      - "effect give {player} speed 60 1"
```

Placeholders: `{player}` = player name, `{item}` = item ID.

#### consumable - Custom Food Effects

Adds healing and potion effects when an edible item is consumed.

```yaml
mechanics:
  consumable:
    heal: 10                   # Hearts to restore (1 heart = 2 HP)
    food: 6                    # Food points to restore
    saturation: 1.2            # Saturation restoration
    effects:
      regeneration:
        duration: 200          # Duration in ticks
        amplifier: 1           # Effect level (0 = I, 1 = II)
      speed:
        duration: 600
        amplifier: 0
```

#### particle - Particle Effect

Spawns particles at the player's location on right-click.

```yaml
mechanics:
  particle:
    type: FLAME               # Particle type (Bukkit Particle enum)
    count: 20                  # Number of particles
    spread: 0.5                # Spread radius
```

#### sound - Play Sound

Plays a sound on right-click.

```yaml
mechanics:
  sound:
    name: entity.experience_orb.pickup   # Minecraft sound key
    volume: 1.0
    pitch: 1.0
```

#### speed - Speed Boost

Grants a speed potion effect on right-click.

```yaml
mechanics:
  speed:
    amplifier: 1               # Speed level (0 = I, 1 = II)
    duration: 200              # Duration in ticks
```

### Combining Mechanics

You can stack multiple mechanics on one item:

```yaml
magic_staff:
  material: BLAZE_ROD
  display_name: "<light_purple>Arcane Staff"
  custom_model_data: 10050
  glow: true
  mechanics:
    cooldown:
      ticks: 100
    command:
      console:
        - "effect give {player} levitation 3 1"
    particle:
      type: ENCHANTED_HIT
      count: 30
      spread: 1.0
    sound:
      name: entity.evoker.cast_spell
      volume: 1.0
      pitch: 0.8
```

---

## Selling Items in the Shop

Items created with ServerItems integrate with the Genesis/BossShopPro shop by running the `witems give` command as a purchase reward.

### Genesis Shop Entry Format

```yaml
  MyItem:
    MenuItem:
    - type:BOOK
    - amount:1
    - name:&aWooden Chair
    - 'lore1: &7A beautiful chair for your home.'
    - 'lore3:&f ▪ category: &afurniture'
    - 'lore4:&f ▪ price: #dad45e%price%'
    - 'lore6:&f &eLeft click to buy'
    RewardType: COMMAND
    Reward:
    - witems give %player% wooden_chair
    PriceType: MONEY
    Price: 350
    Message: '&f &fYou bought a &ewooden chair &ffor &a%price%'
    ExtraPermission: ''
    InventoryLocation: 21
```

The key part is:
```yaml
    RewardType: COMMAND
    Reward:
    - witems give %player% wooden_chair
```

This runs `/witems give <buyer> wooden_chair` when purchased.

### Command Format for Shops

```
witems give %player% <item_id> [amount]
```

- `%player%` - BossShopPro placeholder for the buyer's name
- `<item_id>` - Your item's YAML ID (lowercase)
- `[amount]` - Optional, defaults to 1

---

## Commands Reference

### Item Commands (`/witems` or `/wi`)

| Command | Permission | Description |
|---------|-----------|-------------|
| `/witems give <player> <id> [amount]` | `serveritems.give` | Give a custom item |
| `/witems list [page]` | `serveritems.admin` | List all registered items |
| `/witems info <id>` | `serveritems.admin` | Show item details and mechanics |
| `/witems browse` | `serveritems.admin` | Open the item browser GUI |
| `/witems reload` | `serveritems.reload` | Reload all configs and items |
| `/witems pack generate` | `serveritems.admin` | Regenerate the resource pack |

### Furniture Commands (`/wfurniture` or `/wf`)

| Command | Permission | Description |
|---------|-----------|-------------|
| `/wfurniture place <id>` | `serveritems.place` | Place furniture where you're looking |
| `/wfurniture remove` | `serveritems.place` | Remove the furniture you're looking at |
| `/wfurniture list` | `serveritems.admin` | List all furniture definitions |

### Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `serveritems.give` | OP | Give custom items to players |
| `serveritems.admin` | OP | Admin access (list, info, browse, pack) |
| `serveritems.reload` | OP | Reload configurations |
| `serveritems.place` | OP | Place and remove furniture |

---

## Bedrock (Geyser) Support

The pack generator automatically creates a `geyser_mappings.json` file at:
```
plugins/ServerItems/pack/generated/geyser_mappings.json
```

### Deploying Bedrock Mappings

1. Generate the pack: `/witems pack generate`
2. Copy `geyser_mappings.json` to your Velocity server:
   ```
   velocity/plugins/Geyser-Velocity/custom_mappings/serverplugins_items.json
   ```
3. Restart the Velocity proxy (Geyser doesn't hot-reload mappings)

### Mapping Format

The generated file follows the Geyser format:
```json
{
  "format_version": 1,
  "items": {
    "minecraft:leather_horse_armor": [
      {
        "custom_model_data": 30001,
        "name": "bed",
        "icon": "serverplugins.item.bed"
      }
    ]
  }
}
```

### Bedrock Resource Pack

For Bedrock clients to see custom textures, you also need a `.mcpack` with:
- Matching texture files in Bedrock format
- `textures/item_texture.json` mapping short names to texture paths

Place the `.mcpack` in `velocity/plugins/Geyser-Velocity/packs/`.

---

## Troubleshooting

### Item doesn't show custom texture

1. Verify `custom_model_data` is set in the YAML
2. Check that a matching `.png` exists in `pack/assets/serverplugins/textures/item/`
3. Run `/witems pack generate` to rebuild
4. Re-download the resource pack (disconnect and reconnect, or use `/resourcepack`)
5. Check server console for pack generation errors

### Furniture doesn't appear when placed

1. Make sure `furniture.enabled: true` in `config.yml`
2. Verify the item has a `furniture:` section in its YAML
3. Check `/wfurniture list` to confirm the definition loaded
4. Look at the server console for loading errors
5. Make sure you're looking at a solid block within 5 blocks

### Furniture floats or clips through the ground

Adjust the `translation` Y value in the furniture definition:
- Increase Y to move up: `[0.0, 0.0, 0.0]`
- Decrease Y to move down: `[0.0, -1.0, 0.0]`
- The default `[0.0, -0.5, 0.0]` works for most 1-block-tall models

### Custom block doesn't place

1. Ensure `blocks.enabled: true` in `config.yml`
2. Verify `material: NOTE_BLOCK` in the item YAML
3. Make sure `disable-noteblock-updates: true` is set in `paper-global.yml`
4. Check that the item has a `block:` section

### Items not loading at all

1. Check YAML syntax (spaces, not tabs!)
2. Look at server console during startup for "Failed to load item" warnings
3. Verify the `material` value is a valid Bukkit Material name
4. Run `/witems reload` and watch console output

### Resource pack too large

- Use 16x16 or 32x32 textures instead of 64x64+
- Optimize PNGs with [TinyPNG](https://tinypng.com/) or `optipng`
- Use flat textures instead of 3D models where possible
