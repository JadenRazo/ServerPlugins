package net.serverplugins.arcade.machines;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Defines the structure of an arcade machine with blocks and item displays. */
public class MachineStructure {

    private final ItemStack placementItem;
    private final List<StructureElement> elements;

    public MachineStructure(ItemStack placementItem, List<StructureElement> elements) {
        this.placementItem = placementItem;
        this.elements = elements;
    }

    public MachineStructure(ItemStack placementItem) {
        this(placementItem, new ArrayList<>());
    }

    public void addElement(StructureElement element) {
        elements.add(element);
    }

    /** Check if the structure can be placed at a location. */
    public boolean canPlace(Location location, Direction direction) {
        for (StructureElement element : elements) {
            if (!element.canPlace(location, direction)) {
                return false;
            }
        }
        return true;
    }

    /** Place the structure at a location. */
    public Set<Block> place(
            Location location, Direction direction, String machineId, NamespacedKey entityKey) {
        Set<Block> placedBlocks = new HashSet<>();
        for (StructureElement element : elements) {
            Block block = element.place(location, direction, machineId, entityKey);
            if (block != null) {
                placedBlocks.add(block);
            }
        }
        return placedBlocks;
    }

    /** Remove the structure from a location. */
    public void remove(
            Location location, Direction direction, String machineId, NamespacedKey entityKey) {
        for (StructureElement element : elements) {
            element.remove(location, direction, machineId, entityKey);
        }
    }

    public ItemStack getPlacementItem() {
        return placementItem.clone();
    }

    public List<StructureElement> getElements() {
        return elements;
    }

    // ===== Structure Elements =====

    public abstract static class StructureElement {
        protected final Offset offset;
        protected final Direction relativeDirection;

        protected StructureElement(Offset offset, Direction relativeDirection) {
            this.offset = offset;
            this.relativeDirection = relativeDirection;
        }

        protected StructureElement(Offset offset) {
            this(offset, null);
        }

        public abstract boolean canPlace(Location location, Direction direction);

        public abstract Block place(
                Location location, Direction direction, String machineId, NamespacedKey entityKey);

        public abstract void remove(
                Location location, Direction direction, String machineId, NamespacedKey entityKey);

        protected Location getOffsetLocation(Location location, Direction direction) {
            return direction.addOffset(location.clone(), offset.x, offset.y, offset.z);
        }
    }

    /** A block element that places a solid block. */
    public static class BlockElement extends StructureElement {
        private final Material material;
        private final BlockData blockData;

        public BlockElement(Material material, Offset offset, Direction relativeDirection) {
            super(offset, relativeDirection);
            this.material = material;
            this.blockData = material.createBlockData();
        }

        public BlockElement(Material material, Offset offset) {
            this(material, offset, null);
        }

        public BlockElement(Material material) {
            this(material, new Offset(0, 0, 0));
        }

        @Override
        public boolean canPlace(Location location, Direction direction) {
            Block block = getOffsetLocation(location, direction).getBlock();
            Material type = block.getType();
            // Allow placement on air, passable blocks, or replaceable blocks
            return type.isAir() || block.isPassable() || isReplaceable(type);
        }

        /** Check if a material is replaceable (can be overwritten when placing). */
        private boolean isReplaceable(Material material) {
            return switch (material) {
                    // Vegetation
                case SHORT_GRASS,
                                TALL_GRASS,
                                FERN,
                                LARGE_FERN,
                                DEAD_BUSH,
                                SEAGRASS,
                                TALL_SEAGRASS,
                                DANDELION,
                                POPPY,
                                BLUE_ORCHID,
                                ALLIUM,
                                AZURE_BLUET,
                                RED_TULIP,
                                ORANGE_TULIP,
                                WHITE_TULIP,
                                PINK_TULIP,
                                OXEYE_DAISY,
                                CORNFLOWER,
                                LILY_OF_THE_VALLEY,
                                WITHER_ROSE,
                                SUNFLOWER,
                                LILAC,
                                ROSE_BUSH,
                                PEONY,
                                CRIMSON_ROOTS,
                                WARPED_ROOTS,
                                NETHER_SPROUTS,
                                HANGING_ROOTS,
                                SMALL_DRIPLEAF,
                                BIG_DRIPLEAF,
                                SPORE_BLOSSOM,
                                AZALEA,
                                FLOWERING_AZALEA,
                                MOSS_CARPET,
                                PINK_PETALS,
                                PITCHER_PLANT,
                                TORCHFLOWER,
                                SWEET_BERRY_BUSH ->
                        true;
                    // Snow and ice layers
                case SNOW, POWDER_SNOW -> true;
                    // Water/lava (for underwater placement)
                case WATER, LAVA -> true;
                    // Vines and similar
                case VINE,
                                CAVE_VINES,
                                CAVE_VINES_PLANT,
                                WEEPING_VINES,
                                WEEPING_VINES_PLANT,
                                TWISTING_VINES,
                                TWISTING_VINES_PLANT,
                                GLOW_LICHEN,
                                SCULK_VEIN ->
                        true;
                    // Other replaceable
                case FIRE, SOUL_FIRE, COBWEB -> true;
                default -> false;
            };
        }

        @Override
        public Block place(
                Location location, Direction direction, String machineId, NamespacedKey entityKey) {
            Block block = getOffsetLocation(location, direction).getBlock();
            block.setType(material);

            if (relativeDirection != null
                    && block.getBlockData() instanceof Directional directional) {
                Direction finalDir = relativeDirection.transformDirection(direction);
                directional.setFacing(finalDir.toBlockFace());
                block.setBlockData(directional);
            }

            return block;
        }

        @Override
        public void remove(
                Location location, Direction direction, String machineId, NamespacedKey entityKey) {
            Block block = getOffsetLocation(location, direction).getBlock();
            block.setType(Material.AIR);
        }
    }

    /** An item display element using armor stand. */
    public static class ItemElement extends StructureElement {
        private final ItemStack item;
        private final boolean isHologram;
        private final String hologramText;
        private final Integer seatNumber;

        public ItemElement(
                ItemStack item,
                Offset offset,
                Direction relativeDirection,
                Integer seatNumber,
                String hologramText) {
            super(offset, relativeDirection);
            this.item = item;
            this.seatNumber = seatNumber;
            this.hologramText = hologramText;
            this.isHologram = hologramText != null && !hologramText.isEmpty();
        }

        public ItemElement(ItemStack item, Offset offset, Direction relativeDirection) {
            this(item, offset, relativeDirection, null, null);
        }

        public ItemElement(ItemStack item, Offset offset) {
            this(item, offset, null);
        }

        /** Create a hologram element. */
        public static ItemElement hologram(String text, Offset offset) {
            return new ItemElement(new ItemStack(Material.AIR), offset, null, null, text);
        }

        /** Create a seat element without visual. */
        public static ItemElement seat(int seatNumber, Offset offset, Direction relativeDirection) {
            return new ItemElement(
                    new ItemStack(Material.AIR), offset, relativeDirection, seatNumber, null);
        }

        /** Create a seat element with a visual item. */
        public static ItemElement seat(
                int seatNumber, ItemStack item, Offset offset, Direction relativeDirection) {
            return new ItemElement(item, offset, relativeDirection, seatNumber, null);
        }

        @Override
        public boolean canPlace(Location location, Direction direction) {
            return true; // Armor stands can always be placed
        }

        @Override
        public Block place(
                Location location, Direction direction, String machineId, NamespacedKey entityKey) {
            Location spawnLoc = getOffsetLocation(location, direction);

            ArmorStand stand =
                    (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setVisible(false);
            stand.setPersistent(true);

            // Store machine ID in persistent data
            stand.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, machineId);

            if (isHologram) {
                stand.setCustomNameVisible(true);
                stand.setCustomName(hologramText);
            } else if (item != null && item.getType() != Material.AIR) {
                stand.getEquipment().setItem(EquipmentSlot.HEAD, item);
            }

            if (relativeDirection != null) {
                Direction finalDir = relativeDirection.transformDirection(direction);
                stand.setRotation((float) finalDir.getAngle(), 0);
            }

            if (seatNumber != null) {
                // Store seat number in persistent data (required for seat finding)
                stand.getPersistentDataContainer()
                        .set(Machine.MACHINE_SEAT_KEY, PersistentDataType.INTEGER, seatNumber);
                // Add scoreboard tag for seat identification
                stand.addScoreboardTag("arcade_seat_" + seatNumber);
            }

            return null; // Item elements don't return blocks
        }

        @Override
        public void remove(
                Location location, Direction direction, String machineId, NamespacedKey entityKey) {
            Location targetLoc = getOffsetLocation(location, direction);

            targetLoc
                    .getNearbyEntities(2, 2, 2)
                    .forEach(
                            entity -> {
                                if (entity.getType() == EntityType.ARMOR_STAND
                                        && entity.getPersistentDataContainer()
                                                .has(entityKey, PersistentDataType.STRING)) {
                                    String storedId =
                                            entity.getPersistentDataContainer()
                                                    .get(entityKey, PersistentDataType.STRING);
                                    if (machineId.equals(storedId)) {
                                        entity.remove();
                                    }
                                }
                            });
        }
    }

    /** Represents an offset in 3D space. */
    public record Offset(float x, float y, float z) {
        public static final Offset ZERO = new Offset(0, 0, 0);
    }
}
