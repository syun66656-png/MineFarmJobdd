package kr.minefarm.job.jobminer.shop;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 광부 상점 GUI (54칸).
 * <p>
 * 레이아웃:
 * <pre>
 *  행 0~3 (슬롯 0~35): 상점 카탈로그 — 가격 있는 광석 목록 (페이지네이션)
 *  행 4   (슬롯 36~44): 빈 공간 (구분선)
 *  슬롯 45: ◀ 이전 페이지
 *  슬롯 47: 인벤토리에 있는 판매 가능 아이템 요약
 *  슬롯 49: ★ 전체 판매 (가격 있는 모든 아이템 즉시 판매)
 *  슬롯 51: ▶ 다음 페이지
 *  슬롯 53: ✕ 닫기
 * </pre>
 * 카탈로그 슬롯 클릭 → 해당 머티리얼만 판매
 */
public final class MinerShopGui implements Listener {

    private static final int PAGE_SIZE = 36;   // 행 0~3
    private static final int SLOT_PREV      = 45;
    private static final int SLOT_SUMMARY   = 47;
    private static final int SLOT_SELL_ALL  = 49;
    private static final int SLOT_NEXT      = 51;
    private static final int SLOT_CLOSE     = 53;

    private static final Material BORDER_MAT = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material SELL_ALL_MAT = Material.LIME_STAINED_GLASS_PANE;

    private final JavaPlugin plugin;
    private final JobCoreAPI core;
    private final JobMinerConfig config;
    private final MinerShopService shopService;

    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;
    private List<Material> catalog;

    public MinerShopGui(
            JavaPlugin plugin,
            JobCoreAPI core,
            JobMinerConfig config,
            MinerShopService shopService,
            Player player
    ) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.shopService = shopService;
        this.player = player;
        this.catalog = shopService.getPricedMaterials();
    }

    public void open() {
        buildInventory();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    // ── 인벤토리 빌드 ──────────────────────────────────────────────────────────

    private void buildInventory() {
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        int sellStat = profile != null ? profile.getStatLevel(StatType.SELL) : 0;

        int totalPages = Math.max(1, (int) Math.ceil((double) catalog.size() / PAGE_SIZE));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        String rawTitle = config.getShopGuiTitle()
                .replace("{page}", String.valueOf(currentPage + 1))
                .replace("{total_pages}", String.valueOf(totalPages))
                .replace('&', '§');

        inventory = Bukkit.createInventory(null, 54, component(rawTitle));

        // 테두리 채우기 (행 4)
        for (int slot = 36; slot < 45; slot++) {
            inventory.setItem(slot, border());
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, border());
        }

        // 카탈로그 슬롯 채우기
        int start = currentPage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= catalog.size()) {
                inventory.setItem(i, null);
                continue;
            }
            Material mat = catalog.get(idx);
            inventory.setItem(i, makeCatalogItem(mat, sellStat, profile));
        }

        // 이전 페이지 버튼
        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, makeNavItem(Material.ARROW, "§e◀ 이전 페이지"));
        }

        // 다음 페이지 버튼
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, makeNavItem(Material.ARROW, "§e▶ 다음 페이지"));
        }

        // 전체 판매 버튼
        inventory.setItem(SLOT_SELL_ALL, makeSellAllButton(profile));

        // 요약 버튼 (내 인벤의 팔 수 있는 총액)
        inventory.setItem(SLOT_SUMMARY, makeSummaryItem(sellStat, profile));

        // 닫기
        inventory.setItem(SLOT_CLOSE, makeNavItem(Material.BARRIER, "§c✕ 닫기"));
    }

    private ItemStack makeCatalogItem(Material mat, int sellStat, PlayerJobProfile profile) {
        ItemStack icon = new ItemStack(mat, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        double basePrice = config.getShopBasePrice(mat);
        double statPrice = shopService.formatUnitPrice(mat, sellStat).equals("0") ? basePrice
                : Double.parseDouble(shopService.formatUnitPrice(mat, sellStat).replace(",", ""));

        // 내 인벤토리의 해당 아이템 수량
        int inInventory = countInInventory(mat);

        String name = "§f" + friendlyName(mat);
        List<String> lore = new ArrayList<>();
        lore.add("§7기본 단가: §6" + config.getShopBasePrice(mat) + "§7골드/개");
        if (sellStat > 0) {
            lore.add("§7스탯 적용 단가: §a" + shopService.formatUnitPrice(mat, sellStat) + "§7골드/개");
        }
        lore.add("§7내 보유량: §f" + inInventory + "§7개");
        if (inInventory > 0) {
            double totalEarning = basePrice * inInventory
                    * (1 + config.getSellBonusMultiplier() * sellStat);
            lore.add("§7예상 수익: §6+" + String.format("%.1f", totalEarning) + "§7골드");
            lore.add("");
            lore.add("§e▶ 클릭하여 전량 판매");
        } else {
            lore.add("");
            lore.add("§8보유 없음");
        }

        meta.displayName(component(name));
        meta.lore(lore.stream().map(this::component).toList());
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack makeSellAllButton(PlayerJobProfile profile) {
        ItemStack icon = new ItemStack(SELL_ALL_MAT, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        int sellStat = profile != null ? profile.getStatLevel(StatType.SELL) : 0;
        double totalEarning = estimateTotalEarning(sellStat);

        meta.displayName(component("§a§l★ 전체 판매"));
        List<String> lore = new ArrayList<>();
        lore.add("§7인벤토리의 판매 가능한 광석을 모두 판매합니다.");
        lore.add("");
        lore.add("§7예상 총 수익: §6+" + String.format("%.1f", totalEarning) + "§7골드");
        lore.add("");
        lore.add("§e▶ 클릭하여 전체 판매");
        meta.lore(lore.stream().map(this::component).toList());
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack makeSummaryItem(int sellStat, PlayerJobProfile profile) {
        ItemStack icon = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        meta.displayName(component("§e내 판매 가능 아이템"));
        List<String> lore = new ArrayList<>();
        int kinds = 0;
        for (Material mat : catalog) {
            int cnt = countInInventory(mat);
            if (cnt > 0) {
                kinds++;
                lore.add("§f" + friendlyName(mat) + " §7x§f" + cnt);
            }
        }
        if (kinds == 0) {
            lore.add("§8판매 가능한 아이템 없음");
        }
        meta.lore(lore.stream().map(this::component).toList());
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack makeNavItem(Material mat, String name) {
        ItemStack icon = new ItemStack(mat, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(component(name));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack border() {
        ItemStack pane = new ItemStack(BORDER_MAT, 1);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(component(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    // ── 이벤트 처리 ────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null) return;

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_PREV && currentPage > 0) {
            currentPage--;
            buildInventory();
            player.openInventory(inventory);
            return;
        }

        if (slot == SLOT_NEXT) {
            currentPage++;
            buildInventory();
            player.openInventory(inventory);
            return;
        }

        if (slot == SLOT_SELL_ALL) {
            handleSellAll(profile);
            buildInventory();
            player.openInventory(inventory);
            return;
        }

        if (slot < PAGE_SIZE) {
            int idx = currentPage * PAGE_SIZE + slot;
            if (idx < catalog.size()) {
                handleSellOne(catalog.get(idx), profile);
                buildInventory();
                player.openInventory(inventory);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }

    // ── 판매 처리 ──────────────────────────────────────────────────────────────

    private void handleSellOne(Material material, PlayerJobProfile profile) {
        MinerShopService.SellResult result = shopService.sellAll(player, material, profile);
        if (!config.isShopNotifyOnSell()) return;

        switch (result.status()) {
            case SUCCESS -> player.sendMessage(config.getShopSellMessage()
                    .replace("{item}", friendlyName(material))
                    .replace("{amount}", String.valueOf(result.amount()))
                    .replace("{price}", String.format("%.1f", result.price()))
                    .replace('&', '§'));
            case NO_ITEMS ->
                    player.sendMessage("§c" + friendlyName(material) + "이(가) 인벤토리에 없습니다.");
            case VAULT_ERROR ->
                    player.sendMessage(config.getShopVaultErrorMessage().replace('&', '§'));
            case NO_PRICE ->
                    player.sendMessage("§c해당 아이템은 판매할 수 없습니다.");
        }
    }

    private void handleSellAll(PlayerJobProfile profile) {
        MinerShopService.BulkSellResult result = shopService.sellAllPriced(player, profile);
        if (!config.isShopNotifyOnSell()) return;

        if (!result.isSuccess()) {
            if (result.materialCount() == 0) {
                player.sendMessage(config.getShopNoItemsMessage().replace('&', '§'));
            } else {
                player.sendMessage(config.getShopVaultErrorMessage().replace('&', '§'));
            }
            return;
        }
        player.sendMessage(config.getShopSellAllMessage()
                .replace("{count}", String.valueOf(result.materialCount()))
                .replace("{total}", String.format("%.1f", result.totalGold()))
                .replace('&', '§'));
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    private int countInInventory(Material mat) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private double estimateTotalEarning(int sellStat) {
        double total = 0.0;
        for (Material mat : catalog) {
            int cnt = countInInventory(mat);
            if (cnt == 0) continue;
            double base = config.getShopBasePrice(mat);
            total += base * cnt * (1 + config.getSellBonusMultiplier() * sellStat);
        }
        return total;
    }

    private static String friendlyName(Material mat) {
        return mat.name().replace('_', ' ').toLowerCase(Locale.ROOT)
                .substring(0, 1).toUpperCase(Locale.ROOT)
                + mat.name().replace('_', ' ').toLowerCase(Locale.ROOT).substring(1);
    }

    private Component component(String legacyText) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }
}
