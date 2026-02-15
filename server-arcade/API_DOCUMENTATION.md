# ServerArcade REST API Documentation

API for integrating gambling statistics into example.com website and Discord bot.

## Configuration

Enable the API in `plugins/ServerArcade/config.yml`:

```yaml
api:
  enabled: true
  port: 8080
```

## Base URL

```
http://<server-ip>:8080/api/arcade
```

---

## Endpoints

### 1. Get Leaderboard

Fetch top players by various metrics.

**Endpoint:**
```
GET /api/arcade/leaderboard/{type}?limit=10
```

**Path Parameters:**
- `type` (string) - Leaderboard type:
  - `net_profit` - Top players by net profit (default)
  - `crash_mult` - Highest crash multipliers achieved
  - `biggest_win` - Largest single wins across all games
  - `total_wagered` - Most money wagered

**Query Parameters:**
- `limit` (int, optional) - Number of entries to return (default: 10, max: 100)

**Example Request:**
```
GET /api/arcade/leaderboard/net_profit?limit=10
```

**Example Response:**
```json
[
  {
    "rank": 1,
    "player_name": "PlayerName",
    "player_uuid": "550e8400-e29b-41d4-a716-446655440000",
    "value": 45230,
    "label": "$45.2K"
  },
  {
    "rank": 2,
    "player_name": "AnotherPlayer",
    "player_uuid": "550e8400-e29b-41d4-a716-446655440001",
    "value": 32100,
    "label": "$32.1K"
  }
]
```

**Response Fields:**
- `rank` (int) - Player's position on leaderboard (1-indexed)
- `player_name` (string) - Minecraft username
- `player_uuid` (string) - Player's UUID
- `value` (number) - Raw numeric value
- `label` (string) - Formatted display value (with K/M/B suffixes)

---

### 2. Get Player Statistics

Fetch detailed statistics for a specific player.

**Endpoint:**
```
GET /api/arcade/stats/{uuid}
```

**Path Parameters:**
- `uuid` (string) - Player's Minecraft UUID (with or without dashes)

**Example Request:**
```
GET /api/arcade/stats/550e8400-e29b-41d4-a716-446655440000
```

**Example Response:**
```json
{
  "player_name": "PlayerName",
  "net_profit": 45230,
  "total_wagered": 123450,
  "total_won": 168680,
  "total_lost": 78220,
  "win_rate": "48.2%",
  "crash": {
    "total_bets": 1234,
    "total_wins": 587,
    "biggest_win": 24700,
    "highest_mult": 47.3,
    "win_rate": "47.6%"
  },
  "lottery": {
    "total_bets": 45,
    "total_wins": 3,
    "biggest_win": 89500
  },
  "dice": {
    "total_bets": 567,
    "total_wins": 295,
    "biggest_win": 12000,
    "win_rate": "52.0%"
  },
  "streaks": {
    "current": 3,
    "best_win": 12,
    "worst_loss": -8
  }
}
```

**Response Fields:**

**Overall Stats:**
- `player_name` (string) - Minecraft username
- `net_profit` (long) - Total profit/loss (can be negative)
- `total_wagered` (long) - Total amount bet across all games
- `total_won` (long) - Total amount won
- `total_lost` (long) - Total amount lost
- `win_rate` (string) - Overall win percentage (formatted with %)

**Crash Stats (`crash` object):**
- `total_bets` (int) - Number of crash games played
- `total_wins` (int) - Number of successful cash-outs
- `biggest_win` (int) - Largest single payout
- `highest_mult` (double) - Highest multiplier cashed out at
- `win_rate` (string) - Crash-specific win percentage

**Lottery Stats (`lottery` object):**
- `total_bets` (int) - Number of lottery tickets purchased
- `total_wins` (int) - Number of jackpots won
- `biggest_win` (int) - Largest jackpot won

**Dice Stats (`dice` object):**
- `total_bets` (int) - Number of dice rolls
- `total_wins` (int) - Number of winning rolls
- `biggest_win` (int) - Largest dice payout
- `win_rate` (string) - Dice-specific win percentage

**Streaks (`streaks` object):**
- `current` (int) - Current win/loss streak (positive = wins, negative = losses)
- `best_win` (int) - Longest winning streak
- `worst_loss` (int) - Longest losing streak (negative number)

**Error Responses:**
- `400 Bad Request` - Invalid UUID format
- `404 Not Found` - Player has no gambling statistics

---

### 3. Get Recent Big Wins

Fetch recent high-value wins across all games.

**Endpoint:**
```
GET /api/arcade/recent?limit=20
```

**Query Parameters:**
- `limit` (int, optional) - Number of recent wins to return (default: 20, max: 100)

**Example Request:**
```
GET /api/arcade/recent?limit=20
```

**Example Response:**
```json
[
  {
    "player_name": "PlayerName",
    "game_type": "crash",
    "bet": 1000,
    "payout": 24700,
    "multiplier": 24.7,
    "timestamp": 1706112000000
  },
  {
    "player_name": "AnotherPlayer",
    "game_type": "lottery",
    "bet": 500,
    "payout": 89500,
    "multiplier": 179.0,
    "timestamp": 1706111500000
  }
]
```

**Response Fields:**
- `player_name` (string) - Winner's username
- `game_type` (string) - Type of game (`crash`, `lottery`, `dice`)
- `bet` (int) - Amount wagered
- `payout` (int) - Amount won
- `multiplier` (double) - Win multiplier (payout / bet)
- `timestamp` (long) - Unix timestamp in milliseconds

**Note:** Only includes wins with payout > $10,000

---

## CORS Support

All endpoints include CORS headers to allow cross-origin requests from example.com:

```
Access-Control-Allow-Origin: *
```

---

## Error Handling

All endpoints return standard HTTP status codes:

- `200 OK` - Successful request
- `400 Bad Request` - Invalid parameters
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Database or server error

Error responses include JSON with error message:

```json
{
  "error": "Invalid UUID"
}
```

---

## Rate Limiting

**Current:** No rate limiting implemented

**Recommended for Production:**
- Implement rate limiting on website/proxy level
- Suggested: 60 requests per minute per IP
- Consider caching responses for 30-60 seconds

---

## Example Website Integration

### JavaScript (Fetch API)

```javascript
// Fetch top 10 by net profit
async function getLeaderboard() {
  const response = await fetch('http://SERVER_IP:8080/api/arcade/leaderboard/net_profit?limit=10');
  const data = await response.json();

  data.forEach(entry => {
    console.log(`#${entry.rank} - ${entry.player_name}: ${entry.label}`);
  });
}

// Fetch player stats
async function getPlayerStats(uuid) {
  const response = await fetch(`http://SERVER_IP:8080/api/arcade/stats/${uuid}`);
  const data = await response.json();

  console.log(`${data.player_name} - Net Profit: $${data.net_profit.toLocaleString()}`);
  console.log(`Crash: ${data.crash.total_bets} bets, ${data.crash.win_rate} win rate`);
}

// Fetch recent big wins
async function getRecentWins() {
  const response = await fetch('http://SERVER_IP:8080/api/arcade/recent?limit=10');
  const data = await response.json();

  data.forEach(win => {
    const date = new Date(win.timestamp);
    console.log(`${win.player_name} won $${win.payout.toLocaleString()} on ${win.game_type}`);
  });
}
```

### React Component Example

```jsx
import { useState, useEffect } from 'react';

function ArcadeLeaderboard() {
  const [leaderboard, setLeaderboard] = useState([]);

  useEffect(() => {
    fetch('http://SERVER_IP:8080/api/arcade/leaderboard/net_profit?limit=10')
      .then(res => res.json())
      .then(data => setLeaderboard(data));
  }, []);

  return (
    <div>
      <h2>Top Gamblers</h2>
      <table>
        <thead>
          <tr>
            <th>Rank</th>
            <th>Player</th>
            <th>Net Profit</th>
          </tr>
        </thead>
        <tbody>
          {leaderboard.map(entry => (
            <tr key={entry.player_uuid}>
              <td>{entry.rank}</td>
              <td>{entry.player_name}</td>
              <td>{entry.label}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

---

## Discord Bot Integration

### Discord.js Example

```javascript
const { SlashCommandBuilder } = require('discord.js');
const fetch = require('node-fetch');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('arcadestats')
    .setDescription('View ServerPlugins arcade statistics')
    .addStringOption(option =>
      option.setName('player')
        .setDescription('Minecraft username or UUID')
        .setRequired(true)
    ),

  async execute(interaction) {
    const player = interaction.options.getString('player');

    // Convert username to UUID (you'll need a username->UUID API)
    const uuid = await getUUIDFromUsername(player);

    const response = await fetch(`http://SERVER_IP:8080/api/arcade/stats/${uuid}`);
    const stats = await response.json();

    const embed = {
      title: `🎰 ${stats.player_name}'s Arcade Stats`,
      color: 0xFFD700,
      fields: [
        {
          name: 'Net Profit',
          value: `$${stats.net_profit.toLocaleString()}`,
          inline: true
        },
        {
          name: 'Total Wagered',
          value: `$${stats.total_wagered.toLocaleString()}`,
          inline: true
        },
        {
          name: 'Win Rate',
          value: stats.win_rate,
          inline: true
        },
        {
          name: '🎲 Crash',
          value: `${stats.crash.total_bets} games\n${stats.crash.win_rate} win rate\nHighest: ${stats.crash.highest_mult}x`,
          inline: true
        },
        {
          name: '🎰 Lottery',
          value: `${stats.lottery.total_wins} jackpots won\nBiggest: $${stats.lottery.biggest_win.toLocaleString()}`,
          inline: true
        },
        {
          name: '🎲 Dice',
          value: `${stats.dice.total_bets} rolls\n${stats.dice.win_rate} win rate`,
          inline: true
        }
      ]
    };

    await interaction.reply({ embeds: [embed] });
  }
};
```

---

## Production Deployment

### Security Recommendations

1. **Firewall Rules:**
   ```bash
   # Only allow connections from example.com server
   sudo ufw allow from WEBSITE_IP to any port 8080
   ```

2. **Reverse Proxy (Nginx):**
   ```nginx
   server {
       listen 80;
       server_name api.example.com;

       location /api/arcade {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;

           # Rate limiting
           limit_req zone=api burst=10 nodelay;
       }
   }
   ```

3. **SSL/TLS:**
   - Use Let's Encrypt for HTTPS
   - Update website to use `https://api.example.com`

### Performance Optimization

1. **Database Indexes:**
   Already included in schema.sql:
   - `idx_net_profit` on net_profit column
   - `idx_crash_mult` on crash_highest_mult
   - `idx_timestamp` on history table

2. **Caching:**
   Implement Redis caching for leaderboards (TTL: 60 seconds)

3. **Connection Pooling:**
   ServerAPI handles connection pooling automatically

---

## Support

For API issues or feature requests:
- GitHub: https://github.com/JadenRazo/SMP
- Discord: example.com/discord

---

**Last Updated:** January 24, 2026
**API Version:** 1.0.0
