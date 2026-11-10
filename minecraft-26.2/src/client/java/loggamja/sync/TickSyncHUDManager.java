package loggamja.sync;

import net.minecraft.client.Minecraft;

import java.util.Arrays;

public class TickSyncHUDManager {
    public int packetRange;
    public int avgPacketDelay;
    public int targetMargin;
    public int syncComponentAlpha = 20;
    public int movementCoverage;
    public int wrappedPerSecond;
    public String syncStatus = "PAUSED (Not in game)";
    public int syncStatusColor = 0xAAAAAA;
    public String delayStatus = "Good";
    public int delayStatusColor = 0xAAAAAA;
    public int packetStatsColor = 0xAAAAAA;
    public String puckStatus = "Smooth";
    public int puckStatusColor = 0x55FF55;

    public final int samplingRange = 55;

    public float[] packetDelayHistogram = new float[samplingRange];
    private static final int WINDOW_SIZE = 20;
    private final boolean[] movementTick = new boolean[WINDOW_SIZE];
    private final boolean[] wrappedTick = new boolean[WINDOW_SIZE];
    private int windowIndex;

    public static final TickSyncHUDManager INSTANCE = new TickSyncHUDManager();

    void resetSampling() {
        Arrays.fill(packetDelayHistogram, 0);
        Arrays.fill(movementTick, false);
        Arrays.fill(wrappedTick, false);
        windowIndex = 0;
        movementCoverage = 0;
        wrappedPerSecond = 0;
        packetRange = 0;
        avgPacketDelay = 0;
        syncComponentAlpha = 0;
    }

    public void recordClientTick(int packetCallbacks, int packetDelay) {
        movementTick[windowIndex] = packetCallbacks > 0;
        wrappedTick[windowIndex] = packetCallbacks > 0 && packetDelay >= 38 && packetDelay < TickSyncMain.packetActiveTimeout;
        windowIndex = (windowIndex + 1) % WINDOW_SIZE;

        movementCoverage = 0;
        wrappedPerSecond = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            if (movementTick[i]) movementCoverage++;
            if (wrappedTick[i]) wrappedPerSecond++;
        }
    }

    public void updateHUD() {
        TickSyncMain main = TickSyncMain.INSTANCE;
        Minecraft client = Minecraft.getInstance();
        targetMargin = main.getPacketMargin();
        boolean hasActiveSamples = main.isPlayingInGame() && main.hasActiveMovement();
        boolean samplesReady = main.hasTimingEvidence();
        boolean hasRecentSamples = hasActiveSamples && main.hasRecentTimingEvidence();
        if (samplesReady) {
            packetRange = main.packetRange;
            avgPacketDelay = main.avgPacketDelay;
        }

        if (!TickSyncConfig.INSTANCE.isTickSyncOn) {
            setSyncStatus("Sync: PAUSED (Disabled)", 0xAAAAAA);
        } else if (client.isPaused()) {
            setSyncStatus("Sync: PAUSED (Game Paused)", 0xAAAAAA);
        } else if (client.level == null || client.player == null) {
            setSyncStatus("Sync: PAUSED (Not in game)", 0xAAAAAA);
        } else if (main.serverTPS > 20) {
            setSyncStatus("Sync: PAUSED (Server TPS >20)", 0xFF5555);
        } else if (main.getCurrentFPS() <= TickSyncConfig.INSTANCE.minimumFps) {
            setSyncStatus("Sync: PAUSED (Low FPS)", 0xFF5555);
        } else if (!hasActiveSamples) {
            setSyncStatus("Sync: Idle (No Moving Entities)", 0xAAAAAA);
        } else if (!hasRecentSamples) {
            setSyncStatus("Sync: Waiting for samples", 0xAAAAAA);
        } else if (!main.canSync()) {
            setSyncStatus("Sync: PAUSED (High Jitter)", 0xFF5555);
        } else if (main.pendingShiftMs != 0 || main.isTickRateChangedLastTick
                || (samplesReady && main.isTickSyncRequired())) {
            setSyncStatus("Sync: Adjusting...", 0xFFFF55);
        } else if (samplesReady) {
            setSyncStatus("Sync: LOCKED IN", 0x55FF55);
        }

        // Reuse the existing 25ms target, 38ms Late point, and 55ms histogram ceiling.
        if (avgPacketDelay <= 25) delayStatus = "Fast";
        else if (avgPacketDelay < 38) delayStatus = "Good";
        else if (avgPacketDelay < samplingRange) delayStatus = "Slow";
        else delayStatus = "Lagging";
        delayStatusColor = avgPacketDelay < 38 ? 0x55FF55 : avgPacketDelay < samplingRange ? 0xFFFF55 : 0xFF5555;

        if (wrappedPerSecond >= WINDOW_SIZE / 4) packetStatsColor = 0xFF5555;
        else if (wrappedPerSecond > 0) packetStatsColor = 0xFFFF55;
        else packetStatsColor = 0x55FF55;

        // Existing 8ms outlier limit and 16ms automatic cushion ceiling bound puck jitter states.
        if (packetRange <= 8) {
            puckStatus = "Puck: Smooth";
            puckStatusColor = 0x55FF55;
        } else if (packetRange <= 16) {
            puckStatus = "Puck: Slight Stutter (Try Margin ~" + (packetRange + 5) + ")";
            puckStatusColor = 0xFFFF55;
        } else {
            puckStatus = "Puck: Stuttering (Set Margin ~" + (packetRange + 5) + ")";
            puckStatusColor = 0xFF5555;
        }

        if (!hasRecentSamples) {
            delayStatus = "Waiting for samples";
            puckStatus = "Puck: Waiting for samples";
            delayStatusColor = 0xAAAAAA;
            packetStatsColor = 0xAAAAAA;
            puckStatusColor = 0xAAAAAA;
        }

        fadeDebugHistogram();
        fadeSyncComponent();
    }

    private void setSyncStatus(String status, int color) {
        syncStatus = status;
        syncStatusColor = color;
    }

    void fadeDebugHistogram() {
        if (syncComponentAlpha > 0) syncComponentAlpha--;
    }

    void fadeSyncComponent() {
        for (int i = 0; i < samplingRange; i++) {
            packetDelayHistogram[i] *= 0.95f;
        }
    }

    public void addToDebugHistogram(int packetDelay) {
        if (0 < packetDelay && packetDelay < samplingRange) {
            packetDelayHistogram[packetDelay]++;
        }
    }
}
