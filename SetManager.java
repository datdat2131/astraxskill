package vn.astrax.skill;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class SetManager {
    public record Result(String id, int count, double damageBonus, double damageReduction) {}

    private static final Result NONE = new Result(null, 0, 0, 0);
    private final AstraxSkill plugin;

    public SetManager(AstraxSkill plugin) {
        this.plugin = plugin;
    }

    public boolean exists(String id) {
        return plugin.getConfig().isConfigurationSection("sets." + id);
    }

    public String displayName(String id) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("sets." + id + ".name", id));
    }

    /** Đếm số món set (4 giáp + vũ khí đang cầm), tính bonus; apply=true thì cấp hiệu ứng thuốc. */
    public Result compute(Player p, boolean apply) {
        Map<String, Integer> counts = new HashMap<>();
        List<ItemStack> items = new ArrayList<>(Arrays.asList(p.getInventory().getArmorContents()));
        items.add(p.getInventory().getItemInMainHand());
        for (ItemStack it : items) {
            String id = ItemUtil.getSet(it);
            if (id != null) counts.merge(id, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        if (best == null) return NONE;

        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("sets." + best + ".tiers");
        if (tiers == null) return NONE;

        double dmg = 0, red = 0;
        for (String key : tiers.getKeys(false)) {
            int need;
            try { need = Integer.parseInt(key); } catch (NumberFormatException e) { continue; }
            if (bestCount < need) continue;
            ConfigurationSection t = tiers.getConfigurationSection(key);
            if (t == null) continue;
            dmg += t.getDouble("damage-bonus", 0);
            red += t.getDouble("damage-reduction", 0);
            if (apply) {
                for (String s : t.getStringList("effects")) {
                    String[] a = s.split(":");
                    PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(a[0].toLowerCase()));
                    if (type == null) continue;
                    int amp = 0;
                    try { amp = Integer.parseInt(a[1]); } catch (Exception ignored) {}
                    p.addPotionEffect(new PotionEffect(type, 100, amp, true, false, true));
                }
            }
        }
        return new Result(best, bestCount, dmg, red);
    }
}
