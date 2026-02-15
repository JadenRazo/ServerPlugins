package net.serverplugins.items.managers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.serverplugins.items.models.BlockStateMapping;
import net.serverplugins.items.models.CustomBlock;
import net.serverplugins.items.models.CustomItem;
import net.serverplugins.items.models.PlacedBlock;
import net.serverplugins.items.repository.ItemsRepository;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.configuration.ConfigurationSection;

public class BlockManager {

    private static final Instrument[] INSTRUMENTS = Instrument.values();
    private static final int MAX_NOTES = 25;

    private final Logger logger;
    private final ItemsRepository repository;
    private final ItemManager itemManager;

    private final Map<String, CustomBlock> blocks = new HashMap<>();
    private final Map<String, BlockStateMapping> stateMappings = new HashMap<>();

    // Chunk-indexed placed blocks: "world:chunkX:chunkZ" -> location -> PlacedBlock
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, PlacedBlock>> chunkBlocks =
            new ConcurrentHashMap<>();

    private int nextStateIndex = 0;

    public BlockManager(Logger logger, ItemsRepository repository, ItemManager itemManager) {
        this.logger = logger;
        this.repository = repository;
        this.itemManager = itemManager;
    }

    public void loadStateMappings() {
        List<BlockStateMapping> existing = repository.loadAllStateMappings();
        for (BlockStateMapping mapping : existing) {
            stateMappings.put(mapping.itemId(), mapping);
            int index = stateToIndex(mapping.instrument(), mapping.note(), mapping.powered());
            if (index >= nextStateIndex) {
                nextStateIndex = index + 1;
            }
        }
        logger.info("Loaded " + stateMappings.size() + " block state mappings.");
    }

    public void registerBlock(CustomItem item, ConfigurationSection blockSection) {
        float hardness = (float) blockSection.getDouble("hardness", 1.5);
        String dropId = blockSection.getString("drop", item.getId());
        String breakTool = blockSection.getString("break_tool", "");

        Sound placeSound = parseSound(blockSection.getString("sounds.place", "block.stone.place"));
        Sound breakSound = parseSound(blockSection.getString("sounds.break", "block.stone.break"));

        CustomBlock block =
                new CustomBlock(item, hardness, dropId, breakTool, placeSound, breakSound);

        // Assign or retrieve state mapping
        BlockStateMapping mapping = stateMappings.get(item.getId());
        if (mapping == null) {
            mapping = allocateNextState(item.getId());
            stateMappings.put(item.getId(), mapping);
            repository.saveStateMapping(mapping);
        }
        block.setStateMapping(mapping);
        blocks.put(item.getId(), block);
    }

    private BlockStateMapping allocateNextState(String itemId) {
        int index = nextStateIndex++;
        Instrument instrument = INSTRUMENTS[index / (MAX_NOTES * 2) % INSTRUMENTS.length];
        int note = (index / 2) % MAX_NOTES;
        boolean powered = index % 2 == 1;
        return new BlockStateMapping(itemId, instrument, note, powered);
    }

    private int stateToIndex(Instrument instrument, int note, boolean powered) {
        int instrumentIndex = instrument.ordinal();
        return (instrumentIndex * MAX_NOTES * 2) + (note * 2) + (powered ? 1 : 0);
    }

    public void placeBlock(Location location, CustomBlock block, UUID placedBy) {
        Block bukkitBlock = location.getBlock();
        bukkitBlock.setType(Material.NOTE_BLOCK, false);

        BlockStateMapping mapping = block.getStateMapping();
        if (bukkitBlock.getBlockData() instanceof NoteBlock noteBlock) {
            noteBlock.setInstrument(mapping.instrument());
            noteBlock.setNote(new org.bukkit.Note(mapping.note()));
            noteBlock.setPowered(mapping.powered());
            bukkitBlock.setBlockData(noteBlock, false);
        }

        PlacedBlock placed =
                new PlacedBlock(
                        location.getWorld().getName(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ(),
                        block.getId(),
                        placedBy);

        String chunkKey = getChunkKey(location);
        chunkBlocks
                .computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>())
                .put(locationKey(location), placed);

        repository.insertPlacedBlock(placed);

        if (block.getPlaceSound() != null) {
            location.getWorld().playSound(location, block.getPlaceSound(), 1.0f, 1.0f);
        }
    }

    public PlacedBlock removeBlock(Location location) {
        String chunkKey = getChunkKey(location);
        ConcurrentHashMap<Long, PlacedBlock> chunk = chunkBlocks.get(chunkKey);
        if (chunk == null) return null;

        PlacedBlock placed = chunk.remove(locationKey(location));
        if (placed == null) return null;

        location.getBlock().setType(Material.AIR, false);

        CustomBlock block = blocks.get(placed.blockId());
        if (block != null && block.getBreakSound() != null) {
            location.getWorld().playSound(location, block.getBreakSound(), 1.0f, 1.0f);
        }

        repository.deletePlacedBlock(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());

        return placed;
    }

    public PlacedBlock getPlacedBlock(Location location) {
        String chunkKey = getChunkKey(location);
        ConcurrentHashMap<Long, PlacedBlock> chunk = chunkBlocks.get(chunkKey);
        if (chunk == null) return null;
        return chunk.get(locationKey(location));
    }

    public boolean isCustomBlock(Location location) {
        return getPlacedBlock(location) != null;
    }

    public CustomBlock getBlock(String id) {
        return blocks.get(id);
    }

    public Collection<CustomBlock> getAllBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public void loadChunk(String world, int chunkX, int chunkZ) {
        List<PlacedBlock> loaded = repository.loadPlacedBlocksInChunk(world, chunkX, chunkZ);
        if (loaded.isEmpty()) return;

        String chunkKey = world + ":" + chunkX + ":" + chunkZ;
        ConcurrentHashMap<Long, PlacedBlock> chunkMap =
                chunkBlocks.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>());

        for (PlacedBlock block : loaded) {
            long locKey = packLocationKey(block.x(), block.y(), block.z());
            chunkMap.put(locKey, block);
        }
    }

    public void unloadChunk(String world, int chunkX, int chunkZ) {
        String chunkKey = world + ":" + chunkX + ":" + chunkZ;
        chunkBlocks.remove(chunkKey);
    }

    public CustomBlock getBlockFromNoteBlock(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) return null;
        if (!(block.getBlockData() instanceof NoteBlock noteBlock)) return null;

        Instrument instrument = noteBlock.getInstrument();
        int note = noteBlock.getNote().getId();
        boolean powered = noteBlock.isPowered();

        for (CustomBlock customBlock : blocks.values()) {
            BlockStateMapping mapping = customBlock.getStateMapping();
            if (mapping != null
                    && mapping.instrument() == instrument
                    && mapping.note() == note
                    && mapping.powered() == powered) {
                return customBlock;
            }
        }
        return null;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    private String getChunkKey(Location loc) {
        return loc.getWorld().getName()
                + ":"
                + (loc.getBlockX() >> 4)
                + ":"
                + (loc.getBlockZ() >> 4);
    }

    private long locationKey(Location loc) {
        return packLocationKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private long packLocationKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }

    private Sound parseSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) return null;
        try {
            return Sound.valueOf(soundName.toUpperCase().replace('.', '_'));
        } catch (IllegalArgumentException e) {
            return Sound.BLOCK_STONE_PLACE;
        }
    }
}
