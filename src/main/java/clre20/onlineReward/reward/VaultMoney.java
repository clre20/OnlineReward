package clre20.onlineReward.reward;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** 透過 Vault 發錢。只有伺服器裝了 Vault 才會建立這個類別。 */
public final class VaultMoney implements Money {

    private VaultMoney() {
    }

    /** 有 Vault 插件就回傳 Vault 實作，否則回傳 {@link Money#NONE}。 */
    public static Money create() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return Money.NONE;
        }
        return new VaultMoney();
    }

    private static Economy economy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    @Override
    public boolean available() {
        return economy() != null;
    }

    @Override
    public String deposit(Player player, double amount) {
        Economy economy = economy();
        if (economy == null) {
            return "no economy";
        }
        try {
            EconomyResponse r = economy.depositPlayer(player, amount);
            if (r != null && r.transactionSuccess()) {
                return null;
            }
            return r == null ? "null" : String.valueOf(r.errorMessage);
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Override
    public String format(double amount) {
        Economy economy = economy();
        if (economy != null) {
            try {
                String s = economy.format(amount);
                if (s != null && !s.isBlank()) {
                    return s;
                }
            } catch (RuntimeException ignored) {
                // 用預設格式
            }
        }
        return Money.plain(amount);
    }
}
