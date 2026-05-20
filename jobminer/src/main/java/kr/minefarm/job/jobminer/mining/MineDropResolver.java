package kr.minefarm.job.jobminer.mining;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 지정(100%)·특수(확률) 드롭 해석.
 */
public final class MineDropResolver {

    private final JobMinerConfig config;

    public MineDropResolver(JobMinerConfig config) {
        this.config = config;
    }

    /** 리젠 채굴 시 지급할 커스텀 드롭 (바닐라 드롭 대체). */
    public List<ItemStack> resolveMiningDrops() {
        List<ItemStack> items = new ArrayList<>();

        for (Map.Entry<Material, Integer> entry : config.getGuaranteedDrops().entrySet()) {
            if (entry.getValue() > 0) {
                items.add(new ItemStack(entry.getKey(), entry.getValue()));
            }
        }

        for (JobMinerConfig.SpecialDrop special : config.getSpecialDrops()) {
            if (ThreadLocalRandom.current().nextDouble() < special.chance()) {
                items.add(new ItemStack(special.material(), special.amount()));
            }
        }

        return items;
    }

    /** 자동판매 가격 산정용 — 커스텀 드롭 목록 (가격표에 있는 것만 유효). */
    public List<ItemStack> resolveDropsForPricing() {
        return resolveMiningDrops();
    }
}
