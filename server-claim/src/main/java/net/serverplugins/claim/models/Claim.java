package net.serverplugins.claim.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Material;

/**
 * Represents a claim owned by a player.
 *
 * <p>Thread Safety: - Uses CopyOnWriteArrayList for lists (chunks, profiles, customGroups) -
 * thread-safe for concurrent reads - Uses ConcurrentHashMap for maps (members, memberGroupIds) -
 * thread-safe for concurrent reads/writes - Uses Collections.synchronizedSet for sets
 * (trustedPlayers, bannedPlayers) - thread-safe with explicit synchronization
 */
public class Claim {

    private int id;
    private UUID ownerUuid;
    private String name;
    private String world;
    private String welcomeMessage;
    private boolean teleportProtected;
    private boolean keepInventory;
    private String cachedOwnerName;

    // Per-claim chunk pool
    private int totalChunks = 2;
    private int purchasedChunks = 0;
    private int claimOrder = 1;

    // Claim appearance (moved from profiles)
    private ProfileColor color = ProfileColor.WHITE;
    private Material icon;
    private String particleEffect = "DUST";
    private boolean particleEnabled = true;

    // Claim settings (moved from profiles)
    private ClaimSettings settings;
    private GroupPermissions groupPermissions;

    // Thread-safe collections for concurrent access
    private final List<ClaimedChunk> chunks = new CopyOnWriteArrayList<>();
    @Deprecated private final List<ClaimProfile> profiles = new CopyOnWriteArrayList<>();
    private final Set<UUID> trustedPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> bannedPlayers = Collections.synchronizedSet(new HashSet<>());
    @Deprecated private final Map<UUID, ClaimGroup> members = new ConcurrentHashMap<>();

    // Custom groups system (v2.1)
    private final List<CustomGroup> customGroups = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> memberGroupIds =
            new ConcurrentHashMap<>(); // Player UUID -> CustomGroup ID

    public Claim() {
        this.settings = new ClaimSettings();
    }

    public Claim(int id, UUID ownerUuid, String name, String world) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.world = world;
        this.settings = new ClaimSettings();
    }

    public Claim(UUID ownerUuid, String world) {
        this.ownerUuid = ownerUuid;
        this.name = "My Claim";
        this.world = world;
        this.settings = new ClaimSettings();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public List<ClaimedChunk> getChunks() {
        return chunks;
    }

    public void addChunk(ClaimedChunk chunk) {
        chunks.add(chunk);
    }

    public void removeChunk(ClaimedChunk chunk) {
        chunks.remove(chunk);
        // IMPORTANT: Do NOT decrease purchasedChunks or totalChunks here
        // Players keep their purchased chunk slots even when unclaiming chunks
        // This preserves their investment and allows re-claiming later
    }

    @Deprecated
    public void removeProfile(ClaimProfile profile) {
        profiles.remove(profile);
    }

    // Per-claim chunk pool methods
    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getPurchasedChunks() {
        return purchasedChunks;
    }

    public void setPurchasedChunks(int purchasedChunks) {
        this.purchasedChunks = purchasedChunks;
    }

    public int getClaimOrder() {
        return claimOrder;
    }

    public void setClaimOrder(int claimOrder) {
        this.claimOrder = claimOrder;
    }

    public int getRemainingChunks() {
        // Check for unlimited mode
        if (totalChunks >= 999999 || totalChunks == -1) {
            return 999999; // Display as effectively unlimited
        }

        int remaining = totalChunks - chunks.size();
        // Data integrity check - if negative, it means chunks were claimed without proper total
        if (remaining < 0) {
            return 0; // Return 0 instead of negative to prevent confusing displays
        }
        return remaining;
    }

    public boolean hasAvailableChunks() {
        // Check for unlimited mode
        if (totalChunks >= 999999 || totalChunks == -1) {
            return true; // Always allow claiming in unlimited mode
        }

        return chunks.size() < totalChunks;
    }

    /**
     * Check if this claim has data integrity issues (more chunks than total allowed). This can
     * happen if chunks were claimed without purchasing or if migration issues occurred.
     */
    public boolean hasNegativeChunks() {
        return chunks.size() > totalChunks;
    }

    /**
     * Get the raw chunk deficit (how many chunks over the limit). Returns 0 if not over limit,
     * otherwise returns the positive deficit amount.
     */
    public int getChunkDeficit() {
        int deficit = chunks.size() - totalChunks;
        return Math.max(0, deficit);
    }

    public void addPurchasedChunks(int amount) {
        this.purchasedChunks += amount;
        this.totalChunks += amount;
    }

    /**
     * Synchronize purchasedChunks with actual chunks.size(). Call this after loading from database
     * to fix legacy data. This ensures data integrity when chunks were manually claimed vs
     * purchased.
     */
    public void syncChunkCounts() {
        int actualChunks = chunks.size();
        int startingChunks = 2; // Default starting chunks

        // If we have more chunks than totalChunks, expand totalChunks
        if (actualChunks > totalChunks) {
            int deficit = actualChunks - totalChunks;
            totalChunks = actualChunks;
            purchasedChunks += deficit;
        }

        // If purchasedChunks doesn't match reality, fix it
        // (This can happen if chunks were manually claimed vs purchased)
        int expectedPurchased = Math.max(0, actualChunks - startingChunks);
        if (purchasedChunks != expectedPurchased) {
            // Only update if the difference is significant (more than starting chunks)
            // This prevents overwriting intentional purchases
            if (Math.abs(purchasedChunks - expectedPurchased) > startingChunks) {
                purchasedChunks = expectedPurchased;
                totalChunks = startingChunks + purchasedChunks;
            }
        }
    }

    // Claim appearance methods
    public ProfileColor getColor() {
        return color;
    }

    public void setColor(ProfileColor color) {
        this.color = color;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public String getParticleEffect() {
        return particleEffect;
    }

    public void setParticleEffect(String particleEffect) {
        this.particleEffect = particleEffect;
    }

    public boolean isParticleEnabled() {
        return particleEnabled;
    }

    public void setParticleEnabled(boolean particleEnabled) {
        this.particleEnabled = particleEnabled;
    }

    // Claim settings methods
    public ClaimSettings getSettings() {
        return settings;
    }

    public void setSettings(ClaimSettings settings) {
        this.settings = settings;
    }

    public GroupPermissions getGroupPermissions() {
        return groupPermissions;
    }

    public void setGroupPermissions(GroupPermissions groupPermissions) {
        this.groupPermissions = groupPermissions;
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunks.stream().anyMatch(c -> c.getChunkX() == chunkX && c.getChunkZ() == chunkZ);
    }

    @Deprecated
    public List<ClaimProfile> getProfiles() {
        return profiles;
    }

    @Deprecated
    public void addProfile(ClaimProfile profile) {
        profiles.add(profile);
    }

    @Deprecated
    public ClaimProfile getActiveProfile() {
        return profiles.stream()
                .filter(ClaimProfile::isActive)
                .findFirst()
                .orElse(profiles.isEmpty() ? null : profiles.get(0));
    }

    public Set<UUID> getTrustedPlayers() {
        return trustedPlayers;
    }

    public void trustPlayer(UUID uuid) {
        trustedPlayers.add(uuid);
    }

    public void untrustPlayer(UUID uuid) {
        trustedPlayers.remove(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid) || ownerUuid.equals(uuid);
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    // Member/Group management (legacy - kept for backwards compatibility)
    @Deprecated
    public Map<UUID, ClaimGroup> getMembers() {
        return members;
    }

    @Deprecated
    public void setMemberGroup(UUID uuid, ClaimGroup group) {
        members.put(uuid, group);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        memberGroupIds.remove(uuid);
    }

    @Deprecated
    public ClaimGroup getMemberGroup(UUID uuid) {
        if (isOwner(uuid)) return null; // Owner has all permissions
        return members.getOrDefault(uuid, ClaimGroup.VISITOR);
    }

    public boolean hasPermission(UUID uuid, ClaimPermission permission) {
        // Owner and trusted players have all permissions
        if (isOwner(uuid) || trustedPlayers.contains(uuid)) return true;

        // Check custom groups first if available
        if (!customGroups.isEmpty()) {
            CustomGroup group = getMemberCustomGroup(uuid);
            if (group != null) {
                return group.hasPermission(permission);
            }
            // Fall back to visitor group
            CustomGroup visitorGroup = getCustomGroupByName("Visitor");
            return visitorGroup != null && visitorGroup.hasPermission(permission);
        }

        // Legacy fallback
        ClaimGroup group = getMemberGroup(uuid);
        return groupPermissions != null && groupPermissions.hasPermission(group, permission);
    }

    // Custom Groups System (v2.1)
    public List<CustomGroup> getCustomGroups() {
        return customGroups;
    }

    public void setCustomGroups(List<CustomGroup> groups) {
        customGroups.clear();
        if (groups != null) {
            customGroups.addAll(groups);
        }
    }

    public void addCustomGroup(CustomGroup group) {
        customGroups.add(group);
    }

    public void removeCustomGroup(CustomGroup group) {
        customGroups.remove(group);
    }

    public CustomGroup getCustomGroupById(int groupId) {
        return customGroups.stream().filter(g -> g.getId() == groupId).findFirst().orElse(null);
    }

    public CustomGroup getCustomGroupByName(String name) {
        return customGroups.stream()
                .filter(g -> g.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public CustomGroup getVisitorGroup() {
        return getCustomGroupByName("Visitor");
    }

    public boolean hasCustomGroups() {
        return !customGroups.isEmpty();
    }

    // Member to CustomGroup mapping
    public Map<UUID, Integer> getMemberGroupIds() {
        return memberGroupIds;
    }

    public void setMemberCustomGroup(UUID uuid, CustomGroup group) {
        if (group != null) {
            memberGroupIds.put(uuid, group.getId());
        } else {
            memberGroupIds.remove(uuid);
        }
    }

    public void setMemberGroupId(UUID uuid, int groupId) {
        memberGroupIds.put(uuid, groupId);
    }

    public CustomGroup getMemberCustomGroup(UUID uuid) {
        if (isOwner(uuid)) return null; // Owner has all permissions via separate check
        Integer groupId = memberGroupIds.get(uuid);
        if (groupId != null) {
            return getCustomGroupById(groupId);
        }
        // Return visitor group for non-members
        return getVisitorGroup();
    }

    public boolean isMember(UUID uuid) {
        return memberGroupIds.containsKey(uuid) || members.containsKey(uuid);
    }

    /** Checks if a player has a specific management permission on this claim. */
    public boolean hasManagementPermission(UUID uuid, ManagementPermission permission) {
        // Owner has all management permissions
        if (isOwner(uuid)) return true;

        // Trusted players (legacy) have all management permissions
        if (trustedPlayers.contains(uuid)) return true;

        // Check custom group for management permission
        if (!customGroups.isEmpty()) {
            CustomGroup group = getMemberCustomGroup(uuid);
            if (group != null) {
                return group.hasManagementPermission(permission);
            }
        }

        return false;
    }

    /**
     * Gets all members with any management permission. Used to show "accessible claims" for
     * non-owners. Thread-safe: memberGroupIds is a ConcurrentHashMap.
     */
    public Set<UUID> getMembersWithManagementAccess() {
        Set<UUID> managers = new HashSet<>();
        // ConcurrentHashMap provides thread-safe iteration
        for (Map.Entry<UUID, Integer> entry : memberGroupIds.entrySet()) {
            CustomGroup group = getCustomGroupById(entry.getValue());
            if (group != null && !group.getManagementPermissions().isEmpty()) {
                managers.add(entry.getKey());
            }
        }
        return managers;
    }

    public boolean isAdjacentTo(int chunkX, int chunkZ) {
        if (chunks.isEmpty()) return true;

        return chunks.stream()
                .anyMatch(
                        chunk ->
                                (Math.abs(chunk.getChunkX() - chunkX) == 1
                                                && chunk.getChunkZ() == chunkZ)
                                        || (Math.abs(chunk.getChunkZ() - chunkZ) == 1
                                                && chunk.getChunkX() == chunkX));
    }

    // Welcome message
    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    // Teleport protection
    public boolean isTeleportProtected() {
        return teleportProtected;
    }

    public void setTeleportProtected(boolean teleportProtected) {
        this.teleportProtected = teleportProtected;
    }

    // Keep inventory on death
    public boolean isKeepInventory() {
        return keepInventory;
    }

    public void setKeepInventory(boolean keepInventory) {
        this.keepInventory = keepInventory;
    }

    // Banned players management
    public Set<UUID> getBannedPlayers() {
        return bannedPlayers;
    }

    /** Ban a player from this claim. Thread-safe: uses synchronized set operations. */
    public void banPlayer(UUID uuid) {
        bannedPlayers.add(uuid);
        // Also remove from trusted and members
        // Collections.synchronizedSet ensures thread-safe removal
        trustedPlayers.remove(uuid);
        members.remove(uuid);
    }

    public void unbanPlayer(UUID uuid) {
        bannedPlayers.remove(uuid);
    }

    public boolean isBanned(UUID uuid) {
        return bannedPlayers.contains(uuid);
    }

    // Cached owner name
    public String getCachedOwnerName() {
        return cachedOwnerName;
    }

    public void setCachedOwnerName(String cachedOwnerName) {
        this.cachedOwnerName = cachedOwnerName;
    }

    /**
     * Applies a template's settings to this claim. Updates all claim settings, group permissions,
     * and custom groups based on the template. This method should be called within a transaction
     * for data integrity.
     */
    public void applyTemplate(ClaimTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Template cannot be null");
        }

        // Apply claim settings
        if (settings == null) {
            settings = new ClaimSettings();
        }
        settings.setPvpEnabled(template.isPvpEnabled());
        settings.setFireSpread(template.isFireSpread());
        settings.setHostileSpawns(template.isMobSpawning());
        settings.setExplosions(template.isExplosionDamage());
        // Note: piston_push and fluid_flow are not in ClaimSettings, they may be WorldGuard flags
        settings.setLeafDecay(template.isLeafDecay());
        // crop_growth is not in ClaimSettings, may be a WorldGuard flag

        // Apply group permissions if provided
        if (template.getGroupPermissions() != null && !template.getGroupPermissions().isEmpty()) {
            this.groupPermissions = GroupPermissions.fromCSV(template.getGroupPermissions());
        }

        // Apply custom groups if provided
        if (template.getCustomGroupsCsv() != null && !template.getCustomGroupsCsv().isEmpty()) {
            List<CustomGroup> templateGroups =
                    CustomGroup.deserializeFromCSV(template.getCustomGroupsCsv(), this.id);
            // Clear existing custom groups and replace with template groups
            this.customGroups.clear();
            this.customGroups.addAll(templateGroups);
        }
    }
}
