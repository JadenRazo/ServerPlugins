# NPC Portrait Icons Guide

## Overview

NPC portrait icons are custom Unicode characters mapped to images in your resource pack.

## Available Unicode Ranges

### Korean Hangul Syllables (Most Common)
- Range: U+AC00 to U+D7A3
- Example: 가, 각, 갂, 섑, 섒, 섓
- **Used in your server**: `섑⻔섒` (welcome message)

### Unicode Private Use Area
- Range: U+E000 to U+F8FF
- Safe for custom mapping
- Won't conflict with real text

### CJK Unified Ideographs Extension
- Range: U+4E00 to U+9FFF
- Example: 一, 丁, 七, ⻔
- Large range available

## Creating NPC Portrait Characters

### Step 1: Choose Unicode Characters

Pick unique Unicode characters for each NPC:

```yaml
# NPC Character Mapping (90px height, ascent 25)
Sylvia:   퀁 (U+D001)  - item/ui/npcs/sylvia.png
August:   퀂 (U+D002)  - item/ui/npcs/august.png
Cassian:  퀃 (U+D003)  - item/ui/npcs/cassian.png
Gandalf:  퀄 (U+D004)  - item/ui/npcs/gandalf.png
Marcus:   퀅 (U+D005)  - item/ui/npcs/marcus.png
Mark:     퀆 (U+D006)  - item/ui/npcs/mark.png
Noah:     퀇 (U+D007)  - item/ui/npcs/noah.png
Petra:    퀈 (U+D008)  - item/ui/npcs/petra.png
Thyra:    퀉 (U+D009)  - item/ui/npcs/thyra.png
```

### Step 2: Resource Pack Structure

```
resourcepack/
├── pack.mcmeta
└── assets/
    └── minecraft/
        ├── font/
        │   └── default.json          # Font configuration
        └── textures/
            └── font/
                └── npc_portraits.png  # NPC portrait sprite sheet
```

### Step 3: Font Configuration

**File: `assets/minecraft/font/default.json`**

```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "minecraft:font/npc_portraits.png",
      "ascent": 25,
      "height": 90,
      "chars": [
        "퀁퀂퀃퀄퀅퀆퀇퀈퀉"
      ]
    }
  ]
}
```

### Step 4: Create Portrait Sprite Sheet

**Image specs:**
- Width: 90 pixels × number of characters (810 pixels for 9 NPCs)
- Height: 90 pixels
- Format: PNG with transparency
- Layout: Each 90x90 section = one character

**Example layout for `npc_portraits.png` (810x90):**
```
[Sylvia][August][Cassian][Gandalf][Marcus][Mark][Noah][Petra][Thyra]
  퀁      퀂      퀃       퀄       퀅     퀆     퀇     퀈     퀉
```

## Using in Dialogs

### Basic Usage

```yaml
dialog:
  greeting:
    message:
      - "퀁⻔⻔&d&lSylvia"
      - "퀁⻔⻔&7Hello, {player}!"
```

### With Negative Spaces (Advanced Positioning)

Use negative spaces to overlap text:

```yaml
dialog:
  greeting:
    message:
      - "퀁⻔⻔&d&lSylvia"  # ⻔ = custom space character
      - "퀁⻔⻔&7Hello, {player}!"
```

### Common Negative Space Characters

```yaml
\uF801: -1 pixel
\uF802: -2 pixels
\uF803: -4 pixels
\uF804: -8 pixels
\uF805: -16 pixels
\uF806: -32 pixels
\uF807: -64 pixels
\uF808: -128 pixels
```

## Quick Reference Table

| NPC | Unicode | Hex Code | Character |
|-----|---------|----------|-----------|
| Sylvia | U+D001 | \uD001 | 퀁 |
| August | U+D002 | \uD002 | 퀂 |
| Cassian | U+D003 | \uD003 | 퀃 |
| Gandalf | U+D004 | \uD004 | 퀄 |
| Marcus | U+D005 | \uD005 | 퀅 |
| Mark | U+D006 | \uD006 | 퀆 |
| Noah | U+D007 | \uD007 | 퀇 |
| Petra | U+D008 | \uD008 | 퀈 |
| Thyra | U+D009 | \uD009 | 퀉 |

## Tools for Creating Portraits

### Online Tools
- **MC-Font-Tool**: https://github.com/EventHor1zon/mc-font-tool
- **Minecraft Font Generator**: https://www.fontspace.com/category/minecraft
- **Custom Font Converter**: Various online generators

### Image Editors
- **GIMP**: Free, open-source
- **Photoshop**: Professional tool
- **Paint.NET**: Windows-friendly
- **Aseprite**: Pixel art focused

## Integration with ServerNpcs

### Update Dialog Files

**File: `dialogs/sylvia.yml`**

```yaml
npc:
  name: "sylvia"
  display-name: "퀁⻔⻔&d&lSylvia"  # Added portrait icon with spacing

dialog:
  greeting:
    message:
      - "&f"
      - "퀁⻔⻔&d&lHELLO, {player}!"
      - "퀁⻔⻔&7I'm Sylvia, your guide."
      - "&f"
```

### Java Code Helper

```java
public class NpcIcons {
    // NPC Portrait Icons (90px height, ascent 25)
    public static final String SYLVIA = "\uD001";   // 퀁
    public static final String AUGUST = "\uD002";   // 퀂
    public static final String CASSIAN = "\uD003";  // 퀃
    public static final String GANDALF = "\uD004";  // 퀄
    public static final String MARCUS = "\uD005";   // 퀅
    public static final String MARK = "\uD006";     // 퀆
    public static final String NOAH = "\uD007";     // 퀇
    public static final String PETRA = "\uD008";    // 퀈
    public static final String THYRA = "\uD009";    // 퀉

    // Core UI Elements
    public static final String SPACE_CUSTOM = "\u2ED4";  // ⻔
    public static final String COIN = "\uE001";          //
}
```

## Testing

1. **Enable resource pack** on your client
2. **Reload resource pack**: F3 + T
3. **Test in chat**: `/say 퀁퀂퀃퀄`
4. **Check dialogs**: `/dialog sylvia`

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Shows Korean text instead of icon | Resource pack not loaded |
| Shows blank/box | Unicode not mapped in font file |
| Wrong image shows | Check sprite sheet order |
| Icons too big/small | Adjust `ascent` and `height` in JSON |

## Example: Full Sylvia Dialog with Icon

```yaml
npc:
  name: "sylvia"
  display-name: "퀁⻔⻔&d&lSylvia"  # Icon with custom spacing

dialog:
  greeting:
    message:
      - "&f"
      - "퀁⻔⻔&d&l━━━━━━━━━━━━━━━━━━━━━━"
      - "퀁⻔⻔"
      - "퀁⻔⻔&d&lNPC SYLVIA"
      - "퀁⻔⻔&7ARE YOU NEW HERE? I CAN GIVE YOU THE WISEST"
      - "퀁⻔⻔&7ADVICE TO MAKE YOUR ADVENTURE MUCH EASIER."
      - "퀁⻔⻔&7DO YOU WANT TO KNOW SOMETHING FROM ME?"
      - "퀁⻔⻔"
      - "퀁⻔⻔&f• SERVER GUIDE"
      - "퀁⻔⻔&f• SERVER LINKS"
      - "퀁⻔⻔"
      - "⻔⻔&c&l! &cYOU NEED TO HOVER AND CLICK THE OPTIONS"
      - "&f"
```

This creates a bordered dialog box with the Sylvia icon on each line!
