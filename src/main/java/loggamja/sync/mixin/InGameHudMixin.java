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
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        if (client.player == null || !cfg.useDebugScreen) return;

        TickSyncHUDManager hud = TickSyncHUDManager.INSTANCE;
        final int spacing = 10;
        final int rows = 1 + (cfg.showDelay ? 1 : 0) + (cfg.showJitter ? 1 : 0)
                + (cfg.showPacketStats ? 1 : 0) + (cfg.showPuckStatus ? 1 : 0) + (cfg.showHistogram ? 1 : 0);
        int y = cfg.hudPosition < 2 ? 8 : Math.max(8, client.getWindow().getScaledHeight() - 8 - rows * spacing);
        drawHudText(context, hud.syncStatus, y, hud.syncStatusColor);
        y += spacing;
        if (cfg.showDelay) {
            drawHudText(context, "Hit Delay: " + hud.avgPacketDelay + "ms (" + hud.delayStatus + ")", y, hud.delayStatusColor);
            y += spacing;
        }

        if (cfg.showJitter) {
            String mode = cfg.margin == 0 ? "Auto" : "Set " + cfg.margin;
            drawHudText(context, "Jitter: " + hud.packetRange + "ms | Cushion: " + hud.targetMargin + "ms (" + mode + ")", y, hud.puckStatusColor);
            y += spacing;
        }
        if (cfg.showPacketStats) {
            drawHudText(context, "Packets: " + hud.packetsPerSecond + "/20 | Loss: " + hud.lossPercent + "% (" + hud.wrappedPerSecond + " Late)", y, hud.packetStatsColor);
            y += spacing;
        }
        if (cfg.showPuckStatus) {
            drawHudText(context, hud.puckStatus, y, hud.puckStatusColor);
            y += spacing;
        }

        if (cfg.showHistogram) drawHistogram(context, hud, y);
    }

    private void drawHudText(DrawContext context, String text, int y, int color) {
        final int screenWidth = client.getWindow().getScaledWidth();
        String visible = client.textRenderer.trimToWidth(text, Math.max(0, screenWidth - 16));
        int x = TickSyncConfig.INSTANCE.hudPosition % 2 == 0 ? screenWidth - 8 - client.textRenderer.getWidth(visible) : 8;
        context.drawText(client.textRenderer, visible, x, y, color, TickSyncConfig.INSTANCE.hudTextShadow);
    }

    private void drawHistogram(DrawContext context, TickSyncHUDManager hud, int y) {
        final int screenWidth = client.getWindow().getScaledWidth();
        final String leftLabel = "50ms";
        final String rightLabel = "0ms";
        final int histogramLeft = TickSyncConfig.INSTANCE.hudPosition % 2 == 0
                ? screenWidth - client.textRenderer.getWidth(rightLabel) - 12 - hud.samplingRange
                : 12 + client.textRenderer.getWidth(leftLabel);
        final int leftLabelX = histogramLeft - client.textRenderer.getWidth(leftLabel) - 4;
        final int rightLabelX = histogramLeft + hud.samplingRange + 4;

        context.drawText(client.textRenderer, leftLabel, leftLabelX, y, 0xFFFFFF, TickSyncConfig.INSTANCE.hudTextShadow);
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
        context.drawText(client.textRenderer, rightLabel, rightLabelX, y, 0xFFFFFF, TickSyncConfig.INSTANCE.hudTextShadow);
    }
}
