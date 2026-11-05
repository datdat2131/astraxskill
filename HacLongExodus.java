package vn.astrax.skill;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * ASTRAX - HẮC LONG EXODUS
 * 6 kỹ năng bị động (có giới hạn), chỉ số riêng, hiệu ứng chém, cộng hưởng với giáp Cyber Dragon.
 */
public class HacLongExodus implements Listener {
    private static final Color RED = Color.fromRGB(230, 0, 20);
    private static final Color DARK = Color.fromRGB(70, 0, 10);
    private static final Color EDGE = Color.fromRGB(255, 190, 190);

    private static class State {
        int energy;
        long overlordUntil;
        final Map<String, Long> cd = new HashMap<>();
    }

    private final AstraxSkill pl;
    private final NamespacedKey EXO, MOD_AS;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Integer> combo = new HashMap<>();
    private final Set<UUID> speedOn = new HashSet<>();
    private final Random rnd = new Random();
    private boolean busy = false;
    private Attribute asAttr;

    public HacLongExodus(AstraxSkill pl) {
        this.pl = pl;
        this.EXO = new NamespacedKey(pl, "exodus");
        this.MOD_AS = new NamespacedKey(pl, "exodus_attack_speed");
    }

    // ================= helpers =================
    private FileConfiguration cfg() { return pl.getConfig(); }
    private double d(String path, double def) { return cfg().getDouble("exodus." + path, def); }
    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private long ms(double sec) { return (long) (sec * 1000); }
    private long pct(double v) { return Math.round(v * 100); }
    private boolean roll(double percent) { return rnd.nextDouble() * 100 < percent; }
    private boolean pvp() { return cfg().getBoolean("pvp", true); }

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

    private void heal(Player p, double amt) {
        if (amt <= 0 || p.isDead()) return;
        p.setHealth(Math.min(maxHealth(p), p.getHealth() + amt));
    }

    public boolean isExodus(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(EXO, PersistentDataType.BYTE);
    }

    public boolean holdsExodus(Player p) { return isExodus(p.getInventory().getItemInMainHand()); }

    /** CYBER RESONANCE: cầm kiếm Exodus + mặc đủ 4 món Cyber Dragon. */
    public boolean resonant(Player p) {
        return holdsExodus(p) && pl.cyber() != null && pl.cyber().isFullSet(p);
    }

    public double resonanceReduction() { return d("resonance.damage-reduction", 0.05); }

    private boolean ready(State s, String k, long now) { return now >= s.cd.getOrDefault(k, 0L); }
    private void cdStart(State s, String k, long now, double sec) { s.cd.put(k, now + ms(sec)); }

    private boolean validTarget(Player p, Entity en) {
        return en instanceof LivingEntity && en != p && !(en instanceof ArmorStand)
                && !(en instanceof Player && !pvp());
    }

    /** Sát thương kỹ năng lên mục tiêu phụ: tôn trọng giáp, giảm khi đánh người, có trần. */
    private void hurt(Player p, LivingEntity t, double amt, double cap) {
        if (t instanceof Player) {
            if (!pvp()) return;
            amt *= cfg().getDouble("exodus.pvp-multiplier", 0.6);
        }
        amt = Math.min(amt, cap);
        busy = true;
        try { t.damage(amt, p); } finally { busy = false; }
    }

    // ================= tạo item =================
    public ItemStack build() {
        Material m = Material.matchMaterial(cfg().getString("exodus.material", "NETHERITE_SWORD"));
        if (m == null) m = Material.NETHERITE_SWORD;
        ItemStack it = new ItemStack(m);
        apply(it, true);
        return it;
    }

    @SuppressWarnings("deprecation")
    public void apply(ItemStack it, boolean enchants) {
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(c(cfg().getString("exodus.name", "&4&l⚔ ʜắᴄ ʟᴏɴɢ ᴇxᴏᴅᴜs")));
        List<String> l = new ArrayList<>();
        l.add(c("&cᴀsᴛʀᴀx ᴍʏᴛʜɪᴄᴀʟ ᴡᴇᴀᴘᴏɴ"));
        l.add(c("&8━━━━━━━━━━━━━━━━"));
        l.add(c("&7Vũ khí tối mật của đế chế Astrax."));
        l.add(c("&7Bên trong lưỡi kiếm là lõi năng lượng"));
        l.add(c("&7có khả năng hấp thụ sức mạnh."));
        l.add("");
        l.add(c("&c✦ &fRarity: &4MYTHICAL+"));
        l.add(c("&c✦ &fClass: &4CYBER"));
        l.add(c("&c✦ &fPower: &cEXODUS"));
        l.add(c("&8━━━━━━━━━━━━━━━━"));
        l.add(c("&6✦ ᴄʜỉ sốᴄ"));
        l.add(c("&c✦ &fSát thương cộng thêm: &a+" + pct(d("stats.damage-bonus", 0.30)) + "%"));
        l.add(c("&c✦ &fTỷ lệ chí mạng: &a" + (int) d("stats.crit-chance", 25) + "%"));
        l.add(c("&c✦ &fSát thương chí mạng: &a+" + pct(d("stats.crit-damage", 0.75)) + "%"));
        l.add(c("&c✦ &fHút máu: &a" + pct(d("stats.lifesteal", 0.06)) + "%"));
        l.add(c("&c✦ &fTốc độ đánh: &a+" + pct(d("stats.attack-speed", 0.15)) + "%"));
        l.add(c("&8━━━━━━━━━━━━━━━━"));
        l.add(c("&6✦ ᴘᴀssɪᴠᴇ sᴋɪʟʟs:"));
        l.add(c("&c➤ Dragon Slash"));
        l.add(c("&c➤ Core Explosion"));
        l.add(c("&c➤ Blood Engine"));
        l.add(c("&c➤ Thunder Core"));
        l.add(c("&c➤ Overlord"));
        l.add(c("&c➤ Apocalypse"));
        l.add(c("&8━━━━━━━━━━━━━━━━"));
        l.add(c("&b⚡ Cộng hưởng: &7Full giáp Cyber Dragon"));
        l.add(c("&4&lCHỈ CÓ THỂ MUA TẠI SHOP VIP ASTRAX"));
        m.setLore(l);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(EXO, PersistentDataType.BYTE, (byte) 1);
        if (enchants) {
            for (String en : cfg().getStringList("exodus.enchants")) {
                String[] x = en.split(":");
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(x[0].toLowerCase()));
                if (e == null) continue;
                int lv = 1;
                try { lv = Integer.parseInt(x[1]); } catch (Exception ignored) {}
                m.addEnchant(e, lv, true);
            }
        }
        it.setItemMeta(m);
        setModel(it, false);
    }

    @SuppressWarnings("deprecation")
    private void setModel(ItemStack it, boolean overlord) {
        int base = cfg().getInt("exodus.model-data.normal", 0);
        int ov = cfg().getInt("exodus.model-data.overlord", 0);
        if (base == 0 && ov == 0) return;
        int id = overlord && ov != 0 ? ov : base;
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(id == 0 ? null : id);
        it.setItemMeta(m);
    }

    // ================= đánh trúng =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (busy || e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity v)
                || v instanceof ArmorStand) return;
        if (!holdsExodus(p)) return;

        long now = System.currentTimeMillis();
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        double orig = e.getDamage();

        // --- Chỉ số cơ bản: +30% dmg (+5% cộng hưởng, +30% Overlord) ---
        double bonus = d("stats.damage-bonus", 0.30);
        boolean res = resonant(p);
        if (res) bonus += d("resonance.damage-bonus", 0.05);
        boolean overlord = now < s.overlordUntil;
        if (overlord) bonus += d("overlord.damage-bonus", 0.30);
        double dmg = orig * (1 + bonus);

        // --- Chí mạng 25% / +75% ---
        boolean crit = roll(d("stats.crit-chance", 25));
        if (crit) dmg *= 1 + d("stats.crit-damage", 0.75);

        // --- Nhóm TẤN CÔNG: mỗi đòn chỉ tối đa 1 kỹ năng (Thunder / Slash / Core), thứ tự ngẫu nhiên ---
        boolean proc = false;
        List<String> order = new ArrayList<>(List.of("thunder-core", "dragon-slash", "core-explosion"));
        Collections.shuffle(order, rnd);
        for (String k : order) {
            if (!ready(s, k, now) || !roll(d("skills." + k + ".chance", 10))) continue;
            cdStart(s, k, now, d("skills." + k + ".cooldown", 5));
            proc = true;
            switch (k) {
                case "dragon-slash" -> {
                    dmg *= 1 + d("skills.dragon-slash.damage-ratio", 0.45);
                    dragonSlashFx(p);
                    Sfx.skill(p.getLocation(), "dragon-slash");
                    bar(p, "&c⚔ DRAGON SLASH");
                }
                case "thunder-core" -> {
                    dmg *= 1 + d("skills.thunder-core.damage-ratio", 0.35);
                    thunderFx(p, v);
                    Sfx.skill(p.getLocation(), "thunder");
                    bar(p, "&e⚡ THUNDER CORE");
                }
                default -> {
                    coreExplosion(p, v, dmg);
                    Sfx.skill(p.getLocation(), "core");
                    bar(p, "&6✹ CORE EXPLOSION");
                }
            }
            break;
        }

        // Trần sát thương mỗi đòn (cân bằng PvP)
        dmg = Math.min(dmg, orig * d("max-multiplier", 2.5));
        e.setDamage(dmg);

        // --- Hút máu 6% (có trần mỗi đòn) ---
        heal(p, Math.min(dmg * d("stats.lifesteal", 0.06), d("stats.max-lifesteal-per-hit", 3)));

        // --- BLOOD ENGINE (nhóm sinh tồn) ---
        if (ready(s, "blood-engine", now) && roll(d("skills.blood-engine.chance", 15))) {
            cdStart(s, "blood-engine", now, d("skills.blood-engine.cooldown", 12));
            Sfx.skill(p.getLocation(), "blood");
            heal(p, maxHealth(p) * d("skills.blood-engine.heal-percent", 0.08));
            fx(p.getWorld(), Particle.HEART, p.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3, 0);
            for (int i = 0; i < 12; i++) {
                dust(p.getWorld(), p.getLocation().add(rnd.nextGaussian() * 0.4, rnd.nextDouble() * 1.8, rnd.nextGaussian() * 0.4), RED, 1.3f);
            }
            bar(p, "&4❤ BLOOD ENGINE &7hồi " + pct(d("skills.blood-engine.heal-percent", 0.08)) + "% máu");
            proc = true;
        }

        // --- OVERLORD (tối thượng): tích điểm, không tích khi đang kích hoạt ---
        if (!overlord) {
            int need = (int) d("overlord.hits", 20);
            s.energy++;
            if (s.energy >= need) {
                s.energy = 0;
                s.overlordUntil = now + ms(d("overlord.seconds", 8));
                p.sendTitle("", c("&4&l⚔ OVERLORD"), 3, 25, 8);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.9f);
                Sfx.skill(p.getLocation(), "overlord");
                fx(p.getWorld(), Particle.FLAME, p.getLocation().add(0, 1, 0), 60, 0.6, 0.9, 0.6, 0.08);
                ItemStack h = p.getInventory().getItemInMainHand();
                setModel(h, true);
                p.getInventory().setItemInMainHand(h);
                proc = true;
            } else if (!proc) {
                int filled = Math.min(10, s.energy * 10 / Math.max(1, need));
                bar(p, "&4⚔ Overlord &c" + "▮".repeat(filled) + "&8" + "▮".repeat(10 - filled) + " &7" + s.energy + "/" + need);
            }
        }

        // --- Tiếng leng keng + hiệu ứng chém ---
        Sfx.clang(v.getLocation(), crit);
        if (cfg().getBoolean("exodus.slash-fx", true)) slashFx(p, v, crit);
        if (cfg().getBoolean("exodus.impact.enabled", true)) impact(p, v, crit);
        if (crit) {
            p.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            fx(p.getWorld(), Particle.CRIT, v.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.4);
        }

        // --- Giáp Cyber Dragon phát sáng đồng bộ khi kiếm kích hoạt kỹ năng ---
        if (proc && res) pulseArmor(p);
    }

    // ================= APOCALYPSE: hạ gục -> 10% =================
    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (busy) return;
        LivingEntity v = e.getEntity();
        Player p = v.getKiller();
        if (p == null || p == v || !holdsExodus(p)) return;
        long now = System.currentTimeMillis();
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        if (!ready(s, "apocalypse", now) || !roll(d("skills.apocalypse.chance", 10))) return;
        cdStart(s, "apocalypse", now, d("skills.apocalypse.cooldown", 60));
        apocalypse(p, v.getLocation().add(0, 0.5, 0), v);
        bar(p, "&4☠ APOCALYPSE");
        if (resonant(p)) pulseArmor(p);
    }

    private void apocalypse(Player p, Location at, Entity dead) {
        World w = at.getWorld();
        double r = d("skills.apocalypse.radius", 5);
        double dmg = d("skills.apocalypse.damage", 8);
        int max = (int) d("skills.apocalypse.max-targets", 6);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        w.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        Sfx.skill(at, "apocalypse");
        fx(w, Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        int n = 0;
        for (Entity en : w.getNearbyEntities(at, r, r, r)) {
            if (n >= max) break;
            if (en == dead || !validTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            hurt(p, le, dmg, dmg);
            Vector kb = le.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (kb.lengthSquared() > 1e-6) le.setVelocity(kb.normalize().multiply(0.6).setY(0.3));
            n++;
        }
        // Sóng năng lượng đỏ lan rộng + cột sáng
        new BukkitRunnable() {
            int k = 0;
            @Override public void run() {
                k++;
                double rad = k * (r / 8.0);
                for (int i = 0; i < 28; i++) {
                    double a = i * Math.PI * 2 / 28;
                    dust(w, at.clone().add(Math.cos(a) * rad, 0.2, Math.sin(a) * rad), RED, 1.6f);
                    if (i % 4 == 0) dust(w, at.clone().add(Math.cos(a) * rad, 0.5, Math.sin(a) * rad), EDGE, 1.0f);
                }
                for (int y = 0; y < k * 2; y++) dust(w, at.clone().add(0, y * 0.5, 0), DARK, 1.8f);
                fx(w, Particle.FLAME, at, 6, rad / 2, 0.3, rad / 2, 0.05);
                if (k >= 8) cancel();
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    // ================= Core Explosion / Thunder / Dragon Slash =================
    private void coreExplosion(Player p, LivingEntity main, double hitDmg) {
        World w = main.getWorld();
        Location at = main.getLocation().add(0, 1, 0);
        double r = d("skills.core-explosion.radius", 3);
        double dmg = hitDmg * d("skills.core-explosion.damage-ratio", 0.5);
        double cap = d("skills.core-explosion.max-damage", 8);
        int max = (int) d("skills.core-explosion.max-targets", 5);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
        fx(w, Particle.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
        fx(w, Particle.FLAME, at, 40, r / 3, 0.5, r / 3, 0.08);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            dust(w, at.clone().add(Math.cos(a) * r * 0.9, -0.5, Math.sin(a) * r * 0.9), RED, 1.5f);
        }
        int n = 0;
        for (Entity en : w.getNearbyEntities(at, r, r, r)) {
            if (n >= max) break;
            if (en == main || !validTarget(p, en)) continue;
            hurt(p, (LivingEntity) en, dmg, cap);
            n++;
        }
    }

    private void thunderFx(Player p, LivingEntity v) {
        World w = v.getWorld();
        w.strikeLightningEffect(v.getLocation()); // chỉ hiệu ứng, không đốt cháy/không sát thương
        Location a = p.getEyeLocation().subtract(0, 0.3, 0);
        Location b = v.getLocation().add(0, 1, 0);
        for (int i = 0; i <= 14; i++) {
            Location l = a.clone().add(b.clone().subtract(a).toVector().multiply(i / 14.0));
            l.add(rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12);
            dust(w, l, Color.fromRGB(190, 230, 255), 1.2f);
            if (i % 3 == 0) fx(w, Particle.ELECTRIC_SPARK, l, 2, 0.05, 0.05, 0.05, 0.1);
        }
        fx(w, Particle.ELECTRIC_SPARK, b, 30, 0.4, 0.8, 0.4, 0.3);
    }

    private void dragonSlashFx(Player p) {
        World w = p.getWorld();
        final Location c = p.getEyeLocation();
        final Vector f = c.getDirection().normalize();
        final Vector axis = axis(f, 0);
        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        new BukkitRunnable() {
            int k = 0;
            @Override public void run() {
                Location o = c.clone().add(f.clone().multiply(1.0 + k * 1.4)).subtract(0, 0.3, 0);
                drawArc(w, o, f, axis, 2.4 + 0.3 * k, -75, 75, 5);
                if (++k >= 5) cancel();
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    // ================= LỰC CHÉM: xung lực đẩy lùi nhẹ + sóng chấn dưới chân =================
    private void impact(Player p, LivingEntity v, boolean crit) {
        double kb = d("impact.knockback", 0.35) * (crit ? d("impact.crit-multiplier", 1.6) : 1.0);
        double lift = d("impact.lift", 0.12);
        Vector dir = v.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 1e-6) dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) return;
        final Vector push = dir.normalize().multiply(kb).setY(lift);
        // Chạy sau 1 tick để cộng thêm lên knockback gốc của game, có trần tốc độ để không bị hất bay quá xa
        Bukkit.getScheduler().runTask(pl, () -> {
            if (v.isDead() || !v.isValid()) return;
            Vector nv = v.getVelocity().add(push);
            if (nv.length() > 1.0) nv.normalize();
            v.setVelocity(nv);
        });
        World w = v.getWorld();
        Location at = v.getLocation().add(0, 0.15, 0);
        double r = crit ? 1.1 : 0.8;
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18;
            Location l = at.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            dust(w, l, RED, 1.2f);
            if (i % 3 == 0) dust(w, l.clone().add(0, 0.1, 0), EDGE, 0.9f);
        }
        fx(w, Particle.CLOUD, at, crit ? 10 : 5, 0.3, 0.05, 0.3, 0.04);
        Sfx.impact(at, crit);
    }

    // ================= HIỆU ỨNG CHÉM =================
    private Vector axis(Vector f, double tiltDeg) {
        Vector right = f.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
        right.normalize();
        Vector perp = right.clone().crossProduct(f).normalize();
        double t = Math.toRadians(tiltDeg);
        return right.multiply(Math.cos(t)).add(perp.multiply(Math.sin(t)));
    }

    /** Vẽ một cung chém (lưỡi liềm) trong mặt phẳng nghiêng, đỏ + viền trắng hồng. */
    private void drawArc(World w, Location c, Vector f, Vector axis, double r, double a0, double a1, double step) {
        int i = 0;
        for (double a = a0; a <= a1; a += step, i++) {
            double rad = Math.toRadians(a);
            Vector dir = f.clone().multiply(Math.cos(rad) * r * 0.85).add(axis.clone().multiply(Math.sin(rad) * r));
            Location l = c.clone().add(dir);
            dust(w, l, RED, 1.5f);
            Vector inward = dir.clone().normalize().multiply(-0.18);
            dust(w, l.clone().add(inward), DARK, 1.2f);
            if (i % 2 == 0) dust(w, l.clone().add(dir.clone().normalize().multiply(0.15)), EDGE, 0.9f);
        }
    }

    /** Chém 3 kiểu xoay vòng (chéo trái, chéo phải, bổ dọc). Chí mạng = chém chữ X. */
    private void slashFx(Player p, LivingEntity v, boolean crit) {
        int idx = combo.merge(p.getUniqueId(), 1, Integer::sum) % 3;
        double[] tilts = {-30, 30, 85};
        int[] signs = {1, -1, 1};
        slashAnim(p, 2.2, tilts[idx], signs[idx]);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, crit ? 0.6f : 1.0f);
        if (crit) {
            slashAnim(p, 2.8, 45, 1);
            slashAnim(p, 2.8, -45, -1);
        }
    }

    /** Nhát chém hoạt hoạ trong 4 tick: lưỡi chém quét từ đầu đến cuối cung. */
    private void slashAnim(Player p, double radius, double tilt, int sign) {
        World w = p.getWorld();
        final Location eye = p.getEyeLocation();
        final Vector f = eye.getDirection().normalize();
        final Vector axis = axis(f, tilt);
        final Location o = eye.clone().add(f.clone().multiply(0.5)).subtract(0, 0.35, 0);
        new BukkitRunnable() {
            int k = 0;
            @Override public void run() {
                double a0 = -80 + 40 * k, a1 = a0 + 40;
                if (sign < 0) { double t0 = a0; a0 = -a1; a1 = -t0; }
                drawArc(w, o, f, axis, radius, a0, a1, 6);
                double lead = Math.toRadians(sign < 0 ? a0 : a1);
                Location tip = o.clone().add(f.clone().multiply(Math.cos(lead) * radius * 0.85))
                        .add(axis.clone().multiply(Math.sin(lead) * radius));
                fx(w, Particle.ELECTRIC_SPARK, tip, 2, 0.05, 0.05, 0.05, 0.15);
                if (k == 1) fx(w, Particle.SWEEP_ATTACK, o.clone().add(f.clone().multiply(radius * 0.6)), 1, 0, 0, 0, 0);
                if (++k >= 4) cancel();
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    // ================= Cộng hưởng với giáp Cyber Dragon =================
    private void pulseArmor(Player p) {
        World w = p.getWorld();
        for (double y : new double[]{0.15, 0.7, 1.2, 1.7}) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                dust(w, p.getLocation().add(Math.cos(a) * 0.45, y, Math.sin(a) * 0.45), Color.fromRGB(255, 40, 40), 1.2f);
            }
        }
        fx(w, Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.2);
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.8f);
        Sfx.skill(p.getLocation(), "resonance");

        // Tuỳ chọn resource pack: đổi model giáp sang bản "sáng" trong 1 giây
        PlayerInventory inv = p.getInventory();
        ItemStack[] arm = inv.getArmorContents();
        boolean any = false;
        for (ItemStack it : arm) {
            String slot = pl.cyber().piece(it);
            if (slot == null) continue;
            int pm = cfg().getInt("cyber-dragon.pieces." + slot + ".pulse-model-data", 0);
            if (pm == 0) continue;
            setArmorModel(it, pm);
            any = true;
        }
        if (any) {
            inv.setArmorContents(arm);
            Bukkit.getScheduler().runTaskLater(pl, () -> restoreArmor(p), 20L);
        }
    }

    @SuppressWarnings("deprecation")
    private void setArmorModel(ItemStack it, int id) {
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(id == 0 ? null : id);
        it.setItemMeta(m);
    }

    private void restoreArmor(Player p) {
        if (!p.isOnline()) return;
        PlayerInventory inv = p.getInventory();
        ItemStack[] arm = inv.getArmorContents();
        for (ItemStack it : arm) {
            String slot = pl.cyber().piece(it);
            if (slot == null) continue;
            setArmorModel(it, cfg().getInt("cyber-dragon.pieces." + slot + ".model-data", 0));
        }
        inv.setArmorContents(arm);
    }

    // ================= vòng lặp: tốc độ đánh, hào quang, hết Overlord =================
    private Attribute asAttr() {
        if (asAttr == null) {
            for (String k : new String[]{"attack_speed", "generic.attack_speed"}) {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
                if (a != null) { asAttr = a; break; }
            }
        }
        return asAttr;
    }

    private void setAttackSpeed(Player p, double amount) {
        Attribute at = asAttr();
        AttributeInstance inst = at == null ? null : p.getAttribute(at);
        if (inst == null) return;
        AttributeModifier existing = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (MOD_AS.equals(m.getKey())) { existing = m; break; }
        }
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 1e-9) return;
            inst.removeModifier(existing);
        }
        if (amount > 0) {
            AttributeModifier nm = new AttributeModifier(MOD_AS, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            try { inst.addTransientModifier(nm); } catch (Exception ex) { inst.addModifier(nm); }
        }
    }

    public void startTasks() {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 4;
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    boolean holding = holdsExodus(p);

                    // Tốc độ đánh +15% khi cầm kiếm
                    if (holding) { setAttackSpeed(p, d("stats.attack-speed", 0.15)); speedOn.add(id); }
                    else if (speedOn.remove(id)) setAttackSpeed(p, 0);

                    // Hết Overlord -> trả model
                    State s = states.get(id);
                    if (s != null && s.overlordUntil > 0 && now >= s.overlordUntil) {
                        s.overlordUntil = 0;
                        restoreModels(p);
                        bar(p, "&7Overlord đã tan...");
                    }
                    if (!holding) continue;

                    aura(p, s != null && now < s.overlordUntil, resonant(p), t);
                }
            }
        }.runTaskTimer(pl, 4L, 4L);
    }

    private void restoreModels(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isExodus(it)) { setModel(it, false); inv.setItem(i, it); }
        }
    }

    private void aura(Player p, boolean overlord, boolean resonance, int t) {
        World w = p.getWorld();
        Location tip = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.9)).subtract(0, 0.45, 0);
        dust(w, tip, RED, 1.0f);
        if (rnd.nextInt(3) == 0) fx(w, Particle.ELECTRIC_SPARK, tip, 1, 0.1, 0.1, 0.1, 0.05);

        Location base = p.getLocation().add(0, 1, 0);
        if (overlord) {
            for (int i = 0; i < 3; i++) {
                double a = t * 0.4 + i * (2 * Math.PI / 3);
                dust(w, base.clone().add(Math.cos(a) * 0.9, Math.sin(t * 0.2 + i) * 0.6, Math.sin(a) * 0.9), RED, 1.4f);
            }
            fx(w, Particle.FLAME, p.getLocation().add(0, 0.1, 0), 3, 0.4, 0.05, 0.4, 0.02);
        }
        if (resonance) { // năng lượng đỏ quanh nhân vật
            for (int i = 0; i < 4; i++) {
                double a = -t * 0.3 + i * (Math.PI / 2);
                dust(w, base.clone().add(Math.cos(a) * 0.7, Math.cos(t * 0.15 + i) * 0.8, Math.sin(a) * 0.7),
                        Color.fromRGB(255, 30, 30), 1.1f);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        states.remove(id);
        combo.remove(id);
        speedOn.remove(id);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (speedOn.remove(p.getUniqueId())) setAttackSpeed(p, 0);
        }
    }

    // ================= lệnh /astrax exodus ... =================
    public void command(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(c("&4/astrax exodus give <người> &7- tặng Kiếm Hắc Long Exodus"));
            s.sendMessage(c("&4/astrax exodus apply &7- ép Exodus vào thanh kiếm đang cầm"));
            return;
        }
        switch (a[1].toLowerCase()) {
            case "give" -> {
                if (a.length < 3) { s.sendMessage(c("&cDùng: /astrax exodus give <người>")); return; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { s.sendMessage(c("&cNgười chơi không online.")); return; }
                for (ItemStack drop : t.getInventory().addItem(build()).values()) {
                    t.getWorld().dropItemNaturally(t.getLocation(), drop);
                }
                s.sendMessage(c("&aĐã tặng Hắc Long Exodus cho " + t.getName()));
                t.sendMessage(c("&4&l⚔ &cBạn nhận được &4&lHẮC LONG EXODUS&c!"));
            }
            case "apply" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Chỉ dùng trong game."); return; }
                ItemStack h = p.getInventory().getItemInMainHand();
                if (!h.getType().name().endsWith("_SWORD")) { s.sendMessage(c("&cHãy cầm một thanh kiếm.")); return; }
                apply(h, false);
                p.getInventory().setItemInMainHand(h);
                s.sendMessage(c("&aĐã ép lõi Exodus vào kiếm."));
            }
            default -> s.sendMessage(c("&cLệnh con không tồn tại. Gõ /astrax exodus"));
        }
    }
}
