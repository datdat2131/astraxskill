package vn.astrax.skill;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * CYBER DRAGON - bộ giáp nội tại (Passive). Mặc là tự chạy, không phím, không lệnh.
 * Nội tại từng món + 4 kỹ năng bộ (Cyber Shield, Overdrive, Energy Absorption, Ultimate Mode).
 */
public class CyberDragon implements Listener {
    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots"};

    private static class State {
        long shieldUntil, shieldReady, overUntil, overReady, absorbReady, ultUntil, ultReady;
    }

    private final AstraxSkill pl;
    private final NamespacedKey PIECE, MOD_LEGS, MOD_BOOTS, MOD_OVER;
    private final Map<UUID, State> states = new HashMap<>();
    private final Random rnd = new Random();
    private final Set<UUID> glowing = new HashSet<>();
    private Attribute speedAttr;
    private volatile boolean nativeEquip = detectNative();

    public CyberDragon(AstraxSkill pl) {
        this.pl = pl;
        this.PIECE = new NamespacedKey(pl, "cd_piece");
        this.MOD_LEGS = new NamespacedKey(pl, "cd_legs_speed");
        this.MOD_BOOTS = new NamespacedKey(pl, "cd_boots_speed");
        this.MOD_OVER = new NamespacedKey(pl, "cd_overdrive_speed");
    }

    // ================= helpers =================
    private FileConfiguration cfg() { return pl.getConfig(); }
    private double d(String path, double def) { return cfg().getDouble("cyber-dragon." + path, def); }
    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private long ms(double sec) { return (long) (sec * 1000); }
    private long pct(double v) { return Math.round(v * 100); }
    private boolean roll(double percent) { return rnd.nextDouble() * 100 < percent; }

    private void bar(Player p, String s) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(c(s)));
    }

    private void fx(World w, Particle p, Location l, int n, double dx, double dy, double dz, double sp) {
        try { w.spawnParticle(p, l, n, dx, dy, dz, sp); } catch (Exception ignored) {}
    }

    private void dust(World w, Location l, Color col, float size) {
        try { w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(col, size)); }
        catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private double maxHealth(Player p) { return p.getMaxHealth(); }

    public String piece(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(PIECE, PersistentDataType.STRING);
    }

    private boolean[] worn(Player p) {
        PlayerInventory i = p.getInventory();
        return new boolean[]{
                "helmet".equals(piece(i.getHelmet())),
                "chestplate".equals(piece(i.getChestplate())),
                "leggings".equals(piece(i.getLeggings())),
                "boots".equals(piece(i.getBoots()))
        };
    }

    private boolean full(boolean[] w) { return w[0] && w[1] && w[2] && w[3]; }

    /** Đang mặc đủ 4 món Cyber Dragon. */
    public boolean isFullSet(Player p) { return full(worn(p)); }

    // ================= tạo item =================
    public static String slotOf(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET")) return "helmet";
        if (n.endsWith("_CHESTPLATE")) return "chestplate";
        if (n.endsWith("_LEGGINGS")) return "leggings";
        if (n.endsWith("_BOOTS")) return "boots";
        return null;
    }

    public ItemStack build(String slot) {
        ConfigurationSection s = cfg().getConfigurationSection("cyber-dragon.pieces." + slot);
        Material m = Material.matchMaterial(s == null ? "NETHERITE_HELMET"
                : s.getString("material", "NETHERITE_HELMET"));
        if (m == null) m = Material.NETHERITE_HELMET;
        ItemStack it = new ItemStack(m);
        apply(it, slot, true);
        return it;
    }

    private String passiveText(String slot) {
        return switch (slot) {
            case "helmet" -> "+" + pct(d("passive.helmet-damage", 0.10)) + "% sát thương gây ra";
            case "chestplate" -> "Giảm " + pct(d("passive.chest-reduction", 0.12)) + "% sát thương nhận vào";
            case "leggings" -> "+" + pct(d("passive.legs-speed", 0.15)) + "% tốc độ di chuyển";
            default -> "+" + pct(d("passive.boots-speed", 0.20)) + "% tốc độ di chuyển";
        };
    }

    /** Gắn nội tại Cyber Dragon vào một món giáp bất kỳ (đúng ô trang bị của nó). */
    @SuppressWarnings("deprecation")
    public void apply(ItemStack it, String slot, boolean enchants) {
        ConfigurationSection s = cfg().getConfigurationSection("cyber-dragon.pieces." + slot);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(c(s == null ? "&b&l⚡ Cyber Dragon" : s.getString("name", "&b&l⚡ Cyber Dragon")));

        List<String> lore = new ArrayList<>();
        lore.add(c("&bᴄʏʙᴇʀ ᴀʀᴍᴏʀ"));
        lore.add(c("&8━━━━━━━━━━━━━━━━"));
        lore.add(c("&7Giáp công nghệ lõi năng lượng"));
        lore.add(c("&7của rồng cơ khí cổ đại."));
        lore.add("");
        lore.add(c("&b⚡ &fNội tại: &a" + passiveText(slot)));
        lore.add("");
        lore.add(c("&6✦ ĐỦ BỘ 4 MÓN — TỰ ĐỘNG KÍCH HOẠT"));
        lore.add(c("&b➤ Cyber Shield &7(" + (int) d("cyber-shield.chance", 15) + "% khi nhận đòn)"));
        lore.add(c("&b➤ Overdrive &7(máu < " + pct(d("overdrive.health-below", 0.5)) + "%)"));
        lore.add(c("&b➤ Energy Absorption &7(" + (int) d("energy-absorption.chance", 15) + "% khi nhận đòn)"));
        lore.add(c("&b➤ Ultimate Mode &7(máu < " + pct(d("ultimate.health-below", 0.2)) + "%)"));
        lore.add(c("&8━━━━━━━━━━━━━━━━"));
        lore.add(c("&b&lCHỈ BÁN TẠI SHOP VIP ASTRAX"));
        m.setLore(lore);

        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        m.getPersistentDataContainer().set(PIECE, PersistentDataType.STRING, slot);
        if (enchants && s != null) {
            for (String en : s.getStringList("enchants")) {
                String[] x = en.split(":");
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(x[0].toLowerCase()));
                if (e == null) continue;
                int lv = 1;
                try { lv = Integer.parseInt(x[1]); } catch (Exception ignored) {}
                m.addEnchant(e, lv, true);
            }
        }
        int model = s == null ? 0 : s.getInt("model-data", 0);
        if (model != 0) m.setCustomModelData(model);
        // Món không phải giáp thật (giấy, v.v.) -> cho phép đội/mặc được
        if (slotOf(it.getType()) == null) makeEquippable(m, slot, s == null ? "" : s.getString("equip-model", ""));
        it.setItemMeta(m);
    }

    // ================= nội tại từng món: sát thương gây ra / nhận vào =================
    private Player attacker(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getDamage() <= 0) return;

        // --- Bên gây sát thương: Mũ +10%, Overdrive +25%, Ultimate +25% ---
        if (e instanceof EntityDamageByEntityEvent be) {
            Player a = attacker(be.getDamager());
            if (a != null && a != e.getEntity()) {
                boolean[] w = worn(a);
                double bonus = 0;
                if (w[0]) bonus += d("passive.helmet-damage", 0.10);
                if (full(w)) {
                    State s = states.get(a.getUniqueId());
                    long now = System.currentTimeMillis();
                    if (s != null && now < s.overUntil) bonus += d("overdrive.damage-bonus", 0.25);
                    if (s != null && now < s.ultUntil) bonus += d("ultimate.damage-bonus", 0.25);
                }
                if (bonus > 0) e.setDamage(e.getDamage() * (1 + bonus));
            }
        }

        // --- Bên nhận sát thương ---
        if (e.getEntity() instanceof Player v) victim(e, v);
    }

    private void victim(EntityDamageEvent e, Player v) {
        boolean[] w = worn(v);
        if (!w[1] && !full(w)) return;
        long now = System.currentTimeMillis();
        double mult = 1.0;

        if (w[1]) mult *= 1 - d("passive.chest-reduction", 0.12);   // Giáp ngực

        if (full(w)) {
            State s = states.computeIfAbsent(v.getUniqueId(), k -> new State());

            // CYBER RESONANCE: cầm kiếm Hắc Long Exodus + đủ giáp -> thêm giảm sát thương
            if (pl.exodus() != null && pl.exodus().resonant(v)) mult *= 1 - pl.exodus().resonanceReduction();

            // I. CYBER SHIELD
            if (now >= s.shieldUntil && now >= s.shieldReady && roll(d("cyber-shield.chance", 15))) {
                s.shieldUntil = now + ms(d("cyber-shield.seconds", 4));
                s.shieldReady = now + ms(d("cyber-shield.cooldown", 45));
                bar(v, "&b⚡ CYBER SHIELD &7kích hoạt!");
                v.getWorld().playSound(v.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                Sfx.skill(v.getLocation(), "shield");
            }
            if (now < s.shieldUntil) mult *= 1 - d("cyber-shield.reduction", 0.40);

            // III. ENERGY ABSORPTION
            if (now >= s.absorbReady && roll(d("energy-absorption.chance", 15))) {
                s.absorbReady = now + ms(d("energy-absorption.cooldown", 15));
                double max = maxHealth(v);
                v.setHealth(Math.min(max, v.getHealth() + max * d("energy-absorption.heal-percent", 0.05)));
                bar(v, "&a⚡ ENERGY ABSORPTION &7hồi " + pct(d("energy-absorption.heal-percent", 0.05)) + "% máu");
                v.getWorld().playSound(v.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                Sfx.skill(v.getLocation(), "absorb");
                fx(v.getWorld(), Particle.HEART, v.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
            }

            // II & IV: kiểm tra ngưỡng máu dựa trên máu SAU đòn này
            double after = v.getHealth() - e.getFinalDamage() * mult;
            checkThresholds(v, s, after, now);

            // IV. ULTIMATE MODE giảm sát thương
            if (now < s.ultUntil) mult *= 1 - d("ultimate.reduction", 0.35);
        }

        // Trần giảm sát thương tổng để cân bằng PvP
        mult = Math.max(mult, 1 - d("max-total-reduction", 0.70));
        if (mult < 1) e.setDamage(e.getDamage() * mult);
    }

    /** Overdrive (<50% máu) và Ultimate Mode (<20% máu). Không chạy đồng thời. */
    private void checkThresholds(Player p, State s, double hp, long now) {
        double ratio = hp / maxHealth(p);
        boolean ultOn = now < s.ultUntil;
        boolean overOn = now < s.overUntil;

        if (!ultOn && ratio < d("ultimate.health-below", 0.20) && now >= s.ultReady) {
            s.ultUntil = now + ms(d("ultimate.seconds", 8));
            s.ultReady = now + ms(d("ultimate.cooldown", 120));
            s.overUntil = 0; // Ultimate thay thế Overdrive đang chạy (không chạy đồng thời)
            updateSpeed(p, worn(p), true, s, now);
            p.sendTitle("", c("&6&l⚡ ULTIMATE MODE"), 3, 25, 8);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);
            Sfx.skill(p.getLocation(), "ultimate");
            fx(p.getWorld(), Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
            return;
        }
        if (!ultOn && !overOn && ratio < d("overdrive.health-below", 0.50) && now >= s.overReady) {
            s.overUntil = now + ms(d("overdrive.seconds", 8));
            s.overReady = now + ms(d("overdrive.cooldown", 60));
            updateSpeed(p, worn(p), true, s, now);
            bar(p, "&c⚡ OVERDRIVE &7kích hoạt!");
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
            Sfx.skill(p.getLocation(), "overdrive");
            fx(p.getWorld(), Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.7, 0.4, 0.06);
        }
    }

    // ================= tốc độ di chuyển (Quần +15%, Giày +20%, Overdrive +20%) =================
    private Attribute speedAttr() {
        if (speedAttr == null) {
            for (String k : new String[]{"movement_speed", "generic.movement_speed"}) {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
                if (a != null) { speedAttr = a; break; }
            }
        }
        return speedAttr;
    }

    private void updateSpeed(Player p, boolean[] w, boolean full, State s, long now) {
        Attribute at = speedAttr();
        AttributeInstance inst = at == null ? null : p.getAttribute(at);
        if (inst == null) return;
        setMod(inst, MOD_LEGS, w[2] ? d("passive.legs-speed", 0.15) : 0);
        setMod(inst, MOD_BOOTS, w[3] ? d("passive.boots-speed", 0.20) : 0);
        setMod(inst, MOD_OVER, (full && s != null && now < s.overUntil) ? d("overdrive.speed-bonus", 0.20) : 0);
    }

    private void setMod(AttributeInstance inst, NamespacedKey key, double amount) {
        AttributeModifier existing = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (key.equals(m.getKey())) { existing = m; break; }
        }
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 1e-9) return;
            inst.removeModifier(existing);
        }
        if (amount > 0) {
            AttributeModifier nm = new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            try { inst.addTransientModifier(nm); } catch (Exception ex) { inst.addModifier(nm); }
        }
    }

    // ================= vòng lặp 0.5 giây =================
    public void startTasks() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    boolean[] w = worn(p);
                    boolean full = full(w);
                    State s = states.get(p.getUniqueId());

                    if (!full) {
                        // Tháo bất kỳ món nào -> mất hiệu ứng set (hồi chiêu vẫn giữ để chống lách)
                        if (s != null) { s.shieldUntil = 0; s.overUntil = 0; s.ultUntil = 0; }
                    } else {
                        if (s == null) { s = new State(); states.put(p.getUniqueId(), s); }
                        checkThresholds(p, s, p.getHealth(), now);
                        visuals(p, s, now);
                    }
                    updateSpeed(p, w, full, s, now);
                    updateGlow(p, w);
                }
            }
        }.runTaskTimer(pl, 10L, 10L);
    }

    private void visuals(Player p, State s, long now) {
        World w = p.getWorld();
        Location base = p.getLocation().add(0, 1, 0);
        if (now < s.shieldUntil) {
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                dust(w, base.clone().add(Math.cos(a) * 0.9, Math.sin(a * 2) * 0.6, Math.sin(a) * 0.9),
                        Color.fromRGB(0, 220, 255), 1.1f);
            }
        }
        if (now < s.overUntil) {
            fx(w, Particle.FLAME, p.getLocation().add(0, 0.1, 0), 4, 0.4, 0.05, 0.4, 0.02);
            dust(w, base.clone().add(rnd.nextGaussian() * 0.5, rnd.nextDouble(), rnd.nextGaussian() * 0.5),
                    Color.fromRGB(255, 90, 0), 1.3f);
        }
        if (now < s.ultUntil) {
            fx(w, Particle.END_ROD, base, 4, 0.5, 0.8, 0.5, 0.02);
            dust(w, base.clone().add(rnd.nextGaussian() * 0.5, rnd.nextDouble(), rnd.nextGaussian() * 0.5),
                    Color.fromRGB(255, 200, 0), 1.4f);
        }
    }


    // ================= phát sáng (Glowing) khi mặc giáp VIP =================
    /** Mặc bất kỳ 1 món giáp VIP -> Glowing. Tháo ra -> hết sáng trong tối đa 0.5 giây. */
    private void updateGlow(Player p, boolean[] w) {
        boolean want = false;
        if (cfg().getBoolean("glowing.enabled", true)) {
            if (cfg().getBoolean("glowing.cyber-dragon", true) && (w[0] || w[1] || w[2] || w[3])) want = true;
            if (!want && cfg().getBoolean("glowing.astrax-set", true)) {
                for (ItemStack it : p.getInventory().getArmorContents()) {
                    if (ItemUtil.getSet(it) != null) { want = true; break; }
                }
            }
        }
        UUID id = p.getUniqueId();
        if (want) {
            PotionEffect cur = p.getPotionEffect(PotionEffectType.GLOWING);
            // Không đè lên hiệu ứng Glowing dài hạn từ nguồn khác (event, plugin khác)
            if (cur != null && !glowing.contains(id) && cur.getDuration() > 60) return;
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
            glowing.add(id);
        } else if (glowing.remove(id)) {
            p.removePotionEffect(PotionEffectType.GLOWING);
        }
    }


    // ================= Món KHÔNG phải giáp thật (vd: giấy) đội/mặc được =================
    private boolean detectNative() {
        try {
            ItemMeta.class.getMethod("setEquippable", Class.forName("org.bukkit.inventory.meta.components.EquippableComponent"));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 1.21.2+: dùng component Equippable gốc của game. Server cũ hơn: dùng cơ chế click phải / thả vào ô giáp bên dưới. */
    private void makeEquippable(ItemMeta m, String slot, String equipModel) {
        if (!nativeEquip) return;
        try {
            Class<?> ec = Class.forName("org.bukkit.inventory.meta.components.EquippableComponent");
            Object comp = ItemMeta.class.getMethod("getEquippable").invoke(m);
            EquipmentSlot es = switch (slot) {
                case "helmet" -> EquipmentSlot.HEAD;
                case "chestplate" -> EquipmentSlot.CHEST;
                case "leggings" -> EquipmentSlot.LEGS;
                default -> EquipmentSlot.FEET;
            };
            ec.getMethod("setSlot", EquipmentSlot.class).invoke(comp, es);
            if (equipModel != null && !equipModel.isEmpty()) {
                NamespacedKey key = NamespacedKey.fromString(equipModel);
                if (key != null) ec.getMethod("setModel", NamespacedKey.class).invoke(comp, key);
            }
            ItemMeta.class.getMethod("setEquippable", ec).invoke(m, comp);
        } catch (Throwable t) {
            nativeEquip = false; // không dùng được -> chuyển sang cơ chế dự phòng
        }
    }

    private boolean isCustomWearable(ItemStack it) {
        return it != null && it.getType() != Material.AIR && slotOf(it.getType()) == null && piece(it) != null;
    }

    private ItemStack getWorn(PlayerInventory inv, String slot) {
        return switch (slot) {
            case "helmet" -> inv.getHelmet();
            case "chestplate" -> inv.getChestplate();
            case "leggings" -> inv.getLeggings();
            default -> inv.getBoots();
        };
    }

    private void setWorn(PlayerInventory inv, String slot, ItemStack it) {
        switch (slot) {
            case "helmet" -> inv.setHelmet(it);
            case "chestplate" -> inv.setChestplate(it);
            case "leggings" -> inv.setLeggings(it);
            default -> inv.setBoots(it);
        }
    }

    private int armorIndex(String slot) {
        return switch (slot) { case "helmet" -> 39; case "chestplate" -> 38; case "leggings" -> 37; default -> 36; };
    }

    private void equipSound(Player p) {
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.1f);
    }

    /** Dự phòng: click phải khi cầm vật phẩm -> đội/mặc (đổi với món đang mặc). */
    @EventHandler
    public void onEquipUse(PlayerInteractEvent e) {
        if (nativeEquip || e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isCustomWearable(hand)) return;
        String slot = piece(hand);
        PlayerInventory inv = p.getInventory();
        ItemStack old = getWorn(inv, slot);
        boolean empty = old == null || old.getType() == Material.AIR;
        e.setCancelled(true);
        ItemStack one = hand.clone();
        one.setAmount(1);
        if (hand.getAmount() > 1) {
            if (!empty) return;
            hand.setAmount(hand.getAmount() - 1);
            inv.setItemInMainHand(hand);
            setWorn(inv, slot, one);
        } else {
            setWorn(inv, slot, one);
            inv.setItemInMainHand(empty ? null : old);
        }
        equipSound(p);
    }

    /** Dự phòng: thả vào ô giáp trong túi đồ hoặc shift-click để mặc. */
    @EventHandler
    public void onEquipClick(InventoryClickEvent e) {
        if (nativeEquip || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != p.getInventory()) return;
        PlayerInventory inv = p.getInventory();

        ItemStack cursor = e.getCursor();
        if (isCustomWearable(cursor) && e.getSlotType() == InventoryType.SlotType.ARMOR
                && e.getSlot() == armorIndex(piece(cursor)) && cursor.getAmount() == 1) {
            e.setCancelled(true);
            String slot = piece(cursor);
            ItemStack old = getWorn(inv, slot);
            setWorn(inv, slot, cursor.clone());
            p.setItemOnCursor(old == null || old.getType() == Material.AIR ? null : old);
            equipSound(p);
            return;
        }
        ItemStack cur = e.getCurrentItem();
        if (e.isShiftClick() && e.getSlotType() != InventoryType.SlotType.ARMOR
                && isCustomWearable(cur) && cur.getAmount() == 1) {
            String slot = piece(cur);
            ItemStack old = getWorn(inv, slot);
            if (old == null || old.getType() == Material.AIR) {
                e.setCancelled(true);
                setWorn(inv, slot, cur.clone());
                e.setCurrentItem(null);
                equipSound(p);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
        glowing.remove(e.getPlayer().getUniqueId());
    }

    /** Gỡ các modifier tốc độ khi tắt/reload plugin. */
    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (glowing.remove(p.getUniqueId())) p.removePotionEffect(PotionEffectType.GLOWING);
        }
        Attribute at = speedAttr();
        if (at == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance inst = p.getAttribute(at);
            if (inst == null) continue;
            setMod(inst, MOD_LEGS, 0);
            setMod(inst, MOD_BOOTS, 0);
            setMod(inst, MOD_OVER, 0);
        }
    }

    // ================= lệnh /astrax cyber ... =================
    public void command(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(c("&b/astrax cyber give <người> &7- tặng trọn bộ 4 món"));
            s.sendMessage(c("&b/astrax cyber piece <người> <helmet|chestplate|leggings|boots> &7- tặng 1 món"));
            s.sendMessage(c("&b/astrax cyber apply [slot] &7- ép nội tại vào món đang cầm (giấy/vật phẩm khác: ghi slot, vd: helmet)"));
            return;
        }
        switch (a[1].toLowerCase()) {
            case "give" -> {
                if (a.length < 3) { s.sendMessage(c("&cDùng: /astrax cyber give <người>")); return; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { s.sendMessage(c("&cNgười chơi không online.")); return; }
                for (String slot : SLOTS) give(t, build(slot));
                s.sendMessage(c("&aĐã tặng bộ Cyber Dragon cho " + t.getName()));
                t.sendMessage(c("&b⚡ Bạn nhận được bộ &lCYBER DRAGON&b! Mặc đủ 4 món để kích hoạt nội tại bộ."));
            }
            case "piece" -> {
                if (a.length < 4) { s.sendMessage(c("&cDùng: /astrax cyber piece <người> <slot>")); return; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { s.sendMessage(c("&cNgười chơi không online.")); return; }
                String slot = a[3].toLowerCase();
                if (!Arrays.asList(SLOTS).contains(slot)) { s.sendMessage(c("&cSlot: helmet, chestplate, leggings, boots")); return; }
                give(t, build(slot));
                s.sendMessage(c("&aĐã tặng " + slot + " cho " + t.getName()));
            }
            case "apply" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Chỉ dùng trong game."); return; }
                ItemStack h = p.getInventory().getItemInMainHand();
                if (h.getType() == Material.AIR) { s.sendMessage(c("&cHãy cầm vật phẩm trên tay.")); return; }
                String slot = slotOf(h.getType());
                boolean custom = slot == null;
                if (custom) {
                    if (a.length < 3 || !Arrays.asList(SLOTS).contains(a[2].toLowerCase())) {
                        s.sendMessage(c("&cĐây không phải giáp thật. Dùng: /astrax cyber apply <helmet|chestplate|leggings|boots>"));
                        return;
                    }
                    slot = a[2].toLowerCase();
                }
                apply(h, slot, false);
                p.getInventory().setItemInMainHand(h);
                s.sendMessage(c("&aĐã ép nội tại Cyber Dragon (" + slot + ") vào vật phẩm."));
                if (custom) s.sendMessage(c(nativeEquip
                        ? "&7Vật phẩm này đã có thể mặc như giáp (click phải hoặc kéo vào ô giáp)."
                        : "&7Vật phẩm này mặc bằng cách: click phải khi cầm, hoặc thả vào ô giáp trong túi đồ."));
            }
            default -> s.sendMessage(c("&cLệnh con không tồn tại. Gõ /astrax cyber"));
        }
    }

    private void give(Player t, ItemStack it) {
        for (ItemStack drop : t.getInventory().addItem(it).values()) {
            t.getWorld().dropItemNaturally(t.getLocation(), drop);
        }
    }
}
