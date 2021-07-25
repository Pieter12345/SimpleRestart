package com.hamaluik.SimpleRestart;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class SimpleRestart extends JavaPlugin {
	// Configuration values:
	protected boolean autoRestart = true;
	protected double restartInterval = 1;
	private List<Double> warnTimes;
	// Ampersand will be replaced, as soon as the config is loaded! (Only here as default value for the config).
	private String warningMessage = "&cServer will be restarting in %t minutes!";
	private String restartMessage = "&cServer is restarting, we'll be right back!";
	
	// Timers:
	public ArrayList<Timer> warningTimers = new ArrayList<>();
	public Timer rebootTimer;
	
	// keep track of when we started the scheduler
	// so that we know how much time is left
	protected long startTimestamp;
	
	// startup routine..
	@Override
	public void onEnable() {
		// set up the plugin..
		this.loadConfiguration();
		SimpleRestartCommandListener commandListener = new SimpleRestartCommandListener(this);
		this.getCommand("restart").setExecutor(commandListener);
		this.getCommand("reboot").setExecutor(commandListener);
		this.getCommand("memory").setExecutor(commandListener);
		getLogger().info("Plugin enabled");
		
		// ok, now if we want to schedule a restart, do so!
		if(autoRestart) {
			scheduleTasks();
		}
		else {
			getLogger().info("No automatic restarts scheduled!");
		}
	}
	
	@Override
	public void onDisable() {
		cancelTasks(); //Stop pending tasks.
		getLogger().info("Plugin disabled");
	}
	
	public void loadConfiguration() {
		// Create default config, if it does not exist yet:
		saveDefaultConfig();
		
		// Get configuration values:
		FileConfiguration config = getConfig();
		autoRestart = config.getBoolean("auto-restart", true);
		restartInterval = config.getDouble("auto-restart-interval", 8);
		warnTimes = config.getDoubleList("warn-times");
		warningMessage = colorize(config.getString("warning-message", warningMessage));
		restartMessage = colorize(config.getString("restart-message", restartMessage));
	}
	
	// Colorizing for loaded config strings:
	private static String colorize(String str) {
		return str.replaceAll("(&([a-f0-9]))", ChatColor.COLOR_CHAR + "$2");
	}
	
	protected void cancelTasks() {
		//plugin.getServer().getScheduler().cancelTasks(plugin);
		for(int i = 0; i < warningTimers.size(); i++) {
			warningTimers.get(i).cancel();
		}
		warningTimers.clear();
		if(rebootTimer != null) {
			rebootTimer.cancel();
		}
		rebootTimer = new Timer();
		autoRestart = false;
	}
	
	protected void scheduleTasks() {
		cancelTasks();
		// start the warning tasks
		for(int i = 0; i < warnTimes.size(); i++) {
			if(restartInterval * 60 - warnTimes.get(i) > 0) {
				// only do "positive" warning times
				// start the warning task
				final double warnTime = warnTimes.get(i);
				/*getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
					public void run() {
						getServer().broadcastMessage(processColours(warningMessage.replaceAll("%t", "" + warnTime)));
						plugin.log.info("[SimpleRestart] " + stripColours(warningMessage.replaceAll("%t", "" + warnTime)));
					}
				}, (long)((restartInterval * 60 - warnTimes.get(i)) * 60.0 * 20.0));
				
				log.info("[SimpleRestart] warning scheduled for " + (long)((restartInterval * 60 - warnTimes.get(i)) * 60.0) + " seconds from now!");*/
				Timer warnTimer = new Timer();
				warningTimers.add(warnTimer);
				warnTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						// WoeshEdit - Run the code on the server main thread (fixes ConcurrentModificationExceptions).
						getServer().getScheduler().runTask(SimpleRestart.this, () -> {
							getServer().broadcastMessage(warningMessage.replaceAll("%t", String.valueOf(warnTime)));
							getLogger().info(ChatColor.stripColor(warningMessage.replaceAll("%t", String.valueOf(warnTime))));
						});
					}
				}, (long)((restartInterval * 60 - warnTimes.get(i)) * 60000.0));
				getLogger().info("Warning scheduled for " + (long)((restartInterval * 60 - warnTimes.get(i)) * 60.0) + " seconds from now!");
			}
		}
		
		// start the restart task
		/*getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				stopServer();
			}
		}, (long)(restartInterval * 3600.0 * 20.0));*/
		rebootTimer = new Timer();
		rebootTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				// WoeshEdit - Run the code on the server main thread (fixes ConcurrentModificationExceptions).
				getServer().getScheduler().runTask(SimpleRestart.this, () -> {
					stopServer();
				});
			}
		}, (long)(restartInterval * 3600000.0));
		
		getLogger().info("Reboot scheduled for " + (long)(restartInterval  * 3600.0) + " seconds from now!");
		autoRestart = true;
		startTimestamp = System.currentTimeMillis();
	}
	
	// Kick all players from the server with a friendly message!
	private void clearServer() {
		getServer().broadcastMessage(restartMessage);
		for (Player player : getServer().getOnlinePlayers()) {
			player.kickPlayer(ChatColor.stripColor(restartMessage));
		}
	}
	
	// Shuts the server down!
	protected boolean stopServer() {
		// log it and empty out the server first
		getLogger().info("Restarting...");
		clearServer();
		try {
			File file = new File(getDataFolder().getAbsolutePath() + File.separator + "restart.txt");
			getLogger().info("Touching restart.txt at: " + file.getAbsolutePath());
			if (file.exists()) {
				file.setLastModified(System.currentTimeMillis());
			} else {
				file.createNewFile();
			}
		} catch (Exception e) {
			getLogger().info("Something went wrong while touching restart.txt!");
			return false;
		}
		try {
			getServer().savePlayers();
			for(World world : getServer().getWorlds()) {
				world.save();
			}
			getServer().shutdown();
		} catch (Exception e) {
			getLogger().info("Something went wrong while saving & stopping!");
			return false;
		}
		return true;
	}
}
