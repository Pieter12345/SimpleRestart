package com.hamaluik.SimpleRestart;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.TimeUnit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class SimpleRestart extends JavaPlugin {
	
	// Configuration values:
	private boolean isAutomatedRestartEnabled = true;
	private double restartIntervalInHours = 1;
	private List<Double> warningTimes;
	// Ampersand will be replaced, as soon as the config is loaded! (Only here as default value for the config).
	private String warningMessage = "&cServer will be restarting in %t minutes!";
	private String restartMessage = "&cServer is restarting, we'll be right back!";
	
	// Timers:
	private final ArrayList<Timer> warningTimers = new ArrayList<>();
	private Timer rebootTimer;
	private long rebootTimerStartTime; // Keep track of when the timer started, to reconstruct remaining time.
	
	// Setters/Getters:
	
	protected boolean isAutomatedRestartEnabled() {
		return this.isAutomatedRestartEnabled;
	}
	
	protected long getRebootTimerStartTime() {
		return this.rebootTimerStartTime;
	}
	
	protected double getRestartInterval() {
		return this.restartIntervalInHours;
	}
	
	protected void setRestartInterval(double restartIntervalInHours) {
		this.restartIntervalInHours = restartIntervalInHours;
	}
	
	// Core:
	
	@Override
	public void onEnable() {
		this.loadConfiguration();
		SimpleRestartCommandListener commandListener = new SimpleRestartCommandListener(this);
		this.getCommand("restart").setExecutor(commandListener);
		this.getCommand("reboot").setExecutor(commandListener);
		this.getCommand("memory").setExecutor(commandListener);
		this.getLogger().info("Plugin enabled");
		
		if(this.isAutomatedRestartEnabled) {
			this.scheduleTimer();
		} else {
			this.getLogger().info("No automatic restart scheduled!");
		}
	}
	
	@Override
	public void onDisable() {
		this.cancelTimer(); // Stop pending tasks.
		this.getLogger().info("Plugin disabled");
	}
	
	protected void loadConfiguration() {
		// Create default config, if it does not exist yet:
		this.saveDefaultConfig();
		
		// Get configuration values:
		FileConfiguration config = this.getConfig();
		this.isAutomatedRestartEnabled = config.getBoolean("auto-restart", true);
		this.restartIntervalInHours = config.getDouble("auto-restart-interval", 8);
		this.warningTimes = config.getDoubleList("warn-times");
		this.warningMessage = colorize(config.getString("warning-message", warningMessage));
		this.restartMessage = colorize(config.getString("restart-message", restartMessage));
	}
	
	// Colorizing for loaded config strings:
	private static String colorize(String str) {
		return str.replaceAll("(&([a-f0-9]))", ChatColor.COLOR_CHAR + "$2");
	}
	
	protected void cancelTimer() {
		// Cancel warning timers:
		for(Timer warningTimer : this.warningTimers) {
			warningTimer.cancel();
		}
		this.warningTimers.clear();
		
		// Cancel restart timer:
		if(this.rebootTimer != null) {
			this.rebootTimer.cancel();
		}
		this.rebootTimer = null;
		
		// Disable auto-restart:
		this.isAutomatedRestartEnabled = false;
	}
	
	protected void scheduleTimer() {
		this.cancelTimer();
		// Start the tasks for warning-messages:
		double restartIntervalInMinutes = this.restartIntervalInHours * 60.0;
		for(double warnTime : this.warningTimes) {
			// Only consider warning times before the reboot (non-negative):
			if(restartIntervalInMinutes - warnTime > 0) {
				// Start an asynchronous task to not depend on tick-speed.
				long delayInSeconds = (long) ((restartIntervalInMinutes - warnTime) * 60.0);
				Timer warnTimer = new Timer();
				this.warningTimers.add(warnTimer);
				warnTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						// Transfer the task to the main thread:
						SimpleRestart.this.getServer().getScheduler().runTask(SimpleRestart.this, () -> {
							SimpleRestart.this.getServer().broadcastMessage(
									SimpleRestart.this.warningMessage.replaceAll("%t", String.valueOf(warnTime)));
							SimpleRestart.this.getLogger().info(ChatColor.stripColor(
									SimpleRestart.this.warningMessage.replaceAll("%t", String.valueOf(warnTime))));
						});
					}
				}, delayInSeconds * 1000L);
				this.getLogger().info("Warning scheduled for " + delayInSeconds + " seconds from now!");
			}
		}
		
		// Start the tasks for rebooting:
		// Start an asynchronous task to not depend on tick-speed.
		long delayInSeconds = (long) (restartIntervalInMinutes * 60.0);
		this.rebootTimer = new Timer();
		this.rebootTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				// Transfer the task to the main thread:
				SimpleRestart.this.getServer().getScheduler().runTask(SimpleRestart.this, () -> {
					SimpleRestart.this.shutdownServer();
				});
			}
		}, delayInSeconds * 1000L);
		this.getLogger().info("Reboot scheduled for " + delayInSeconds + " seconds from now!");
		
		this.isAutomatedRestartEnabled = true;
		this.rebootTimerStartTime = System.currentTimeMillis();
	}
	
	// Kick all players from the server with a friendly message!
	private void announceRestartAndKickPlayers() {
		this.getServer().broadcastMessage(this.restartMessage);
		for(Player player : this.getServer().getOnlinePlayers()) {
			player.kickPlayer(ChatColor.stripColor(this.restartMessage));
		}
	}
	
	// Shuts the server down!
	protected void shutdownServer() {
		this.getLogger().info("Restarting...");
		this.announceRestartAndKickPlayers();
		
		// Touch the restart.txt file:
		try {
			Path dataFolder = getDataFolder().toPath();
			if(!Files.exists(dataFolder)) {
				Files.createDirectory(dataFolder);
			}
			dataFolder = dataFolder.toRealPath(LinkOption.NOFOLLOW_LINKS);
			
			Path restartFile = dataFolder.resolve("restart.txt");
			this.getLogger().info("Touching restart.txt at: " + restartFile);
			if(Files.exists(restartFile)) {
				Files.setLastModifiedTime(restartFile,
						FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
			} else {
				Files.createFile(restartFile);
			}
		} catch (Exception e) {
			this.getLogger().info("Something went wrong while touching restart.txt. See stacktrace.");
			e.printStackTrace();
			return;
		}
		
		//Save the server and shut it down:
		try {
			this.getServer().savePlayers();
			for(World world : this.getServer().getWorlds()) {
				world.save();
			}
			this.getServer().shutdown();
		} catch (Exception e) {
			this.getLogger().info("Something went wrong while saving & stopping!");
			e.printStackTrace();
		}
	}
}
