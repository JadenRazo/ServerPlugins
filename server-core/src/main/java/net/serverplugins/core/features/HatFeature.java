package net.serverplugins.core.features;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.core.ServerCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class HatFeature extends Feature implements Listener {

    private NamespacedKey hatKey;
    private Set<Material> allowedMaterials;
    private List<CustomModelDataRange> customModelDataRanges;

    private static class CustomModelDataRange {
        final int min;
        final int max;

        CustomModelDataRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        boolean contains(int value) {
            return value >= min && value <= max;
        }
    }

    public HatFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Hat";
    }

    @Override
    public String getDescription() {
        return "/hat command to wear items as hats";
    }

    @Override
    protected void onEnable() {
        hatKey = new NamespacedKey(plugin, "hat_item");
        loadConfiguration();
    }

    private void loadConfiguration() {
        // Load allowed materials
        allowedMaterials = new HashSet<>();
        List<String> materialNames = plugin.getCoreConfig().getHatAllowedMaterials();

        if (materialNames != null && !materialNames.isEmpty()) {
            for (String materialName : materialNames) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    allowedMaterials.add(material);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger()
                            .warning("Invalid material in hat allowed-materials: " + materialName);
                }
            }
        }

        // Load custom model data ranges
        customModelDataRanges = new ArrayList<>();
        List<Map<String, Integer>> ranges = plugin.getCoreConfig().getHatCustomModelDataRanges();

        if (ranges != null) {
            for (Map<String, Integer> rangeMap : ranges) {
                Integer min = rangeMap.get("min");
                Integer max = rangeMap.get("max");

                if (min != null && max != null) {
                    if (min <= max) {
                        customModelDataRanges.add(new CustomModelDataRange(min, max));
                    } else {
                        plugin.getLogger()
                                .warning(
                                        "Invalid custom-model-data range: min ("
                                                + min
                                                + ") > max ("
                                                + max
                                                + ")");
                    }
                }
            }
        }
    }

    public boolean wearHat(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem.getType() == Material.AIR) {
            plugin.getCoreConfig().getMessenger().send(player, "hat-must-hold-item");
            return false;
        }

        if (isHelmet(handItem.getType())) {
            plugin.getCoreConfig().getMessenger().send(player, "hat-no-armor");
            return false;
        }

        // Validate item against configured restrictions
        if (!isItemAllowed(handItem)) {
            plugin.getCoreConfig().getMessenger().send(player, "hat-not-allowed");
            return false;
        }

        ItemStack currentHelmet = player.getInventory().getHelmet();

        ItemStack hat = handItem.clone();
        hat.setAmount(1);
        markAsHat(hat);

        player.getInventory().setHelmet(hat);

        if (handItem.getAmount() > 1) {
            handItem.setAmount(handItem.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        if (currentHelmet != null && currentHelmet.getType() != Material.AIR) {
            if (isMarkedAsHat(currentHelmet)) unmarkAsHat(currentHelmet);
            player.getInventory().addItem(currentHelmet);
        }

        String itemName = hat.getType().name().toLowerCase().replace("_", " ");
        plugin.getCoreConfig()
                .getMessenger()
                .send(player, "hat-equipped", Placeholder.of("item", itemName));
        return true;
    }

    private void markAsHat(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(hatKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    private void unmarkAsHat(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(hatKey);
            item.setItemMeta(meta);
        }
    }

    private boolean isMarkedAsHat(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(hatKey, PersistentDataType.BYTE);
    }

    private boolean isItemAllowed(ItemStack item) {
        // If no restrictions are configured, allow all items
        boolean hasMaterialRestrictions = allowedMaterials != null && !allowedMaterials.isEmpty();
        boolean hasCustomModelDataRestrictions =
                customModelDataRanges != null && !customModelDataRanges.isEmpty();

        if (!hasMaterialRestrictions && !hasCustomModelDataRestrictions) {
            return true;
        }

        // Check material restrictions
        if (hasMaterialRestrictions) {
            if (!allowedMaterials.contains(item.getType())) {
                return false;
            }
        }

        // Check custom model data restrictions
        if (hasCustomModelDataRestrictions) {
            ItemMeta meta = item.getItemMeta();

            // If item doesn't have meta or custom model data, reject it if restrictions exist
            if (meta == null || !meta.hasCustomModelData()) {
                return false;
            }

            int customModelData = meta.getCustomModelData();
            boolean inRange = false;

            for (CustomModelDataRange range : customModelDataRanges) {
                if (range.contains(customModelData)) {
                    inRange = true;
                    break;
                }
            }

            if (!inRange) {
                return false;
            }
        }

        return true;
    }

    private boolean isHelmet(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.equals("TURTLE_HELMET");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isEnabled()) return;
        if (!plugin.getCoreConfig().shouldKeepHatOnDeath()) return;

        Player player = event.getEntity();
        ItemStack helmet = player.getInventory().getHelmet();

        if (helmet != null && isMarkedAsHat(helmet)) {
            event.getDrops().remove(helmet);
            event.getItemsToKeep().add(helmet);
        }
    }
}
