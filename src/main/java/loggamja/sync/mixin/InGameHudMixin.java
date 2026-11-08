package loggamja.sync.mixin;

import loggamja.sync.TickSyncConfig;
import loggamja.sync.TickSyncHUDManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (client.player == null || !TickSyncConfig.INSTANCE.useDebugScreen) return;

        TickSyncHUDManager hud = TickSyncHUDManager.INSTANCE;
        final int firstY = 8;
        final int spacing = 10;
        drawRightAligned(context, hud.syncStatus, firstY, hud.syncStatusColor);
        drawRightAligned(context, "Hit Delay: " + hud.avgPacketDelay + "ms (" + hud.delayStatus + ")", firstY + spacing, 0xFFFFFF);

        String mode = TickSyncConfig.INSTANCE.margin == 0 ? "Auto" : "Set " + TickSyncConfig.INSTANCE.margin;
        drawRightAligned(context, "Jitter: " + hud.packetRange + "ms | Cushion: " + hud.targetMargin + "ms (" + mode + ")", firstY + spacing * 2, 0xFFFFFF);
        drawRightAligned(context, "Packets: " + hud.packetsPerSecond + "/20 | Loss: " + hud.lossPercent + "% (" + hud.wrappedPerSecond + " Late)", firstY + spacing * 3, 0xFFFFFF);
        drawRightAligned(context, hud.puckStatus, firstY + spacing * 4, hud.puckStatusColor);

        drawHistogram(context, hud, firstY + spacing * 5);
    }

    private void drawRightAligned(DrawContext context, String text, int y, int color) {
        final int screenWidth = client.getWindow().getScaledWidth();
        String visible = client.textRenderer.trimToWidth(text, Math.max(0, screenWidth - 16));
        int x = screenWidth - 8 - client.textRenderer.getWidth(visible);
        context.drawTextWithShadow(client.textRenderer, visible, x, y, color);
    }

    private void drawHistogram(DrawContext context, TickSyncHUDManager hud, int y) {
        final int screenWidth = client.getWindow().getScaledWidth();
        final String leftLabel = "50ms";
        final String rightLabel = "0ms";
        final int rightLabelX = screenWidth - client.textRenderer.getWidth(rightLabel) - 8;
        final int histogramRight = rightLabelX - 4;
        final int histogramLeft = histogramRight - hud.samplingRange;
        final int leftLabelX = histogramLeft - client.textRenderer.getWidth(leftLabel) - 4;

        context.drawTextWithShadow(client.textRenderer, leftLabel, leftLabelX, y, 0xFFFFFF);
        for (int i = 0; i < hud.samplingRange; i++) {
            float intensity = hud.packetDelayHistogram[i];
            float red = intensity;
            float green = intensity;
            float blue = intensity;
            if (i < 25) { red *= 0.01f; green *= 0.10f; }
            else { green *= 0.10f; blue *= 0.01f; }
            if (i % 50 == 0) { red += 0.01f; green += 0.10f; blue += 0.01f; }
            int r = (int) MathHelper.clamp(Math.round(255.0 * Math.pow(Math.max(red, 0.0), 1.0 / 2.2)), 0, 255);
            int g = (int) MathHelper.clamp(Math.round(255.0 * Math.pow(Math.max(green, 0.0), 1.0 / 2.2)), 0, 255);
            int b = (int) MathHelper.clamp(Math.round(255.0 * Math.pow(Math.max(blue, 0.0), 1.0 / 2.2)), 0, 255);
            context.fill(histogramLeft + i, y + 1, histogramLeft + i + 1, y + 8,
                    0xFF000000 | r << 16 | g << 8 | b);
        }
        context.drawTextWithShadow(client.textRenderer, rightLabel, rightLabelX, y, 0xFFFFFF);
    }
}
