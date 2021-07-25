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
		
		if(label.equals("memory")) {
			handleMemoryCommand(sender);
		}
		else { //Assume this is the /restart or /reboot command.
			handleRebootCommand(sender, args);
		}
		
		return true;
	}
	
	private void handleRebootCommand(CommandSender sender, String[] args)
	{
		if(!sender.hasPermission("simplerestart.restart")) {
			//No permission for the restart command.
			if(sender.hasPermission("simplerestart.time")) {
				//But has the time permission!
				if(args.length == 1 && args[0].toLowerCase().equals("time")) {
					subCommandTimeUntilRestart(sender);
				}
				else {
					String message = ChatColor.WHITE + "--- " + ChatColor.DARK_AQUA + "Restart " + ChatColor.AQUA + "Help " + ChatColor.WHITE + "---" + "\n"
							+ ChatColor.DARK_AQUA + "/restart " + ChatColor.AQUA + "time" + "\n     " + ChatColor.GRAY + "informs you how much time is left before restarting";
					//Send the (only time) help message:
					if(sender instanceof Player) {
						sender.sendMessage(message);
					}
					else {
						sender.sendMessage(ChatColor.stripColor(message));
					}
				}
			}
			else {
				//No time permission, or no time sub-command, thus deny:
				sender.sendMessage(noPermissionMessage);
			}
			return;
		}
		//At this point in time, the sender has permission to use the restart command.
		
		if(args.length == 1) {
			String subcommand = args[0].toLowerCase();
			if(subcommand.equals("now")) {
				//Perform restart now:
				sendFeedback(sender, ChatColor.RED + "As per request, performing restart now!");
				plugin.shutdownServer();
			}
			else if(subcommand.equals("time")) {
				subCommandTimeUntilRestart(sender);
			}
			else if(subcommand.equals("help")) {
				printHelp(sender);
			}
			else if(subcommand.equals("on")) {
				subCommandOn(sender);
			}
			else if(subcommand.equals("off")) {
				subCommandOff(sender);
			}
		}
		else if(args.length == 2) {
			subCommandScheduleCustomRestartTime(sender, args);
		}
		else {
			//No sub-command matched, print the help:
			sendFeedback(sender, ChatColor.RED + "Unknown subcommand!");
			printHelp(sender);
		}
	}
	
	//Restart in a given time:
	private void subCommandScheduleCustomRestartTime(CommandSender sender, String[] args) {
		//Parse the time unit:
		Double timeToSecondConversionFactor = null;
		if(args[0].length() == 1) {
			char timeUnitLetter = args[0].charAt(0);
			if(timeUnitLetter == 'h' || timeUnitLetter == 'H') {
				timeToSecondConversionFactor = 3600.0;
			}
			else if(timeUnitLetter == 'm' || timeUnitLetter == 'M') {
				timeToSecondConversionFactor = 60.0;
			}
			else if(timeUnitLetter == 's' || timeUnitLetter == 'S') {
				timeToSecondConversionFactor = 1.0;
			}
		}
		if(timeToSecondConversionFactor == null) {
			sendFeedback(sender, ChatColor.RED + "Invalid time unit!");
			sendFeedback(sender, ChatColor.AQUA + "Use 'h' for time in hours, 'm' for minutes and 's' for seconds as first argument.");
			return;
		}
		
		//Parse the time amount:
		double timeAmount;
		try {
			 timeAmount = Double.parseDouble(args[1]);
		}
		catch(Exception e) {
			sendFeedback(sender, ChatColor.RED + "Could not parse second argument, expected a number.");
			return;
		}
		
		plugin.getLogger().info(sender.getName() + " is setting a new restart time...");
		
		if(plugin.autoRestart) {
			plugin.cancelTimer(); //Cancel all running timers (given there are some).
		}
		
		//Overwrite the restart interval with the command value:
		double restartTimeInSeconds = timeAmount * timeToSecondConversionFactor;
		plugin.restartInterval = restartTimeInSeconds / 3600.0;
		
		//Start the restart with the custom time again:
		plugin.getLogger().info("Scheduling restart tasks...");
		plugin.scheduleTimer();
		
		sendFeedback(sender, ChatColor.AQUA + "The server will be restarting in " + ChatColor.WHITE + getTimeUntilNextRestart());
	}
	
	private void subCommandOn(CommandSender sender) {
		//Check if not already on:
		if(plugin.autoRestart) {
			sendFeedback(sender, ChatColor.RED + "Automatic restart is already turned on.");
			return;
		}
		
		//Enable automated restart:
		plugin.getLogger().info("Reloading configuration...");
		plugin.loadConfiguration();
		
		plugin.getLogger().info("Scheduling restart tasks...");
		plugin.autoRestart = true; //Has to be done after the config load to overwrite the active flag.
		plugin.scheduleTimer();
		
		sendFeedback(sender, ChatColor.AQUA + "Automatic restarts have been turned on!");
		plugin.getLogger().info(sender.getName() + " turned automatic restarts on!");
		sendFeedback(sender, ChatColor.AQUA + "The server will be restarting in " + ChatColor.WHITE + getTimeUntilNextRestart());
	}
	
	private void subCommandOff(CommandSender sender) {
		//Abort command if auto-restart is already off:
		if(!plugin.autoRestart) {
			sendFeedback(sender, ChatColor.RED + "Automatic restart is already turned off.");
			return;
		}
		
		plugin.cancelTimer(); //Abort all currently running timers.
		
		sendFeedback(sender, ChatColor.AQUA + "Automatic restarts have been turned off!");
		plugin.getLogger().info(sender.getName() + " turned automatic restarts off!");
	}
	
	//Reports the time until the next scheduled restart:
	private void subCommandTimeUntilRestart(CommandSender sender) {
		//Abort if there is no restart pending:
		if(!plugin.autoRestart) {
			sendFeedback(sender, ChatColor.RED + "There is no auto-restart scheduled!");
			return;
		}
		
		sendFeedback(sender, ChatColor.AQUA + "The server will be restarting in " + ChatColor.WHITE + getTimeUntilNextRestart());
	}
	
	private String getTimeUntilNextRestart() {
		long restartIntervalInSeconds = (long) (plugin.restartInterval * 60.0 * 60.0);
		long timeSinceRebootTimerStartInSeconds = (System.currentTimeMillis() - plugin.startTimestamp) / 1000L;
		long secondsUntilReboot = restartIntervalInSeconds - timeSinceRebootTimerStartInSeconds;
		int hours = (int) (secondsUntilReboot / 3600L);
		int minutes = (int) ((secondsUntilReboot - hours * 3600L) / 60L);
		int seconds = (int) secondsUntilReboot % 60;
		return hours + "h" + minutes + "m" + seconds + "s";
	}
	
	private void handleMemoryCommand(CommandSender sender) {
		if(!sender.hasPermission("simplerestart.memory")) {
			sendFeedback(sender, noPermissionMessage);
			return;
		}
		
		//Show memory usage:
		Runtime runtime = Runtime.getRuntime();
		float freeMemory = (float) runtime.freeMemory() / (1024F * 1024F);
		float totalMemory = (float) runtime.totalMemory() / (1024F * 1024F);
		float maxMemory = (float) runtime.maxMemory() / (1024F * 1024F);
		
		sendFeedback(sender, ChatColor.RED + "Free memory: " + ChatColor.WHITE + String.format("%.1f", freeMemory) + "MB");
		sendFeedback(sender, ChatColor.RED + "Total memory: " + ChatColor.WHITE + String.format("%.1f", totalMemory) + "MB");
		sendFeedback(sender, ChatColor.RED + "Max memory: " + ChatColor.WHITE + String.format("%.1f", maxMemory) + "MB");
	}
	
	private static void sendFeedback(CommandSender sender, String message) {
		if(sender instanceof Player) {
			sender.sendMessage(message);
		}
		else {
			sender.sendMessage(ChatColor.stripColor(message));
		}
	}
	
	private void printHelp(CommandSender sender) {
		StringBuilder builder = new StringBuilder();
		//Title:
		builder.append(ChatColor.WHITE + "--- " + ChatColor.DARK_AQUA + "Restart " + ChatColor.AQUA + "Help " + ChatColor.WHITE + "---");
		//Subcommands:
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
		
		//Send the help message:
		if(sender instanceof Player) {
			sender.sendMessage(builder.toString());
		}
		else {
			sender.sendMessage(ChatColor.stripColor(builder.toString()));
		}
	}
}
