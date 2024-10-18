package me.caseload.knockbacksync;

import com.github.retrooper.packetevents.PacketEvents;
import lombok.Getter;
import lombok.Setter;
import me.caseload.knockbacksync.command.SenderFactory;
import me.caseload.knockbacksync.listener.packetevents.AttributeChangeListener;
import me.caseload.knockbacksync.listener.packetevents.PingReceiveListener;
import me.caseload.knockbacksync.manager.ConfigManager;
import me.caseload.knockbacksync.permission.PermissionChecker;
import me.caseload.knockbacksync.scheduler.SchedulerAdapter;
import me.caseload.knockbacksync.stats.custom.PluginJarHashProvider;
import me.caseload.knockbacksync.stats.custom.StatsManager;
import me.caseload.knockbacksync.world.PlatformServer;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

// Base class
public abstract class KnockbackSyncBase {
    public static Logger LOGGER;
    public static KnockbackSyncBase INSTANCE;
    public Platform platform;
    public StatsManager statsManager;
    public PlatformServer platformServer;
    public PluginJarHashProvider pluginJarHashProvider;
    @Getter
    protected SchedulerAdapter scheduler;
    @Getter
    protected ConfigManager configManager;
    
    @Setter
    @Getter
    private SenderFactory<? extends KnockbackSyncBase, ?> senderFactory;

    protected KnockbackSyncBase() {
        this.platform = getPlatform();
        INSTANCE = this;
        configManager = new ConfigManager();
    }

    private Platform getPlatform() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return Platform.FOLIA; // Paper (Folia) detected
        } catch (ClassNotFoundException ignored1) {
        }

        try {
            Class.forName("org.bukkit.Bukkit");
            return Platform.BUKKIT; // Bukkit (Spigot/Paper without Folia) detected
        } catch (ClassNotFoundException ignored2) {
        }

        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return Platform.FABRIC; // Fabric detected
        } catch (ClassNotFoundException ignored3) {
        }

        throw new IllegalStateException("Unknown platform!");
    }

    public abstract Logger getLogger();

    public abstract File getDataFolder();

    public abstract InputStream getResource(String filename);

    public abstract void load();

    public void enable() {
        LOGGER = getLogger();
        saveDefaultConfig();
        initializePacketEvents();
        registerCommonListeners();
        registerPlatformListeners();
        registerCommands();
    }

    public abstract void initializeScheduler();

    public void initializePacketEvents() {
        PacketEvents.getAPI().getSettings()
                .checkForUpdates(false)
                .debug(false);

        PacketEvents.getAPI().init();
    }

    protected void registerCommonListeners() {
        PacketEvents.getAPI().getEventManager().registerListeners(
                new AttributeChangeListener(),
                new PingReceiveListener()
        );
    }

    protected abstract void registerPlatformListeners();

    protected abstract void registerCommands();

    protected abstract String getVersion();

    protected void checkForUpdates() {
        getLogger().info("Checking for updates...");

        scheduler.runTaskAsynchronously(() -> {
            try {
                GitHub github = GitHub.connectAnonymously();
                String latestVersion = github.getRepository("CASELOAD7000/knockback-sync")
                        .getLatestRelease()
                        .getTagName();

                String currentVersion = getVersion();

                int comparisonResult = compareVersions(currentVersion, latestVersion);

                if (comparisonResult < 0) {
                    LOGGER.warning("You are running an older version. A new update is available for download at: https://github.com/CASELOAD7000/knockback-sync/releases/latest");
                    configManager.setUpdateAvailable(true);
                } else if (comparisonResult > 0) {
                    if (currentVersion.contains("-dev")) {
                        LOGGER.info("You are running a development build newer than the latest release.");
                    } else {
                        LOGGER.info("You are running a version newer than the latest release.");
                    }
                    configManager.setUpdateAvailable(false);
                } else {
                    LOGGER.info("You are running the latest release.");
                    configManager.setUpdateAvailable(false);
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    private int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("[-.]");
        String[] v2Parts = version2.split("[-.]");

        int length = Math.min(v1Parts.length, v2Parts.length);

        for (int i = 0; i < length; i++) {
            int comparison = compareVersionPart(v1Parts[i], v2Parts[i]);
            if (comparison != 0) {
                return comparison;
            }
        }

        // If we're here, all compared parts are equal
        if (v1Parts.length != v2Parts.length) {
            return compareSpecialVersions(v1Parts, v2Parts);
        }

        return 0; // Versions are equal
    }

    private int compareVersionPart(String part1, String part2) {
        try {
            int v1 = Integer.parseInt(part1);
            int v2 = Integer.parseInt(part2);
            return Integer.compare(v1, v2);
        } catch (NumberFormatException e) {
            // If parts are not numbers, compare them based on dev < snapshot < release
            return compareSpecialPart(part1, part2);
        }
    }

    private int compareSpecialPart(String part1, String part2) {
        if (part1.equals(part2)) return 0;
        if (part1.startsWith("dev")) return part2.startsWith("dev") ? 0 : -1;
        if (part2.startsWith("dev")) return 1;
        if (part1.equals("SNAPSHOT")) return part2.equals("SNAPSHOT") ? 0 : -1;
        if (part2.equals("SNAPSHOT")) return 1;
        return part1.compareTo(part2);
    }

    private int compareSpecialVersions(String[] v1Parts, String[] v2Parts) {
        if (v1Parts.length > v2Parts.length) {
            String specialPart = v1Parts[v2Parts.length];
            if (specialPart.startsWith("dev")) return -1;
            if (specialPart.equals("SNAPSHOT")) return -1;
            return 1; // Assume it's a release version part
        } else {
            String specialPart = v2Parts[v1Parts.length];
            if (specialPart.startsWith("dev")) return 1;
            if (specialPart.equals("SNAPSHOT")) return 1;
            return -1; // Assume it's a release version part
        }
    }

    public abstract void saveDefaultConfig();

    public abstract PermissionChecker getPermissionChecker();

}


