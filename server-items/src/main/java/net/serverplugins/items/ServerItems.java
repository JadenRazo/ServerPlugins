package net.serverplugins.items;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;
import net.serverplugins.items.api.ServerItemsAPI;
import net.serverplugins.items.commands.FurnitureCommand;
import net.serverplugins.items.commands.ItemsCommand;
import net.serverplugins.items.gui.ItemBrowserGui;
import net.serverplugins.items.listeners.BlockListener;
import net.serverplugins.items.listeners.FurnitureListener;
import net.serverplugins.items.listeners.ItemListener;
import net.serverplugins.items.managers.BlockManager;
import net.serverplugins.items.managers.FurnitureManager;
import net.serverplugins.items.managers.ItemManager;
import net.serverplugins.items.models.CustomItem;
import net.serverplugins.items.pack.PackGenerator;
import net.serverplugins.items.pack.PackManifest;
import net.serverplugins.items.repository.ItemsRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerItems extends JavaPlugin {

    private static ServerItems instance;

    private ItemsConfig itemsConfig;
    private Database database;
    private ItemsRepository repository;
    private ItemManager itemManager;
    private BlockManager blockManager;
    private FurnitureManager furnitureManager;
    private PackGenerator packGenerator;
    private ItemBrowserGui browserGui;
    private ServerItemsAPI api;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        itemsConfig = new ItemsConfig(this);

        if (!setupDatabase()) {
            getLogger().severe("Failed to setup database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new ItemsRepository(database);

        // Initialize managers
        itemManager = new ItemManager(getLogger());
        blockManager = new BlockManager(getLogger(), repository, itemManager);
        furnitureManager = new FurnitureManager(this, repository);

        // Load block state mappings from DB
        blockManager.loadStateMappings();

        // Create default items folder
        File itemsFolder = new File(getDataFolder(), "items");
        if (!itemsFolder.exists()) {
            itemsFolder.mkdirs();
            saveExampleItems(itemsFolder);
        }
        saveDefaultFurniture(itemsFolder);

        // Load items from YAML + register blocks
        itemManager.loadItems(itemsFolder);
        registerBlocks(itemsFolder);

        // Load furniture definitions
        furnitureManager.loadFurnitureDefs(itemsFolder, itemManager);

        // Resource pack generator
        packGenerator = new PackGenerator(getLogger(), itemsConfig, getDataFolder());
        if (itemsConfig.isAutoGenerate()) {
            generatePack();
        }

        // Public API
        api = new ServerItemsAPI(itemManager);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        browserGui = new ItemBrowserGui(this);
        getServer().getPluginManager().registerEvents(browserGui, this);
        if (itemsConfig.isBlocksEnabled()) {
            getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        }
        if (itemsConfig.isFurnitureEnabled()) {
            getServer().getPluginManager().registerEvents(new FurnitureListener(this), this);
            furnitureManager.startCleanupTask();
        }

        // Register commands
        registerCommands();

        getLogger()
                .info(
                        "ServerItems enabled: "
                                + itemManager.getItemCount()
                                + " items, "
                                + blockManager.getBlockCount()
                                + " blocks, "
                                + furnitureManager.getDefinitionCount()
                                + " furniture.");
    }

    @Override
    public void onDisable() {
        if (furnitureManager != null) {
            furnitureManager.shutdown();
        }
        instance = null;
        getLogger().info("ServerItems disabled.");
    }

    private boolean setupDatabase() {
        database = ServerAPI.getInstance().getDatabase();
        if (database == null) {
            getLogger().severe("ServerAPI database not available");
            return false;
        }

        getLogger().info("Using shared database connection from ServerAPI");

        try (InputStream is = getResource("schema.sql")) {
            if (is != null) {
                String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                for (String stmt : schema.split(";")) {
                    String trimmed = stmt.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        try {
                            database.execute(trimmed);
                        } catch (Exception e) {
                            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                            if (msg.contains("already exists") || msg.contains("duplicate")) {
                                // Expected on subsequent startups
                            } else {
                                getLogger().warning("Schema execution error: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().severe("Schema execution failed: " + e.getMessage());
            return false;
        }

        return true;
    }

    private void registerBlocks(File itemsFolder) {
        registerBlocksFromDir(itemsFolder);
        getLogger().info("Registered " + blockManager.getBlockCount() + " custom blocks.");
    }

    private void registerBlocksFromDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                registerBlocksFromDir(file);
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (String id : yaml.getKeys(false)) {
                    ConfigurationSection section = yaml.getConfigurationSection(id);
                    if (section == null) continue;
                    ConfigurationSection blockSection = section.getConfigurationSection("block");
                    if (blockSection == null) continue;

                    String materialName = section.getString("material", "");
                    if (!"NOTE_BLOCK".equalsIgnoreCase(materialName)) continue;

                    CustomItem item = itemManager.getItem(id);
                    if (item != null) {
                        blockManager.registerBlock(item, blockSection);
                    }
                }
            }
        }
    }

    private void registerCommands() {
        ItemsCommand itemsCmd = new ItemsCommand(this);
        PluginCommand cmd = getCommand("witems");
        if (cmd != null) {
            cmd.setExecutor(itemsCmd);
            cmd.setTabCompleter(itemsCmd);
        }

        FurnitureCommand furnitureCmd = new FurnitureCommand(this);
        PluginCommand fCmd = getCommand("wfurniture");
        if (fCmd != null) {
            fCmd.setExecutor(furnitureCmd);
            fCmd.setTabCompleter(furnitureCmd);
        }
    }

    private void saveExampleItems(File itemsFolder) {
        try {
            File exampleFile = new File(itemsFolder, "example_items.yml");
            if (!exampleFile.exists()) {
                String content =
                        "# Example custom items - edit or replace this file\n"
                                + "# All .yml files in this folder (and subfolders) are loaded\n\n"
                                + "flame_sword:\n"
                                + "  material: DIAMOND_SWORD\n"
                                + "  display_name: \"<gradient:#FF4500:#FFD700>Flame Sword</gradient>\"\n"
                                + "  lore:\n"
                                + "    - \"<gray>A blade forged in dragonfire\"\n"
                                + "    - \"\"\n"
                                + "    - \"<gold>Special Item\"\n"
                                + "  custom_model_data: 10001\n"
                                + "  enchants:\n"
                                + "    fire_aspect: 2\n"
                                + "    sharpness: 5\n"
                                + "  item_flags: [HIDE_ENCHANTS]\n"
                                + "  mechanics:\n"
                                + "    durability:\n"
                                + "      max: 500\n"
                                + "    cooldown:\n"
                                + "      ticks: 40\n\n"
                                + "healing_apple:\n"
                                + "  material: GOLDEN_APPLE\n"
                                + "  display_name: \"<green>Healing Apple\"\n"
                                + "  lore:\n"
                                + "    - \"<gray>Restores health on consumption\"\n"
                                + "  custom_model_data: 10002\n"
                                + "  mechanics:\n"
                                + "    consumable:\n"
                                + "      heal: 10\n"
                                + "      effects:\n"
                                + "        regeneration:\n"
                                + "          duration: 200\n"
                                + "          amplifier: 1\n\n"
                                + "admin_wand:\n"
                                + "  material: BLAZE_ROD\n"
                                + "  display_name: \"<red>Admin Wand\"\n"
                                + "  lore:\n"
                                + "    - \"<gray>Right-click to smite\"\n"
                                + "  custom_model_data: 10003\n"
                                + "  glow: true\n"
                                + "  mechanics:\n"
                                + "    command:\n"
                                + "      console:\n"
                                + "        - \"smite {player}\"\n"
                                + "    cooldown:\n"
                                + "      ticks: 100\n";

                java.nio.file.Files.writeString(exampleFile.toPath(), content);
            }
        } catch (Exception e) {
            getLogger().warning("Could not create example items file: " + e.getMessage());
        }
    }

    private void saveDefaultFurniture(File itemsFolder) {
        String[] furnitureFiles = {
            "items/furniture_living.yml",
            "items/furniture_decorations.yml",
            "items/furniture_electronics.yml",
            "items/furniture_misc.yml"
        };
        for (String resourcePath : furnitureFiles) {
            try (InputStream is = getResource(resourcePath)) {
                if (is != null) {
                    File target =
                            new File(
                                    itemsFolder,
                                    resourcePath.substring(resourcePath.lastIndexOf('/') + 1));
                    if (!target.exists()) {
                        java.nio.file.Files.copy(is, target.toPath());
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Could not extract " + resourcePath + ": " + e.getMessage());
            }
        }
    }

    public void reload() {
        itemsConfig.reload();
        File itemsFolder = new File(getDataFolder(), "items");
        itemManager.loadItems(itemsFolder);
        registerBlocks(itemsFolder);
        furnitureManager.loadFurnitureDefs(itemsFolder, itemManager);
        if (itemsConfig.isAutoGenerate()) {
            generatePack();
        }
        getLogger()
                .info("ServerItems reloaded with " + itemManager.getItemCount() + " custom items.");
    }

    public PackManifest generatePack() {
        try {
            return packGenerator.generate(itemManager.getAllItems());
        } catch (Exception e) {
            getLogger().warning("Failed to generate resource pack: " + e.getMessage());
            return null;
        }
    }

    public static ServerItems getInstance() {
        return instance;
    }

    public ItemsConfig getItemsConfig() {
        return itemsConfig;
    }

    public Database getDatabase() {
        return database;
    }

    public ItemsRepository getRepository() {
        return repository;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public BlockManager getBlockManager() {
        return blockManager;
    }

    public FurnitureManager getFurnitureManager() {
        return furnitureManager;
    }

    public ItemBrowserGui getBrowserGui() {
        return browserGui;
    }

    public ServerItemsAPI getApi() {
        return api;
    }
}
