package com.velocitychat;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-based scheduled broadcast announcements.
 * <p>
 * Each message in auto_broadcast.yml can either fire at a fixed clock time
 * ({@code time: "HH:mm"}) with a recurrence mode ({@code daily} / {@code weekly} /
 * {@code monthly} / {@code once}), or loop on a fixed interval written as a
 * duration string ({@code repeat: "30min"}), e.g. {@code 1h30min}. Time-based
 * messages are scheduled one-shot and rescheduled for the following occurrence;
 * interval messages use a repeating scheduler task. Supports {@code &} color
 * codes and the {@code {online}} placeholder.
 */
public class AutoBroadcast {

    private static final Pattern DURATION = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)min)?(?:(\\d+)s)?");

    // Lenient: accepts "9:00" and "09:00", optional seconds
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm[:ss]");

    private final VelocityChat plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private boolean enabled;
    private final List<ScheduledMessage> messages = new ArrayList<>();
    private final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();
    // Bumped on every stop()/start() so a task that fired during a reload won't
    // reschedule itself after its generation was invalidated.
    private final AtomicInteger generation = new AtomicInteger();

    public AutoBroadcast(VelocityChat plugin, ProxyServer server, Logger logger, Path dataDirectory) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Load auto_broadcast.yml from the data directory, creating a default if missing.
     */
    public void load() {
        Path file = dataDirectory.resolve("auto_broadcast.yml");
        if (!Files.exists(file)) {
            copyDefault(file);
        }

        enabled = false;
        messages.clear();

        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                Object loaded = new Yaml().load(in);
                if (loaded instanceof Map<?, ?> map) {
                    Object en = map.get("enabled");
                    if (en instanceof Boolean b) enabled = b;
                    else if (en instanceof String s) enabled = s.equalsIgnoreCase("true");
                    parseMessages(map.get("messages"));
                }
            } catch (Exception e) {
                logger.warn("Failed to parse auto_broadcast.yml", e);
            }
        }

        logger.info("Auto broadcast config: enabled={}, scheduled messages={}", enabled, messages.size());
    }

    /**
     * Start (or restart) scheduling for every configured message.
     */
    public void start() {
        stop();
        if (!enabled || messages.isEmpty()) {
            logger.info("Auto broadcast is disabled (enabled={}, messages={})", enabled, messages.size());
            return;
        }
        for (ScheduledMessage sm : messages) {
            scheduleNext(sm);
        }
        logger.info("Auto broadcast scheduling started: {} message(s)", messages.size());
    }

    /**
     * Cancel all scheduled tasks (called on reload and plugin shutdown).
     */
    public void stop() {
        generation.incrementAndGet();
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void copyDefault(Path file) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("auto_broadcast.yml")) {
            if (in != null) {
                Files.createDirectories(dataDirectory);
                Files.copy(in, file);
                logger.info("Generated default auto_broadcast.yml");
            }
        } catch (IOException e) {
            logger.warn("Could not write default auto_broadcast.yml", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseMessages(Object raw) {
        if (!(raw instanceof List<?> list)) return;

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;

            String timeStr = m.get("time") == null ? null : m.get("time").toString();
            String repeatStr = m.get("repeat") == null ? null : m.get("repeat").toString();
            String rawMessage = m.get("message") == null ? null : m.get("message").toString();
            if (rawMessage == null || rawMessage.isBlank()) {
                logger.warn("Auto broadcast: skipped message at '{}' with empty content", timeStr);
                continue;
            }

            // Interval mode: repeat is a duration string (e.g. "30min", "1h30min")
            Duration interval = null;
            if (repeatStr != null && !isRepeatMode(repeatStr)) {
                interval = parseDuration(repeatStr);
                if (interval == null) {
                    logger.warn("Auto broadcast: unknown repeat '{}', treating as daily", repeatStr);
                } else if (interval.isZero() || interval.isNegative()) {
                    logger.warn("Auto broadcast: interval must be positive, skipped '{}'", rawMessage);
                    continue;
                }
            }

            if (interval != null) {
                messages.add(new ScheduledMessage(null, interval, null, 0, 0, rawMessage));
                continue;
            }

            // Time-based mode
            LocalTime time = parseTime(timeStr);
            if (time == null) {
                logger.warn("Auto broadcast: skipped message with invalid time '{}'", timeStr);
                continue;
            }
            String repeat = repeatStr == null ? "daily" : repeatStr.toLowerCase(Locale.ROOT);
            if (!isRepeatMode(repeat)) repeat = "daily";
            int weekday = repeat.equals("weekly") ? parseInt(m.get("weekday"), 1) : 1;
            int dayOfMonth = repeat.equals("monthly") ? parseInt(m.get("day"), 1) : 1;
            messages.add(new ScheduledMessage(time, null, repeat, weekday, dayOfMonth, rawMessage));
        }

        if (messages.isEmpty() && !list.isEmpty()) {
            logger.warn("No valid scheduled messages found — check that auto_broadcast.yml uses the "
                    + "new per-message 'time'/'repeat' format (delete the file and run /vchat reload to regenerate)");
        }
    }

    private static boolean isRepeatMode(String s) {
        String r = s.toLowerCase(Locale.ROOT);
        return r.equals("daily") || r.equals("weekly") || r.equals("monthly") || r.equals("once");
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalTime.parse(s, TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Duration parseDuration(String s) {
        if (s == null || s.isBlank()) return null;
        Matcher m = DURATION.matcher(s.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        long sec = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
        if (h == 0 && min == 0 && sec == 0) return null;
        return Duration.ofSeconds(h * 3600 + min * 60 + sec);
    }

    private static int parseInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(o.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private void scheduleNext(ScheduledMessage sm) {
        if (sm.isInterval()) {
            scheduleInterval(sm);
            return;
        }
        scheduleTimeBased(sm);
    }

    private void scheduleInterval(ScheduledMessage sm) {
        ScheduledTask task = server.getScheduler()
                .buildTask(plugin, () -> broadcast(sm.message()))
                .repeat(sm.interval())
                .schedule();
        tasks.add(task);
        logger.info("Auto broadcast: '{}' loops every {}", sm.message(), formatDuration(sm.interval()));
    }

    private void scheduleTimeBased(ScheduledMessage sm) {
        Duration delay = delayUntilNext(sm, LocalDateTime.now());
        if (delay == null) {
            logger.info("Auto broadcast: message at {} ({}) has no upcoming occurrence", sm.time(), sm.repeat());
            return;
        }
        int gen = generation.get();
        ScheduledTask task = server.getScheduler()
                .buildTask(plugin, () -> {
                    broadcast(sm.message());
                    if ("once".equals(sm.repeat())) return;
                    // Don't reschedule if a reload/shutdown happened while we fired.
                    if (gen != generation.get()) return;
                    scheduleNext(sm);
                })
                .delay(delay)
                .schedule();
        tasks.add(task);

        long s = delay.getSeconds();
        logger.info("Auto broadcast: next message at {} ({}) in {}h {}m {}s",
                sm.time(), sm.repeat(), s / 3600, (s % 3600) / 60, s % 60);
    }

    /**
     * Compute the delay until the next occurrence of this time-based message.
     * Returns null if there is no upcoming occurrence (once-mode that already passed).
     */
    private static Duration delayUntilNext(ScheduledMessage sm, LocalDateTime now) {
        LocalDateTime target = now.toLocalDate().atTime(sm.time());
        switch (sm.repeat()) {
            case "weekly": {
                int todayDow = now.getDayOfWeek().getValue(); // 1=Mon .. 7=Sun
                int targetDow = Math.max(1, Math.min(7, sm.weekday()));
                int daysAhead = (targetDow - todayDow + 7) % 7;
                if (daysAhead == 0 && !target.isAfter(now)) daysAhead = 7;
                return Duration.between(now, target.plusDays(daysAhead));
            }
            case "monthly": {
                int dom = Math.max(1, sm.dayOfMonth());
                YearMonth ym = YearMonth.from(now);
                LocalDateTime first = ym.atDay(Math.min(dom, ym.lengthOfMonth())).atTime(sm.time());
                if (first.isAfter(now)) return Duration.between(now, first);
                YearMonth next = ym.plusMonths(1);
                LocalDateTime second = next.atDay(Math.min(dom, next.lengthOfMonth())).atTime(sm.time());
                return Duration.between(now, second);
            }
            case "once": {
                if (!target.isAfter(now)) return null;
                return Duration.between(now, target);
            }
            default: { // daily
                if (!target.isAfter(now)) target = target.plusDays(1);
                return Duration.between(now, target);
            }
        }
    }

    private void broadcast(String rawMessage) {
        String line = ColorUtils.translate(rawMessage.replace("{online}", String.valueOf(server.getAllPlayers().size())));
        Component component = Component.text(line);
        for (Player player : server.getAllPlayers()) {
            player.sendMessage(component);
        }
        logger.info(ColorUtils.toAnsi(line));
    }

    private static String formatDuration(Duration d) {
        long total = d.getSeconds();
        long h = total / 3600;
        long min = (total % 3600) / 60;
        long s = total % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h");
        if (min > 0) sb.append(min).append("min");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString();
    }

    private record ScheduledMessage(LocalTime time, Duration interval, String repeat, int weekday, int dayOfMonth, String message) {
        boolean isInterval() {
            return interval != null;
        }
    }
}
