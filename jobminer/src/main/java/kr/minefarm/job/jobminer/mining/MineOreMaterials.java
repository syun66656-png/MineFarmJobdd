package kr.minefarm.job.jobminer.mining;

import org.bukkit.Material;

/**
 * 다이너마이트 폭발 등에서 "광물(광석)"만 구분할 때 사용.
 * <p>
 * {@link Material#isAir()} 는 Bukkit Registry 초기화가 필요하므로
 * 단위 테스트 환경에서 실행 불가. 이름 비교로 대체한다.
 * AIR / CAVE_AIR / VOID_AIR 는 "_ORE" 로 끝나지 않으므로 자연스럽게 false 반환.
 */
public final class MineOreMaterials {

    private MineOreMaterials() {}

    public static boolean isOre(Material material) {
        if (material == null) return false;
        String name = material.name();
        // _ORE 로 끝나는 모든 바닐라 광석
        if (name.endsWith("_ORE")) return true;
        // 별도 처리 필요한 광물
        return material == Material.ANCIENT_DEBRIS
                || material == Material.GILDED_BLACKSTONE;
    }
}
