package loggamja.sync.mixin;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import loggamja.sync.TickSyncConfig;
import loggamja.sync.TickSyncMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.class)
public class RenderTickCounterMixin {
    @Shadow
    public float lastFrameDuration;
    @Shadow
    private long prevTimeMillis;
    @Shadow
    public float tickDelta;
    @Shadow
    @Final
    private float tickTime;
    @Unique
    private final FloatUnaryOperator targetMillisPerTick = millis -> {
        if (MinecraftClient.getInstance().world != null) {
            if (TickSyncMain.INSTANCE.clientTPS > 20
                    || (TickSyncConfig.INSTANCE.margin > 0 && TickSyncMain.INSTANCE.clientTPS > 0)) {
                return 1000 / TickSyncMain.INSTANCE.clientTPS;
            }
            else {
                return 50;
            }
        }
        return millis;
    };
    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    public void beginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        this.lastFrameDuration = (float)(timeMillis - this.prevTimeMillis) / this.targetMillisPerTick.apply(this.tickTime);
        this.prevTimeMillis = timeMillis;
        this.tickDelta = this.tickDelta + this.lastFrameDuration;
        int i = (int)this.tickDelta;
        this.tickDelta -= (float)i;
        cir.setReturnValue(i);
        cir.cancel();
    }
}
