package vn.astrax.skill;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AstraxCommand implements CommandExecutor, TabCompleter {
    private final AstraxSkill pl;

    public AstraxCommand(AstraxSkill pl) {
        this.pl = pl;
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void help(CommandSender s) {
        s.sendMessage(c("&6&l=== AstraxSkill ==="));
        s.sendMessage(c("&e/astrax bind <skill> [cấp] &7- ép skill vào món đang cầm"));
        s.sendMessage(c("&e/astrax unbind <skill|all> &7- gỡ skill"));
        s.sendMessage(c("&e/astrax set <id|none> &7- gán món đang cầm vào set"));
        s.sendMessage(c("&e/astrax give <người chơi> <setid> &7- phát trọn bộ set"));
        s.sendMessage(c("&e/astrax hac ... &7- Kiếm huyền thoại Hắc Long (gõ /astrax hac)"));
        s.sendMessage(c("&e/astrax cyber ... &7- Bộ giáp Cyber Dragon (gõ /astrax cyber)"));
        s.sendMessage(c("&e/astrax list &7- danh sách skill & set"));
        s.sendMessage(c("&e/astrax reload &7- tải lại config"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("astrax.admin")) {
            sender.sendMessage(c("&cBạn không có quyền."));
            return true;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> {
                pl.reloadConfig();
                sender.sendMessage(c("&aĐã tải lại config."));
            }
            case "list" -> {
                sender.sendMessage(c("&6Skill kiếm/rìu: &f" + names(false)));
                sender.sendMessage(c("&6Skill giáp: &f" + names(true)));
                ConfigurationSection sets = pl.getConfig().getConfigurationSection("sets");
                sender.sendMessage(c("&6Set: &f" + (sets == null ? "-" : String.join(", ", sets.getKeys(false)))));
            }
            case "bind" -> bind(sender, a);
            case "unbind" -> unbind(sender, a);
            case "set" -> setItem(sender, a);
            case "give" -> give(sender, a);
            case "hac" -> pl.hac().command(sender, a);
            case "cyber" -> pl.cyber().command(sender, a);
            default -> help(sender);
        }
        return true;
    }

    private String names(boolean armor) {
        StringJoiner j = new StringJoiner(", ");
        for (SkillType t : SkillType.values()) if (t.armor == armor) j.add(t.name());
        return j.toString();
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage("Lệnh này chỉ dùng trong game.");
        return null;
    }

    private ItemStack held(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType() == Material.AIR) {
            p.sendMessage(c("&cHãy cầm kiếm/giáp trên tay."));
            return null;
        }
        return it;
    }

    private void bind(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) { p.sendMessage(c("&cDùng: /astrax bind <skill> [cấp]")); return; }
        ItemStack it = held(p);
        if (it == null) return;
        SkillType t = SkillType.from(a[1]);
        if (t == null) { p.sendMessage(c("&cSkill không tồn tại. Xem /astrax list")); return; }
        if (t.armor ? !ItemUtil.isArmor(it) : !ItemUtil.isWeapon(it)) {
            p.sendMessage(c(t.armor ? "&cSkill này chỉ ép được vào giáp." : "&cSkill này chỉ ép được vào kiếm/rìu."));
            return;
        }
        int max = pl.getConfig().getInt("max-level", 5);
        int lv = 1;
        if (a.length >= 3) {
            try { lv = Integer.parseInt(a[2]); } catch (NumberFormatException ex) { p.sendMessage(c("&cCấp phải là số.")); return; }
        }
        lv = Math.max(1, Math.min(max, lv));
        Map<SkillType, Integer> m = ItemUtil.getSkills(it);
        m.put(t, lv);
        ItemUtil.setSkills(it, m);
        p.sendMessage(c("&aĐã ép &e" + t.display + " &7Lv." + lv + " &avào vật phẩm."));
    }

    private void unbind(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) { p.sendMessage(c("&cDùng: /astrax unbind <skill|all>")); return; }
        ItemStack it = held(p);
        if (it == null) return;
        Map<SkillType, Integer> m = ItemUtil.getSkills(it);
        if (a[1].equalsIgnoreCase("all")) m.clear();
        else {
            SkillType t = SkillType.from(a[1]);
            if (t == null) { p.sendMessage(c("&cSkill không tồn tại.")); return; }
            m.remove(t);
        }
        ItemUtil.setSkills(it, m);
        p.sendMessage(c("&aĐã gỡ skill."));
    }

    private void setItem(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) { p.sendMessage(c("&cDùng: /astrax set <id|none>")); return; }
        ItemStack it = held(p);
        if (it == null) return;
        if (a[1].equalsIgnoreCase("none")) {
            ItemUtil.setSet(it, null);
            p.sendMessage(c("&aĐã gỡ khỏi set."));
            return;
        }
        if (!pl.sets().exists(a[1])) { p.sendMessage(c("&cSet không tồn tại trong config.")); return; }
        ItemUtil.setSet(it, a[1]);
        p.sendMessage(c("&aĐã gán vào set " + pl.sets().displayName(a[1])));
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(c("&cDùng: /astrax give <người chơi> <setid>")); return; }
        Player target = pl.getServer().getPlayerExact(a[1]);
        if (target == null) { s.sendMessage(c("&cNgười chơi không online.")); return; }
        ConfigurationSection items = pl.getConfig().getConfigurationSection("sets." + a[2] + ".items");
        if (items == null) { s.sendMessage(c("&cSet không có mục items trong config.")); return; }
        int n = 0;
        for (String key : items.getKeys(false)) {
            ItemStack it = build(items.getConfigurationSection(key), a[2]);
            if (it == null) continue;
            Map<Integer, ItemStack> left = target.getInventory().addItem(it);
            for (ItemStack drop : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), drop);
            n++;
        }
        s.sendMessage(c("&aĐã phát " + n + " món set " + a[2] + " cho " + target.getName()));
        target.sendMessage(c("&6Bạn nhận được set " + pl.sets().displayName(a[2]) + "&6!"));
    }

    @SuppressWarnings("deprecation")
    private ItemStack build(ConfigurationSection s, String setId) {
        if (s == null) return null;
        Material m = Material.matchMaterial(s.getString("material", "DIAMOND_SWORD"));
        if (m == null) return null;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(c(s.getString("name", m.name())));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        for (String en : s.getStringList("enchants")) {
            String[] x = en.split(":");
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(x[0].toLowerCase()));
            if (ench == null) continue;
            int lv = 1;
            try { lv = Integer.parseInt(x[1]); } catch (Exception ignored) {}
            meta.addEnchant(ench, lv, true);
        }
        it.setItemMeta(meta);

        Map<SkillType, Integer> skills = new EnumMap<>(SkillType.class);
        for (String sk : s.getStringList("skills")) {
            String[] x = sk.split(":");
            SkillType t = SkillType.from(x[0]);
            if (t == null) continue;
            int lv = 1;
            try { lv = Integer.parseInt(x[1]); } catch (Exception ignored) {}
            skills.put(t, lv);
        }
        ItemUtil.setSkills(it, skills);
        ItemUtil.setSet(it, setId);
        return it;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("astrax.admin")) return out;
        if (a.length == 1) {
            out.addAll(List.of("bind", "unbind", "set", "give", "hac", "cyber", "list", "reload", "help"));
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("bind") || a[0].equalsIgnoreCase("unbind"))) {
            for (SkillType t : SkillType.values()) out.add(t.name());
            if (a[0].equalsIgnoreCase("unbind")) out.add("all");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("set")) {
            out.add("none");
            addSets(out);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            pl.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            addSets(out);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("hac")) {
            out.addAll(List.of("give", "apply", "tier", "stone"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("cyber")) {
            out.addAll(List.of("give", "piece", "apply"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("cyber")
                && (a[1].equalsIgnoreCase("give") || a[1].equalsIgnoreCase("piece"))) {
            pl.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 4 && a[0].equalsIgnoreCase("cyber") && a[1].equalsIgnoreCase("piece")) {
            out.addAll(List.of("helmet", "chestplate", "leggings", "boots"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("hac")
                && (a[1].equalsIgnoreCase("give") || a[1].equalsIgnoreCase("stone"))) {
            pl.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }

    private void addSets(List<String> out) {
        ConfigurationSection sets = pl.getConfig().getConfigurationSection("sets");
        if (sets != null) out.addAll(sets.getKeys(false));
    }
}
