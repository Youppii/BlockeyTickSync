package loggamja.sync.mixin;

import loggamja.sync.TickSyncConfig;
import loggamja.sync.TickSyncHUDManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickCounter, CallbackInfo ci) {
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !cfg.useDebugScreen) return;

        TickSyncHUDManager hud = TickSyncHUDManager.INSTANCE;
        final int spacing = 10;
        final int rows = 1 + (cfg.showDelay ? 1 : 0) + (cfg.showJitter ? 1 : 0)
                + (cfg.showPacketStats ? 1 : 0) + (cfg.showPuckStatus ? 1 : 0) + (cfg.showHistogram ? 1 : 0);
        int y = cfg.hudPosition < 2 ? 8 : Math.max(8, graphics.guiHeight() - 8 - rows * spacing);
        drawHudText(graphics, hud.syncStatus, y, hud.syncStatusColor);
        y += spacing;
        if (cfg.showDelay) {
            drawHudText(graphics, "Hit Delay: " + hud.avgPacketDelay + "ms (" + hud.delayStatus + ")", y, hud.delayStatusColor);
            y += spacing;
        }

        if (cfg.showJitter) {
            String mode = cfg.margin == 0 ? "Auto" : "Set " + cfg.margin;
            drawHudText(graphics, "Jitter: " + hud.packetRange + "ms | Cushion: " + hud.targetMargin + "ms (" + mode + ")", y, hud.puckStatusColor);
            y += spacing;
        }
        if (cfg.showPacketStats) {
            drawHudText(graphics, "Packets: " + hud.packetsPerSecond + "/20 | Loss: " + hud.lossPercent + "% (" + hud.wrappedPerSecond + " Late)", y, hud.packetStatsColor);
            y += spacing;
        }
        if (cfg.showPuckStatus) {
            drawHudText(graphics, hud.puckStatus, y, hud.puckStatusColor);
            y += spacing;
        }

        if (cfg.showHistogram) drawHistogram(graphics, hud, y);
    }

    private void drawHudText(GuiGraphicsExtractor graphics, String text, int y, int color) {
        Minecraft client = Minecraft.getInstance();
        final int screenWidth = graphics.guiWidth();
        String visible = client.font.plainSubstrByWidth(text, Math.max(0, screenWidth - 16));
        int x = TickSyncConfig.INSTANCE.hudPosition % 2 == 0 ? screenWidth - 8 - client.font.width(visible) : 8;
        graphics.text(client.font, visible, x, y, 0xFF000000 | color, TickSyncConfig.INSTANCE.hudTextShadow);
    }

    private void drawHistogram(GuiGraphicsExtractor graphics, TickSyncHUDManager hud, int y) {
        Minecraft client = Minecraft.getInstance();
        final int screenWidth = graphics.guiWidth();
        final String leftLabel = "50ms";
        final String rightLabel = "0ms";
        final int histogramLeft = TickSyncConfig.INSTANCE.hudPosition % 2 == 0
                ? screenWidth - client.font.width(rightLabel) - 12 - hud.samplingRange
                : 12 + client.font.width(leftLabel);
        final int leftLabelX = histogramLeft - client.font.width(leftLabel) - 4;
        final int rightLabelX = histogramLeft + hud.samplingRange + 4;

        graphics.text(client.font, leftLabel, leftLabelX, y, 0xFFFFFFFF, TickSyncConfig.INSTANCE.hudTextShadow);
        for (int i = 0; i < hud.samplingRange; i++) {
            float intensity = hud.packetDelayHistogram[i];
            float red = intensity;
            float green = intensity;
            float blue = intensity;
            if (i < 25) { red *= 0.01f; green *= 0.10f; }
            else { green *= 0.10f; blue *= 0.01f; }
            if (i % 50 == 0) { red += 0.01f; green += 0.10f; blue += 0.01f; }
            int r = (int) Mth.clamp(Math.round(255.0 * Math.pow(Math.max(red, 0.0), 1.0 / 2.2)), 0, 255);
            int g = (int) Mth.clamp(Math.round(255.0 * Math.pow(Math.max(green, 0.0), 1.0 / 2.2)), 0, 255);
            int b = (int) Mth.clamp(Math.round(255.0 * Math.pow(Math.max(blue, 0.0), 1.0 / 2.2)), 0, 255);
            graphics.fill(histogramLeft + i, y + 1, histogramLeft + i + 1, y + 8,
                    0xFF000000 | r << 16 | g << 8 | b);
        }
        graphics.text(client.font, rightLabel, rightLabelX, y, 0xFFFFFFFF, TickSyncConfig.INSTANCE.hudTextShadow);
    }
}
