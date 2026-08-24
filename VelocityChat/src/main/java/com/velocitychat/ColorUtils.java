package com.velocitychat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/**
 * Utility class for translating Minecraft color codes.
 * Converts '&amp;' character to '§' (section sign) used by Minecraft,
 * and '§' to ANSI escape codes for console display.
 */
public class ColorUtils {

    /**
     * Translates '&amp;' color codes to Minecraft '§' color codes.
     * Supports all standard Minecraft color and formatting codes:
     * &amp;0-9 (colors), &amp;a-f (colors), &amp;k-o (formatting), &amp;r (reset)
     *
     * @param message The message containing '&amp;' color codes
     * @return The message with '&amp;' replaced by '§'
     */
    public static String translate(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return message.replace('&', '§');
    }

    /**
     * Sends a color-coded message (§ codes) to a command source.
     * Players receive the message in chat; the console receives it with ANSI
     * colors so it doesn't show literal § codes.
     *
     * @param source  The command source (player or console)
     * @param logger  Logger used for console output
     * @param message The message containing § color codes
     */
    public static void sendMessage(CommandSource source, Logger logger, String message) {
        if (source instanceof Player) {
            source.sendMessage(Component.text(message));
        } else {
            logger.info(toAnsi(message));
        }
    }

    /**
     * Translates color codes in a string array (e.g. for lore).
     *
     * @param messages Array of messages containing '&amp;' color codes
     * @return Array with all messages translated
     */
    public static String[] translate(String... messages) {
        String[] result = new String[messages.length];
        for (int i = 0; i < messages.length; i++) {
            result[i] = translate(messages[i]);
        }
        return result;
    }

    /**
     * Converts Minecraft '§' color codes to ANSI escape codes for terminal/console display.
     * Unrecognized or formatting codes (k, l, m, n, o) are stripped.
     *
     * @param message The message containing '§' color codes
     * @return The message with ANSI escape codes replacing '§' codes
     */
    public static String toAnsi(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        boolean inColor = false;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            if (c == '§' && i + 1 < message.length()) {
                char code = message.charAt(i + 1);
                String ansi = getAnsiCode(code);
                if (ansi != null) {
                    sb.append(ansi);
                }
                i++; // skip the code character
                inColor = true;
            } else {
                sb.append(c);
            }
        }

        // Reset at the end if we applied any color
        if (inColor) {
            sb.append("[0m");
        }

        return sb.toString();
    }

    /**
     * Maps a Minecraft color code character to its ANSI escape code.
     *
     * @param code The Minecraft color/format code character (e.g. 'c' for red)
     * @return ANSI escape code string, or null if unknown
     */
    private static String getAnsiCode(char code) {
        return switch (code) {
            // Colors (0-9, a-f)
            case '0' -> "[30m";   // Black
            case '1' -> "[34m";   // Dark Blue
            case '2' -> "[32m";   // Dark Green
            case '3' -> "[36m";   // Dark Aqua
            case '4' -> "[31m";   // Dark Red
            case '5' -> "[35m";   // Dark Purple
            case '6' -> "[33m";   // Gold
            case '7' -> "[37m";   // Gray
            case '8' -> "[90m";   // Dark Gray
            case '9' -> "[94m";   // Blue
            case 'a' -> "[92m";   // Green
            case 'b' -> "[96m";   // Aqua
            case 'c' -> "[91m";   // Red
            case 'd' -> "[95m";   // Light Purple
            case 'e' -> "[93m";   // Yellow
            case 'f' -> "[97m";   // White
            // Formatting
            case 'l' -> "[1m";    // Bold
            case 'n' -> "[4m";    // Underline
            case 'o' -> "[3m";    // Italic
            case 'm' -> "[9m";    // Strikethrough
            case 'r' -> "[0m";    // Reset
            default -> null;
        };
    }
}
