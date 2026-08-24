package com.velocitychat;

import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Manages forbidden-word (违禁词) filtering for cross-server chat.
 * <p>
 * Words are loaded from a separate text file (banned_words.txt) in the plugin's
 * data directory. Words in the file are separated by semicolons (;); lines starting
 * with '#' are treated as comments. Matching is case-insensitive and ignores
 * Minecraft color codes.
 */
public class ForbiddenWordsManager {

    private static final Pattern COLOR_CODE = Pattern.compile("(?i)(?:&|§)[0-9a-fklmnor]");

    private final Logger logger;
    private final Path dataDirectory;

    private boolean enabled;
    private String bypassPermission = "velocitychat.admin";
    // CopyOnWriteArrayList: 每次聊天都会遍历，reload 时安全替换，避免并发修改异常
    private final List<String> forbiddenWords = new CopyOnWriteArrayList<>();

    public ForbiddenWordsManager(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * (Re)load the filter switch, bypass permission and the banned words file.
     */
    public void load(ConfigManager config) {
        this.enabled = config.isForbiddenWordsEnabled();
        this.bypassPermission = config.getForbiddenWordsBypassPermission();
        loadWords();
    }

    /**
     * Whether the forbidden-word filter is currently enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether the given player is exempt from the filter (has the bypass permission).
     */
    public boolean hasBypass(Player player) {
        return player != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission);
    }

    /**
     * Whether the given message contains any forbidden word.
     */
    public boolean containsForbiddenWord(String message) {
        if (!enabled || message == null || message.isEmpty()) return false;
        String normalized = normalize(message);
        for (String word : forbiddenWords) {
            if (!word.isEmpty() && normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // ── Internal ─────────────────────────────────────────────

    private void loadWords() {
        forbiddenWords.clear();

        Path file = dataDirectory.resolve("banned_words.txt");
        if (!Files.exists(file)) {
            createDefaultFile(file);
        }

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                // 兼容记事本保存的 UTF-8 BOM 文件 / strip UTF-8 BOM (e.g. from Notepad)
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1).trim();
                }
                if (line.isEmpty() || line.startsWith("#")) continue;

                for (String part : line.split(";")) {
                    String word = normalize(part.trim());
                    if (!word.isEmpty() && !forbiddenWords.contains(word)) {
                        forbiddenWords.add(word);
                    }
                }
            }
            logger.info("Loaded {} forbidden word(s) from banned_words.txt", forbiddenWords.size());
        } catch (IOException e) {
            logger.warn("Failed to read banned_words.txt", e);
        }
    }

    private void createDefaultFile(Path file) {
        String content = String.join("\n",
                "# VelocityChat 违禁词列表 / Banned Words List",
                "#",
                "# 用法 / Usage:",
                "#   - 用分号 ; 分隔多个违禁词，每行可写多个 / separate words with semicolons ;",
                "#   - 以 # 开头的行是注释，会被忽略 / lines starting with # are ignored",
                "#   - 匹配不区分大小写，并忽略颜色代码 / matching is case-insensitive and ignores color codes",
                "#   - 修改后执行 /vchat reload 生效 / run /vchat reload after editing",
                "#",
                "# 默认违禁词 / Default banned words:",
                "傻逼;你妈;操你妈;傻逼九月;傻子;傻逼玩意;智障;乐子;妈逼",
                "");
        try {
            Files.createDirectories(dataDirectory);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            logger.info("Generated default banned_words.txt");
        } catch (IOException e) {
            logger.warn("Could not write default banned_words.txt", e);
        }
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return COLOR_CODE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
