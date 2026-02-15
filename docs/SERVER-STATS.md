# ServerStats

**Version:** 1.0-SNAPSHOT
**Type:** HTTP Statistics API
**Main Class:** `net.serverplugins.stats.ServerStats`

## Overview

ServerStats provides a REST API for server statistics accessible via HTTP. Perfect for website integration, Discord bots, and external monitoring tools.

## Key Features

### HTTP Server
- Built-in HTTP server
- RESTful API endpoints
- JSON responses
- CORS support

### Real-Time Data
- Live player count
- TPS monitoring
- Server version
- Uptime tracking
- Plugin list

### External Integration
- Website widgets
- Discord bot integration
- Monitoring dashboards
- Mobile apps

## API Endpoints

### GET /api/status
Server status information
```json
{
  "online": true,
  "players": {
    "online": 15,
    "max": 100
  },
  "tps": 20.0,
  "version": "1.21.4",
  "uptime": 3600000
}
```

### GET /api/players
List of online players
```json
{
  "count": 15,
  "players": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Player1",
      "ping": 45
    }
  ]
}
```

### GET /api/plugins
List of plugins
```json
{
  "count": 50,
  "plugins": [
    {
      "name": "ServerAPI",
      "version": "1.0-SNAPSHOT",
      "enabled": true
    }
  ]
}
```

## Configuration

### config.yml
```yaml
# HTTP Server
http:
  enabled: true
  host: "0.0.0.0"
  port: 8080
  cors-enabled: true
  allowed-origins:
    - "*"

# API Settings
api:
  require-auth: false
  api-key: "your-secret-key"
  rate-limit: 60  # Requests per minute

# Cached Data
cache:
  update-interval: 5  # Seconds
  player-list: true
  tps: true
  plugin-list: false  # Privacy

# Security
security:
  whitelist-ips: []
  blacklist-ips: []
```

## Dependencies

- ServerAPI
