package kr.minefarm.job.jobminer.shop;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.message.MinerMessages;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 광부 상점 GUI — config(shop-gui.items) 기반.
 * <p>
 * 각 슬롯은 ShopGuiItem 정의로 채워진다:
 *   - display: GUI 표시용(material/name/lore/custom-model-data 자유 설정)
 *   - sell:    실제 판매 매칭 기준(material + customModelData + name) + unit-price
 * <p>
 * 클릭 시 GUI 아이콘이 아니라 sell 정의를 기준으로 인벤에서 일치하는 아이템을 찾아 판매.
 */
public final class MinerShopGui implements Listener {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final JobCoreAPI core;
    private final JobMinerConfig config;
    private final MinerShopService shopService;
    private final MinerMessages messages;
    private final Player player;

    private Inventory inventory;
    /** slot → ShopGuiItem (클릭 라우팅용) */
    private final Map<Integer, JobMinerConfig.ShopGuiItem> slotMap = new HashMap<>();

    public MinerShopGui(
            JavaPlugin plugin,
            JobCoreAPI core,
            JobMinerConfig config,
            MinerShopService shopService,
            MinerMessages messages,
            Player player
    ) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.shopService = shopService;
        this.messages = messages;
        this.player = player;
    }

    public void open() {
        buildInventory();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    private void buildInventory() {
        slotMap.clear();
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        int sellStat = profile != null ? profile.getStatLevel(StatType.SELL) : 0;

        int size = config.getShopGuiSize();
        String title = config.getShopGuiTitle().replace('&', '§');
        inventory = Bukkit.createInventory(null, size, LegacyComponentSerializer.legacySection().deserialize(title));

        // shop-gui.items 의 각 슬롯 채우기
        for (JobMinerConfig.ShopGuiItem guiItem : config.getShopGuiItems()) {
            int slot = guiItem.slot();
            if (slot < 0 || slot >= size) continue;
            inventory.setItem(slot, buildDisplayItem(guiItem, sellStat));
            slotMap.put(slot, guiItem);
        }

        // 레이아웃(전체판매/닫기 등) — config의 shop-gui.layout.<key>.{slot,material,name,lore}
        // sell-all
        int sellAllSlot = config.getShopGuiLayoutSlot("sell-all", -1);
        if (sellAllSlot >= 0 && sellAllSlot < size) {
            Material mat = config.getShopGuiLayoutMaterial("sell-all", Material.LIME_STAINED_GLASS_PANE);
            String name = config.getShopGuiLayoutName("sell-all", "&a&l★ 전체 판매");
            List<String> lore = withDefault(config.getShopGuiLayoutLore("sell-all"),
                    List.of("&7판매 가능한 아이템을 모두 판매합니다.", "", "&e▶ 클릭"));
            inventory.setItem(sellAllSlot, makeLayoutItem(mat, name, lore));
        }
        // close
        int closeSlot = config.getShopGuiLayoutSlot("close", -1);
        if (closeSlot >= 0 && closeSlot < size) {
            Material mat = config.getShopGuiLayoutMaterial("close", Material.BARRIER);
            String name = config.getShopGuiLayoutName("close", "&c✕ 닫기");
            List<String> lore = withDefault(config.getShopGuiLayoutLore("close"), List.of());
            inventory.setItem(closeSlot, makeLayoutItem(mat, name, lore));
        }
        // filler (지정 슬롯 외 빈 공간을 채울 머티리얼)
        Material filler = config.getShopGuiLayoutMaterial("filler", null);
        if (filler != null) {
            String fillerName = config.getShopGuiLayoutName("filler", " ");
            for (int i = 0; i < size; i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, makeLayoutItem(filler, fillerName, List.of()));
                }
            }
        }
    }

    private ItemStack buildDisplayItem(JobMinerConfig.ShopGuiItem guiItem, int sellStat) {
        JobMinerConfig.ShopGuiItem.DisplayDef d = guiItem.display();
        JobMinerConfig.ShopGuiItem.SellDef sell = guiItem.sell();

        ItemStack stack = new ItemStack(d.material(), 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        double basePrice = sell.unitPrice();
        double bonusMul = config.getSellBonusMultiplier();
        double myUnit = basePrice * (1 + bonusMul * sellStat);
        int myAmount = shopService.countMatching(player, sell);
        double expectedTotal = myUnit * myAmount;

        // 플레이스홀더 변환
        Map<String, String> ph = new HashMap<>();
        ph.put("{base-price}", fmtNum(basePrice));
        ph.put("{my-price}", fmtNum(myUnit));
        ph.put("{my-amount}", String.valueOf(myAmount));
        ph.put("{expected-total}", fmtNum(expectedTotal));
        ph.put("{sell-stat}", String.valueOf(sellStat));
        ph.put("{bonus-percent}", fmtNum(bonusMul * sellStat * 100));

        if (d.name() != null && !d.name().isBlank()) {
            meta.displayName(AMP.deserialize(applyPlaceholders(d.name(), ph)));
        }
        if (d.lore() != null && !d.lore().isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : d.lore()) {
                loreComponents.add(AMP.deserialize(applyPlaceholders(line == null ? "" : line, ph)));
            }
            meta.lore(loreComponents);
        }
        if (d.customModelData() != null && d.customModelData() > 0) {
            meta.setCustomModelData(d.customModelData());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack makeLayoutItem(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (name != null) meta.displayName(AMP.deserialize(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(AMP.deserialize(line == null ? "" : line));
            }
            meta.lore(components);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static String applyPlaceholders(String input, Map<String, String> ph) {
        String out = input;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private static String fmtNum(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }

    private static List<String> withDefault(List<String> list, List<String> def) {
        if (list == null || list.isEmpty()) return def;
        return list;
    }

    // ── 이벤트 ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.equals(player)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null) return;

        // 닫기
        if (slot == config.getShopGuiLayoutSlot("close", -1)) {
            player.closeInventory();
            return;
        }
        // 전체 판매
        if (slot == config.getShopGuiLayoutSlot("sell-all", -1)) {
            handleSellAll(profile);
            buildInventory();
            player.openInventory(inventory);
            return;
        }
        // 카탈로그 슬롯
        JobMinerConfig.ShopGuiItem guiItem = slotMap.get(slot);
        if (guiItem != null) {
            handleSellOne(guiItem, profile);
            buildInventory();
            player.openInventory(inventory);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }

    // ── 판매 처리 ─────────────────────────────────────────────────────────────

    private void handleSellOne(JobMinerConfig.ShopGuiItem guiItem, PlayerJobProfile profile) {
        MinerShopService.SellResult result = shopService.sellByGuiItem(player, guiItem, profile);
        if (!config.isShopNotifyOnSell()) return;
        notifyResult(result, guiItem);
    }

    private void handleSellAll(PlayerJobProfile profile) {
        MinerShopService.BulkSellResult result = shopService.sellAllByGui(player, profile);
        if (!config.isShopNotifyOnSell()) return;
        if (result.materialCount() == 0) {
            player.sendMessage(messages.format("shop-no-items"));
            return;
        }
        player.sendMessage(messages.format("shop-sell-all", java.util.Map.of(
                "count", String.valueOf(result.materialCount()),
                "total", fmtNum(result.totalGold()))));
    }

    private void notifyResult(MinerShopService.SellResult result, JobMinerConfig.ShopGuiItem guiItem) {
        MinerShopService.SellResult.Status status = result.status();
        String itemName = guiItem.display().name() != null
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(AMP.deserialize(guiItem.display().name()))
                : result.material().name();
        if (status == MinerShopService.SellResult.Status.SUCCESS) {
            player.sendMessage(messages.format("shop-sell-success", java.util.Map.of(
                    "item", itemName,
                    "amount", String.valueOf(result.amount()),
                    "price", fmtNum(result.price()))));
        } else if (status == MinerShopService.SellResult.Status.NO_ITEMS) {
            player.sendMessage(messages.format("shop-item-not-found", java.util.Map.of("item", itemName)));
        } else if (status == MinerShopService.SellResult.Status.VAULT_ERROR) {
            player.sendMessage(messages.format("shop-vault-error"));
        } else if (status == MinerShopService.SellResult.Status.NO_PRICE) {
            player.sendMessage(messages.format("shop-no-price"));
        }
    }
}
