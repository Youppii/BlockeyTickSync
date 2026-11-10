package loggamja.sync;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TickSyncTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger("tick-sync");
    private static final String STOP = "\u0000STOP";
    private static final BlockingQueue<String> EVENTS = new LinkedBlockingQueue<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean STOPPING = new AtomicBoolean();

    private TickSyncTelemetry() { }

    public static void start() {
        if (!TickSyncConfig.INSTANCE.enableTelemetry) return;
        if (!STARTED.compareAndSet(false, true)) return;
        Thread writer = new Thread(TickSyncTelemetry::writeEvents, "TickSync telemetry writer");
        writer.setDaemon(false);
        writer.start();
    }

    public static void shutdown() {
        if (STARTED.get() && STOPPING.compareAndSet(false, true)) EVENTS.offer(STOP);
    }

    public static void logSummary(int delay, TickSyncHUDManager hud, int late) {
        boolean samplesReady = TickSyncMain.INSTANCE.hasTimingEvidence();
        log("SUMMARY movementCoverage=" + hud.movementCoverage + "/20 ticks samplesReady=" + samplesReady
                + " hitDelay=" + (samplesReady ? delay + "ms" : "n/a") + " late=" + late);
    }

    public static void logSyncEvent(String status) { log("SYNC_EVENT " + status); }

    public static void logAdjustment(String type, int correction, int delay, int margin) {
        log("SYNC_ADJUSTMENT type=" + type + " correction=" + correction + "ms hitDelay="
                + delay + "ms cushion=" + margin + "ms");
    }

    public static void logLateEvent(int delay) { log("LATE_EVENT movementUpdateDelay=" + delay + "ms threshold=38ms"); }

    public static void logFrameHitch(long duration) {
        if (duration >= 85) log("FRAME_HITCH duration=" + duration + "ms threshold=85ms");
    }

    private static void log(String event) {
        if (!TickSyncConfig.INSTANCE.enableTelemetry) return;
        start();
        if (STARTED.get() && !STOPPING.get()) EVENTS.offer(Instant.now() + " " + event);
    }

    private static void writeEvents() {
        List<BufferedWriter> writers = new ArrayList<>();
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        paths.add(FabricLoader.getInstance().getGameDir().resolve("ticksync-log.txt"));
        paths.add(Path.of(System.getProperty("user.home"), "Desktop", "ticksync-log.txt"));
        for (Path path : paths) {
            try {
                writers.add(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND));
            } catch (IOException e) {
                LOGGER.warn("Could not open TickSync telemetry log at {}", path, e);
            }
        }

        try {
            while (true) {
                String event = EVENTS.take();
                if (STOP.equals(event)) break;
                Iterator<BufferedWriter> iterator = writers.iterator();
                while (iterator.hasNext()) {
                    BufferedWriter writer = iterator.next();
                    try {
                        writer.write(event);
                        writer.newLine();
                        writer.flush();
                    } catch (IOException e) {
                        LOGGER.warn("Could not write TickSync telemetry log", e);
                        try { writer.close(); } catch (IOException ignored) { }
                        iterator.remove();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            for (BufferedWriter writer : writers) {
                try { writer.close(); } catch (IOException e) { LOGGER.warn("Could not close TickSync telemetry log", e); }
            }
        }
    }
}
