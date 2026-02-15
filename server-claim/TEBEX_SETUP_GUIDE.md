# Tebex Store Integration Guide - Claim Chunks

This guide explains how to sell claim chunks on your Tebex store using the ServerClaim plugin.

## Prerequisites

- ServerClaim plugin installed and running
- Tebex (Buycraft) plugin installed on your server
- Tebex store account configured

## Command for Tebex

The ServerClaim plugin provides a command specifically designed for store integration:

```
/claimadmin givechunks <player> <amount>
```

### Features

- **Works offline**: Players don't need to be online to receive chunks
- **Auto-creates player data**: If a player hasn't joined before, data will be created when they first join
- **Audit logging**: All chunk grants are logged to console with timestamp and admin/source
- **Player notification**: Online players receive an in-game notification when they receive chunks
- **Safe and atomic**: Transaction is saved to database immediately

---

## Setting Up Packages on Tebex

### Example 1: Small Chunk Package (10 chunks)

1. Go to your Tebex dashboard → Packages → Create Package
2. Set the package details:
   - **Name**: 10 Claim Chunks
   - **Price**: $2.99 (or your desired price)
   - **Description**: Expand your territory! Get 10 additional claim chunks to protect your builds.

3. Under **Commands**, add:
   ```
   claimadmin givechunks {username} 10
   ```

4. **Command mode**: Run command once
5. **Require online**: No (unchecked) - players can purchase even when offline
6. Save the package

### Example 2: Medium Chunk Package (50 chunks)

**Commands**:
```
claimadmin givechunks {username} 50
```

### Example 3: Large Chunk Package (100 chunks)

**Commands**:
```
claimadmin givechunks {username} 100
```

### Example 4: Bundle with Bonus Chunks

You can create a bundle that gives chunks + runs other commands:

**Commands**:
```
claimadmin givechunks {username} 75
give {username} diamond 32
eco give {username} 50000
```

---

## Tebex Variables

When creating packages, you can use these Tebex variables:

- `{username}` - The player's Minecraft username
- `{uuid}` - The player's UUID (not needed for this command)
- `{id}` - The transaction ID
- `{price}` - The amount paid

**Example**: Use `{username}` in the command so Tebex automatically fills in the purchaser's name.

---

## Testing Your Setup

### Test 1: Offline Player Purchase
1. Create a test package on Tebex
2. Make a test purchase (or use Tebex test mode)
3. Check server console - you should see:
   ```
   [ServerClaim] [TEBEX/ADMIN] CONSOLE gave 10 chunk(s) to PlayerName (UUID: xxx-xxx-xxx)
   ```

### Test 2: Online Player Purchase
1. Have a player online
2. Make a test purchase
3. Player should receive an in-game message:
   ```
   You received 10 claim chunk(s)! Use /claim to claim land.
   Total chunks: 20
   ```

### Test 3: Manual Testing (In-Game)
As an admin, test the command manually:
```
/claimadmin givechunks TestPlayer 10
```

You should see:
```
Successfully gave 10 chunk(s) to TestPlayer
  UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  Total chunks: 10 -> 20 (+10)
  Purchased chunks: 0 -> 10
```

---

## Common Issues and Solutions

### Issue: "Player not found"
**Cause**: Player has never joined the server
**Solution**: Player must join the server at least once before purchasing. Alternatively, encourage players to join before purchasing.

### Issue: Chunks not appearing
**Solution**:
1. Check server console for errors
2. Verify the command syntax in Tebex uses `{username}` correctly
3. Test the command manually: `/claimadmin givechunks <player> 10`
4. Check player data: `/claimadmin list <player>`

### Issue: Command not executing
**Solutions**:
1. Ensure Tebex plugin is installed and configured
2. Check that "Require online" is set to **No** in Tebex
3. Verify the command doesn't have extra spaces or typos
4. Check Tebex logs in `plugins/Tebex/` folder

---

## Pricing Recommendations

Based on your server economy and chunk system:

| Package | Chunks | Suggested Price | Value/$ |
|---------|--------|----------------|---------|
| Starter | 10     | $2.99          | 3.3 chunks/$ |
| Regular | 25     | $5.99          | 4.2 chunks/$ |
| Pro     | 50     | $9.99          | 5.0 chunks/$ |
| Elite   | 100    | $14.99         | 6.7 chunks/$ |
| Mega    | 250    | $29.99         | 8.3 chunks/$ |

**Tip**: Offer better value for larger packages to encourage bigger purchases.

---

## Advanced Configuration

### One-Time Purchase Limit

In Tebex, you can set packages to:
- **Limit**: 1 per player (one-time purchase)
- **Limit**: 3 per player (allow up to 3 purchases)
- **Unlimited**: Player can buy as many times as they want

### Sale Events

Create limited-time sales:
1. Duplicate your regular package
2. Add "[50% BONUS]" to the name
3. Modify the command:
   ```
   claimadmin givechunks {username} 75
   ```
   (for a 50 chunk package, give 75 instead)
4. Set sale dates in Tebex

### Subscription Packages

You can create monthly subscriptions that give chunks:

**Package**: VIP Subscription ($4.99/month)
**Commands**:
```
claimadmin givechunks {username} 20
lp user {username} parent add vip
```

This gives 20 chunks per month + VIP rank.

---

## Player Communication

### In-Game Signs
Create signs at spawn advertising your chunk packages:

```
[Claim Chunks]
Buy chunks at
store.yourserver.com
Expand your land!
```

### Discord Announcements
```
🏰 **Claim Chunk Store Now Open!**

Protect more of your builds with additional claim chunks!

🔹 10 Chunks - $2.99
🔹 50 Chunks - $9.99
🔹 100 Chunks - $14.99

Visit: store.yourserver.com
```

### /links Command
Add your store to `/links`:
```
Store: https://store.yourserver.com
```

---

## Audit and Analytics

### Viewing Purchase History

Check server logs for all chunk grants:
```
grep "TEBEX/ADMIN" logs/latest.log
```

### Player Chunk Totals

Check a player's total chunks:
```
/claimadmin list <player>
```

Output shows:
- Total chunks (starting + purchased)
- Purchased chunks (from store + in-game)
- All their claims

---

## Support

If you encounter issues:

1. **Check Console Logs**: Look for `[ServerClaim]` messages
2. **Test Manually**: Use `/claimadmin givechunks <player> <amount>` to verify the plugin works
3. **Verify Tebex Setup**: Check Tebex dashboard for command execution logs
4. **Check Permissions**: Ensure the console has permission to run the command (it should by default)

---

## Security Notes

- The `givechunks` command can only be run by admins or console
- All purchases are logged with timestamp and source
- Database transactions are atomic - either fully completes or fails safely
- Player UUIDs are verified before granting chunks

---

**Happy selling! 💰**

For more information about ServerClaim, see the main README.md
