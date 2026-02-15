package net.serverplugins.admin.xray;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import org.bukkit.Material;

public class XrayTracker {

    private final UUID playerId;
    private final Queue<MiningEvent> recentEvents;
    private int totalStone;
    private int totalOres;
    private int unexposedOres;

    public XrayTracker(UUID playerId) {
        this.playerId = playerId;
        this.recentEvents = new LinkedList<>();
        this.totalStone = 0;
        this.totalOres = 0;
        this.unexposedOres = 0;
    }

    public void recordMine(Material block, boolean wasExposed, long timestamp) {
        MiningEvent event = new MiningEvent(block, wasExposed, timestamp);
        recentEvents.add(event);

        if (isStoneBlock(block)) {
            totalStone++;
        } else {
            totalOres++;
            if (!wasExposed) {
                unexposedOres++;
            }
        }
    }

    public void pruneOldEvents(long cutoffTime) {
        while (!recentEvents.isEmpty() && recentEvents.peek().timestamp() < cutoffTime) {
            MiningEvent removed = recentEvents.poll();
            if (isStoneBlock(removed.block())) {
                totalStone--;
            } else {
                totalOres--;
                if (!removed.wasExposed()) {
                    unexposedOres--;
                }
            }
        }
    }

    public int calculateSuspicion() {
        if (totalOres == 0) return 0;

        int suspicion = 0;

        // Factor 1: Ore-to-stone ratio
        // Normal mining: ~1-2% ore, Xray: 10%+ ore
        if (totalStone + totalOres > 10) {
            double oreRatio = (double) totalOres / (totalStone + totalOres);
            if (oreRatio > 0.10) {
                suspicion += 30;
            } else if (oreRatio > 0.05) {
                suspicion += 15;
            }
        }

        // Factor 2: Unexposed ore ratio
        // Normal mining rarely breaks unexposed ores, xray always does
        if (totalOres > 3) {
            double unexposedRatio = (double) unexposedOres / totalOres;
            if (unexposedRatio > 0.5) {
                suspicion += 40;
            } else if (unexposedRatio > 0.25) {
                suspicion += 20;
            }
        }

        // Factor 3: Rapid diamond/debris finding
        int rareOres = countRareOres();
        if (rareOres >= 5) {
            suspicion += 30;
        } else if (rareOres >= 3) {
            suspicion += 15;
        }

        return Math.min(100, suspicion);
    }

    private int countRareOres() {
        int count = 0;
        for (MiningEvent event : recentEvents) {
            if (event.block() == Material.DIAMOND_ORE
                    || event.block() == Material.DEEPSLATE_DIAMOND_ORE
                    || event.block() == Material.ANCIENT_DEBRIS) {
                count++;
            }
        }
        return count;
    }

    private boolean isStoneBlock(Material material) {
        return switch (material) {
            case STONE,
                            DEEPSLATE,
                            GRANITE,
                            DIORITE,
                            ANDESITE,
                            TUFF,
                            CALCITE,
                            COBBLESTONE,
                            COBBLED_DEEPSLATE,
                            NETHERRACK,
                            BLACKSTONE,
                            BASALT ->
                    true;
            default -> false;
        };
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTotalStone() {
        return totalStone;
    }

    public int getTotalOres() {
        return totalOres;
    }

    public int getUnexposedOres() {
        return unexposedOres;
    }

    public int getRecentEventCount() {
        return recentEvents.size();
    }

    public record MiningEvent(Material block, boolean wasExposed, long timestamp) {}
}
