package vn.astrax.skill;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Kiếm huyền thoại ASTRAX - HẮC LONG (3 cấp tiến hóa)
 * Long Trảm (chuột phải) | Hắc Long Giáng Thế (phím F) | Long Huyết (nội tại) | Long Nộ (tích năng lượng)
 */
public class HacLong implements Listener {
    private final AstraxSkill pl;
    private final NamespacedKey TIER, STONE;
    private final Map<String, Long> cds = new HashMap<>();
    private final Map<UUID, Integer> charge = new HashMap<>();
    private final Map<UUID, Long> rage = new HashMap<>();
    private final Random rnd = new Random();
    private boolean busy = false;

    public HacLong(AstraxSkill pl) {
        this.pl = pl;
        this.TIER = new NamespacedKey(pl, "hl_tier");
        this.STONE = new NamespacedKey(pl, "hl_stone");
    }

    // ================= helpers =================
    private FileConfiguration cfg() { return pl.getConfig(); }
    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private boolean pvp() { return cfg().getBoolean("pvp", true); }

    private double tierVal(String path, int tier, double def) {
        List<Double> l = cfg().getDoubleList("hac-long.damage." + path);
        if (l.isEmpty()) return def;
        return l.get(Math.min(l.size(), Math.max(1, tier)) - 1);
    }

    private void bar(Player p, String s) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(c(s)));
    }

    public int tier(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer t = it.getItemMeta().getPersistentDataContainer().get(TIER, PersistentDataType.INTEGER);
        return t == null ? 0 : t;
    }

    private boolean isStone(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(STONE, PersistentDataType.BYTE);
    }

    private long cdLeft(Player p, String k, double sec) {
        String key = p.getUniqueId() + ":" + k;
        long now = System.currentTimeMillis();
        Long until = cds.get(key);
        if (until != null && now < until) return (until - now + 999) / 1000;
        cds.put(key, now + (long) (sec * 1000));
        return 0;
    }

    private boolean isRaging(Player p) {
        Long u = rage.get(p.getUniqueId());
        return u != null && System.currentTimeMillis() < u;
    }

    private void fx(World w, Particle p, Location l, int n, double dx, double dy, double dz, double sp) {
        try { w.spawnParticle(p, l, n, dx, dy, dz, sp); } catch (Exception ignored) {}
    }

    private void dust(World w, Location l, Color col, float size) {
        try { w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(col, size)); }
        catch (Exception ignored) {}
    }

    private Location lerp(Location a, Location b, double q) {
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * q,
                a.getY() + (b.getY() - a.getY()) * q,
                a.getZ() + (b.getZ() - a.getZ()) * q);
    }

    private Vector flat(Player p) {
        Vector f = p.getLocation().getDirection().setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(0, 0, 1);
        return f.normalize();
    }

    /** Sát thương có giới hạn: tôn trọng giáp, giảm khi đánh người, không vượt max-damage. */
    private void hurt(Player p, LivingEntity v, double amt) {
        if (v instanceof Player) {
            if (!pvp()) return;
            amt *= cfg().getDouble("hac-long.pvp-multiplier", 0.6);
        }
        amt = Math.min(amt, cfg().getDouble("hac-long.max-damage", 12));
        busy = true;
        try { v.damage(amt, p); } finally { busy = false; }
    }

    private boolean validTarget(Player p, Entity en) {
        return en instanceof LivingEntity && en != p && !(en instanceof ArmorStand)
                && !(en instanceof Player && !pvp());
    }

    // ================= tạo / nâng cấp item =================
    private String tierLabel(int t) {
        return switch (t) { case 1 -> "I • Hắc Long"; case 2 -> "II • Thức Tỉnh"; default -> "III • Thần Diệt"; };
    }

    public ItemStack build(int tier) {
        Material m = Material.matchMaterial(cfg().getString("hac-long.material", "NETHERITE_SWORD"));
        if (m == null) m = Material.NETHERITE_SWORD;
        ItemStack it = new ItemStack(m);
        apply(it, tier, true);
        return it;
    }

    /** Ép "linh hồn Hắc Long" vào một thanh kiếm bất kỳ. */
    @SuppressWarnings("deprecation")
    public void apply(ItemStack it, int tier, boolean withEnchants) {
        tier = Math.max(1, Math.min(3, tier));
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(c(cfg().getString("hac-long.names." + tier, "&4&l☠ HẮC LONG")));
        List<String> lore = new ArrayList<>();
        for (String l : cfg().getStringList("hac-long.lore")) {
            lore.add(c(l.replace("{tier}", String.valueOf(tier)).replace("{tier_name}", tierLabel(tier))));
        }
        m.setLore(lore);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        m.setEnchantmentGlintOverride(tier >= 2 ? Boolean.TRUE : null);
        m.getPersistentDataContainer().set(TIER, PersistentDataType.INTEGER, tier);
        if (withEnchants) {
            for (String en : cfg().getStringList("hac-long.enchants")) {
                String[] x = en.split(":");
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(x[0].toLowerCase()));
                if (e == null) continue;
                int lv = 1;
                try { lv = Integer.parseInt(x[1]); } catch (Exception ignored) {}
                m.addEnchant(e, lv, true);
            }
        }
        it.setItemMeta(m);
        setModel(it, tier, false);
        ItemUtil.refreshLore(it);
    }

    /** CustomModelData cho 3 cấp + trạng thái Long Nộ (0 = giữ nguyên). Dùng cho resource pack / Oraxen. */
    @SuppressWarnings("deprecation")
    private void setModel(ItemStack it, int tier, boolean raging) {
        int base = cfg().getInt("hac-long.model-data.tier" + tier, 0);
        int rg = cfg().getInt("hac-long.model-data.rage" + tier, 0);
        if (base == 0 && rg == 0) return;
        int id = raging && rg != 0 ? rg : base;
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(id == 0 ? null : id);
        it.setItemMeta(m);
    }

    public ItemStack stone(int amount) {
        Material m = Material.matchMaterial(cfg().getString("hac-long.stone.material", "NETHER_STAR"));
        if (m == null) m = Material.NETHER_STAR;
        ItemStack it = new ItemStack(m, amount);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(c(cfg().getString("hac-long.stone.name", "&c&l✦ Đá Nâng Cấp Hắc Long")));
        List<String> lore = new ArrayList<>();
        for (String l : cfg().getStringList("hac-long.stone.lore")) lore.add(c(l));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(STONE, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    // ================= nâng cấp bằng cách kéo đá vào kiếm =================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack cur = e.getCurrentItem();
        if (!isStone(cursor) || tier(cur) == 0) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);

        int tier = tier(cur);
        if (tier >= 3) { bar(p, "&cKiếm đã đạt cấp tối đa!"); return; }
        int cost = cfg().getInt("hac-long.upgrade-cost." + tier, 1);
        if (cursor.getAmount() < cost) { bar(p, "&cCần " + cost + " Đá Nâng Cấp để lên cấp " + (tier + 1)); return; }

        int left = cursor.getAmount() - cost;
        ItemStack rest = cursor.clone();
        rest.setAmount(left);
        p.setItemOnCursor(left <= 0 ? null : rest);

        apply(cur, tier + 1, false);
        e.setCurrentItem(cur);

        Location l = p.getLocation().add(0, 1, 0);
        p.getWorld().playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        fx(p.getWorld(), Particle.FLAME, l, 80, 0.6, 0.8, 0.6, 0.08);
        p.sendMessage(c("&4&l☠ &cKiếm tiến hóa lên &e" + tierLabel(tier + 1) + "&c!"));
        if (tier + 1 == 3) {
            Bukkit.broadcastMessage(c("&4&l☠ &c" + p.getName() + " &7vừa đánh thức &4&lHẮC LONG THẦN DIỆT&7!"));
        }
    }

    // ================= đánh thường: móng vuốt rồng + Long Huyết + Long Nộ =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (busy || e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity v)) return;
        int tier = tier(p.getInventory().getItemInMainHand());
        if (tier == 0) return;

        if (isRaging(p)) e.setDamage(e.getDamage() * (1 + cfg().getDouble("hac-long.rage.damage-bonus", 0.15)));

        claw(v.getLocation().add(0, 1, 0), p.getLocation().getDirection());
        p.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);

        // Long Huyết (nội tại)
        double chance = cfg().getDouble("hac-long.long-huyet.chance", 12);
        if (rnd.nextDouble() * 100 < chance
                && cdLeft(p, "LH", cfg().getDouble("hac-long.long-huyet.internal-cooldown", 6)) == 0) {
            int ticks = (int) (cfg().getDouble("hac-long.long-huyet.seconds", 4) * 20);
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
            fx(p.getWorld(), Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.7, 0.4, 0.05);
            bar(p, "&c✦ Long Huyết &7kích hoạt!");
        }

        // Long Nộ (tích năng lượng)
        if (!isRaging(p)) {
            int max = cfg().getInt("hac-long.rage.charge-max", 100);
            int now = charge.merge(p.getUniqueId(), cfg().getInt("hac-long.rage.charge-per-hit", 8), Integer::sum);
            if (now >= max) startRage(p, tier);
            else {
                int filled = Math.min(10, now * 10 / max);
                bar(p, "&4☠ Long Nộ &c" + "▮".repeat(filled) + "&8" + "▮".repeat(10 - filled));
            }
        }
    }

    /** 3 vệt móng vuốt rồng chéo trên người mục tiêu. */
    private void claw(Location center, Vector facing) {
        World w = center.getWorld();
        Vector f = facing.clone().setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(0, 0, 1);
        f.normalize();
        Vector right = new Vector(-f.getZ(), 0, f.getX());
        for (int i = -1; i <= 1; i++) {
            for (double t = -1; t <= 1; t += 0.17) {
                Location l = center.clone().add(right.clone().multiply(i * 0.38 + t * 0.45)).add(0, -t * 0.8, 0);
                dust(w, l, Color.fromRGB(220, 0, 0), 1.1f);
                if (Math.abs(t) < 0.1) fx(w, Particle.CRIT, l, 2, 0.05, 0.05, 0.05, 0.05);
            }
        }
        fx(w, Particle.DRAGON_BREATH, center, 6, 0.3, 0.3, 0.3, 0.02);
    }

    // ================= Long Nộ =================
    private void startRage(Player p, int tier) {
        long dur = (long) (cfg().getDouble("hac-long.rage.seconds", 10) * 1000);
        rage.put(p.getUniqueId(), System.currentTimeMillis() + dur);
        charge.put(p.getUniqueId(), 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
        fx(p.getWorld(), Particle.FLAME, p.getLocation().add(0, 1, 0), 120, 0.8, 1, 0.8, 0.12);
        ItemStack h = p.getInventory().getItemInMainHand();
        setModel(h, tier, true);
        p.getInventory().setItemInMainHand(h);
        p.sendTitle(c("&4&l☠ LONG NỘ"), c("&cHắc Long thức tỉnh!"), 5, 30, 10);
    }

    private void endRage(UUID id) {
        rage.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            int t = tier(it);
            if (t > 0) { setModel(it, t, false); inv.setItem(i, it); }
        }
        bar(p, "&7Long Nộ đã tan...");
    }

    // ================= hiệu ứng khi cầm (trạng thái 2) =================
    public void startTasks() {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 4;
                for (UUID id : new ArrayList<>(rage.keySet())) {
                    if (System.currentTimeMillis() >= rage.get(id)) endRage(id);
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    int tier = tier(p.getInventory().getItemInMainHand());
                    if (tier > 0) aura(p, tier, isRaging(p), t);
                }
            }
        }.runTaskTimer(pl, 4L, 4L);
    }

    private void aura(Player p, int tier, boolean raging, int t) {
        World w = p.getWorld();
        Location base = p.getLocation().add(0, 1, 0);
        int n = raging ? 3 : 1;
        for (int i = 0; i < n; i++) {
            double a = t * 0.35 + i * (2 * Math.PI / n);
            Location l = base.clone().add(Math.cos(a) * 0.8, Math.sin(t * 0.2 + i) * 0.5, Math.sin(a) * 0.8);
            fx(w, Particle.FLAME, l, 1, 0.02, 0.02, 0.02, 0.01);
            if (rnd.nextBoolean()) fx(w, Particle.SMOKE, l, 1, 0.05, 0.05, 0.05, 0.01);
        }
        Location tip = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.9)).subtract(0, 0.4, 0);
        dust(w, tip, Color.fromRGB(255, 20, 0), 1.0f);
        if (raging) {
            fx(w, Particle.FLAME, p.getLocation().add(0, 0.1, 0), 3, 0.4, 0.05, 0.4, 0.02);
            dust(w, base.clone().add(rnd.nextGaussian() * 0.6, rnd.nextDouble(), rnd.nextGaussian() * 0.6),
                    Color.fromRGB(15, 0, 0), 1.6f);
        }
    }

    // ================= Long Trảm: chuột phải =================
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (p.isSneaking()) return; // Shift + chuột phải dành cho skill giáp
        int tier = tier(p.getInventory().getItemInMainHand());
        if (tier == 0) return;
        long left = cdLeft(p, "LT", cfg().getDouble("hac-long.cooldowns.long-tram", 10));
        if (left > 0) { bar(p, "&cLong Trảm hồi chiêu: " + left + "s"); return; }
        longTram(p, tier);
    }

    private void longTram(Player p, int tier) {
        World w = p.getWorld();
        final Location origin = p.getEyeLocation().subtract(0, 0.6, 0);
        final Vector d = flat(p);
        final Vector right = new Vector(-d.getZ(), 0, d.getX());
        final double dmg = tierVal("long-tram", tier, 6);
        final int steps = (int) (cfg().getDouble("hac-long.long-tram-range", 10) / 1.25);
        final Set<UUID> hit = new HashSet<>();
        w.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        w.playSound(origin, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        bar(p, "&c✦ Long Trảm!");

        new BukkitRunnable() {
            int s = 0;
            @Override public void run() {
                s++;
                Location c = origin.clone().add(d.clone().multiply(s * 1.25));
                if (s > steps || !c.getBlock().isPassable()) { cancel(); return; }
                for (double wd = -1.8; wd <= 1.8; wd += 0.3) {
                    Location pt = c.clone().add(right.clone().multiply(wd)).add(d.clone().multiply(-0.1 * wd * wd));
                    fx(w, Particle.FLAME, pt, 1, 0, 0.05, 0, 0.01);
                    dust(w, pt.clone().add(0, 0.15, 0), Color.fromRGB(200, 10, 0), 1.3f);
                    dust(w, pt.clone().add(0, -0.15, 0), Color.fromRGB(15, 0, 0), 1.3f);
                }
                for (Entity en : w.getNearbyEntities(c, 1.9, 1.5, 1.9)) {
                    if (!validTarget(p, en) || hit.contains(en.getUniqueId())) continue;
                    LivingEntity le = (LivingEntity) en;
                    hit.add(en.getUniqueId());
                    hurt(p, le, dmg);
                    le.setFireTicks(40);
                    le.setVelocity(d.clone().multiply(0.4).setY(0.2));
                }
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    // ================= Hắc Long Giáng Thế: phím F =================
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        int tier = tier(e.getOffHandItem());
        if (tier == 0) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long left = cdLeft(p, "GT", cfg().getDouble("hac-long.cooldowns.giang-the", 45));
        if (left > 0) { bar(p, "&cHắc Long Giáng Thế hồi chiêu: " + left + "s"); return; }
        giangThe(p, tier);
    }

    private void giangThe(Player p, int tier) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double reach = cfg().getDouble("hac-long.giang-the.reach", 22);
        RayTraceResult r = w.rayTraceBlocks(eye, dir, reach);
        final Location end = (r != null && r.getHitPosition() != null)
                ? r.getHitPosition().toLocation(w)
                : eye.clone().add(dir.clone().multiply(reach));
        final Location start = p.getLocation().add(0, 3.5, 0).subtract(flat(p).multiply(2));
        final double radius = cfg().getDouble("hac-long.giang-the.radius", 4) + (tier >= 3 ? 1 : 0);
        final double dmg = tierVal("giang-the", tier, 8);
        final double scale = tier >= 3 ? 1.4 : 1.0;

        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);
        p.sendTitle("", c("&4&l☠ HẮC LONG GIÁNG THẾ"), 3, 25, 8);

        // Hình bóng rồng sau lưng người chơi (trạng thái 3)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t >= 40) { cancel(); return; }
                drawBackDragon(p, scale, t);
                t += 2;
            }
        }.runTaskTimer(pl, 0L, 2L);

        // Rồng bay tới mục tiêu
        final Vector fwd = end.toVector().subtract(start.toVector());
        final Vector side = fwd.lengthSquared() < 1e-6 ? new Vector(1, 0, 0)
                : new Vector(-fwd.getZ(), 0, fwd.getX()).normalize();
        final int flight = 20;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                double prog = (double) t / flight;
                for (int j = 0; j < 16; j++) {
                    double q = prog - j * 0.03;
                    if (q < 0) break;
                    if (q > 1) q = 1;
                    Location l = lerp(start, end, q)
                            .add(side.clone().multiply(Math.sin(q * 14 + j * 0.4) * 0.7))
                            .add(0, Math.cos(q * 10 + j * 0.3) * 0.5, 0);
                    float size = (float) ((j == 0 ? 2.4 : 1.9 - j * 0.07) * (tier >= 3 ? 1.2 : 1.0));
                    dust(w, l, Color.fromRGB(12, 0, 0), size);
                    if (j % 2 == 0) dust(w, l.clone().add(0, 0.3, 0), Color.fromRGB(200, 10, 0), 1.0f);
                    if (j == 0) {
                        dust(w, l.clone().add(side.clone().multiply(0.25)).add(0, 0.2, 0), Color.fromRGB(255, 40, 0), 1.3f);
                        dust(w, l.clone().subtract(side.clone().multiply(0.25)).add(0, 0.2, 0), Color.fromRGB(255, 40, 0), 1.3f);
                        fx(w, Particle.DRAGON_BREATH, l, 3, 0.2, 0.2, 0.2, 0.02);
                        fx(w, Particle.FLAME, l, 3, 0.2, 0.2, 0.2, 0.03);
                    }
                    if (tier >= 3 && j == 3) {
                        for (int s = -1; s <= 1; s += 2) {
                            dust(w, l.clone().add(side.clone().multiply(1.6 * s)).add(0, 0.5, 0), Color.fromRGB(200, 10, 0), 1.5f);
                            dust(w, l.clone().add(side.clone().multiply(0.8 * s)).add(0, 0.3, 0), Color.fromRGB(12, 0, 0), 1.5f);
                        }
                    }
                }
                if (t >= flight) {
                    impact(p, end, radius, dmg);
                    cancel();
                }
            }
        }.runTaskTimer(pl, 5L, 1L);
    }

    private void impact(Player p, Location at, double radius, double dmg) {
        World w = at.getWorld();
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        w.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.0f);
        fx(w, Particle.FLAME, at, 150, radius / 2, 0.8, radius / 2, 0.15);
        fx(w, Particle.EXPLOSION, at, 3, radius / 3, 0.3, radius / 3, 0);
        fx(w, Particle.DRAGON_BREATH, at, 60, radius / 2, 0.5, radius / 2, 0.05);
        for (Entity en : w.getNearbyEntities(at, radius, radius, radius)) {
            if (!validTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            hurt(p, le, dmg);
            le.setFireTicks(60);
            Vector kb = le.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (kb.lengthSquared() > 1e-6) le.setVelocity(kb.normalize().multiply(0.7).setY(0.35));
        }
    }

    /** Bóng rồng đen đỏ sau lưng người chơi: cánh vỗ + cổ + đầu mắt đỏ. */
    private void drawBackDragon(Player p, double sc, int t) {
        World w = p.getWorld();
        Location base = p.getLocation().add(0, 0.9, 0);
        Vector f = flat(p);
        Vector back = f.clone().multiply(-1);
        Vector right = new Vector(-f.getZ(), 0, f.getX());
        double flap = Math.sin(t * 0.35) * 0.35;
        Color dark = Color.fromRGB(12, 0, 0), red = Color.fromRGB(200, 10, 0), glow = Color.fromRGB(255, 40, 0);

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i <= 10; i++) {
                double u = i / 10.0;
                double lat = side * (0.35 + 2.4 * sc * u);
                double h = 0.5 + (1.6 * Math.sin(u * Math.PI * 0.75) + flap * u * 2) * sc;
                double bk = 0.7 + 0.5 * u * sc;
                Location edge = base.clone().add(right.clone().multiply(lat)).add(back.clone().multiply(bk)).add(0, h, 0);
                dust(w, edge, red, 1.4f);
                if (i >= 3 && i % 3 == 0) { // xương cánh
                    for (double k = 0.25; k < 1; k += 0.25) {
                        Location rb = base.clone().add(right.clone().multiply(lat * k))
                                .add(back.clone().multiply(0.7 + 0.5 * u * sc * k)).add(0, 0.5 + (h - 0.5) * k, 0);
                        dust(w, rb, dark, 1.2f);
                    }
                }
            }
        }
        for (int i = 0; i <= 8; i++) { // cổ
            double u = i / 8.0;
            Location nk = base.clone().add(back.clone().multiply(0.8 + 0.5 * Math.sin(u * Math.PI) * sc))
                    .add(0, 0.6 + 2.2 * u * sc, 0);
            dust(w, nk, dark, 1.8f);
        }
        Location head = base.clone().add(back.clone().multiply(0.7)).add(0, 0.6 + 2.4 * sc, 0);
        dust(w, head, dark, 2.4f);
        dust(w, head.clone().add(right.clone().multiply(0.15)).add(f.clone().multiply(0.2)), glow, 1.0f);
        dust(w, head.clone().subtract(right.clone().multiply(0.15)).add(f.clone().multiply(0.2)), glow, 1.0f);
        fx(w, Particle.FLAME, head.clone().add(f.clone().multiply(0.5)), 2, 0.05, 0.05, 0.05, 0.02);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        charge.remove(id);
        rage.remove(id);
        cds.keySet().removeIf(k -> k.startsWith(id + ":"));
    }

    // ================= lệnh /astrax hac ... =================
    public void command(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(c("&6/astrax hac give <người> [cấp 1-3] &7- tặng Kiếm Hắc Long"));
            s.sendMessage(c("&6/astrax hac apply [cấp] &7- ép Hắc Long vào kiếm đang cầm"));
            s.sendMessage(c("&6/astrax hac tier <1-3> &7- chỉnh cấp kiếm đang cầm"));
            s.sendMessage(c("&6/astrax hac stone <người> [số lượng] &7- tặng Đá Nâng Cấp"));
            return;
        }
        switch (a[1].toLowerCase()) {
            case "give" -> {
                if (a.length < 3) { s.sendMessage(c("&cDùng: /astrax hac give <người> [cấp]")); return; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { s.sendMessage(c("&cNgười chơi không online.")); return; }
                give(t, build(intArg(a, 3, 1, 1, 3)));
                s.sendMessage(c("&aĐã tặng Kiếm Hắc Long cho " + t.getName()));
            }
            case "stone" -> {
                if (a.length < 3) { s.sendMessage(c("&cDùng: /astrax hac stone <người> [số lượng]")); return; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { s.sendMessage(c("&cNgười chơi không online.")); return; }
                int n = intArg(a, 3, 1, 1, 64);
                give(t, stone(n));
                s.sendMessage(c("&aĐã tặng " + n + " Đá Nâng Cấp cho " + t.getName()));
            }
            case "apply", "tier" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Chỉ dùng trong game."); return; }
                ItemStack h = p.getInventory().getItemInMainHand();
                if (!h.getType().name().endsWith("_SWORD")) { s.sendMessage(c("&cHãy cầm một thanh kiếm.")); return; }
                boolean tierCmd = a[1].equalsIgnoreCase("tier");
                int tier = intArg(a, 2, tierCmd ? tier(h) : 1, 1, 3);
                apply(h, tier, false);
                p.getInventory().setItemInMainHand(h);
                s.sendMessage(c("&aĐã ép Hắc Long cấp " + tier + " vào kiếm."));
            }
            default -> s.sendMessage(c("&cLệnh con không tồn tại. Gõ /astrax hac"));
        }
    }

    private int intArg(String[] a, int i, int def, int min, int max) {
        if (a.length <= i) return Math.max(min, Math.min(max, def));
        try { return Math.max(min, Math.min(max, Integer.parseInt(a[i]))); }
        catch (NumberFormatException ex) { return def; }
    }

    private void give(Player t, ItemStack it) {
        for (ItemStack drop : t.getInventory().addItem(it).values()) {
            t.getWorld().dropItemNaturally(t.getLocation(), drop);
        }
    }
}
