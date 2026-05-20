package kr.minefarm.job.jobminer.autosell;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 리젠 채굴 자동판매 결과: Vault 입금액과 인벤으로 돌려줄(가격 없음·입금 실패 시 전량) 스택 목록.
 */
public record PricedAutoSellResult(double goldDeposited, List<ItemStack> stacksForInventory) {
}
