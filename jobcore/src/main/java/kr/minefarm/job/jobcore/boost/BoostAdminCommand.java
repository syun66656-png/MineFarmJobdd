package kr.minefarm.job.jobcore.boost;

import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /직업부스트생성 <배율> <분>     : 손에 든 아이템을 부스트 쿠폰으로 마킹
 * /직업부스트지급 <닉> <배율> <분> : 특정 플레이어에게 즉시 부스트 적용 (오프라인 가능)
 */
public final class BoostAdminCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ExperienceBoostItem boostItem;
    private final PlayerProfileService profileService;
    private final MessageConfig messages;

    public BoostAdminCommand(JavaPlugin plugin, ExperienceBoostItem boostItem,
                             PlayerProfileService profileService, MessageConfig messages) {
        this.plugin = plugin;
        this.boostItem = boostItem;
        this.profileService = profileService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }
        String name = command.getName();
        if ("직업부스트생성".equalsIgnoreCase(name)) {
            return handleCreate(sender, args);
        }
        if ("직업부스트지급".equalsIgnoreCase(name)) {
            return handleGrant(sender, args);
        }
        sender.sendMessage("§c알 수 없는 명령어: " + name);
        return true;
    }

    /** /직업부스트생성 <배율> <분> */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(messages.get("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /직업부스트생성 <배율> <분>");
            return true;
        }
        double mul = parseDouble(args[0]);
        int min = parseInt(args[1]);
        if (mul <= 1.0 || min <= 0) {
            sender.sendMessage("§c배율은 1.0 초과, 분은 1 이상이어야 합니다.");
            return true;
        }
        ItemStack hand = admin.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            sender.sendMessage("§c손에 아이템을 들고 사용해주세요.");
            return true;
        }
        if (!boostItem.stampItem(hand, mul, min)) {
            sender.sendMessage("§c쿠폰 마킹 실패.");
            return true;
        }
        admin.sendMessage("§a[부스트] §f쿠폰 생성 완료. 배율 §e" + formatMul(mul)
                + "배 §f· 지속 §e" + min + "분§f.");
        return true;
    }

    /** /직업부스트지급 <닉> <배율> <분> */
    private boolean handleGrant(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /직업부스트지급 <닉네임> <배율> <분>");
            return true;
        }
        String targetName = args[0];
        double mul = parseDouble(args[1]);
        int min = parseInt(args[2]);
        if (mul <= 1.0 || min <= 0) {
            sender.sendMessage("§c배율은 1.0 초과, 분은 1 이상이어야 합니다.");
            return true;
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(targetName);
        if (cached != null) {
            applyBoost(sender, cached, targetName, mul, min);
            return true;
        }

        sender.sendMessage(messages.get("admin-resolving"));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!offline.hasPlayedBefore() && !offline.isOnline()) {
                    sender.sendMessage(messages.get("admin-player-not-found"));
                    return;
                }
                applyBoost(sender, offline, targetName, mul, min);
            });
        });
        return true;
    }

    private void applyBoost(CommandSender sender, OfflinePlayer target, String name,
                            double mul, int min) {
        profileService.loadOrCreate(target.getUniqueId()).whenComplete((profile, err) -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (err != null || profile == null) {
                    sender.sendMessage("§c[부스트] §f프로필 로드 실패: " + name);
                    return;
                }
                long now = System.currentTimeMillis();
                profile.setBoostMultiplier(mul);
                profile.setBoostExpiryTime(now + min * 60_000L);
                profileService.saveAsync(profile);
                sender.sendMessage("§a[부스트] §f" + name + " 에게 §e"
                        + formatMul(mul) + "배 §f부스트 §e" + min + "분 §f적용.");
                if (target.isOnline() && target.getPlayer() != null) {
                    target.getPlayer().sendMessage(messages.format("boost-started", Map.of(
                            "multiplier", formatMul(mul),
                            "minutes", String.valueOf(min)
                    )));
                }
            });
        });
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String formatMul(double m) {
        if (m == Math.rint(m)) return String.valueOf((long) m);
        return String.format(Locale.ROOT, "%.1f", m);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) return List.of();
        List<String> out = new ArrayList<>();
        String name = command.getName();
        if ("직업부스트지급".equalsIgnoreCase(name) && args.length == 1) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) out.add(p.getName());
            }
        }
        return out;
    }
}
