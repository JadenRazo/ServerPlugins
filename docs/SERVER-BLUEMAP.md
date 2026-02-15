# ServerBlueMap

**Version:** 1.0-SNAPSHOT
**Type:** BlueMap Integration for Claims
**Main Class:** `net.serverplugins.bluemap.ServerBlueMap`

## Overview

ServerBlueMap integrates ServerClaim with BlueMap to display claims and POIs on the 3D web map with real-time synchronization.

## Key Features

### Claim Visualization
- Display claims on BlueMap
- 3D boundary rendering
- Owner information
- Custom colors per claim

### POI Management
- Points of interest system
- Add/remove POIs
- Show/hide controls
- Custom icons and descriptions

### Real-Time Updates
- Sync claim changes to map
- Update on claim modifications
- POI synchronization
- Database persistence

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/poi add <name>` | `serverbluemap.poi.add` | Add POI |
| `/poi remove <name>` | `serverbluemap.poi.remove` | Remove POI |
| `/poi list` | `serverbluemap.poi.list` | List POIs |
| `/poi info <name>` | `serverbluemap.poi.info` | POI info |
| `/poi hide <name>` | `serverbluemap.poi.hide` | Hide POI |
| `/poi show <name>` | `serverbluemap.poi.show` | Show POI |
| `/poi reload` | `serverbluemap.admin` | Reload config |

## Configuration

### config.yml
```yaml
# BlueMap Integration
bluemap:
  enabled: true
  marker-set: "claims"

  # Claim Styling
  claims:
    line-color: "#FF0000"
    line-opacity: 0.8
    line-width: 2
    fill-color: "#FF0000"
    fill-opacity: 0.15

# POI Settings
pois:
  default-icon: "poi"
  show-on-map: true
  show-coordinates: true

# Update Settings
updates:
  real-time: true
  sync-interval: 60  # Seconds
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_bluemap_pois (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    icon VARCHAR(64) DEFAULT 'poi',
    description TEXT,
    visible BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Dependencies

- ServerAPI
- ServerClaim
- BlueMap
