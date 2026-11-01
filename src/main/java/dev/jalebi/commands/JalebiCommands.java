package dev.jalebi.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class JalebiCommands extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<String> allowed = new HashSet<>();
    private boolean includeAliases;
    private String invalidMessage;
    private SimpleCommandMap commandMap;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        resolveCommandMap();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("jalebicommands").setExecutor(this);
        refreshPlayers();
        getLogger().info("Jalebi Commands enabled with " + allowed.size() + " allowed commands.");
    }

    @Override
    public void onDisable() {
        // Restore the normal command list for everyone
        allowed.clear();
        refreshPlayers();
    }

    // ------------------------------------------------------------------
    // Setup helpers
    // ------------------------------------------------------------------

    private void resolveCommandMap() {
        try {
            Object server = Bukkit.getServer();
            commandMap = (SimpleCommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
        } catch (Exception e) {
            commandMap = null;
            getLogger().warning("Could not access the command map; alias support is disabled.");
        }
    }

    private void loadSettings() {
        reloadConfig();
        allowed.clear();
        for (String s : getConfig().getStringList("allowed-commands")) {
            String n = normalize(s);
            if (!n.isEmpty()) allowed.add(n);
        }
        includeAliases = getConfig().getBoolean("include-aliases", true);
        invalidMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("invalid-command-message", "&cInvalid command."));
    }

    private void refreshPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.updateCommands();
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        s = s.trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Core checks
    // ------------------------------------------------------------------

    private boolean isExempt(Player p) {
        return p.isOp() || p.hasPermission("jalebicommands.bypass");
    }

    private boolean isAllowed(String label) {
        label = normalize(label);
        if (label.isEmpty()) return false;
        if (allowed.contains(label)) return true;

        if (includeAliases && commandMap != null) {
            Command cmd = commandMap.getCommand(label);
            if (cmd != null) {
                if (allowed.contains(cmd.getName().toLowerCase(Locale.ROOT))) return true;
                for (String alias : cmd.getAliases()) {
                    if (allowed.contains(alias.toLowerCase(Locale.ROOT))) return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /** Block commands that aren't on the list. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (isExempt(p)) return;

        String body = e.getMessage().trim();
        if (body.startsWith("/")) body = body.substring(1).trim();
        String label = body.split("\\s+", 2)[0];

        if (!isAllowed(label)) {
            e.setCancelled(true);
            p.sendMessage(invalidMessage);
        }
    }

    /** Hide disallowed commands from the client's command list (tab complete). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent e) {
        if (isExempt(e.getPlayer())) return;
        e.getCommands().removeIf(name -> !isAllowed(name));
    }

    /** Extra safety for servers/clients that use server-side tab completion. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTab(TabCompleteEvent e) {
        if (!(e.getSender() instanceof Player)) return;
        Player p = (Player) e.getSender();
        if (isExempt(p)) return;

        String buffer = e.getBuffer();
        if (!buffer.startsWith("/")) return; // normal chat completion

        String body = buffer.substring(1);
        int space = body.indexOf(' ');

        if (space == -1) {
            // Still typing the command name: only suggest allowed ones
            List<String> filtered = new ArrayList<>();
            for (String c : e.getCompletions()) {
                if (isAllowed(c)) filtered.add(c);
            }
            e.setCompletions(filtered);
        } else if (!isAllowed(body.substring(0, space))) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // /jalebicommands
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <reload|list|add|remove> [command]");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload":
                loadSettings();
                refreshPlayers();
                sender.sendMessage(ChatColor.GREEN + "Jalebi Commands reloaded (" + allowed.size() + " commands).");
                break;

            case "list":
                sender.sendMessage(ChatColor.GOLD + "Allowed commands (" + allowed.size() + "): "
                        + ChatColor.WHITE + String.join(", ", new java.util.TreeSet<>(allowed)));
                break;

            case "add":
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " <command>");
                    break;
                }
                String name = normalize(args[1]);
                List<String> list = new ArrayList<>(getConfig().getStringList("allowed-commands"));
                list.removeIf(s -> normalize(s).equals(name));
                boolean adding = args[0].equalsIgnoreCase("add");
                if (adding) list.add(name);
                getConfig().set("allowed-commands", list);
                saveConfig();
                loadSettings();
                refreshPlayers();
                sender.sendMessage(ChatColor.GREEN + (adding ? "Added " : "Removed ") + "/" + name + ".");
                break;

            default:
                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <reload|list|add|remove> [command]");
        }
        return true;
    }
}
