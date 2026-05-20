package kr.minefarm.job.jobminer.mining;

import org.bukkit.Material;

/**
 * 다이너마이트 폭발 등에서 "광물(광석)"만 구분할 때 사용.
 */
public final class MineOreMaterials {

    private MineOreMaterials() {}

    public static boolean isOre(Material material) {
        if (material == null || material.isAir()) return false;
        String name = material.name();
        // _ORE 로 끝나는 모든 바닐라 광석 (COAL_ORE, DEEPSLATE_IRON_ORE 등)
        if (name.endsWith("_ORE")) return true;
        // 별도 처리 필요한 광물
        return material == Material.ANCIENT_DEBRIS
                || material == Material.NETHER_GOLD_ORE
                || material == Material.GILDED_BLACKSTONE;
    }
}
