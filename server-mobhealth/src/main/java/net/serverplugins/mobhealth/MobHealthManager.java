package net.serverplugins.mobhealth;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

public class MobHealthManager {

    private final ServerMobHealth plugin;
    private final ConcurrentHashMap<UUID, MobHealthDisplay> activeDisplays =
            new ConcurrentHashMap<>();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public MobHealthManager(ServerMobHealth plugin) {
        this.plugin = plugin;
    }

    public void showHealthBar(LivingEntity entity) {
        UUID entityId = entity.getUniqueId();
        MobHealthConfig config = plugin.getMobHealthConfig();

        double health = entity.getHealth();
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : health;

        if (health <= 0) {
            removeDisplay(entityId);
            return;
        }

        Component text = buildHealthText(entity, health, maxHealth);

        MobHealthDisplay existing = activeDisplays.get(entityId);
        if (existing != null && existing.getTextDisplay().isValid()) {
            existing.getTextDisplay().text(text);
            existing.getExpiryTask().cancel();
            existing.setExpiryTask(scheduleExpiry(entityId, config.getDisplayDuration()));
            return;
        }

        if (existing != null) {
            activeDisplays.remove(entityId);
        }

        TextDisplay display =
                entity.getWorld()
                        .spawn(
                                entity.getLocation(),
                                TextDisplay.class,
                                td -> {
                                    td.text(text);
                                    td.setBillboard(Display.Billboard.CENTER);
                                    td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                    td.setShadowed(true);
                                    td.setSeeThrough(false);
                                    td.setTransformation(
                                            new org.bukkit.util.Transformation(
                                                    new org.joml.Vector3f(
                                                            0, (float) config.getYOffset(), 0),
                                                    new org.joml.Quaternionf(),
                                                    new org.joml.Vector3f(1, 1, 1),
                                                    new org.joml.Quaternionf()));
                                });

        entity.addPassenger(display);

        MobHealthDisplay mobDisplay =
                new MobHealthDisplay(
                        entityId, display, scheduleExpiry(entityId, config.getDisplayDuration()));
        activeDisplays.put(entityId, mobDisplay);
    }

    private Component buildHealthText(LivingEntity entity, double health, double maxHealth) {
        MobHealthConfig config = plugin.getMobHealthConfig();

        String mobName = getMobName(entity);
        double healthPercent = health / maxHealth;
        String color = config.getHealthColor(healthPercent);
        String heartSymbol = config.getHeartSymbol();

        int displayHealth = (int) Math.ceil(health);
        int displayMax = (int) Math.ceil(maxHealth);

        String healthText =
                color
                        + displayHealth
                        + "/"
                        + displayMax
                        + heartSymbol
                        + "</"
                        + stripTag(color)
                        + ">";

        String formatted =
                config.getDisplayFormat()
                        .replace("{name}", "<white>" + mobName + "</white>")
                        .replace("{health}", healthText)
                        .replace("{max}", String.valueOf(displayMax));

        return MINI_MESSAGE.deserialize(formatted);
    }

    private String stripTag(String tag) {
        return tag.replace("<", "").replace(">", "");
    }

    private String getMobName(LivingEntity entity) {
        Component customName = entity.customName();
        if (customName != null) {
            return MINI_MESSAGE.serialize(customName);
        }
        return formatEntityType(entity.getType());
    }

    private String formatEntityType(EntityType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                result.append(' ');
                capitalize = true;
            } else {
                result.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            }
        }
        return result.toString();
    }

    public void removeDisplay(UUID entityId) {
        MobHealthDisplay display = activeDisplays.remove(entityId);
        if (display != null) {
            display.getExpiryTask().cancel();
            if (display.getTextDisplay().isValid()) {
                display.getTextDisplay().remove();
            }
        }
    }

    public void removeAll() {
        for (UUID entityId : activeDisplays.keySet()) {
            MobHealthDisplay display = activeDisplays.remove(entityId);
            if (display != null) {
                display.getExpiryTask().cancel();
                if (display.getTextDisplay().isValid()) {
                    display.getTextDisplay().remove();
                }
            }
        }
    }

    private org.bukkit.scheduler.BukkitTask scheduleExpiry(UUID entityId, int seconds) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                MobHealthDisplay display = activeDisplays.remove(entityId);
                if (display != null && display.getTextDisplay().isValid()) {
                    display.getTextDisplay().remove();
                }
            }
        }.runTaskLater(plugin, seconds * 20L);
    }

    public int getActiveDisplayCount() {
        return activeDisplays.size();
    }
}
