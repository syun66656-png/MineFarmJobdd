package kr.minefarm.job.jobminer.mining;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MineOreMaterials.isOre")
class MineOreMaterialsTest {

    @ParameterizedTest(name = "{0} → isOre=true")
    @EnumSource(value = Material.class, names = {
            "COAL_ORE", "IRON_ORE", "GOLD_ORE", "DIAMOND_ORE",
            "EMERALD_ORE", "LAPIS_ORE", "REDSTONE_ORE", "COPPER_ORE",
            "DEEPSLATE_COAL_ORE", "DEEPSLATE_IRON_ORE", "DEEPSLATE_GOLD_ORE",
            "DEEPSLATE_DIAMOND_ORE", "DEEPSLATE_EMERALD_ORE",
            "DEEPSLATE_LAPIS_ORE", "DEEPSLATE_REDSTONE_ORE", "DEEPSLATE_COPPER_ORE",
            "NETHER_GOLD_ORE", "NETHER_QUARTZ_ORE",
            "ANCIENT_DEBRIS", "GILDED_BLACKSTONE"
    })
    void 광석으로_판별되어야_함(Material material) {
        assertTrue(MineOreMaterials.isOre(material),
                material.name() + "은 광석으로 분류되어야 합니다.");
    }

    @ParameterizedTest(name = "{0} → isOre=false")
    @EnumSource(value = Material.class, names = {
            "STONE", "DIRT", "GRASS_BLOCK", "OAK_LOG",
            "COBBLESTONE", "SAND", "GRAVEL",
            "RAW_IRON", "RAW_GOLD", "COAL",
            "IRON_INGOT", "GOLD_INGOT", "DIAMOND"
    })
    void 광석이_아닌것은_false(Material material) {
        assertFalse(MineOreMaterials.isOre(material),
                material.name() + "은 광석이 아니어야 합니다.");
    }

    @Test
    void null_입력은_false() {
        assertFalse(MineOreMaterials.isOre(null));
    }

    @Test
    void AIR_입력은_false() {
        assertFalse(MineOreMaterials.isOre(Material.AIR));
        assertFalse(MineOreMaterials.isOre(Material.CAVE_AIR));
        assertFalse(MineOreMaterials.isOre(Material.VOID_AIR));
    }
}
