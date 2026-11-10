package loggamja.sync.mixin;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import loggamja.sync.TickSyncMain;
import loggamja.sync.TickSyncTelemetry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    @Shadow
    private long lastUiMs;

    @Redirect(
            method = "advanceGameTime(J)I",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F"))
    private float useTickSyncDuration(FloatUnaryOperator vanillaProvider, float vanillaMillisPerTick) {
        TickSyncMain.INSTANCE.onTickRateUsed();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return vanillaProvider.apply(vanillaMillisPerTick);

        float clientTPS = TickSyncMain.INSTANCE.clientTPS;
        return clientTPS > 0 && clientTPS != 20.0f ? 1000.0f / clientTPS : 50.0f;
    }

    @Inject(method = "advanceRealTime(J)V", at = @At("HEAD"))
    private void onAdvanceRealTime(long timeMillis, CallbackInfo ci) {
        long frameDurationMs = timeMillis - this.lastUiMs;
        if (this.lastUiMs > 0 && frameDurationMs >= 85) TickSyncTelemetry.logFrameHitch(frameDurationMs);
    }
}
