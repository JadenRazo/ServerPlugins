package net.serverplugins.api.ui;

/**
 * Comprehensive resource pack Unicode character mappings for ServerPlugins.
 *
 * <p>This class contains all custom Unicode characters mapped to images in the ServerPlugins resource
 * pack. Use these constants to display custom UI elements, menus, icons, and NPC portraits in chat,
 * GUIs, and holograms.
 *
 * @author ServerPlugins Development Team
 * @version 1.0.0
 */
public class ResourcePackIcons {

    // ========================================
    // NPC PORTRAITS (90px height, ascent 25)
    // ========================================

    /** NPC portrait icons (90x90 pixels, large portraits for dialog displays) */
    public static class NPCs {
        public static final String SYLVIA = "\uD001"; // 퀁 - item/ui/npcs/sylvia.png
        public static final String AUGUST = "\uD002"; // 퀂 - item/ui/npcs/august.png
        public static final String CASSIAN = "\uD003"; // 퀃 - item/ui/npcs/cassian.png
        public static final String GANDALF = "\uD004"; // 퀄 - item/ui/npcs/gandalf.png
        public static final String MARCUS = "\uD005"; // 퀅 - item/ui/npcs/marcus.png
        public static final String MARK = "\uD006"; // 퀆 - item/ui/npcs/mark.png
        public static final String NOAH = "\uD007"; // 퀇 - item/ui/npcs/noah.png
        public static final String PETRA = "\uD008"; // 퀈 - item/ui/npcs/petra.png
        public static final String THYRA = "\uD009"; // 퀉 - item/ui/npcs/thyra.png
    }

    // ========================================
    // CORE UI ELEMENTS
    // ========================================

    /** Basic UI elements and utility characters */
    public static class UI {
        // Spacing
        public static final String SPACE = "\u2ED4"; // ⻔ - item/ui/space.png

        // Welcome Screen Characters
        public static final String WELCOME_1 = "\uC111"; // 섑 - item/ui/welcome_title.png
        public static final String WELCOME_2 = "\uC112"; // 섒 - item/ui/welcome_title.png
        public static final String WELCOME_3 = "\uC001"; // 쀁 - item/ui/welcome_title.png

        // Currency & Items
        public static final String COIN = "\uE001"; //  - item/ui/coin.png
        public static final String CHEST = "\uE002"; //  - item/ui/chest.png

        // Boss Bar
        public static final String BOSSBAR = "お"; // - item/ui/bossbar/bossbar.png

        // Negative Spaces (for precise positioning)
        public static final String SPACE_MINUS_1 = "\uF801";
        public static final String SPACE_MINUS_2 = "\uF802";
        public static final String SPACE_MINUS_4 = "\uF803";
        public static final String SPACE_MINUS_8 = "\uF804";
        public static final String SPACE_MINUS_16 = "\uF805";
        public static final String SPACE_MINUS_32 = "\uF806";
        public static final String SPACE_MINUS_64 = "\uF807";
        public static final String SPACE_MINUS_128 = "\uF808";

        // Positive Spaces
        public static final String SPACE_PLUS_1 = "\uF821";
        public static final String SPACE_PLUS_2 = "\uF822";
        public static final String SPACE_PLUS_4 = "\uF823";
        public static final String SPACE_PLUS_8 = "\uF824";
    }

    // ========================================
    // MENU TITLE UTILITIES
    // ========================================

    /**
     * Utility class for creating menu titles with resource pack backgrounds. Uses negative space
     * characters to position custom font textures.
     */
    public static class MenuTitles {
        /** Negative space character for positioning menu backgrounds */
        public static final String NEGATIVE_SPACE = "\u2ED4"; // ⻔

        // Core Menus
        public static final String SURVIVAL_MENU =
                "\uE2CA"; // item/survival_menu/gui_survival_menu.png
        public static final String RTP_MENU = "\uE5A7"; // item/rtp_menu/rtp_menu.png
        public static final String REWARDS = "\uE406"; // item/rewards_menu/rewards.png
        public static final String REWARDS_2 = "\uE331"; // item/rewards_menu/rewards2.png
        public static final String PROFILE = "\uE49F"; // item/profile_menu/profile.png
        public static final String SETTINGS = "\uE328"; // item/settings_menu/gui_settings.png
        public static final String SETTINGS_2 = "\uE721"; // item/settings_menu/gui_settings2.png
        public static final String GUIDE = "\uE326"; // item/guide_menu/gui_server_guide.png
        public static final String GUIDE_2 = "\uE327"; // item/guide_menu/gui2_server_guide.png
        public static final String JOBS = "\uE7D1"; // item/jobs_menu/jobs.png
        public static final String DUNGEONS = "\uE5BF"; // item/dungeons/dungeons.png
        public static final String UPGRADES =
                "\uE310"; // item/survival_menu/titles/upgrades_title.png

        // Claims
        public static final String CLAIMS = "\uE582"; // item/others_menu/claims.png
        public static final String CLAIMS_2 = "\uE77E"; // item/others_menu/claims2.png

        // Warps
        public static final String WARPS = "\uE30B"; // item/survival_menu/titles/warps_title.png
        public static final String WARPS_DUNGEONS = "\uE885"; // item/warps_menu/warps_dungeons.png
        public static final String WARPS_MINES = "\uE886"; // item/warps_menu/warps_mines.png
        public static final String WARPS_FARMS = "\uE887"; // item/warps_menu/warps_farms.png
        public static final String WARPS_ARENA = "\uE888"; // item/warps_menu/warps_arena.png
        public static final String WARPS_AUCTIONS = "\uE889"; // item/warps_menu/warps_auctions.png
        public static final String WARPS_CASINO = "\uE88A"; // item/warps_menu/warps_casino.png
        public static final String WARPS_CRATES = "\uE88B"; // item/warps_menu/warps_crates.png

        // Backpacks (6 tiers)
        public static final String BACKPACK_1 = "\uE5D4"; // item/others_menu/backpack1.png
        public static final String BACKPACK_2 = "\uE5D5"; // item/others_menu/backpack2.png
        public static final String BACKPACK_3 = "\uE5D6"; // item/others_menu/backpack3.png
        public static final String BACKPACK_4 = "\uE5D7"; // item/others_menu/backpack4.png
        public static final String BACKPACK_5 = "\uE5D8"; // item/others_menu/backpack5.png
        public static final String BACKPACK_6 = "\uE5D9"; // item/others_menu/backpack6.png

        // Kits (3 pages)
        public static final String KITS_1 = "\uE826"; // item/kits_menu/gui_kits.png
        public static final String KITS_2 = "\uE827"; // item/kits_menu/gui2_kits.png
        public static final String KITS_3 = "\uE828"; // item/kits_menu/gui3_kits.png

        // Battlepass (6 pages)
        public static final String BATTLEPASS_1 = "\uE7A9"; // item/battlepass_menu/battlepass.png
        public static final String BATTLEPASS_2 = "\uE62C"; // item/battlepass_menu/battlepass2.png
        public static final String BATTLEPASS_3 = "\uE339"; // item/battlepass_menu/battlepass3.png
        public static final String BATTLEPASS_4 = "\uE364"; // item/battlepass_menu/battlepass4.png
        public static final String BATTLEPASS_5 = "\uE365"; // item/battlepass_menu/battlepass5.png
        public static final String BATTLEPASS_6 = "\uE366"; // item/battlepass_menu/battlepass6.png

        // The Rift (4 pages)
        public static final String RIFT_1 = "\uE6A9"; // item/therift_menu/therift.png
        public static final String RIFT_2 = "\uE4FC"; // item/therift_menu/therift2.png
        public static final String RIFT_3 = "\uE4FB"; // item/therift_menu/therift3.png
        public static final String RIFT_4 = "\uE4FA"; // item/therift_menu/therift4.png

        // Casino
        public static final String BLACKJACK_MENU =
                "\uE409"; // item/casino/blackjack/menu_blackjack.png
        public static final String BLACKJACK_BET =
                "\uE6CA"; // item/casino/blackjack/bet_blackjack.png
        public static final String BETS = "\uE408"; // item/casino/bets.png
        public static final String SLOTS = "\uE66C"; // item/casino/slots/slot.png
        public static final String CRASH_WAITING = "\uE40A"; // item/casino/crash/crash_waiting.png
        public static final String CRASH_CRASHED = "\uE40B"; // item/casino/crash/crash_crashed.png
        public static final String CRASH_STARTED = "\uE40C"; // item/casino/crash/crash_started.png
        public static final String CRASH_REMOVE = "\uE5B1"; // item/casino/crash/crash_remove.png
        public static final String ROULETTE = "\uE6C3"; // item/casino/roulette/roulette.png
        public static final String ROULETTE_2 = "\uE6C4"; // item/casino/roulette/roulette2.png
        public static final String LOTTERY_BETS = "\uE737"; // item/casino/lottery/lottery_bets.png
        public static final String LOTTERY_START =
                "\uE738"; // item/casino/lottery/lottery_start.png
        public static final String LOTTERY_REMOVE =
                "\uE5B4"; // item/casino/lottery/lottery_remove.png

        // Auction
        public static final String AUCTION_HOUSE = "\uE6AB"; // item/auction_menu/auction_house.png
        public static final String AUCTION_BROWSER =
                "\uE7D6"; // item/auction_menu/auction_browser.png
        public static final String AUCTION_MANAGER =
                "\uE31D"; // item/auction_menu/auction_manager.png
        public static final String AUCTION_CREATOR =
                "\uE6AC"; // item/auction_menu/auction_creator.png
        public static final String AUCTION_DURATION =
                "\uE33D"; // item/auction_menu/auction_duration.png
        public static final String AUCTION_CONFIRM =
                "\uE73F"; // item/auction_menu/auction_confirm.png
        public static final String AUCTION_VIEW = "\uE729"; // item/auction_menu/auction_view.png

        // Crates Preview
        public static final String CRATE_DAILY = "\uE7EE"; // item/crates/preview/daily_crate.png
        public static final String CRATE_BALANCED =
                "\uE8AB"; // item/crates/preview/balanced_crate.png
        public static final String CRATE_DIVERSITY =
                "\uE7F0"; // item/crates/preview/diversity_crate.png
        public static final String CRATE_EPIC = "\uE7F1"; // item/crates/preview/epic_crate.png
        public static final String CRATE_VOTE = "\uE75D"; // item/crates/preview/vote_crate.png

        // Crates Opening
        public static final String CRATE_DAILY_OPEN =
                "\uE69B"; // item/crates/opening/daily_crate.png
        public static final String CRATE_BALANCED_OPEN =
                "\uE69C"; // item/crates/opening/balanced_crate.png
        public static final String CRATE_DIVERSITY_OPEN =
                "\uE69D"; // item/crates/opening/diversity_crate.png
        public static final String CRATE_EPIC_OPEN = "\uE69E"; // item/crates/opening/epic_crate.png
        public static final String CRATE_VOTE_OPEN =
                "\uE75E"; // item/crates/opening/vote_crate_opening.png

        // Shops
        public static final String SHOP_MAIN_1 = "\uE4D6"; // item/shop_menu/main_shop1.png
        public static final String SHOP_MAIN_2 = "\uE4D7"; // item/shop_menu/main_shop2.png
        public static final String SHOP_MAIN_3 = "\uE4D8"; // item/shop_menu/main_shop3.png
        public static final String SHOP_SELLALL = "\uE6B9"; // item/shop_menu/shop_sellall.png

        // Store
        public static final String STORE_MAIN = "\uE4D9"; // item/store_menu/main_store.png
        public static final String STORE_1 = "\uE4DA"; // item/store_menu/store1.png
        public static final String STORE_2 = "\uE4DB"; // item/store_menu/store2.png
        public static final String STORE_3 = "\uE4DC"; // item/store_menu/store3.png

        // Other Menus
        public static final String DISPOSAL = "\uE4AF"; // item/others_menu/disposal.png
        public static final String BOOKSHELF = "\uE6C2"; // item/others_menu/bookshelf.png
        public static final String TRADE = "\uE6C5"; // item/others_menu/trade.png
        public static final String ENDERCHEST = "\uE71F"; // item/others_menu/enderchest.png
        public static final String ENDERCHEST_2 = "\uE680"; // item/others_menu/enderchest2.png
        public static final String BARREL = "\uE71E"; // item/others_menu/barrel.png
        public static final String SHULKER = "\uE71D"; // item/others_menu/shulker.png
        public static final String INVENTORY = "\uE3A8"; // item/others_menu/inventory.png
        public static final String FURNITURE = "\uE514"; // item/others_menu/furniture.png
        public static final String FURNITURE_2 = "\uE693"; // item/others_menu/furniture2.png
        public static final String CHEST_RECIPE = "\uE32D"; // item/others_menu/chest_recipe.png

        /**
         * Create a menu title with resource pack background (default 10 negative spaces)
         *
         * @param menuChar The menu background character
         * @return Formatted title string
         */
        public static String createTitle(String menuChar) {
            return createTitle(menuChar, 10);
        }

        /**
         * Create a menu title with resource pack background
         *
         * @param menuChar The menu background character
         * @param negativeSpaceCount Number of negative space characters to use
         * @return Formatted title string
         */
        public static String createTitle(String menuChar, int negativeSpaceCount) {
            StringBuilder sb = new StringBuilder("<white>");
            for (int i = 0; i < negativeSpaceCount; i++) {
                sb.append(NEGATIVE_SPACE);
            }
            sb.append(menuChar);
            return sb.toString();
        }

        /**
         * Create a title for CONTAINER GUIs (backpacks, ender chests, bookshelf) - Shows custom
         * background via NEGATIVE_SPACE positioning - Does NOT hide player inventory (no hiding
         * trigger) - Players can see and interact with their inventory
         *
         * @param menuChar The font character to display (e.g., BACKPACK_1)
         * @return Formatted title string
         */
        public static String createContainerTitle(String menuChar) {
            return createContainerTitle(menuChar, 10);
        }

        /**
         * Create a title for CONTAINER GUIs with custom negative space count
         *
         * @param menuChar The font character to display
         * @param negativeSpaceCount Number of NEGATIVE_SPACE characters for positioning
         * @return Formatted title string WITHOUT inventory hiding trigger
         */
        public static String createContainerTitle(String menuChar, int negativeSpaceCount) {
            StringBuilder sb = new StringBuilder("<white>");
            for (int i = 0; i < negativeSpaceCount; i++) {
                sb.append(NEGATIVE_SPACE); // Font positioning only
            }
            sb.append(menuChar);
            // NO inventory hiding trigger added
            return sb.toString();
        }

        /**
         * Create a title for FULL-SCREEN GUIs (casino, crates, shops, battlepass) - Shows custom
         * background via NEGATIVE_SPACE positioning - HIDES player inventory via special trigger
         * character - Used for menus with full-screen backgrounds
         *
         * @param menuChar The font character to display (e.g., BLACKJACK_MENU)
         * @return Formatted title string WITH inventory hiding trigger
         */
        public static String createFullscreenTitle(String menuChar) {
            return createFullscreenTitle(menuChar, 10);
        }

        /**
         * Create a title for FULL-SCREEN GUIs with custom negative space count
         *
         * @param menuChar The font character to display
         * @param negativeSpaceCount Number of NEGATIVE_SPACE characters for positioning
         * @return Formatted title string WITH inventory hiding trigger
         */
        public static String createFullscreenTitle(String menuChar, int negativeSpaceCount) {
            StringBuilder sb = new StringBuilder("<white>");
            for (int i = 0; i < negativeSpaceCount; i++) {
                sb.append(NEGATIVE_SPACE); // Font positioning
            }
            sb.append(menuChar);

            // Add inventory hiding trigger at the END (invisible in client)
            // This character tells PacketUtils to hide player inventory
            sb.append("\uF000"); // Must match ServerAPI config invisible-inventory-title

            return sb.toString();
        }

        /**
         * Get backpack menu character by tier (1-6)
         *
         * @param tier The backpack tier
         * @return The menu character for that tier
         */
        public static String getBackpackByTier(int tier) {
            switch (tier) {
                case 1:
                    return BACKPACK_1;
                case 2:
                    return BACKPACK_2;
                case 3:
                    return BACKPACK_3;
                case 4:
                    return BACKPACK_4;
                case 5:
                    return BACKPACK_5;
                case 6:
                    return BACKPACK_6;
                default:
                    return BACKPACK_1;
            }
        }

        /**
         * Get battlepass page menu character (1-6)
         *
         * @param page The page number
         * @return The menu character for that page
         */
        public static String getBattlepassByPage(int page) {
            switch (page) {
                case 1:
                    return BATTLEPASS_1;
                case 2:
                    return BATTLEPASS_2;
                case 3:
                    return BATTLEPASS_3;
                case 4:
                    return BATTLEPASS_4;
                case 5:
                    return BATTLEPASS_5;
                case 6:
                    return BATTLEPASS_6;
                default:
                    return BATTLEPASS_1;
            }
        }

        /**
         * Get kits page menu character (1-3)
         *
         * @param page The page number
         * @return The menu character for that page
         */
        public static String getKitsByPage(int page) {
            switch (page) {
                case 1:
                    return KITS_1;
                case 2:
                    return KITS_2;
                case 3:
                    return KITS_3;
                default:
                    return KITS_1;
            }
        }
    }

    // ========================================
    // MENU BACKGROUNDS (256px height) - Legacy
    // ========================================

    /**
     * Full-screen menu backgrounds for GUIs
     *
     * @deprecated Use {@link MenuTitles} instead for consistent title creation
     */
    @Deprecated
    public static class Menus {

        /** Auction house menu backgrounds */
        public static class Auction {
            public static final String HOUSE = MenuTitles.AUCTION_HOUSE;
            public static final String BROWSER = MenuTitles.AUCTION_BROWSER;
        }

        /** Casino game menu backgrounds */
        public static class Casino {
            public static final String BLACKJACK_MENU = MenuTitles.BLACKJACK_MENU;
            public static final String BLACKJACK_TABLE = MenuTitles.BLACKJACK_BET;
            public static final String CRASH_MENU = MenuTitles.CRASH_WAITING;
            public static final String CRASH_GAME = MenuTitles.CRASH_STARTED;
            public static final String LOTTERY = MenuTitles.LOTTERY_START;
            public static final String ROULETTE_MENU = MenuTitles.ROULETTE;
            public static final String ROULETTE_GAME = MenuTitles.ROULETTE_2;
            public static final String SLOTS_MENU = MenuTitles.SLOTS;
            public static final String SLOTS_GAME = MenuTitles.SLOTS;
        }

        /** Shop menu backgrounds */
        public static class Shop {
            public static final String BLOCKS = MenuTitles.SHOP_MAIN_1;
            public static final String DECORATION = MenuTitles.SHOP_MAIN_1;
            public static final String FARMING = MenuTitles.SHOP_MAIN_1;
            public static final String FOOD = MenuTitles.SHOP_MAIN_1;
            public static final String MAIN = MenuTitles.SHOP_MAIN_1;
            public static final String MISC = MenuTitles.SHOP_MAIN_1;
            public static final String REDSTONE = MenuTitles.SHOP_MAIN_1;
            public static final String TOOLS = MenuTitles.SHOP_MAIN_1;
        }

        /** Warps menu background */
        public static class Warps {
            public static final String MAIN = MenuTitles.WARPS;
        }

        /** Crates menu backgrounds */
        public static class Crates {
            public static final String CLAIM = MenuTitles.CRATE_DAILY;
            public static final String MAIN = MenuTitles.CRATE_DAILY;
        }

        /** Battle pass menu backgrounds */
        public static class BattlePass {
            public static final String MAIN = MenuTitles.BATTLEPASS_1;
        }

        /** Survival menu (main hub menu) */
        public static class Survival {
            public static final String MAIN = MenuTitles.SURVIVAL_MENU;
        }
    }

    // ========================================
    // SMALL ICONS (7-13px height)
    // ========================================

    /** Small icons for UI elements and decorations */
    public static class Icons {
        public static final String COMPASS = ""; //  - item/icons/compass.png
        public static final String DICE = ""; //  - item/icons/dice.png
        public static final String EARTH = ""; //  - item/icons/earth.png
        public static final String HEART = ""; //  - item/icons/heart.png
        public static final String PICKAXE = ""; //  - item/icons/pickaxe.png
        public static final String PING = ""; //  - item/icons/ping.png
        public static final String SWORD = ""; //  - item/icons/sword.png

        /** Arcade machine icon */
        public static final String ARCADE = ""; //  - item/icons/arcade.png
    }

    // ========================================
    // EMOJIS (8px height)
    // ========================================

    /** Chat emojis for player communication */
    public static class Emojis {
        public static final String CLOWN = "\u2F14"; // ⼔ - item/emojis/clown.png
        public static final String DEAD = "\u2F15"; // ⼕ - item/emojis/dead.png
        public static final String EYES = "\u2F16"; // ⼖ - item/emojis/eyes.png
        public static final String FIRE = "\u2F17"; // ⼗ - item/emojis/fire.png
        public static final String GRIMACE = "\u2F18"; // ⼘ - item/emojis/grimace.png
        public static final String GUN = "\u2F19"; // ⼙ - item/emojis/gun.png
        public static final String HAPPY = "\u2F1A"; // ⼚ - item/emojis/happy.png
        public static final String HEART = "\u2F1B"; // ⼛ - item/emojis/heart.png
        public static final String LAUGH = "\u2F1C"; // ⼜ - item/emojis/laugh.png
        public static final String LOVE = "\u2F1D"; // ⼝ - item/emojis/love.png
        public static final String MUSCLE = "\u2F1E"; // ⼞ - item/emojis/muscle.png
        public static final String PARTY = "\u2F1F"; // ⼟ - item/emojis/party.png
        public static final String PLEADING = "\u2F20"; // ⼠ - item/emojis/pleading.png
        public static final String SKULL = "\u2F21"; // ⼡ - item/emojis/skull.png
        public static final String SUNGLASSES = "\u2F22"; // ⼢ - item/emojis/sunglasses.png
        public static final String THUMBS_UP = "\u2F23"; // ⼣ - item/emojis/thumbsup.png
        public static final String WEARY = "\u2F24"; // ⼤ - item/emojis/weary.png
    }

    // ========================================
    // RANKS (9px height)
    // ========================================

    /** Player rank badges */
    public static class Ranks {
        public static final String ADVENTURER = ""; //  - item/ranks/adventurer.png
        public static final String VIP = ""; //  - item/ranks/vip.png
        public static final String MVP = ""; //  - item/ranks/mvp.png
        public static final String PRO = ""; //  - item/ranks/pro.png
        public static final String HELPER = ""; //  - item/ranks/helper.png
        public static final String MOD = ""; //  - item/ranks/mod.png
        public static final String ADMIN = ""; //  - item/ranks/admin.png
        public static final String DEVELOPER = ""; //  - item/ranks/developer.png
        public static final String OWNER = ""; //  - item/ranks/owner.png
        public static final String YOUTUBE = ""; //  - item/ranks/youtube.png
        public static final String TWITCH = ""; //  - item/ranks/twitch.png
        public static final String PARTNER = ""; //  - item/ranks/partner.png
    }

    // ========================================
    // CHAT TAGS (9px height)
    // ========================================

    /** Chat prefix tags for messaging */
    public static class ChatTags {
        public static final String RED = ""; //  - item/chat_tags/red.png
        public static final String GREEN = ""; //  - item/chat_tags/green.png
        public static final String BLUE = ""; //  - item/chat_tags/blue.png
        public static final String YELLOW = ""; //  - item/chat_tags/yellow.png
        public static final String PURPLE = ""; //  - item/chat_tags/purple.png
        public static final String ORANGE = ""; //  - item/chat_tags/orange.png
        public static final String PINK = ""; //  - item/chat_tags/pink.png
        public static final String WHITE = ""; //  - item/chat_tags/white.png
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Get NPC icon by name (case-insensitive)
     *
     * @param npcName The NPC name
     * @return The Unicode icon character, or empty string if not found
     */
    public static String getNpcIcon(String npcName) {
        if (npcName == null) return "";

        switch (npcName.toLowerCase()) {
            case "sylvia":
                return NPCs.SYLVIA;
            case "august":
                return NPCs.AUGUST;
            case "cassian":
                return NPCs.CASSIAN;
            case "gandalf":
                return NPCs.GANDALF;
            case "marcus":
                return NPCs.MARCUS;
            case "mark":
                return NPCs.MARK;
            case "noah":
                return NPCs.NOAH;
            case "petra":
                return NPCs.PETRA;
            case "thyra":
                return NPCs.THYRA;
            default:
                return "";
        }
    }

    /**
     * Get emoji icon by name (case-insensitive)
     *
     * @param emojiName The emoji name
     * @return The Unicode emoji character, or empty string if not found
     */
    public static String getEmoji(String emojiName) {
        if (emojiName == null) return "";

        switch (emojiName.toLowerCase()) {
            case "clown":
                return Emojis.CLOWN;
            case "dead":
                return Emojis.DEAD;
            case "eyes":
                return Emojis.EYES;
            case "fire":
                return Emojis.FIRE;
            case "grimace":
                return Emojis.GRIMACE;
            case "gun":
                return Emojis.GUN;
            case "happy":
                return Emojis.HAPPY;
            case "heart":
                return Emojis.HEART;
            case "laugh":
                return Emojis.LAUGH;
            case "love":
                return Emojis.LOVE;
            case "muscle":
                return Emojis.MUSCLE;
            case "party":
                return Emojis.PARTY;
            case "pleading":
                return Emojis.PLEADING;
            case "skull":
                return Emojis.SKULL;
            case "sunglasses":
                return Emojis.SUNGLASSES;
            case "thumbsup":
            case "thumbs_up":
                return Emojis.THUMBS_UP;
            case "weary":
                return Emojis.WEARY;
            default:
                return "";
        }
    }

    /**
     * Create a formatted NPC dialog line with portrait icon
     *
     * @param npcName The NPC name
     * @param message The message text
     * @return Formatted string with icon + spacing + message
     */
    public static String createNpcDialogLine(String npcName, String message) {
        String icon = getNpcIcon(npcName);
        if (icon.isEmpty()) {
            return message;
        }
        return icon + UI.SPACE + UI.SPACE + message;
    }

    /**
     * Create a separator line with NPC icon
     *
     * @param npcName The NPC name
     * @param color The Minecraft color code (e.g., "&d")
     * @param length Number of separator characters
     * @return Formatted separator line
     */
    public static String createNpcSeparator(String npcName, String color, int length) {
        String icon = getNpcIcon(npcName);
        if (icon.isEmpty()) {
            return color + "&l" + "━".repeat(length);
        }

        StringBuilder separator = new StringBuilder();
        separator.append(icon);
        separator.append(UI.SPACE);
        separator.append(UI.SPACE);
        separator.append(color).append("&l");
        for (int i = 0; i < length; i++) {
            separator.append("━");
        }
        return separator.toString();
    }
}
