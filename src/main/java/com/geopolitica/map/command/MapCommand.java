package com.geopolitica.map.command;

import com.geopolitica.map.GeopoliticaMapPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class MapCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.GOLD + "[GeopoliticaMap] " + ChatColor.RESET;

    private final GeopoliticaMapPlugin plugin;

    public MapCommand(GeopoliticaMapPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> sendStatus(sender);
            case "resync" -> {
                if (!requirePermission(sender)) {
                    return true;
                }
                plugin.getMapManager().resyncAll();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Resynced every claim marker.");
            }
            case "reload" -> {
                if (!requirePermission(sender)) {
                    return true;
                }
                plugin.reload();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded and map integrations re-detected.");
            }
            default -> sender.sendMessage(PREFIX + "Usage: /gmap <status|resync|reload>");
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        List<String> active = plugin.getMapManager().activeProviderNames();
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Connected map plugins: "
                + (active.isEmpty() ? ChatColor.RED + "none" : ChatColor.WHITE + String.join(", ", active)));
        if (active.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Install Dynmap, BlueMap, squaremap or Pl3xMap and run /gmap reload.");
        }
    }

    private boolean requirePermission(CommandSender sender) {
        if (!sender.hasPermission("geopolitica.map.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You do not have permission to do that.");
            return false;
        }
        return true;
    }
}
