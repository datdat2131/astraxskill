package vn.astrax.skill;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AstraxSkill extends JavaPlugin {
    private static AstraxSkill instance;
    private SetManager sets;
    private HacLong hac;
    private CyberDragon cyber;
    private HacLongExodus exodus;

    public static AstraxSkill get() {
        return instance;
    }

    public SetManager sets() {
        return sets;
    }

    public HacLong hac() {
        return hac;
    }

    public CyberDragon cyber() {
        return cyber;
    }

    public HacLongExodus exodus() {
        return exodus;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ItemUtil.init(this);
        sets = new SetManager(this);

        getServer().getPluginManager().registerEvents(new SkillListener(this), this);
        hac = new HacLong(this);
        getServer().getPluginManager().registerEvents(hac, this);
        hac.startTasks();
        cyber = new CyberDragon(this);
        getServer().getPluginManager().registerEvents(cyber, this);
        cyber.startTasks();
        exodus = new HacLongExodus(this);
        getServer().getPluginManager().registerEvents(exodus, this);
        exodus.startTasks();
        AstraxCommand cmd = new AstraxCommand(this);
        getCommand("astrax").setExecutor(cmd);
        getCommand("astrax").setTabCompleter(cmd);

        // Cập nhật hiệu ứng set bonus mỗi 2 giây
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sets.compute(p, true);
        }, 40L, 40L);

        getLogger().info("AstraxSkill da bat!");
    }

    @Override
    public void onDisable() {
        if (cyber != null) cyber.shutdown();
        if (exodus != null) exodus.shutdown();
    }
}
