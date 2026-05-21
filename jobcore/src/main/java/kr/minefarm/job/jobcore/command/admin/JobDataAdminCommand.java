package kr.minefarm.job.jobcore.command.admin;

import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 관리자 데이터 직접 조작 명령어.
 *
 * <pre>
 * /직업관리 레벨 set    &lt;닉&gt; &lt;값&gt;     레벨을 지정 값으로 설정
 * /직업관리 레벨 add    &lt;닉&gt; &lt;양&gt;     레벨에 더하기 (음수 가능)
 * /직업관리 경험치 set  &lt;닉&gt; &lt;값&gt;
 * /직업관리 경험치 add  &lt;닉&gt; &lt;양&gt;
 * /직업관리 포인트 set  &lt;닉&gt; &lt;값&gt;     스탯 투자 가능 포인트
 * /직업관리 포인트 add  &lt;닉&gt; &lt;양&gt;
 * /직업관리 스탯 set    &lt;닉&gt; &lt;타입&gt; &lt;값&gt;   특정 스탯 레벨 설정
 * /직업관리 스탯 add    &lt;닉&gt; &lt;타입&gt; &lt;양&gt;   특정 스탯 레벨 더하기
 * /직업관리 정보       &lt;닉&gt;            현재 데이터 조회
 * 타입: relic, skill, sell, auto_sell
 * </pre>
 *
 * 오프라인 플레이어 지원. {@code minefarmjob.admin} 권한 필요.
 */
public final class JobDataAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("레벨", "경험치", "포인트", "스탯", "정보");
    private static final List<String> OPS = List.of("set", "add");

    private final PlayerProfileService profileService;
    private final MessageConfig messages;
    private final JavaPlugin plugin;

    public JobDataAdminCommand(JavaPlugin plugin, PlayerProfileService profileService, MessageConfig messages) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String action = args[0];
        switch (action) {
            case "레벨"   -> handleNumeric(sender, args, "level");
            case "경험치" -> handleNumeric(sender, args, "exp");
            case "포인트" -> handleNumeric(sender, args, "points");
            case "스탯"   -> handleStat(sender, args);
            case "정보"   -> handleInfo(sender, args);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    // ── 레벨/경험치/포인트 공통 처리 ─────────────────────────────────────────
    private void handleNumeric(CommandSender sender, String[] args, String target) {
        // /직업관리 레벨 <op> <닉> <값>
        if (args.length < 4) {
            sender.sendMessage("§c사용법: /직업관리 " + args[0] + " <set|add> <닉네임> <값>");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        if (!OPS.contains(op)) {
            sender.sendMessage("§c연산은 'set' 또는 'add' 만 가능합니다.");
            return;
        }
        String targetName = args[2];
        long value;
        try {
            value = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c숫자를 입력해주세요: " + args[3]);
            return;
        }

        resolveAndApply(sender, targetName, (profile) -> {
            int before;
            int after;
            switch (target) {
                case "level" -> {
                    before = profile.getLevel();
                    after = op.equals("set") ? (int) value : before + (int) value;
                    if (after < 1) after = 1;
                    profile.setLevel(after);
                    sender.sendMessage("§a[관리] §f" + targetName + " 레벨 §e" + before + " §7→ §a" + after);
                }
                case "exp" -> {
                    before = (int) profile.getExperience();
                    long newVal = op.equals("set") ? value : profile.getExperience() + value;
                    if (newVal < 0) newVal = 0;
                    profile.setExperience(newVal);
                    after = (int) newVal;
                    sender.sendMessage("§a[관리] §f" + targetName + " 경험치 §e" + before + " §7→ §a" + after);
                }
                case "points" -> {
                    before = profile.getStatPoints();
                    after = op.equals("set") ? (int) value : before + (int) value;
                    if (after < 0) after = 0;
                    profile.setStatPoints(after);
                    sender.sendMessage("§a[관리] §f" + targetName + " 스탯 포인트 §e" + before + " §7→ §a" + after);
                }
                default -> {}
            }
        });
    }

    // ── 스탯 ─────────────────────────────────────────────────────────────────
    private void handleStat(CommandSender sender, String[] args) {
        // /직업관리 스탯 <op> <닉> <타입> <값>
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /직업관리 스탯 <set|add> <닉네임> <relic|skill|sell|auto_sell> <값>");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        if (!OPS.contains(op)) {
            sender.sendMessage("§c연산은 'set' 또는 'add' 만 가능합니다.");
            return;
        }
        String targetName = args[2];
        StatType statType = StatType.fromKey(args[3]).orElse(null);
        if (statType == null) {
            sender.sendMessage("§c알 수 없는 스탯: " + args[3] + " §7(relic, skill, sell, auto_sell)");
            return;
        }
        int value;
        try {
            value = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c숫자를 입력해주세요: " + args[4]);
            return;
        }

        resolveAndApply(sender, targetName, profile -> {
            int before = profile.getStatLevel(statType);
            int after = op.equals("set") ? value : before + value;
            if (after < 0) after = 0;
            profile.setStatLevel(statType, after);
            sender.sendMessage("§a[관리] §f" + targetName + " " + statType.getDisplayName()
                    + " §e" + before + " §7→ §a" + after);
        });
    }

    // ── 정보 ─────────────────────────────────────────────────────────────────
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /직업관리 정보 <닉네임>");
            return;
        }
        String targetName = args[1];
        resolveProfile(sender, targetName, profile -> {
            sender.sendMessage("§6§l───── §f" + targetName + " §6§l─────");
            sender.sendMessage("§7직업: §f" + profile.getJobId().name());
            sender.sendMessage("§7레벨: §f" + profile.getLevel());
            sender.sendMessage("§7경험치: §f" + profile.getExperience());
            sender.sendMessage("§7스탯 포인트: §f" + profile.getStatPoints());
            for (StatType t : StatType.values()) {
                sender.sendMessage("§7  " + t.getDisplayName() + ": §f" + profile.getStatLevel(t));
            }
        });
    }

    // ── 헬퍼: 대상 플레이어 resolve → 메인스레드에서 작업 + 저장 ───────────────
    private void resolveAndApply(CommandSender sender, String targetName, ProfileMutator action) {
        resolveProfile(sender, targetName, profile -> {
            action.apply(profile);
            profileService.saveAsync(profile).whenComplete((v, err) -> {
                if (err != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage("§c[관리] §f저장 실패: " + err.getMessage()));
                }
            });
        });
    }

    /**
     * 닉네임으로 플레이어를 resolve → loadOrCreate → 메인스레드에서 콜백 실행.
     * 오프라인 플레이어 지원.
     */
    private void resolveProfile(CommandSender sender, String targetName, ProfileMutator callback) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(targetName);
        if (cached != null) {
            applyProfile(sender, cached.getUniqueId(), targetName, callback);
            return;
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
                applyProfile(sender, offline.getUniqueId(), targetName, callback);
            });
        });
    }

    private void applyProfile(CommandSender sender, UUID uuid, String targetName, ProfileMutator callback) {
        CompletableFuture<PlayerJobProfile> future = profileService.loadOrCreate(uuid);
        future.whenComplete((profile, err) -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (err != null || profile == null) {
                    sender.sendMessage("§c[관리] §f프로필 로드 실패: " + targetName);
                    return;
                }
                callback.apply(profile);
            });
        });
    }

    @FunctionalInterface
    private interface ProfileMutator {
        void apply(PlayerJobProfile profile);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§6§l───── §f/" + label + " 사용법 §6§l─────");
        sender.sendMessage("§7/" + label + " 레벨   <set|add> <닉> <값>");
        sender.sendMessage("§7/" + label + " 경험치 <set|add> <닉> <값>");
        sender.sendMessage("§7/" + label + " 포인트 <set|add> <닉> <값>");
        sender.sendMessage("§7/" + label + " 스탯   <set|add> <닉> <relic|skill|sell|auto_sell> <값>");
        sender.sendMessage("§7/" + label + " 정보   <닉>");
    }

    // ── Tab Completion ──────────────────────────────────────────────────────
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) return List.of();
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            for (String a : ACTIONS) if (a.startsWith(args[0])) out.add(a);
            return out;
        }
        if (args.length == 2) {
            if (args[0].equals("정보")) {
                return onlineNames(args[1]);
            }
            for (String o : OPS) if (o.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(o);
            return out;
        }
        if (args.length == 3) {
            return onlineNames(args[2]);
        }
        if (args.length == 4 && args[0].equals("스탯")) {
            for (StatType t : StatType.values()) {
                if (t.getKey().startsWith(args[3].toLowerCase(Locale.ROOT))) out.add(t.getKey());
            }
            return out;
        }
        return out;
    }

    private List<String> onlineNames(String prefix) {
        List<String> names = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (var p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) names.add(p.getName());
        }
        return names;
    }
}
