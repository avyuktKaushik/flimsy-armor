package com.flimsyarmor;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlimsyArmorPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private boolean active = false;
    private long endMillis;
    private long totalSeconds;
    private BukkitTask task;
    private BossBar bar;
    private final Set<Integer> firedReminders = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("startflimsyarmor").setExecutor(this);
        getCommand("startflimsyarmor").setTabCompleter(this);
        getCommand("stopflimsyarmor").setExecutor(this);
        getCommand("stopflimsyarmor").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        endEvent(false, false);
    }

    // ---------------- Command handling ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("stopflimsyarmor")) {
            if (!active) {
                sender.sendMessage(msg("messages.not-running", ""));
                return true;
            }
            endEvent(true, true);
            return true;
        }

        // /startflimsyarmor [minutes]
        if (active) {
            sender.sendMessage(msg("messages.already-running", ""));
            return true;
        }

        reloadConfig();
        int max = Math.max(1, getConfig().getInt("max-duration-minutes", 180));
        int minutes = getConfig().getInt("default-duration-minutes", 15);

        if (args.length > 0) {
            String raw = args[0].toLowerCase().replace("m", "");
            try {
                minutes = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                sender.sendMessage(msg("messages.invalid-time", "").replace("{max}", String.valueOf(max)));
                return true;
            }
            if (minutes < 1 || minutes > max) {
                sender.sendMessage(msg("messages.invalid-time", "").replace("{max}", String.valueOf(max)));
                return true;
            }
        }

        startEvent(minutes * 60L);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("startflimsyarmor") && args.length == 1) {
            for (String s : new String[]{"15", "30", "45", "60"}) {
                if (s.startsWith(args[0])) out.add(s);
            }
        }
        return out;
    }

    // ---------------- Event logic ----------------

    private void startEvent(long seconds) {
        active = true;
        totalSeconds = seconds;
        endMillis = System.currentTimeMillis() + seconds * 1000L;
        firedReminders.clear();

        broadcastLines("messages.start", formatTime(seconds));

        if (getConfig().getBoolean("bossbar.enabled", true)) {
            BossBar.Color color = parseEnum(BossBar.Color.class, getConfig().getString("bossbar.color"), BossBar.Color.RED);
            BossBar.Overlay style = parseEnum(BossBar.Overlay.class, getConfig().getString("bossbar.style"), BossBar.Overlay.PROGRESS);
            bar = BossBar.bossBar(bossTitle(seconds), 1.0f, color, style);
            Bukkit.getServer().showBossBar(bar);
        }

        task = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    private void tick() {
        long remaining = (long) Math.ceil((endMillis - System.currentTimeMillis()) / 1000.0);
        if (remaining <= 0) {
            endEvent(true, false);
            return;
        }

        if (bar != null) {
            bar.name(bossTitle(remaining));
            bar.progress(Math.max(0f, Math.min(1f, (float) remaining / (float) totalSeconds)));
        }

        for (int r : getConfig().getIntegerList("reminders-seconds")) {
            if (remaining <= r && r < totalSeconds && firedReminders.add(r)) {
                Bukkit.getServer().sendMessage(
                        LEGACY.deserialize(msg("messages.reminder", "").replace("{time}", formatTime(r))));
                break;
            }
        }
    }

    private void endEvent(boolean announce, boolean early) {
        if (!active) return;
        active = false;

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            Bukkit.getServer().hideBossBar(bar);
            bar = null;
        }
        if (announce) {
            broadcastLines(early ? "messages.stopped-early" : "messages.end", "");
        }
    }

    /**
     * PlayerItemDamageEvent#getDamage is the amount AFTER Unbreaking is applied;
     * getOriginalDamage is the amount BEFORE. Restoring the original amount makes
     * Unbreaking do nothing while the event is running.
     */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (!active) return;
        if (!isArmor(e.getItem().getType())) return;
        e.setDamage(Math.max(e.getDamage(), e.getOriginalDamage()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (active && bar != null) {
            e.getPlayer().showBossBar(bar);
        }
    }

    // ---------------- Helpers ----------------

    private boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private Component bossTitle(long seconds) {
        String t = getConfig().getString("bossbar.title", "&c&lFLIMSY ARMOR &7- &f{time} left");
        return LEGACY.deserialize(t.replace("{time}", formatTime(seconds)));
    }

    private String msg(String path, String time) {
        return getConfig().getString(path, "").replace("{time}", time);
    }

    private void broadcastLines(String path, String time) {
        List<String> lines = getConfig().isList(path)
                ? getConfig().getStringList(path)
                : List.of(getConfig().getString(path, ""));
        for (String line : lines) {
            Bukkit.getServer().sendMessage(LEGACY.deserialize(line.replace("{time}", time)));
        }
    }

    private static String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (m > 0) sb.append(m).append(m == 1 ? " minute" : " minutes");
        if (s > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(s).append(s == 1 ? " second" : " seconds");
        }
        return sb.toString();
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
