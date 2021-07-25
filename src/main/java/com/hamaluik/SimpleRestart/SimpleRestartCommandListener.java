package com.hamaluik.SimpleRestart;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SimpleRestartCommandListener implements CommandExecutor {
	private static final String noPermissionMessage = ChatColor.RED + "You don't have permission to do that!";
	
	private final SimpleRestart plugin;
	
	public SimpleRestartCommandListener(SimpleRestart instance) {
		plugin = instance;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		//Since this is only one command handler, the label needs to be normalized.
		// It may contain the plugin as prefix, which has to be removed.
		{
			int colonIndex = label.lastIndexOf(':');
			if(colonIndex > -1)	{
				label = label.substring(colonIndex + 1);
			}
			label = label.toLowerCase();
		}
		
		if(label.equals("restart") || label.equals("reboot")) {
			if(args.length == 1 && args[0].equalsIgnoreCase("now")) {
				if(checkIfHasNoPermission(sender, "restart")) {
					return true;
				}
				
				// restarting NOW
				sendFeedback(sender, ChatColor.RED + "Ok, you asked for it!");
				plugin.stopServer();
				return true;
			}
			else if(args.length == 1 && args[0].equalsIgnoreCase("time")) {
				// report the amount of time before the next restart
				if(checkIfHasNoPermission(sender, "time")) {
					return true;
				}
				
				if(!plugin.autoRestart) {
					// make sure there IS an auto-restart
					sendFeedback(sender, ChatColor.RED + "There is no auto-restart scheduled!");
					return true;
				}
				
				// ok, now see how long is left!
				// (in seconds)
				double timeLeft = (plugin.restartInterval * 3600) - ((double)(System.currentTimeMillis() - plugin.startTimestamp) / 1000);
				int hours = (int)(timeLeft / 3600);
				int minutes = (int)((timeLeft - hours * 3600) / 60);
				int seconds = (int)timeLeft % 60;
				
				sendFeedback(sender, ChatColor.AQUA + "The server will be restarting in " + ChatColor.WHITE + hours + "h" + minutes + "m" + seconds + "s");
				
				return true;
			}
			else if(args.length == 1 && args[0].equalsIgnoreCase("help")) {
				if(checkIfHasNoPermission(sender, "restart")) {
					return true;
				}
				
				// show help!
				showHelp(sender);
				return true;
			}
			else if(args.length == 1 && args[0].equalsIgnoreCase("on")) {
				if(checkIfHasNoPermission(sender, "restart")) {
					return true;
				}
				
				// only if we're not already auto-restarting
				if(plugin.autoRestart) {
					sendFeedback(sender, ChatColor.RED + "The server was already automatically restarting!");
					return true;
				}
				
				// turn auto-restarts back on..
				plugin.autoRestart = true;
				plugin.getLogger().info("Reloading configuration..");
				plugin.loadConfiguration();
				plugin.getLogger().info("Scheduling restart tasks...");
				plugin.scheduleTasks();
				
				// and inform!
				sendFeedback(sender, ChatColor.AQUA + "Automatic restarts have been turned on!");
				plugin.getLogger().info(sender.getName() + " turned automatic restarts on!");
				
				// ok, now see how long is left!
				// (in seconds)
				double timeLeft = (plugin.restartInterval * 3600) - ((double)(System.currentTimeMillis() - plugin.startTimestamp) / 1000);
				int hours = (int)(timeLeft / 3600);
				int minutes = (int)((timeLeft - hours * 3600) / 60);
				int seconds = (int)timeLeft % 60;
				
				sendFeedback(sender, ChatColor.AQUA + "The server will be restarting in " + ChatColor.WHITE + hours + "h" + minutes + "m" + seconds + "s");
				
				return true;
			}
			else if(args.length == 1 && args[0].equalsIgnoreCase("off")) {
				if(checkIfHasNoPermission(sender, "restart")) {
					return true;
				}
				
				// only if we're not already auto-restarting
				if(!plugin.autoRestart) {
					sendFeedback(sender, ChatColor.RED + "The server already wasn't automatically restarting!");
					return true;
				}
				
				// ok, cancel all the tasks associated with this plugin!
				plugin.cancelTasks();
				
				// and inform!
				sendFeedback(sender, ChatColor.AQUA + "Automatic restarts have been turned off!");
				plugin.getLogger().info(sender.getName() + " turned automatic restarts off!");
				
				return true;
			}
			else if(args.length == 2) {
				// restarting in a set time
				// note: doing it this way DOES NOT give a restart warning
				if(checkIfHasNoPermission(sender, "restart")) {
					return true;
				}
				
				String timeFormat = args[0];
				double timeAmount;
				try {
					 timeAmount = Double.parseDouble(args[1]);
				}
				catch(Exception e) {
					sendFeedback(sender, ChatColor.RED + "Bad time!");
					return true;
				}
				
				// "parse" the restart time
				double restartTime; // in seconds
				if(timeFormat.equalsIgnoreCase("h")) {
					restartTime = timeAmount * 3600;
				}
				else if(timeFormat.equalsIgnoreCase("m")) {
					restartTime = timeAmount * 60;
				}
				else if(timeFormat.equalsIgnoreCase("s")) {
					restartTime = timeAmount;
				}
				else {
					sendFeedback(sender, ChatColor.RED + "Invalid time scale!");
					sendFeedback(sender, ChatColor.AQUA + "Use 'h' for time in hours, etc");
					return true;
				}
				
				// log to console
				plugin.getLogger().info(sender.getName() + " is setting a new restart time...");
				
				// ok, we have the proper time
				// if the scheduler is already going, cancel it!
				if(plugin.autoRestart) {
					// ok, cancel all the tasks associated with this plugin!
					plugin.cancelTasks();
				}
				
				// and set the restart interval for /restart time
				plugin.restartInterval = restartTime / 3600.0;
				
				// now, start it up again!
				plugin.getLogger().info("Scheduling restart tasks...");
				plugin.scheduleTasks();
				
				// and inform!
				double timeLeft = (plugin.restartInterval * 3600) - ((double)(System.currentTimeMillis() - plugin.startTimestamp) / 1000);
				int hours = (int)(timeLeft / 3600);
				int minutes = (int)((timeLeft - hours * 3600) / 60);
				int seconds = (int)timeLeft % 60;
				
				sendFeedback(sender, ChatColor.AQUA + "The server will now be restarting in " + ChatColor.WHITE + hours + "h" + minutes + "m" + seconds + "s");
				
				return true;
			}
		}
		else if(label.equals("memory")) {
			if(checkIfHasNoPermission(sender, "memory")) {
				return true;
			}
			
			// Show memory usage:
			float freeMemory = (float)java.lang.Runtime.getRuntime().freeMemory() / (1024F * 1024F);
			float totalMemory = (float)java.lang.Runtime.getRuntime().totalMemory() / (1024F * 1024F);
			float maxMemory = (float)java.lang.Runtime.getRuntime().maxMemory() / (1024F * 1024F);
			
			sendFeedback(sender, ChatColor.RED + "Free memory: " + ChatColor.WHITE + String.format("%.1f", freeMemory) + "MB");
			sendFeedback(sender, ChatColor.RED + "Total memory: " + ChatColor.WHITE + String.format("%.1f", totalMemory) + "MB");
			sendFeedback(sender, ChatColor.RED + "Max memory: " + ChatColor.WHITE + String.format("%.1f", maxMemory) + "MB");
			
			return true;
		}
		
		// Assume the restart command failed and print the help for that one:
		sendFeedback(sender, ChatColor.RED + "Invalid command usage!");
		showHelp(sender);
		return true;
	}
	
	private static void sendFeedback(CommandSender sender, String message) {
		if(sender instanceof Player) {
			sender.sendMessage(message);
		} else {
			sender.sendMessage(ChatColor.stripColor(message));
		}
	}
	
	private boolean checkIfHasNoPermission(CommandSender sender, String permission)
	{
		// Check any type of sender, not only players.
		if(sender.hasPermission("simplerestart."  + permission)) {
			return false; // Return false, to not abort the command execution.
		}
		sendFeedback(sender, noPermissionMessage);
		return true; // True aborts the command execution.
	}
	
	private void showHelp(CommandSender sender) {
		StringBuilder builder = new StringBuilder();
		// Title:
		builder.append(ChatColor.WHITE + "--- " + ChatColor.DARK_AQUA + "Restart " + ChatColor.AQUA + "Help " + ChatColor.WHITE + "---");
		// Subcommands:
		LinkedHashMap<String, String> helpList = new LinkedHashMap<>(); // Format: Command => Description.
		helpList.put(ChatColor.DARK_AQUA + "/restart " + ChatColor.AQUA + "help", ChatColor.GRAY + "shows this help");
		helpList.put(ChatColor.DARK_AQUA + "/restart " + ChatColor.AQUA + "now", ChatColor.GRAY + "restarts the server NOW");
		helpList.put(ChatColor.DARK_AQUA + "/restart " + ChatColor.AQUA + "time", ChatColor.GRAY + "informs you how much time is left before restarting");
		helpList.put(ChatColor.DARK_AQUA + "/restart " + ChatColor.GRAY + "(" + ChatColor.AQUA + "h" + ChatColor.GRAY + "|" + ChatColor.AQUA + "m" + ChatColor.GRAY + "|" + ChatColor.AQUA + "s" + ChatColor.GRAY + ") " + ChatColor.WHITE + "<time>", ChatColor.GRAY + "restarts the server after a given amount of time");
		helpList.put(ChatColor.DARK_AQUA + "/restart " + ChatColor.AQUA + "on", ChatColor.GRAY + "turns auto-restarts on");
		helpList.put(ChatColor.DARK_AQUA + "/restart " + ChatColor.AQUA + "off", ChatColor.GRAY + "turns auto-restarts off");
		for(Entry<String, String> entry : helpList.entrySet()) {
			builder.append('\n').append(entry.getKey()).append("\n     ").append(entry.getValue());
		}
		
		// Send the help message:
		if(sender instanceof Player) {
			sender.sendMessage(builder.toString());
		} else {
			sender.sendMessage(ChatColor.stripColor(builder.toString()));
		}
	}
}
