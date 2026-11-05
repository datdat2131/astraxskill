package vn.astrax.skill;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AstraxSkill extends JavaPlugin {
    private static AstraxSkill instance;
    private SetManager sets;

    public static AstraxSkill get() {
        return instance;
    }

    public SetManager sets() {
        return sets;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ItemUtil.init(this);
        sets = new SetManager(this);

        getServer().getPluginManager().registerEvents(new SkillListener(this), this);
        AstraxCommand cmd = new AstraxCommand(this);
        getCommand("astrax").setExecutor(cmd);
        getCommand("astrax").setTabCompleter(cmd);

        // Cập nhật hiệu ứng set bonus mỗi 2 giây
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sets.compute(p, true);
        }, 40L, 40L);

        getLogger().info("AstraxSkill da bat!");
    }
}
