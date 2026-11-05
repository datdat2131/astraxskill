package vn.astrax.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

/** Âm thanh bổ sung cho kiếm/giáp VIP. Bật/tắt trong config.yml mục "sounds". */
public final class Sfx {
    private Sfx() {}

    private static FileConfiguration cfg() { return AstraxSkill.get().getConfig(); }

    private static boolean on(String key) {
        return cfg().getBoolean("sounds.enabled", true) && cfg().getBoolean("sounds." + key, true);
    }

    private static void play(Location l, Sound s, double vol, double pitch) {
        if (l == null || l.getWorld() == null) return;
        float v = (float) (vol * cfg().getDouble("sounds.volume", 1.0));
        l.getWorld().playSound(l, s, v, (float) pitch);
    }

    /** Tiếng "leng keng" kim loại khi kiếm chém trúng. */
    public static void clang(Location l, boolean crit) {
        if (!on("sword-clang")) return;
        final Location at = l.clone();
        play(at, Sound.ITEM_SHIELD_BLOCK, 0.5, crit ? 1.3 : 1.7);              // "keng"
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7, crit ? 1.5 : 1.9);     // "leng"
        Bukkit.getScheduler().runTaskLater(AstraxSkill.get(),
                () -> play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3, 2.0), 3L); // dư âm ngân
        if (crit) play(at, Sound.BLOCK_ANVIL_LAND, 0.2, 1.8);
    }

    /** Tiếng "bịch" xung lực khi đòn chém Exodus đẩy lùi mục tiêu. */
    public static void impact(Location l, boolean crit) {
        if (!on("impact")) return;
        play(l, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, crit ? 1.0 : 0.7, crit ? 0.8 : 1.0);
        play(l, Sound.ENTITY_IRON_GOLEM_ATTACK, crit ? 0.7 : 0.45, crit ? 0.8 : 1.1);
    }

    /** Lớp âm thanh bổ sung cho từng kỹ năng. */
    public static void skill(Location l, String key) {
        if (!on("skills")) return;
        switch (key) {
            case "dragon-slash" -> {
                play(l, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8, 1.3);
                play(l, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.6, 1.4);
            }
            case "thunder" -> {
                play(l, Sound.ITEM_TRIDENT_THUNDER, 0.7, 1.4);
                play(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6, 2.0);
            }
            case "core" -> play(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8, 0.7);
            case "blood" -> {
                play(l, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6, 1.5);
                play(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6, 0.6);
            }
            case "overlord" -> {
                play(l, Sound.BLOCK_BEACON_POWER_SELECT, 1.0, 0.6);
                play(l, Sound.ENTITY_WITHER_SPAWN, 0.3, 1.8);
            }
            case "apocalypse" -> play(l, Sound.ENTITY_WITHER_SPAWN, 0.5, 1.2);
            case "resonance" -> {
                play(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8, 1.2);
                play(l, Sound.ITEM_SHIELD_BLOCK, 0.3, 1.6);
            }
            case "shield" -> {
                play(l, Sound.ITEM_SHIELD_BLOCK, 0.8, 1.2);
                play(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6, 1.6);
            }
            case "overdrive" -> play(l, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7, 0.8);
            case "absorb" -> play(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7, 1.4);
            case "ultimate" -> {
                play(l, Sound.BLOCK_BEACON_POWER_SELECT, 1.0, 0.8);
                play(l, Sound.ENTITY_WITHER_SPAWN, 0.3, 1.6);
            }
            default -> {}
        }
    }
}
