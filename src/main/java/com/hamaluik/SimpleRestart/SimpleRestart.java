package com.hamaluik.SimpleRestart;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class SimpleRestart extends JavaPlugin {
	// the basics
	Logger log = Logger.getLogger("Minecraft");
	
	// keep track of ourself!
	SimpleRestart plugin = this;
	// and the command executor!
	SimpleRestartCommandListener commandListener = new SimpleRestartCommandListener(this);
	
	// options
	boolean autoRestart = true;
	double restartInterval = 1;
	List<Double> warnTimes;
	boolean delayUntilEmpty = false;
	int maxPlayersConsideredEmpty = 0;
	// Ampersand will be replaced, as soon as the config is loaded! (Only here as default value for the config).
	String warningMessage = "&cServer will be restarting in %t minutes!";
	String restartMessage = "&cServer is restarting, we'll be right back!";
	
	// timers
	public ArrayList<Timer> warningTimers = new ArrayList<Timer>();
	public Timer rebootTimer;
	
	// keep track of when we started the scheduler
	// so that we know how much time is left
	long startTimestamp;
	
	// startup routine..
	@Override
	public void onEnable() {		
		// set up the plugin..
		this.loadConfiguration();
		this.getCommand("restart").setExecutor(commandListener);
		this.getCommand("reboot").setExecutor(commandListener);
		this.getCommand("memory").setExecutor(commandListener);
		log.info("[SimpleRestart] plugin enabled");
		
		// ok, now if we want to schedule a restart, do so!
		if(autoRestart) {
			scheduleTasks();
		}
		else {
			log.info("[SimpleRestart] No automatic restarts scheduled!");
		}
	}

	// shutdown routine
	@Override
	public void onDisable() {
		cancelTasks();
		log.info("[SimpleRestart] plugin disabled");
	}
	
	public void loadConfiguration() {
		// Create default config, if it does not exist yet:
		this.saveDefaultConfig();
		
		// Get configuration values:
		FileConfiguration config = getConfig();
		this.autoRestart = config.getBoolean("auto-restart", true);
		this.restartInterval = config.getDouble("auto-restart-interval", 8);
		this.warnTimes = config.getDoubleList("warn-times");
		this.warningMessage = colorize(config.getString("warning-message", warningMessage));
		this.restartMessage = colorize(config.getString("restart-message", restartMessage));
	}
	
	// allow for colour tags to be used in strings..
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
		plugin.autoRestart = false;
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
						Bukkit.getScheduler().runTask(plugin, new Runnable() {
							@Override
							public void run() {
								getServer().broadcastMessage(warningMessage.replaceAll("%t", String.valueOf(warnTime)));
								plugin.log.info("[SimpleRestart] " + ChatColor.stripColor(warningMessage.replaceAll("%t", String.valueOf(warnTime))));
							}
						});
					}
				}, (long)((restartInterval * 60 - warnTimes.get(i)) * 60000.0));
				log.info("[SimpleRestart] warning scheduled for " + (long)((restartInterval * 60 - warnTimes.get(i)) * 60.0) + " seconds from now!");
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
				Bukkit.getScheduler().runTask(plugin, new Runnable() {
					@Override
					public void run() {
						stopServer();
					}
				});
			}
		}, (long)(restartInterval * 3600000.0));
		
		log.info("[SimpleRestart] reboot scheduled for " + (long)(restartInterval  * 3600.0) + " seconds from now!");
		plugin.autoRestart = true;
		plugin.startTimestamp = System.currentTimeMillis();
	}
	
	// kick all players from the server
	// with a friendly message!
	private void clearServer() {
		this.getServer().broadcastMessage(restartMessage);
		Player[] players = this.getServer().getOnlinePlayers().toArray(new Player[0]); // WoeshEdit - Added ".toArray(new Player[0])".
		for (Player player : players) {
			player.kickPlayer(ChatColor.stripColor(restartMessage));
		}
	}
	
	// shut the server down!
	// hack into craftbukkit in order to do this
	// since bukkit doesn't normally allow access -_-
	// full kudos to the Redecouverte at:
	// http://forums.bukkit.org/threads/send-commands-to-console.3241/
	// for the code on executing commands as the console
	protected boolean stopServer() {
		// log it and empty out the server first
		log.info("[SimpleRestart] Restarting...");
		clearServer();
		try {
			File file = new File(this.getDataFolder().getAbsolutePath() + File.separator + "restart.txt");
			log.info("[SimpleRestart] Touching restart.txt at: " + file.getAbsolutePath());
			if (file.exists()) {
				file.setLastModified(System.currentTimeMillis());
			} else {
				file.createNewFile();
			}
		} catch (Exception e) {
			log.info("[SimpleRestart] Something went wrong while touching restart.txt!");
			return false;
		}
		try {
//            this.getServer().dispatchCommand(this.getServer().getConsoleSender(), "save-all");
//            this.getServer().dispatchCommand(this.getServer().getConsoleSender(), "stop");
			this.getServer().savePlayers();
			for(org.bukkit.World world : this.getServer().getWorlds()) {
				world.save();
			}
            this.getServer().shutdown();
		} catch (Exception e) {
			log.info("[SimpleRestart] Something went wrong while saving & stoping!");
			return false;
		}
		return true;
	}
}
