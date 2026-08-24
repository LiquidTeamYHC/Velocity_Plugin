package com.velocitychat;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages plugin configuration and internationalization (i18n) messages.
 * <p>
 * Loads config.yml and language files (lang/*.yml) from the plugin's data directory.
 * Supports runtime reload and per-key overrides via config.yml's {@code messages:} section.
 */
public class ConfigManager {

    private final Logger logger;
    private final Path dataDirectory;
    private String currentLanguage = "zh_CN";

    // Raw config values
    private Map<String, Object> config;
    private final Map<String, String> messages = new LinkedHashMap<>();
    // 服务器别名: 小写服务器ID -> 原始显示名称 (直接写在 config.yml 的 server-aliases 中)
    private final Map<String, String> serverAliases = new HashMap<>();

    public ConfigManager(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Load config.yml from the data directory.
     * If it doesn't exist, copy the built-in default.
     *
     * @return true if config was loaded successfully
     */
    @SuppressWarnings("unchecked")
    public boolean loadConfig() {
        Path configFile = dataDirectory.resolve("config.yml");

        // Write default config if not present
        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.createDirectories(dataDirectory);
                    Files.copy(in, configFile);
                    logger.info("Generated default config.yml");
                }
            } catch (IOException e) {
                logger.warn("Could not write default config.yml", e);
            }
        }

        // Load config
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                Yaml yaml = new Yaml();
                config = yaml.load(in);
                logger.info("Configuration loaded from config.yml");
            } catch (Exception e) {
                logger.warn("Failed to parse config.yml", e);
                config = new LinkedHashMap<>();
            }
        } else {
            config = new LinkedHashMap<>();
        }

        // Read language setting
        if (config.containsKey("language")) {
            currentLanguage = config.get("language").toString();
        }

        // Read server aliases map: 服务器ID -> 显示名称
        serverAliases.clear();
        if (config.get("server-aliases") instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String id && entry.getValue() != null) {
                    serverAliases.put(id.toLowerCase(), entry.getValue().toString());
                }
            }
        }

        // Load language file
        loadLanguage(currentLanguage);

        // Apply message overrides from config.yml
        if (config.get("messages") instanceof Map<?, ?> msgs) {
            for (var entry : msgs.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                    messages.put(key, value);
                }
            }
        }

        return true;
    }

    /**
     * Load the specified language file.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Plugin data directory: {@code lang/&lt;language&gt;.yml} (user-customizable)</li>
     *   <li>Plugin jar resources: {@code lang/&lt;language&gt;.yml} (built-in)</li>
     * </ol>
     *
     * @param language Language code, e.g. "zh_CN", "en_US"
     */
    private void loadLanguage(String language) {
        this.currentLanguage = (language != null && !language.isBlank()) ? language : "zh_CN";

        Path langDir = dataDirectory.resolve("lang");
        try {
            Files.createDirectories(langDir);
        } catch (IOException e) {
            logger.warn("Failed to create lang/ directory", e);
        }

        // If language file doesn't exist in data directory, copy from resources
        Path langFile = langDir.resolve(this.currentLanguage + ".yml");
        if (!Files.exists(langFile)) {
            String resourcePath = "lang/" + this.currentLanguage + ".yml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in != null) {
                    Files.copy(in, langFile);
                    logger.info("Generated language file: lang/{}.yml", this.currentLanguage);
                }
            } catch (Exception e) {
                logger.warn("Failed to copy built-in lang/{}.yml to data directory", this.currentLanguage, e);
            }
        }

        // Load from data directory first
        boolean loaded = false;
        if (Files.exists(langFile)) {
            try (InputStream in = Files.newInputStream(langFile)) {
                loaded = loadFromStream(in);
                if (loaded) {
                    logger.info("Loaded language file: lang/{}.yml", this.currentLanguage);
                }
            } catch (Exception e) {
                logger.warn("Failed to load lang/{}.yml from data directory", this.currentLanguage, e);
            }
        }

        // 兼容升级：旧版语言文件缺少新版本新增的键时，从内置资源补齐缺失键，
        // 用户自定义的键不会被覆盖 / Merge missing keys from the built-in resource so
        // older language files gain newly-added keys without overwriting customizations.
        if (loaded) {
            String resourcePath = "lang/" + this.currentLanguage + ".yml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in != null) {
                    int merged = mergeMissingKeys(in);
                    if (merged > 0) {
                        logger.info("Merged {} missing key(s) from built-in {} into existing language file",
                                merged, resourcePath);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to merge built-in language keys", e);
            }
        }

        // Fall back to built-in resources
        if (!loaded) {
            String resourcePath = "lang/" + this.currentLanguage + ".yml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in != null && loadFromStream(in)) {
                    logger.info("Loaded built-in language: {}", resourcePath);
                } else {
                    logger.error("Language file '{}' not found in plugin resources", resourcePath);
                }
            } catch (Exception e) {
                logger.error("Failed to load language file '{}' from resources", resourcePath, e);
            }
        }
    }

    /**
     * Reload config and language from disk.
     */
    public void reload() {
        messages.clear();
        loadConfig();
        logger.info("Configuration and language reloaded successfully");
    }

    // ── Message API ──────────────────────────────────────────

    /**
     * Get a localized message by key, with placeholders replaced.
     *
     * @param key        Message identifier (e.g. "qu_an.chat.message.connected")
     * @param placeholders Values to replace {0}, {1}, {2}, ... in the message
     * @return The formatted message string, or a visible error placeholder if missing
     */
    public String getMessage(String key, String... placeholders) {
        String msg = messages.get(key);
        if (msg == null) {
            logger.warn("Missing message key '{}' in language '{}'", key, currentLanguage);
            return "§cMissing message: " + key;
        }
        for (int i = 0; i < placeholders.length; i++) {
            if (placeholders[i] != null) {
                msg = msg.replace("{" + i + "}", placeholders[i]);
            }
        }
        return ColorUtils.translate(msg);
    }

    /**
     * Get the display name for a server.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>config.yml {@code server-aliases} map (recommended — single-file config)</li>
     *   <li>language file alias (backward compatibility)</li>
     *   <li>raw server ID</li>
     * </ol>
     *
     * @param serverId The raw server ID (e.g. "lobby", "survival")
     * @return The display name with color codes translated
     */
    public String getServerDisplayName(String serverId) {
        if (serverId == null) return "§7unknown";

        // 优先从 config.yml 的 server-aliases map 读取显示名称
        String alias = serverAliases.get(serverId.toLowerCase());
        if (alias != null && !alias.isBlank()) {
            return ColorUtils.translate(alias);
        }

        // 兼容旧版本: 语言文件中定义的别名
        String aliasKey = "qu_an.chat.server.name." + serverId.toLowerCase();
        if (messages.containsKey(aliasKey)) {
            return ColorUtils.translate(messages.get(aliasKey));
        }

        // Return raw server ID with a default color
        return "§7" + serverId;
    }

    /**
     * Get the proxy display name.
     */
    public String getProxyName() {
        return getMessage("qu_an.chat.proxy.name");
    }

    /**
     * Get the default chat format.
     */
    public String getChatFormat() {
        return messages.getOrDefault("qu_an.chat.message.chat.default",
                "§8[§r{0}§8][§r{1}§8]§r<{2}§r> {3}");
    }

    /**
     * Get the broadcast message format.
     */
    public String getBroadcastFormat() {
        return messages.getOrDefault("qu_an.chat.message.broadcast",
                "§6[Broadcast] §r{0}§f: {1}");
    }

    /**
     * Get broadcast command aliases from config.
     */
    @SuppressWarnings("unchecked")
    public List<String> getBroadcastAliases() {
        if (config != null && config.get("broadcast-aliases") instanceof List<?> list) {
            List<String> aliases = new ArrayList<>();
            for (Object o : list) {
                if (o != null) aliases.add(o.toString());
            }
            return aliases;
        }
        return Arrays.asList("br", "broadcast");
    }

    /**
     * Check if a server has a custom alias in the server-aliases map.
     */
    public boolean hasServerAlias(String serverId) {
        return serverId != null && serverAliases.containsKey(serverId.toLowerCase());
    }

    /**
     * Get the notify mode for join/switch/leave messages.
     *
     * @return "all" (everyone), "admin" (admins only), or "none" (disabled)
     */
    public String getNotifyMode() {
        if (config != null && config.containsKey("notify-mode")) {
            String mode = config.get("notify-mode").toString().toLowerCase();
            if (mode.equals("all") || mode.equals("admin") || mode.equals("none")) {
                return mode;
            }
        }
        return "all";
    }

    /**
     * Get the broadcast cooldown in seconds.
     * 0 means no cooldown.
     */
    public int getBroadcastCooldown() {
        if (config != null && config.containsKey("broadcast-cooldown")) {
            Object val = config.get("broadcast-cooldown");
            if (val instanceof Number n) return n.intValue();
        }
        return 0;
    }

    /**
     * Check if broadcast cooldown bypass is enabled for velocitychat.admin.
     */
    public boolean isCooldownBypassEnabled() {
        if (config != null && config.containsKey("broadcast-cooldown-bypass")) {
            Object val = config.get("broadcast-cooldown-bypass");
            if (val instanceof Boolean b) return b;
            if (val instanceof String s) return s.equalsIgnoreCase("true");
        }
        return false;
    }

    /**
     * Get the server-invite cooldown in seconds.
     * 0 means no cooldown.
     */
    public int getInviteCooldown() {
        if (config != null && config.containsKey("invite-cooldown")) {
            Object val = config.get("invite-cooldown");
            if (val instanceof Number n) return n.intValue();
        }
        return 0;
    }

    /**
     * Check if invite cooldown bypass is enabled.
     * When enabled, players with the configured bypass permission ignore the cooldown.
     */
    public boolean isInviteCooldownBypassEnabled() {
        if (config != null && config.containsKey("invite-cooldown-bypass")) {
            Object val = config.get("invite-cooldown-bypass");
            if (val instanceof Boolean b) return b;
            if (val instanceof String s) return s.equalsIgnoreCase("true");
        }
        return false;
    }

    /**
     * Get the permission node that bypasses the invite cooldown.
     */
    public String getInviteBypassPermission() {
        if (config != null && config.containsKey("invite-bypass-permission")) {
            Object val = config.get("invite-bypass-permission");
            if (val != null && !val.toString().isBlank()) return val.toString();
        }
        return "velocitychat.admin";
    }

    /**
     * Check if forbidden-word (违禁词) filtering is enabled.
     * Defaults to false when the key is absent (keeps older configs safe).
     */
    public boolean isForbiddenWordsEnabled() {
        if (config != null && config.containsKey("forbidden-words-enabled")) {
            Object val = config.get("forbidden-words-enabled");
            if (val instanceof Boolean b) return b;
            if (val instanceof String s) return s.equalsIgnoreCase("true");
        }
        return false;
    }

    /**
     * Get the permission that bypasses the forbidden-word filter.
     */
    public String getForbiddenWordsBypassPermission() {
        if (config != null && config.containsKey("forbidden-words-bypass-permission")) {
            Object val = config.get("forbidden-words-bypass-permission");
            if (val != null && !val.toString().isBlank()) return val.toString();
        }
        return "velocitychat.admin";
    }

    // ── Internal Helpers ─────────────────────────────────────

    /**
     * Merge keys from a built-in language resource that are missing from the current
     * messages map. Existing keys (including user customizations) are never overwritten.
     *
     * @param in InputStream of a built-in language resource
     * @return the number of newly merged keys
     */
    @SuppressWarnings("unchecked")
    private int mergeMissingKeys(InputStream in) {
        int merged = 0;
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(in);
        if (loaded instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() != null
                        && !messages.containsKey(key)) {
                    messages.put(key, entry.getValue().toString());
                    merged++;
                }
            }
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private boolean loadFromStream(InputStream in) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(in);
        if (loaded instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                    messages.put(key, value);
                } else if (entry.getKey() instanceof String key && entry.getValue() != null) {
                    messages.put(key, entry.getValue().toString());
                }
            }
            return !messages.isEmpty();
        }
        return false;
    }
}
