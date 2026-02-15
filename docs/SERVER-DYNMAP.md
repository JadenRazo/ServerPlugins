# ServerDynmap

**Version:** 1.0-SNAPSHOT
**Type:** Dynmap Integration for Claims
**Main Class:** `net.serverplugins.dynmap.ServerDynmap`

## Overview

ServerDynmap integrates ServerClaim with Dynmap to display claims on the web map with real-time updates and custom markers.

## Key Features

### Claim Visualization
- Display claims on Dynmap
- Show claim boundaries
- Color-coded by owner
- Custom claim markers

### Real-Time Updates
- Sync claim changes to map
- Update on claim/unclaim
- Member changes reflected
- Name/description updates

### Custom Markers
- Claim markers with owner info
- Warp point markers
- Nation territory display
- Configurable styles

## Configuration

### config.yml
```yaml
# Dynmap Integration
dynmap:
  enabled: true
  layer-name: "Claims"
  layer-priority: 10

  # Claim Styling
  claims:
    stroke-color: "#FF0000"
    stroke-opacity: 0.8
    stroke-weight: 2
    fill-color: "#FF0000"
    fill-opacity: 0.2

  # Markers
  markers:
    show-warps: true
    show-spawns: true
    warp-icon: "portal"

# Update Settings
updates:
  real-time: true
  batch-updates: false
```

## Dependencies

- ServerAPI
- ServerClaim
- dynmap
