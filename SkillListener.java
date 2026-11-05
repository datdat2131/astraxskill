package vn.astrax.skill;

import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class SkillListener implements Listener {
    private final AstraxSkill pl;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Random rnd = new Random();

    public SkillListener(AstraxSkill pl) {
        this.pl = pl;
    }

    // ---------- helpers ----------
    private double chance(SkillType t, int lv) {
        FileConfiguration c = pl.getConfig();
        String p = "skills." + t.name() + ".";
        return c.getDouble(p + "chance", 10) + c.getDouble(p + "chance-per-level", 0) * (lv - 1);
    }

    private double power(SkillType t, int lv) {
        FileConfiguration c = pl.getConfig();
        String p = "skills." + t.name() + ".";
        return c.getDouble(p + "power", 1) + c.getDouble(p + "power-per-level", 0) * (lv - 1);
    }

    private boolean roll(SkillType t, int lv) {
        return rnd.nextDouble() * 100 < chance(t, lv);
    }

    private String msg(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void heal(Player p, double amount) {
        @SuppressWarnings("deprecation")
        double max = p.getMaxHealth();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }

    private Map<SkillType, Integer> weaponSkills(Player p) {
        Map<SkillType, Integer> out = new EnumMap<>(SkillType.class);
        ItemStack hand = p.getInventory().getItemInMainHand();
        for (Map.Entry<SkillType, Integer> e : ItemUtil.getSkills(hand).entrySet()) {
            if (!e.getKey().armor) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** Gộp skill từ tất cả giáp đang mặc (cộng dồn cấp, tối đa max-level). */
    private Map<SkillType, Integer> armorSkills(Player p) {
        int max = pl.getConfig().getInt("max-level", 5);
        Map<SkillType, Integer> out = new EnumMap<>(SkillType.class);
        for (ItemStack it : p.getInventory().getArmorContents()) {
            for (Map.Entry<SkillType, Integer> e : ItemUtil.getSkills(it).entrySet()) {
                if (e.getKey().armor) out.merge(e.getKey(), e.getValue(), (a, b) -> Math.min(max, a + b));
            }
        }
        return out;
    }

    private List<LivingEntity> nearby(Player p, double r) {
        boolean pvp = pl.getConfig().getBoolean("pvp", true);
        List<LivingEntity> list = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand) continue;
            if (e instanceof Player && !pvp) continue;
            list.add(le);
        }
        return list;
    }

    private boolean inFront(Player p, LivingEntity e) {
        Vector dir = p.getLocation().getDirection().normalize();
        Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
        return dir.dot(to) > 0.5;
    }

    // ---------- skill bị động ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // Người chơi đánh -> skill kiếm + set bonus
        if (e.getDamager() instanceof Player p && e.getEntity() instanceof LivingEntity v) {
            SetManager.Result set = pl.sets().compute(p, false);
            if (set.damageBonus() > 0) e.setDamage(e.getDamage() * (1 + set.damageBonus()));

            for (Map.Entry<SkillType, Integer> en : weaponSkills(p).entrySet()) {
                SkillType t = en.getKey();
                int lv = en.getValue();
                if (!roll(t, lv)) continue;
                double pw = power(t, lv);
                switch (t) {
                    case FLAME -> {
                        v.setFireTicks((int) (pw * 20));
                        v.getWorld().spawnParticle(Particle.FLAME, v.getLocation().add(0, 1, 0), 25, .3, .5, .3, .02);
                    }
                    case LIGHTNING -> {
                        v.getWorld().strikeLightningEffect(v.getLocation());
                        v.damage(pw); // không truyền source để tránh lặp sự kiện
                    }
                    case LIFESTEAL -> {
                        heal(p, e.getDamage() * pw / 100.0);
                        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 4, .3, .2, .3);
                    }
                    case FREEZE -> {
                        v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (pw * 20), 1));
                        v.getWorld().spawnParticle(Particle.SNOWFLAKE, v.getLocation().add(0, 1, 0), 25, .3, .5, .3, .02);
                    }
                    default -> {}
                }
            }
        }

        // Người chơi bị đánh -> skill giáp + set bonus
        if (e.getEntity() instanceof Player v) {
            double dmg = e.getDamage();
            SetManager.Result set = pl.sets().compute(v, false);
            double totalRed = set.damageReduction();

            for (Map.Entry<SkillType, Integer> en : armorSkills(v).entrySet()) {
                SkillType t = en.getKey();
                int lv = en.getValue();
                if (!roll(t, lv)) continue;
                double pw = power(t, lv);
                switch (t) {
                    case GUARD -> {
                        totalRed += pw / 100.0;
                        v.getWorld().spawnParticle(Particle.CRIT, v.getLocation().add(0, 1, 0), 15, .4, .6, .4, .1);
                    }
                    case REFLECT -> {
                        if (e.getDamager() instanceof LivingEntity a && a != v) {
                            a.damage(dmg * pw / 100.0);
                        }
                    }
                    case REGEN -> {
                        heal(v, pw);
                        v.getWorld().spawnParticle(Particle.HEART, v.getLocation().add(0, 2, 0), 4, .3, .2, .3);
                    }
                    default -> {}
                }
            }
            if (totalRed > 0) e.setDamage(dmg * (1 - Math.min(totalRed, 0.8)));
        }
    }

    // ---------- skill chủ động ----------
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        boolean armorMode = p.isSneaking();
        Map<SkillType, Integer> skills = armorMode ? armorSkills(p) : weaponSkills(p);
        if (skills.isEmpty()) return;

        for (Map.Entry<SkillType, Integer> en : skills.entrySet()) {
            SkillType t = en.getKey();
            String key = p.getUniqueId() + ":" + t.name();
            long now = System.currentTimeMillis();
            long until = cooldowns.getOrDefault(key, 0L);
            if (now < until) {
                long left = (until - now + 999) / 1000;
                p.sendMessage(msg("&c" + t.display + " còn hồi chiêu " + left + "s"));
                continue;
            }
            if (cast(p, t, en.getValue())) {
                long cd = (long) (pl.getConfig().getDouble("skills." + t.name() + ".cooldown", 10) * 1000);
                cooldowns.put(key, now + cd);
                p.sendMessage(msg("&e✦ Kích hoạt &6" + t.display));
            }
        }
    }

    private boolean cast(Player p, SkillType t, int lv) {
        double pw = power(t, lv);
        switch (t) {
            case FLAME -> {
                for (LivingEntity le : nearby(p, 6)) {
                    if (!inFront(p, le)) continue;
                    le.setFireTicks((int) (pw * 20));
                    le.damage(3 + lv);
                }
                Vector d = p.getLocation().getDirection().normalize();
                for (int i = 1; i <= 6; i++) {
                    p.getWorld().spawnParticle(Particle.FLAME, p.getEyeLocation().add(d.clone().multiply(i)), 8, .4, .4, .4, .01);
                }
                return true;
            }
            case LIGHTNING -> {
                LivingEntity target = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity le : nearby(p, 12)) {
                    if (!inFront(p, le)) continue;
                    double dist = le.getLocation().distanceSquared(p.getLocation());
                    if (dist < best) { best = dist; target = le; }
                }
                if (target == null) {
                    p.sendMessage(msg("&cKhông có mục tiêu phía trước!"));
                    return false;
                }
                target.getWorld().strikeLightningEffect(target.getLocation());
                target.damage(pw + 4);
                return true;
            }
            case LIFESTEAL -> {
                double total = 0;
                for (LivingEntity le : nearby(p, 4)) {
                    double d = 4 + lv;
                    le.damage(d);
                    total += d;
                }
                heal(p, total * pw / 100.0);
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 40, 1.5, .5, 1.5, .1);
                return true;
            }
            case FREEZE -> {
                for (LivingEntity le : nearby(p, 5)) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (pw * 20), 2));
                }
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 80, 2, .5, 2, .05);
                return true;
            }
            case GUARD -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, Math.max(0, lv - 1)));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 40, .6, .8, .6, .1);
                return true;
            }
            case REFLECT -> {
                for (LivingEntity le : nearby(p, 5)) {
                    Vector push = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0.3);
                    if (push.lengthSquared() > 0) le.setVelocity(push.normalize().multiply(1.2));
                    le.damage(2 + lv);
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 40, 1.5, .3, 1.5, .1);
                return true;
            }
            case REGEN -> {
                heal(p, pw * 2);
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 10, .5, .3, .5);
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String prefix = e.getPlayer().getUniqueId() + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
