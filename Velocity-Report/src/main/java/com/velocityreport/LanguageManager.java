package com.velocityreport;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages multilingual message loading and lookups.
 * <p>
 * Loads language files from the plugin's data directory (customizable)
 * or falls back to the built-in resources bundled with the plugin jar.
 * Supports runtime reload and per-key overrides via config.yml.
 */
public class LanguageManager {

    private final Logger logger;
    private final Path dataDirectory;
    private String currentLanguage = "zh_CN";
    private final Map<String, String> messages = new LinkedHashMap<>();

    public LanguageManager(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Load the specified language file.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Plugin data directory: {@code lang/&lt;language&gt;.yml} (user-customizable)</li>
     *   <li>Plugin jar resources: {@code lang/&lt;language&gt;.yml} (built-in, auto-copied if missing)</li>
     * </ol>
     *
     * @param language Language code, e.g. "zh_CN", "en_US"
     * @return true if the language was loaded successfully
     */
    public boolean loadLanguage(String language) {
        this.currentLanguage = (language != null && !language.isBlank()) ? language : "zh_CN";
        messages.clear();

        // Ensure lang/ directory exists in the plugin data folder
        Path langDir = dataDirectory.resolve("lang");
        try {
            Files.createDirectories(langDir);
        } catch (IOException e) {
            logger.warn("Failed to create lang/ directory", e);
        }

        // If the language file doesn't exist in the data directory, copy it from JAR resources
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

        // Load from data directory (user-customizable)
        if (Files.exists(langFile)) {
            try (InputStream in = Files.newInputStream(langFile)) {
                if (loadFromStream(in)) {
                    mergeBuiltInDefaults();
                    logger.info("Loaded language file: lang/{}.yml from data directory", this.currentLanguage);
                    return true;
                }
            } catch (Exception e) {
                logger.warn("Failed to load lang/{}.yml from data directory, trying plugin resources",
                        this.currentLanguage, e);
            }
        }

        // Fall back to the built-in language file bundled in the plugin jar
        String resourcePath = "lang/" + this.currentLanguage + ".yml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.error("Language file '{}' not found in plugin resources. " +
                        "Make sure lang/{}.yml is bundled with the plugin.", resourcePath, this.currentLanguage);
                return false;
            }
            if (loadFromStream(in)) {
                logger.info("Loaded built-in language: {}", resourcePath);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Failed to load language file '{}' from resources", resourcePath, e);
            return false;
        }
    }

    /**
     * Override a specific message key with a custom value.
     * Used when applying per-key overrides from config.yml {@code messages:} section.
     *
     * @param key   Message key (e.g. "report-sent", "command-player-only")
     * @param value MiniMessage-formatted value with optional {placeholder}s
     */
    public void setMessage(String key, String value) {
        if (key != null && value != null) {
            messages.put(key, value);
        }
    }

    /**
     * Get a localized message by its key.
     *
     * @param key Message identifier (e.g. "report-sent")
     * @return The localized message string, or a visible error placeholder if missing
     */
    public String getMessage(String key) {
        String msg = messages.get(key);
        if (msg == null) {
            logger.warn("Missing message key '{}' in language '{}'", key, currentLanguage);
            return "<red>Missing message: " + key + "</red>";
        }
        return msg;
    }

    /**
     * Check if a message key exists.
     */
    public boolean hasMessage(String key) {
        return messages.containsKey(key);
    }

    /**
     * Returns the currently active language code.
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Returns the total number of loaded message keys.
     */
    public int getMessageCount() {
        return messages.size();
    }

    // ── Internal helpers ──────────────────────────────────────

    /**
     * Copies every key of the built-in language file that the user's own file
     * does not define yet, so messages added by a plugin update work without
     * editing anything. Values from the data directory always win.
     */
    private void mergeBuiltInDefaults() {
        String resourcePath = "lang/" + currentLanguage + ".yml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return;

            Map<String, String> builtIn = new LinkedHashMap<>();
            if (!parseInto(in, builtIn)) return;

            int added = 0;
            for (Map.Entry<String, String> entry : builtIn.entrySet()) {
                if (!messages.containsKey(entry.getKey())) {
                    messages.put(entry.getKey(), entry.getValue());
                    added++;
                }
            }
            if (added > 0) {
                logger.info("Added {} missing message key(s) from built-in lang/{}.yml",
                        added, currentLanguage);
            }
        } catch (Exception e) {
            logger.warn("Failed to merge built-in message defaults for lang/{}.yml", currentLanguage, e);
        }
    }

    private boolean loadFromStream(InputStream in) {
        return parseInto(in, messages);
    }

    @SuppressWarnings("unchecked")
    private static boolean parseInto(InputStream in, Map<String, String> target) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(in);
        if (loaded instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                    target.put(key, value);
                } else if (entry.getKey() instanceof String key && entry.getValue() != null) {
                    // For non-string values (e.g. multi-line strings parsed as TextBlock),
                    // convert to string
                    target.put(key, entry.getValue().toString());
                }
            }
            return !target.isEmpty();
        }
        return false;
    }
}
