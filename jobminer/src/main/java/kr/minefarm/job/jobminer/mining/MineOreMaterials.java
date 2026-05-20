package kr.minefarm.job.jobminer.mining;

import org.bukkit.Material;

/**
 * 다이너마이트 폭발 등에서 "광물(광석)"만 구분할 때 사용.
 */
public final class MineOreMaterials {

    private MineOreMaterials() {
    }

    public static boolean isOre(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        String name = material.name();
        if (name.endsWith("_ORE")) {
            return true;
        }
        if (name.contains("_ORE")) {
            return true;
        }
        return material == Material.ANCIENT_DEBRIS
                || material == Material.NETHER_GOLD_ORE
                || material == Material.GILDED_BLACKSTONE;
    }
}
