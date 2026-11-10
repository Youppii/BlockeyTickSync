package loggamja.sync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class TickSyncSetting extends OptionsSubScreen {
    public TickSyncSetting(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("tick-sync.settings"));
    }

    @Override
    protected void addOptions() {
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        list.addSmall(
                booleanOption("enabled", cfg.isTickSyncOn, value -> cfg.isTickSyncOn = value),
                integerOption("margin", cfg.margin, 0, 40,
                        value -> value == 0 ? Component.translatable("tick-sync.auto") : Component.literal(value + "ms"),
                        value -> cfg.margin = value));
        list.addSmall(
                integerOption("minimumFps", cfg.minimumFps, 20, 240,
                        value -> Component.literal(value + " FPS"), value -> cfg.minimumFps = value),
                integerOption("syncCooldownMs", cfg.syncCooldownMs, 100, 5000,
                        value -> Component.literal(value + "ms"), value -> cfg.syncCooldownMs = value));
        list.addSmall(
                integerOption("maxStepMs", cfg.maxStepMs, 1, 10,
                        value -> Component.literal(value + "ms"), value -> cfg.maxStepMs = value),
                integerOption("clampTolerance", cfg.clampTolerance, 0, 20,
                        value -> Component.literal(value + "ms"), value -> cfg.clampTolerance = value));
        list.addSmall(
                booleanOption("debug", cfg.useDebugScreen, value -> cfg.useDebugScreen = value),
                booleanOption("enableTelemetry", cfg.enableTelemetry, value -> cfg.enableTelemetry = value));
        list.addSmall(
                integerOption("hudPosition", cfg.hudPosition, 0, 3,
                        value -> Component.translatable("tick-sync.hudPosition." + value),
                        value -> cfg.hudPosition = value),
                booleanOption("hudTextShadow", cfg.hudTextShadow, value -> cfg.hudTextShadow = value));
        list.addSmall(
                booleanOption("showDelay", cfg.showDelay, value -> cfg.showDelay = value),
                booleanOption("showJitter", cfg.showJitter, value -> cfg.showJitter = value));
        list.addSmall(
                booleanOption("showPacketStats", cfg.showPacketStats, value -> cfg.showPacketStats = value),
                booleanOption("showPuckStatus", cfg.showPuckStatus, value -> cfg.showPuckStatus = value));
        list.addBig(booleanOption("showHistogram", cfg.showHistogram, value -> cfg.showHistogram = value));
    }

    private OptionInstance<Boolean> booleanOption(String name, boolean value, Consumer<Boolean> onChange) {
        String key = "tick-sync.option." + name;
        return OptionInstance.createBoolean(key, OptionInstance.cachedConstantTooltip(
                Component.translatable(key + ".tooltip")), value, onChange::accept);
    }

    private OptionInstance<Integer> integerOption(String name, int value, int min, int max,
                                                  IntFunction<Component> valueComponent, IntConsumer onChange) {
        String key = "tick-sync.option." + name;
        return new OptionInstance<>(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
                (text, number) -> Component.translatable("options.generic_value", text, valueComponent.apply(number)),
                new OptionInstance.IntRange(min, max), value, onChange::accept);
    }

    @Override
    protected void addFooter() {
        layout.addToFooter(Button.builder(Component.translatable("tick-sync.reset"), button -> {
            TickSyncConfig.INSTANCE.resetToDefaults();
            this.minecraft.gui.setScreen(new TickSyncSetting(this.lastScreen));
        }).width(120).build());
        super.addFooter();
    }

    @Override
    public void removed() {
        TickSyncConfig.INSTANCE.save();
        super.removed();
    }
}
