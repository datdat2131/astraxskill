package vn.astrax.skill;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class ItemUtil {
    // Dòng lore do plugin tạo luôn bắt đầu bằng MARK để dễ xoá/cập nhật
    public static final String MARK = "§8\u200B";
    private static NamespacedKey SKILLS, SET;

    private ItemUtil() {}

    public static void init(Plugin p) {
        SKILLS = new NamespacedKey(p, "skills");
        SET = new NamespacedKey(p, "setid");
    }

    public static boolean isArmor(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    public static boolean isWeapon(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE");
    }

    public static Map<SkillType, Integer> getSkills(ItemStack item) {
        Map<SkillType, Integer> m = new EnumMap<>(SkillType.class);
        if (item == null || !item.hasItemMeta()) return m;
        String s = item.getItemMeta().getPersistentDataContainer().get(SKILLS, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split(",")) {
            String[] a = part.split(":");
            SkillType t = SkillType.from(a[0]);
            if (t == null) continue;
            int lv = 1;
            try { lv = Integer.parseInt(a[1]); } catch (Exception ignored) {}
            m.put(t, lv);
        }
        return m;
    }

    public static void setSkills(ItemStack item, Map<SkillType, Integer> skills) {
        ItemMeta meta = item.getItemMeta();
        if (skills.isEmpty()) {
            meta.getPersistentDataContainer().remove(SKILLS);
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<SkillType, Integer> e : skills.entrySet()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(e.getKey().name()).append(':').append(e.getValue());
            }
            meta.getPersistentDataContainer().set(SKILLS, PersistentDataType.STRING, sb.toString());
        }
        item.setItemMeta(meta);
        refreshLore(item);
    }

    public static String getSet(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(SET, PersistentDataType.STRING);
    }

    public static void setSet(ItemStack item, String id) {
        ItemMeta meta = item.getItemMeta();
        if (id == null) meta.getPersistentDataContainer().remove(SET);
        else meta.getPersistentDataContainer().set(SET, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        refreshLore(item);
    }

    @SuppressWarnings("deprecation")
    public static void refreshLore(ItemStack item) {
        Map<SkillType, Integer> skills = getSkills(item);
        String set = getSet(item);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) {
            for (String l : meta.getLore()) if (!l.startsWith(MARK)) lore.add(l);
        }
        if (!skills.isEmpty() || set != null) {
            lore.add(MARK);
            for (Map.Entry<SkillType, Integer> e : skills.entrySet()) {
                lore.add(MARK + "§e✦ " + e.getKey().display + " §7Lv." + e.getValue());
            }
            if (set != null) {
                lore.add(MARK + "§6◆ Set: " + AstraxSkill.get().sets().displayName(set));
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }
}
