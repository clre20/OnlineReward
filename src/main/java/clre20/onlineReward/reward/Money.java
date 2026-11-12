package clre20.onlineReward.reward;

import org.bukkit.entity.Player;

import java.util.Locale;

/** 發錢的介面。實作放在 VaultMoney，沒裝 Vault 時不會載入 Vault 的類別。 */
public interface Money {

    /** 目前找得到經濟插件。 */
    boolean available();

    /** 成功回傳 null，失敗回傳錯誤說明。 */
    String deposit(Player player, double amount);

    String format(double amount);

    /** 沒有經濟插件時的金額格式。 */
    static String plain(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    /** 沒裝 Vault 時使用：永遠不可用。 */
    Money NONE = new Money() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String deposit(Player player, double amount) {
            return "Vault not installed";
        }

        @Override
        public String format(double amount) {
            return plain(amount);
        }
    };
}
