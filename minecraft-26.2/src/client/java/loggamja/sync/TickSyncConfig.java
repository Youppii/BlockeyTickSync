package loggamja.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TickSyncConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "tick-sync-config.json");

    public boolean isTickSyncOn = true;
    public int margin = 0;
    public boolean useDebugScreen = false;
    public int minimumFps = 40;
    public int syncCooldownMs = 1000;
    public int maxStepMs = 4;
    public int clampTolerance = 5;
    public int hudPosition = 0;
    public boolean showDelay = true;
    public boolean showJitter = true;
    public boolean showPacketStats = true;
    public boolean showPuckStatus = true;
    public boolean showHistogram = true;
    public boolean hudTextShadow = true;
    public volatile boolean enableTelemetry = true;

    // Singleton
    public static final TickSyncConfig INSTANCE = new TickSyncConfig();

    public void load() {
        if (!CONFIG_FILE.exists()) return;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            TickSyncConfig loaded = GSON.fromJson(reader, TickSyncConfig.class);
            if (loaded != null) {
                copyFrom(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void resetToDefaults() {
        copyFrom(new TickSyncConfig());
    }

    private void copyFrom(TickSyncConfig loaded) {
        this.isTickSyncOn = loaded.isTickSyncOn;
        this.margin = Mth.clamp(loaded.margin, 0, 40);
        this.useDebugScreen = loaded.useDebugScreen;
        this.minimumFps = Mth.clamp(loaded.minimumFps, 20, 240);
        this.syncCooldownMs = Mth.clamp(loaded.syncCooldownMs, 100, 5000);
        this.maxStepMs = Mth.clamp(loaded.maxStepMs, 1, 10);
        this.clampTolerance = Mth.clamp(loaded.clampTolerance, 0, 20);
        this.hudPosition = Mth.clamp(loaded.hudPosition, 0, 3);
        this.showDelay = loaded.showDelay;
        this.showJitter = loaded.showJitter;
        this.showPacketStats = loaded.showPacketStats;
        this.showPuckStatus = loaded.showPuckStatus;
        this.showHistogram = loaded.showHistogram;
        this.hudTextShadow = loaded.hudTextShadow;
        this.enableTelemetry = loaded.enableTelemetry;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
