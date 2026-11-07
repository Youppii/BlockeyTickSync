package loggamja.sync;

import net.minecraft.client.MinecraftClient;

public class TickSyncHUDManager {
    public int packetRange;
    public int avgPacketDelay;
    public int targetMargin;
    public int syncTextAlpha = 20;
    public int packetsPerSecond;
    public int lossPercent;
    public int wrappedPerSecond;
    public String syncStatus = "PAUSED (Not in game)";
    public int syncStatusColor = 0xAAAAAA;
    public String delayStatus = "Good";
    public String puckStatus = "Smooth";
    public int puckStatusColor = 0x55FF55;

    public final int samplingRange = 55;

    public float[] packetDelayHistogram = new float[samplingRange];
    private static final int WINDOW_SIZE = 20;
    private final int[] packetsPerTick = new int[WINDOW_SIZE];
    private final boolean[] activeTick = new boolean[WINDOW_SIZE];
    private final boolean[] wrappedTick = new boolean[WINDOW_SIZE];
    private int windowIndex;

    public static final TickSyncHUDManager INSTANCE = new TickSyncHUDManager();

    public void recordClientTick(int packetCallbacks, int packetDelay, boolean hasReceivedEntityPacket) {
        final boolean active = hasReceivedEntityPacket && 0 <= packetDelay && packetDelay < TickSyncMain.packetActiveTimeout;
        packetsPerTick[windowIndex] = packetCallbacks;
        activeTick[windowIndex] = active;
        wrappedTick[windowIndex] = active && packetDelay >= 38;
        windowIndex = (windowIndex + 1) % WINDOW_SIZE;

        int activeSlots = 0;
        int receivedSlots = 0;
        packetsPerSecond = 0;
        wrappedPerSecond = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            packetsPerSecond += packetsPerTick[i];
            if (activeTick[i]) {
                activeSlots++;
                if (packetsPerTick[i] > 0) receivedSlots++;
                if (wrappedTick[i]) wrappedPerSecond++;
            }
        }
        lossPercent = activeSlots == 0 ? 0 : Math.round(100f * (activeSlots - receivedSlots) / activeSlots);
    }

    public void updateHUD() {
        TickSyncMain main = TickSyncMain.INSTANCE;
        MinecraftClient client = MinecraftClient.getInstance();
        packetRange = main.packetRange;
        avgPacketDelay = main.avgPacketDelay;
        targetMargin = main.getPacketMargin();

        if (!TickSyncConfig.INSTANCE.isTickSyncOn) {
            setSyncStatus("PAUSED (Disabled)", 0xFF5555);
        } else if (client.isPaused()) {
            setSyncStatus("PAUSED (Game Paused)", 0xFF5555);
        } else if (client.world == null || client.player == null) {
            setSyncStatus("PAUSED (Not in game)", 0xFF5555);
        } else if (main.serverTPS > 20) {
            setSyncStatus("PAUSED (Server TPS >20)", 0xFF5555);
        } else if (main.getCurrentFPS() <= 40) {
            setSyncStatus("PAUSED (Low FPS)", 0xFF5555);
        } else if (!main.hasReceivedEntityPacket || System.currentTimeMillis() - main.lastServerPacketTime >= TickSyncMain.packetActiveTimeout) {
            setSyncStatus("Idle (No Moving Entities)", 0xAAAAAA);
        } else if (!main.canSync()) {
            setSyncStatus("PAUSED (High Jitter)", 0xFF5555);
        } else if (main.pendingTickShift != 0 || main.isTickSyncRequired()) {
            setSyncStatus("Adjusting...", 0xFFFF55);
        } else {
            setSyncStatus("LOCKED IN", 0x55FF55);
        }

        if (avgPacketDelay < targetMargin - 3) delayStatus = "Fast";
        else if (avgPacketDelay <= targetMargin + 3) delayStatus = "Good";
        else if (avgPacketDelay <= targetMargin + 8) delayStatus = "Slow";
        else delayStatus = "Lagging";

        if (packetRange <= 5) {
            puckStatus = "Smooth";
            puckStatusColor = 0x55FF55;
        } else if (packetRange <= 10) {
            puckStatus = "Slight Stutter (Try Margin ~" + (packetRange + 5) + ")";
            puckStatusColor = 0xFFFF55;
        } else {
            puckStatus = "Stuttering (Set Margin ~" + (packetRange + 5) + ")";
            puckStatusColor = 0xFF5555;
        }

        fadeDebugHistogram();
        fadeSyncText();
    }

    private void setSyncStatus(String status, int color) {
        syncStatus = status;
        syncStatusColor = color;
    }

    void fadeDebugHistogram() {
        if (syncTextAlpha > 0) syncTextAlpha--;
    }

    void fadeSyncText() {
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
