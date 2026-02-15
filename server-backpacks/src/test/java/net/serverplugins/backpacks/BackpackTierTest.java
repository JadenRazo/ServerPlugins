package net.serverplugins.backpacks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BackpackTier Tests")
class BackpackTierTest {

    @Test
    @DisplayName("TIER_1 should have correct properties")
    void testTier1Properties() {
        assertThat(BackpackTier.TIER_1.getId()).isEqualTo("tier1");
        assertThat(BackpackTier.TIER_1.getRomanNumeral()).isEqualTo("I");
        assertThat(BackpackTier.TIER_1.getDefaultSize()).isEqualTo(9);
        assertThat(BackpackTier.TIER_1.getCustomModelData()).isEqualTo(100);
        assertThat(BackpackTier.TIER_1.getTierNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("TIER_2 should have correct properties")
    void testTier2Properties() {
        assertThat(BackpackTier.TIER_2.getId()).isEqualTo("tier2");
        assertThat(BackpackTier.TIER_2.getRomanNumeral()).isEqualTo("II");
        assertThat(BackpackTier.TIER_2.getDefaultSize()).isEqualTo(18);
        assertThat(BackpackTier.TIER_2.getCustomModelData()).isEqualTo(101);
        assertThat(BackpackTier.TIER_2.getTierNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("TIER_3 should have correct properties")
    void testTier3Properties() {
        assertThat(BackpackTier.TIER_3.getId()).isEqualTo("tier3");
        assertThat(BackpackTier.TIER_3.getRomanNumeral()).isEqualTo("III");
        assertThat(BackpackTier.TIER_3.getDefaultSize()).isEqualTo(27);
        assertThat(BackpackTier.TIER_3.getCustomModelData()).isEqualTo(102);
        assertThat(BackpackTier.TIER_3.getTierNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("TIER_4 should have correct properties")
    void testTier4Properties() {
        assertThat(BackpackTier.TIER_4.getId()).isEqualTo("tier4");
        assertThat(BackpackTier.TIER_4.getRomanNumeral()).isEqualTo("IV");
        assertThat(BackpackTier.TIER_4.getDefaultSize()).isEqualTo(36);
        assertThat(BackpackTier.TIER_4.getCustomModelData()).isEqualTo(105);
        assertThat(BackpackTier.TIER_4.getTierNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("TIER_5 should have correct properties")
    void testTier5Properties() {
        assertThat(BackpackTier.TIER_5.getId()).isEqualTo("tier5");
        assertThat(BackpackTier.TIER_5.getRomanNumeral()).isEqualTo("V");
        assertThat(BackpackTier.TIER_5.getDefaultSize()).isEqualTo(45);
        assertThat(BackpackTier.TIER_5.getCustomModelData()).isEqualTo(104);
        assertThat(BackpackTier.TIER_5.getTierNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("TIER_6 should have correct properties")
    void testTier6Properties() {
        assertThat(BackpackTier.TIER_6.getId()).isEqualTo("tier6");
        assertThat(BackpackTier.TIER_6.getRomanNumeral()).isEqualTo("VI");
        assertThat(BackpackTier.TIER_6.getDefaultSize()).isEqualTo(54);
        assertThat(BackpackTier.TIER_6.getCustomModelData()).isEqualTo(103);
        assertThat(BackpackTier.TIER_6.getTierNumber()).isEqualTo(6);
    }

    @Test
    @DisplayName("getNextTier() should return next tier for TIER_1 through TIER_5")
    void testGetNextTier() {
        assertThat(BackpackTier.TIER_1.getNextTier()).isEqualTo(BackpackTier.TIER_2);
        assertThat(BackpackTier.TIER_2.getNextTier()).isEqualTo(BackpackTier.TIER_3);
        assertThat(BackpackTier.TIER_3.getNextTier()).isEqualTo(BackpackTier.TIER_4);
        assertThat(BackpackTier.TIER_4.getNextTier()).isEqualTo(BackpackTier.TIER_5);
        assertThat(BackpackTier.TIER_5.getNextTier()).isEqualTo(BackpackTier.TIER_6);
    }

    @Test
    @DisplayName("getNextTier() should return null for TIER_6")
    void testGetNextTierMaxTier() {
        assertThat(BackpackTier.TIER_6.getNextTier()).isNull();
    }

    @Test
    @DisplayName("getPreviousTier() should return null for TIER_1")
    void testGetPreviousTierFirstTier() {
        assertThat(BackpackTier.TIER_1.getPreviousTier()).isNull();
    }

    @Test
    @DisplayName("getPreviousTier() should return previous tier for TIER_2 through TIER_6")
    void testGetPreviousTier() {
        assertThat(BackpackTier.TIER_2.getPreviousTier()).isEqualTo(BackpackTier.TIER_1);
        assertThat(BackpackTier.TIER_3.getPreviousTier()).isEqualTo(BackpackTier.TIER_2);
        assertThat(BackpackTier.TIER_4.getPreviousTier()).isEqualTo(BackpackTier.TIER_3);
        assertThat(BackpackTier.TIER_5.getPreviousTier()).isEqualTo(BackpackTier.TIER_4);
        assertThat(BackpackTier.TIER_6.getPreviousTier()).isEqualTo(BackpackTier.TIER_5);
    }

    @Test
    @DisplayName("canUpgrade() should return true for TIER_1 through TIER_5")
    void testCanUpgrade() {
        assertThat(BackpackTier.TIER_1.canUpgrade()).isTrue();
        assertThat(BackpackTier.TIER_2.canUpgrade()).isTrue();
        assertThat(BackpackTier.TIER_3.canUpgrade()).isTrue();
        assertThat(BackpackTier.TIER_4.canUpgrade()).isTrue();
        assertThat(BackpackTier.TIER_5.canUpgrade()).isTrue();
    }

    @Test
    @DisplayName("canUpgrade() should return false for TIER_6")
    void testCanUpgradeMaxTier() {
        assertThat(BackpackTier.TIER_6.canUpgrade()).isFalse();
    }

    @Test
    @DisplayName("fromId() should return correct tier for valid IDs")
    void testFromIdValid() {
        assertThat(BackpackTier.fromId("tier1")).isEqualTo(BackpackTier.TIER_1);
        assertThat(BackpackTier.fromId("tier2")).isEqualTo(BackpackTier.TIER_2);
        assertThat(BackpackTier.fromId("tier3")).isEqualTo(BackpackTier.TIER_3);
        assertThat(BackpackTier.fromId("tier4")).isEqualTo(BackpackTier.TIER_4);
        assertThat(BackpackTier.fromId("tier5")).isEqualTo(BackpackTier.TIER_5);
        assertThat(BackpackTier.fromId("tier6")).isEqualTo(BackpackTier.TIER_6);
    }

    @Test
    @DisplayName("fromId() should be case-insensitive")
    void testFromIdCaseInsensitive() {
        assertThat(BackpackTier.fromId("TIER1")).isEqualTo(BackpackTier.TIER_1);
        assertThat(BackpackTier.fromId("Tier2")).isEqualTo(BackpackTier.TIER_2);
        assertThat(BackpackTier.fromId("TiEr3")).isEqualTo(BackpackTier.TIER_3);
    }

    @Test
    @DisplayName("fromId() should return null for invalid IDs")
    void testFromIdInvalid() {
        assertThat(BackpackTier.fromId("tier0")).isNull();
        assertThat(BackpackTier.fromId("tier7")).isNull();
        assertThat(BackpackTier.fromId("invalid")).isNull();
        assertThat(BackpackTier.fromId("")).isNull();
    }

    @Test
    @DisplayName("fromId() should return null for null input")
    void testFromIdNull() {
        assertThat(BackpackTier.fromId(null)).isNull();
    }

    @Test
    @DisplayName("fromNumber() should return correct tier for valid numbers 1-6")
    void testFromNumberValid() {
        assertThat(BackpackTier.fromNumber(1)).isEqualTo(BackpackTier.TIER_1);
        assertThat(BackpackTier.fromNumber(2)).isEqualTo(BackpackTier.TIER_2);
        assertThat(BackpackTier.fromNumber(3)).isEqualTo(BackpackTier.TIER_3);
        assertThat(BackpackTier.fromNumber(4)).isEqualTo(BackpackTier.TIER_4);
        assertThat(BackpackTier.fromNumber(5)).isEqualTo(BackpackTier.TIER_5);
        assertThat(BackpackTier.fromNumber(6)).isEqualTo(BackpackTier.TIER_6);
    }

    @Test
    @DisplayName("fromNumber() should return null for numbers out of range")
    void testFromNumberInvalid() {
        assertThat(BackpackTier.fromNumber(0)).isNull();
        assertThat(BackpackTier.fromNumber(7)).isNull();
        assertThat(BackpackTier.fromNumber(-1)).isNull();
        assertThat(BackpackTier.fromNumber(100)).isNull();
    }

    @Test
    @DisplayName("fromCustomModelData() should return correct tier for valid CMD values")
    void testFromCustomModelDataValid() {
        assertThat(BackpackTier.fromCustomModelData(100)).isEqualTo(BackpackTier.TIER_1);
        assertThat(BackpackTier.fromCustomModelData(101)).isEqualTo(BackpackTier.TIER_2);
        assertThat(BackpackTier.fromCustomModelData(102)).isEqualTo(BackpackTier.TIER_3);
        assertThat(BackpackTier.fromCustomModelData(105)).isEqualTo(BackpackTier.TIER_4);
        assertThat(BackpackTier.fromCustomModelData(104)).isEqualTo(BackpackTier.TIER_5);
        assertThat(BackpackTier.fromCustomModelData(103)).isEqualTo(BackpackTier.TIER_6);
    }

    @Test
    @DisplayName("fromCustomModelData() should return null for invalid CMD values")
    void testFromCustomModelDataInvalid() {
        assertThat(BackpackTier.fromCustomModelData(0)).isNull();
        assertThat(BackpackTier.fromCustomModelData(99)).isNull();
        assertThat(BackpackTier.fromCustomModelData(106)).isNull();
        assertThat(BackpackTier.fromCustomModelData(200)).isNull();
        assertThat(BackpackTier.fromCustomModelData(-1)).isNull();
    }

    @Test
    @DisplayName("values() should return all 6 tiers in order")
    void testValues() {
        BackpackTier[] tiers = BackpackTier.values();
        assertThat(tiers).hasSize(6);
        assertThat(tiers[0]).isEqualTo(BackpackTier.TIER_1);
        assertThat(tiers[1]).isEqualTo(BackpackTier.TIER_2);
        assertThat(tiers[2]).isEqualTo(BackpackTier.TIER_3);
        assertThat(tiers[3]).isEqualTo(BackpackTier.TIER_4);
        assertThat(tiers[4]).isEqualTo(BackpackTier.TIER_5);
        assertThat(tiers[5]).isEqualTo(BackpackTier.TIER_6);
    }

    @Test
    @DisplayName("Tier numbers should increment sequentially")
    void testTierNumbersSequential() {
        for (int i = 1; i <= 6; i++) {
            BackpackTier tier = BackpackTier.fromNumber(i);
            assertThat(tier).isNotNull();
            assertThat(tier.getTierNumber()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("Default sizes should be multiples of 9")
    void testDefaultSizesMultiplesOfNine() {
        for (BackpackTier tier : BackpackTier.values()) {
            assertThat(tier.getDefaultSize() % 9)
                    .withFailMessage(
                            "Tier %s size %d is not a multiple of 9",
                            tier.getId(), tier.getDefaultSize())
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Default sizes should increase with tier")
    void testDefaultSizesIncreasing() {
        assertThat(BackpackTier.TIER_1.getDefaultSize())
                .isLessThan(BackpackTier.TIER_2.getDefaultSize());
        assertThat(BackpackTier.TIER_2.getDefaultSize())
                .isLessThan(BackpackTier.TIER_3.getDefaultSize());
        assertThat(BackpackTier.TIER_3.getDefaultSize())
                .isLessThan(BackpackTier.TIER_4.getDefaultSize());
        assertThat(BackpackTier.TIER_4.getDefaultSize())
                .isLessThan(BackpackTier.TIER_5.getDefaultSize());
        assertThat(BackpackTier.TIER_5.getDefaultSize())
                .isLessThan(BackpackTier.TIER_6.getDefaultSize());
    }

    @Test
    @DisplayName("All custom model data values should be unique")
    void testCustomModelDataUnique() {
        BackpackTier[] tiers = BackpackTier.values();
        for (int i = 0; i < tiers.length; i++) {
            for (int j = i + 1; j < tiers.length; j++) {
                assertThat(tiers[i].getCustomModelData())
                        .withFailMessage(
                                "Tiers %s and %s have duplicate CMD: %d",
                                tiers[i].getId(), tiers[j].getId(), tiers[i].getCustomModelData())
                        .isNotEqualTo(tiers[j].getCustomModelData());
            }
        }
    }

    @Test
    @DisplayName("Roman numerals should be correct")
    void testRomanNumerals() {
        String[] expectedNumerals = {"I", "II", "III", "IV", "V", "VI"};
        BackpackTier[] tiers = BackpackTier.values();

        for (int i = 0; i < tiers.length; i++) {
            assertThat(tiers[i].getRomanNumeral()).isEqualTo(expectedNumerals[i]);
        }
    }
}
