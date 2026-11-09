package loggamja.sync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TickSyncMain implements ModInitializer {
	public static final String MOD_ID = "tick-sync";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	boolean isTickRateChangedLastTick;
	boolean isPacketReceivedThisTick;
	boolean isPacketRangeUpdatedThisTick;
	boolean hasReceivedEntityPacket;
	boolean lateDelayRecorded;
	int lateEventsThisSecond;
	long lastTelemetrySummaryTime = System.currentTimeMillis();
	String lastLoggedSyncStatus;

	int afterLazyPacketCooldown;
	int pendingShiftMs;
	int entityPacketCallbacksThisTick;
	Thread packetThread;
	ClientPlayNetworkHandler networkHandler;
	public int packetRange;
	public int instantPacketRange;
	public int avgPacketDelay;

	long lastSyncTime = System.currentTimeMillis();
	long lastServerPacketTime = System.currentTimeMillis();
	long lastPacketRangeUpdatedTime = System.currentTimeMillis();

	public float clientTPS = 20;
	public float serverTPS = 20;

	// Constant definitions
	final int tickBufferSize = 10;
	final int rangeBufferSize = 40;
	final int fastRangeBufferSize = 10;

	final int outlierLimit = 8;
	static final int packetActiveTimeout = 250;

	public final int samplingRange = 55;

	List<Integer> packetDelayBuffer = new ArrayList<>(Collections.nCopies(1, 12));
	List<Integer> packetRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));
	List<Integer> fastPacketRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));

	static TickSyncConfig cfg;
	public static final TickSyncMain INSTANCE = new TickSyncMain();

	@Override
	public void onInitialize() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> INSTANCE.onClientTickStart());
		ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.onClientTickEnd());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TickSyncTelemetry.shutdown());
		ClientPlayConnectionEvents.INIT.register((handler, client) -> INSTANCE.resetConnection(handler));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> INSTANCE.onDisconnect(handler)));

		TickSyncConfig.INSTANCE.load();
		cfg = TickSyncConfig.INSTANCE;
		cfg.margin = MathHelper.clamp(cfg.margin, 0, 40);
		TickSyncTelemetry.start();
	}

	synchronized void resetConnection(ClientPlayNetworkHandler handler) {
		networkHandler = handler;
		packetThread = null;
		isTickRateChangedLastTick = false;
		isPacketReceivedThisTick = false;
		isPacketRangeUpdatedThisTick = false;
		hasReceivedEntityPacket = false;
		lateDelayRecorded = false;
		lateEventsThisSecond = 0;
		lastLoggedSyncStatus = null;
		afterLazyPacketCooldown = 0;
		pendingShiftMs = 0;
		entityPacketCallbacksThisTick = 0;
		packetRange = 0;
		instantPacketRange = 0;
		avgPacketDelay = 0;
		final long now = System.currentTimeMillis();
		lastSyncTime = now;
		lastServerPacketTime = now;
		lastPacketRangeUpdatedTime = now;
		lastTelemetrySummaryTime = now;
		clientTPS = 20;
		serverTPS = 20;
		packetDelayBuffer.clear();
		packetDelayBuffer.add(12);
		packetRangeBuffer.clear();
		packetRangeBuffer.add(0);
		fastPacketRangeBuffer.clear();
		fastPacketRangeBuffer.add(0);
		TickSyncHUDManager.INSTANCE.resetSampling();
	}

	synchronized void onDisconnect(ClientPlayNetworkHandler handler) {
		if (networkHandler == handler) resetConnection(null);
	}

	public synchronized void onEntityPacket(ClientPlayNetworkHandler handler) {
		if (handler != networkHandler || MinecraftClient.getInstance().isOnThread()) return;
		Thread currentThread = Thread.currentThread();
		if (packetThread == null) packetThread = currentThread;
		else if (packetThread != currentThread) return;

		final long now = System.currentTimeMillis();

		lastServerPacketTime = now;
		lateDelayRecorded = false;
		hasReceivedEntityPacket = true;
		isPacketReceivedThisTick = true;
		entityPacketCallbacksThisTick++;

		// Update the range buffer
		if (!isPacketRangeUpdatedThisTick) {
			final int delta = (int) (now - lastPacketRangeUpdatedTime);

			if (0 < delta && delta < (int) applyRatio(75.0f)) {
				final int halfDuration = (int) applyRatio(25);
				final int normalDeviation = (delta % getTickDuration() + halfDuration) % getTickDuration() - halfDuration;
				addToPacketRangeBuffer(normalDeviation);
			}
			// When packets arrive infrequently
			if (getTickDuration() * 2 <= delta) {
				afterLazyPacketCooldown = 20;
			}
			lastPacketRangeUpdatedTime = now;
			isPacketRangeUpdatedThisTick = true;
		}
	}

	int getTickDuration() {
		return (int) (1000 / serverTPS);
	}

	float applyRatio(float x) {
		return x * (20 / serverTPS);
	}

	boolean isPlayingInGame() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null && client.player != null && !client.isPaused();
	}

	public boolean canSync() {
		if (cfg.margin == 0) return serverTPS <= 20 && getCurrentFPS() > cfg.minimumFps && instantPacketRange < applyRatio(25);
		else return serverTPS <= 20 && getCurrentFPS() > cfg.minimumFps;
	}

	int getCurrentFPS() {
		return MinecraftClient.getInstance().getCurrentFps();
	}

	public synchronized void onClientTickStart() {
		final long now = System.currentTimeMillis();
		final int packetDelay = (int) (now - lastServerPacketTime);
		TickSyncHUDManager.INSTANCE.recordClientTick(entityPacketCallbacksThisTick, packetDelay, hasReceivedEntityPacket);
		entityPacketCallbacksThisTick = 0;

		if (isPlayingInGame()) {
			if (0 < packetDelay && packetDelay < applyRatio(samplingRange)) {
				addToTickDeltaBuffer(packetDelay);
			} else {
				addToTickDeltaBuffer(avgPacketDelay);
			}

			if (afterLazyPacketCooldown > 0) afterLazyPacketCooldown--;
			TickSyncHUDManager.INSTANCE.addToDebugHistogram(packetDelay);

			avgPacketDelay = calculateMean(packetDelayBuffer);
			packetRange = calculateRange(packetRangeBuffer, rangeBufferSize);
			instantPacketRange = calculateRange(fastPacketRangeBuffer, fastRangeBufferSize);
		}
		TickSyncHUDManager.INSTANCE.updateHUD();
		if (packetDelay >= 38 && packetDelay < packetActiveTimeout && !lateDelayRecorded) {
			lateDelayRecorded = true;
			lateEventsThisSecond++;
			TickSyncTelemetry.logLateEvent(packetDelay);
		}
		String syncStatus = TickSyncHUDManager.INSTANCE.syncStatus;
		if (!syncStatus.equals(lastLoggedSyncStatus)) {
			TickSyncTelemetry.logSyncEvent(syncStatus);
			lastLoggedSyncStatus = syncStatus;
		}
		if (now - lastTelemetrySummaryTime >= 1000) {
			TickSyncTelemetry.logSummary(avgPacketDelay, TickSyncHUDManager.INSTANCE, lateEventsThisSecond);
			lateEventsThisSecond = 0;
			lastTelemetrySummaryTime = now;
		}
		isPacketRangeUpdatedThisTick = false;
		isPacketReceivedThisTick = false;
	}

	void addToTickDeltaBuffer(int newDelta) {
		packetDelayBuffer.add(newDelta);

		if (packetDelayBuffer.size() > tickBufferSize) {
			packetDelayBuffer.remove(0);
		}
	}

	void addToPacketRangeBuffer(int newDelta) {
		if (Math.abs(newDelta) <= outlierLimit) packetRangeBuffer.add(newDelta);
		if (packetRangeBuffer.size() > rangeBufferSize) {
			packetRangeBuffer.remove(0);
		}

		fastPacketRangeBuffer.add(newDelta);
		if (fastPacketRangeBuffer.size() > fastRangeBufferSize) {
			fastPacketRangeBuffer.remove(0);
		}
	}

	int calculateMean(List<Integer> list) {
		final int size = list.size();
		if (size == 0) return 10;

		int sum = 0;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int v : list) {
			sum += v;
			if (v < min) min = v;
			if (v > max) max = v;
		}
		if (size >= 5) return (sum - min - max) / (size - 2);
		return sum / size;
	}

	int calculateRange(List<Integer> list, int requireBufferSize) {
		try {
			final int size = list.size();
			if (size < requireBufferSize) return 12;

			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;

			for (Integer i : list) {
				if (i == null) continue;
				if (i < min) min = i;
				if (i > max) max = i;
			}

			return max - min;
		} catch (Exception e) {
			return 12;
		}
	}

	synchronized void onClientTickEnd() {
		if (isTickRateChangedLastTick) {
			isTickRateChangedLastTick = false;
			setTickRate(Math.min(20, serverTPS));
		}
		final long now = System.currentTimeMillis();

		if (pendingShiftMs == 0 && cfg.isTickSyncOn && isPlayingInGame() && canSync() && (now - lastSyncTime) > cfg.syncCooldownMs) {
			if (cfg.margin == 0 && isWeirdSyncOccurred()) {
				fixWeirdSync();
			} else if (isTickSyncRequired()) {
				matchTickSync();
			}
		}
		if (pendingShiftMs != 0) applyPendingTickShift();
	}

	boolean isWeirdSyncOccurred() {
		if (packetDelayBuffer.isEmpty()) return false;

		final int range = calculateRange(packetDelayBuffer, tickBufferSize);
		return (packetDelayBuffer.size() >= tickBufferSize) && range > (applyRatio(35));
	}

	void fixWeirdSync() {
		if (packetDelayBuffer.isEmpty() || pendingShiftMs != 0) return;

		final float min = Math.min(getPacketMargin() / 2, Collections.min(packetDelayBuffer));
		final int tickToPush = Math.max(2, quantizeToFrame(applyRatio((getPacketMargin() - min))));
		pendingShiftMs = tickToPush;

		TickSyncHUDManager.INSTANCE.syncTextAlpha = 10;
	}

	int getPacketMargin() {
		final int min = afterLazyPacketCooldown > 0 ? 7 : 6;
		return cfg.margin == 0 ? MathHelper.clamp(packetRange + 3, min, 16) : cfg.margin;
	}

	boolean isTickSyncRequired() {
		if (cfg.margin == 0) return getThreshold() < avgPacketDelay;

		return avgPacketDelay < cfg.margin || avgPacketDelay > cfg.margin + cfg.clampTolerance;
	}

	int getThreshold() {
		final int threshold = (int) applyRatio(getPacketMargin()) + 2;

		if (threshold < getFrameDuration()) {
			return (int) (getFrameDuration() * 1.5f);
		} else {
			return threshold;
		}
	}

	float getFrameDuration() {
		return 1000f / getCurrentFPS();
	}

	void matchTickSync() {
		if (pendingShiftMs != 0) return;
		final int tickToPush;

		if (cfg.margin == 0) {
			int autoCorrection = (getTickDuration() - avgPacketDelay)
					+ quantizeToFrame(applyRatio(getPacketMargin() + getMatchSyncOffset()));
			if (autoCorrection > getTickDuration() / 2) autoCorrection -= getTickDuration();
			tickToPush = autoCorrection;
		} else {
			final int targetDelay = Math.min(getTickDuration() - 1, cfg.margin + cfg.clampTolerance / 2);
			int clampCorrection = targetDelay - avgPacketDelay;
			final int halfTick = getTickDuration() / 2;

			if (clampCorrection > halfTick) clampCorrection -= getTickDuration();
			if (clampCorrection < -halfTick) clampCorrection += getTickDuration();
			tickToPush = clampCorrection;
		}

		pendingShiftMs = tickToPush;
		TickSyncHUDManager.INSTANCE.syncTextAlpha = 20;
	}

	int quantizeToFrame(float margin) {
		int minSafeForFps = (int) Math.ceil(getFrameDuration()) + 1;
		return Math.max(minSafeForFps, Math.round(margin));
	}

	int getMatchSyncOffset() {
		if (cfg.margin != 0) return 0;

		if (getCurrentFPS() > 240) return 0;
		if (getCurrentFPS() > 120) return 1;
		return 2;
	}

	void applyPendingTickShift() {
		final int shift = Integer.signum(pendingShiftMs) * Math.min(cfg.maxStepMs, Math.abs(pendingShiftMs));
		pendingShiftMs -= shift;
		shiftNextTickDuration(shift);
		TickSyncTelemetry.logAdjustment("tick-sync", shift, avgPacketDelay, getPacketMargin());
		if (pendingShiftMs == 0) {
			packetDelayBuffer.clear();
			addToTickDeltaBuffer(getPacketMargin());
			avgPacketDelay = getPacketMargin();
			lastSyncTime = System.currentTimeMillis();
		}
	}

	void shiftNextTickDuration(int term) {
		final float tickRate = 1000f / (term + getTickDuration());

		setTickRate(tickRate);
		isTickRateChangedLastTick = true;
	}

	void setTickRate(float tickRate) {
		clientTPS = tickRate;
	}
	// -------------
}
