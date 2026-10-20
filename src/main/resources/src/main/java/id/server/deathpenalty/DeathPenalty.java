
package id.server.deathpenalty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class DeathPenalty extends JavaPlugin implements Listener {

    private static final long DURATION = 15 * 60 * 1000L;
    private static final long EFFECT_DURATION = 100L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasPenalty(player.getUniqueId())) {
                    applyEffects(player);
                } else {
                    removeEffects(player);
                }
            }
        }, 1L, 40L);
    }

    private String key(UUID uuid) {
        return "penalties." + uuid;
    }

    private boolean hasPenalty(UUID uuid) {
        long expiry = getConfig().getLong(key(uuid), 0L);
        return expiry > System.currentTimeMillis();
    }

    private void startPenalty(Player player) {
        getConfig().set(key(player.getUniqueId()),
                System.currentTimeMillis() + DURATION);
        saveConfig();
        applyEffects(player);
    }

    private void applyEffects(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.DARKNESS, (int) EFFECT_DURATION, 0, true, false, true));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS, (int) EFFECT_DURATION, 4, true, false, true));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, (int) EFFECT_DURATION, 4, true, false, true));
    }

    private void removeEffects(Player player) {
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        getConfig().set("pending." + event.getEntity().getUniqueId(), true);
        saveConfig();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String pendingKey = "pending." + player.getUniqueId();

            if (getConfig().getBoolean(pendingKey, false)) {
                getConfig().set(pendingKey, null);
                startPenalty(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String pendingKey = "pending." + player.getUniqueId();

            if (getConfig().getBoolean(pendingKey, false)) {
                getConfig().set(pendingKey, null);
                startPenalty(player);
            } else if (hasPenalty(player.getUniqueId())) {
                applyEffects(player);
            }
            saveConfig();
        }, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("penalty")) {
            return false;
        }

        if (!sender.hasPermission("deathpenalty.admin")) {
            sender.sendMessage("§cKamu tidak punya izin untuk perintah ini.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§eGunakan: /penalty clear <player>");
            sender.sendMessage("§eGunakan: /penalty check <player>");
            return true;
        }

        String action = args[0].toLowerCase();
        Player online = Bukkit.getPlayerExact(args[1]);
        OfflinePlayer target = online != null
                ? online : Bukkit.getOfflinePlayer(args[1]);
        UUID uuid = target.getUniqueId();

        if (action.equals("clear")) {
            getConfig().set(key(uuid), null);
            getConfig().set("pending." + uuid, null);
            saveConfig();

            if (online != null) {
                removeEffects(online);
            }

            sender.sendMessage("§aHukuman " + target.getName()
                    + " berhasil dihapus.");
            return true;
        }

        if (action.equals("check")) {
            long remaining = getConfig().getLong(key(uuid), 0L)
                    - System.currentTimeMillis();

            if (remaining <= 0L) {
                sender.sendMessage("§ePemain tersebut tidak memiliki hukuman aktif.");
            } else {
                long seconds = (remaining + 999L) / 1000L;
                long minutes = seconds / 60L;
                seconds %= 60L;
                sender.sendMessage("§eSisa hukuman " + target.getName()
                        + ": " + minutes + " menit " + seconds + " detik.");
            }
            return true;
        }

        sender.sendMessage("§cGunakan: /penalty clear <player> atau /penalty check <player>");
        return true;
    }
  }
