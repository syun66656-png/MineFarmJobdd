package kr.minefarm.job.jobminer.message;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

/**
 * jobminer/messages.yml 로더.
 * 모든 광부 모듈 출력 메시지를 단일 파일로 관리한다.
 * 플러그인 데이터 폴더의 jobminer/messages.yml 가 우선이며,
 * 없으면 jar 내부 리소스의 기본값을 복사한다.
 */
public final class MinerMessages {

    private static final String RESOURCE_PATH = "jobminer/messages.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public MinerMessages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);

        // 데이터 폴더에 파일이 없으면 jar 내부 리소스를 복사 (저장)
        if (!file.exists()) {
            try {
                plugin.saveResource(RESOURCE_PATH, false);
            } catch (IllegalArgumentException ignored) {
                // jar에 리소스 없을 경우 — 빈 config로 시작
            }
        }

        if (file.exists()) {
            this.config = YamlConfiguration.loadConfiguration(file);
        } else {
            this.config = new YamlConfiguration();
        }

        // jar 내부 리소스를 default 값으로 덮어 (사용자가 일부 키만 작성해도 fallback)
        try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
            if (in != null) {
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                this.config.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[JobMiner] messages.yml defaults 로드 실패", e);
        }
    }

    /** 단순 메시지 조회. 키 미존재 시 placeholder 형태로 반환(디버깅 용). */
    public String get(String key) {
        String value = config.getString("messages." + key);
        if (value == null) {
            value = config.getString(key); // messages. prefix 없는 경우 호환
        }
        return value != null ? value : "<missing:" + key + ">";
    }

    /** get(key) + {placeholder} 치환 + 색코드 변환 (&  →  §). */
    public String format(String key, Map<String, String> placeholders) {
        String raw = get(key);
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return raw.replace('&', '§');
    }

    /** 플레이스홀더 없는 단순 메시지 (색코드 변환만) */
    public String format(String key) {
        return get(key).replace('&', '§');
    }

    /** 메시지 정의 존재 여부 */
    public boolean has(String key) {
        return config.contains("messages." + key) || config.contains(key);
    }
}
