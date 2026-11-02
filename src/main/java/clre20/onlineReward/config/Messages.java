package clre20.onlineReward.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * messages.yml。取值時使用者沒有的項目回退到 jar 內預設；
 * 先代入 {prefix} 再轉換顏色碼，其他變數最後才代入，所以變數內容裡的 &amp; 不會變成顏色。
 */
public final class Messages {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private final YamlConfiguration user;
    private final YamlConfiguration defaults;
    private final String prefix;

    public Messages(YamlConfiguration user, YamlConfiguration defaults, String prefix) {
        this.user = user;
        this.defaults = defaults;
        this.prefix = prefix == null ? "" : prefix;
    }

    /** 給玩家看的訊息（有顏色）。vars 依序為「名稱, 值」。 */
    public String get(String key, Object... vars) {
        String raw = raw(key).replace("{prefix}", prefix);
        return apply(color(raw), vars);
    }

    /** 給後台的訊息（純文字，沒有前綴與顏色碼）。 */
    public String console(String key, Object... vars) {
        String raw = raw(key).replace("{prefix}", "");
        return ChatColor.stripColor(apply(color(raw), vars));
    }

    private String raw(String key) {
        String v = user.getString(key);
        if (v == null) {
            v = defaults.getString(key);
        }
        return v == null ? key : v;
    }

    private static String apply(String text, Object... vars) {
        for (int i = 0; i + 1 < vars.length; i += 2) {
            text = text.replace("{" + vars[i] + "}", String.valueOf(vars[i + 1]));
        }
        return text;
    }

    @SuppressWarnings("deprecation")
    public static String color(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        Matcher m = HEX.matcher(text);
        if (m.find()) {
            StringBuilder sb = new StringBuilder();
            do {
                StringBuilder rep = new StringBuilder("§x");
                for (char ch : m.group(1).toCharArray()) {
                    rep.append('§').append(ch);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
            } while (m.find());
            m.appendTail(sb);
            text = sb.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
