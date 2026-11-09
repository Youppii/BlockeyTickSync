package loggamja.sync;

import com.mojang.serialization.Codec;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class TickSyncSetting extends Screen {
    private final Screen parent;

    public TickSyncSetting(Screen parent) {
        super(Text.translatable("tick-sync.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        OptionListWidget options = new OptionListWidget(this.client, this.width, this.height - 72, 32, 25);
        options.addAll(new SimpleOption<?>[] {
                booleanOption("enabled", cfg.isTickSyncOn, value -> cfg.isTickSyncOn = value),
                integerOption("margin", cfg.margin, 0, 40,
                        value -> value == 0 ? Text.translatable("tick-sync.auto") : Text.literal(value + "ms"),
                        value -> cfg.margin = value),
                integerOption("minimumFps", cfg.minimumFps, 20, 240,
                        value -> Text.literal(value + " FPS"), value -> cfg.minimumFps = value),
                integerOption("syncCooldownMs", cfg.syncCooldownMs, 100, 5000,
                        value -> Text.literal(value + "ms"), value -> cfg.syncCooldownMs = value),
                integerOption("maxStepMs", cfg.maxStepMs, 1, 10,
                        value -> Text.literal(value + "ms"), value -> cfg.maxStepMs = value),
                integerOption("clampTolerance", cfg.clampTolerance, 0, 20,
                        value -> Text.literal(value + "ms"), value -> cfg.clampTolerance = value),
                booleanOption("debug", cfg.useDebugScreen, value -> cfg.useDebugScreen = value),
                booleanOption("enableTelemetry", cfg.enableTelemetry, value -> cfg.enableTelemetry = value),
                new SimpleOption<>("tick-sync.option.hudPosition",
                        SimpleOption.constantTooltip(Text.translatable("tick-sync.option.hudPosition.tooltip")),
                        (text, value) -> Text.translatable("options.generic_value", text,
                                Text.translatable("tick-sync.hudPosition." + value)),
                        new SimpleOption.PotentialValuesBasedCallbacks<>(List.of(0, 1, 2, 3), Codec.INT),
                        cfg.hudPosition, value -> cfg.hudPosition = value),
                booleanOption("hudTextShadow", cfg.hudTextShadow, value -> cfg.hudTextShadow = value),
                booleanOption("showDelay", cfg.showDelay, value -> cfg.showDelay = value),
                booleanOption("showJitter", cfg.showJitter, value -> cfg.showJitter = value),
                booleanOption("showPacketStats", cfg.showPacketStats, value -> cfg.showPacketStats = value),
                booleanOption("showPuckStatus", cfg.showPuckStatus, value -> cfg.showPuckStatus = value),
                booleanOption("showHistogram", cfg.showHistogram, value -> cfg.showHistogram = value)
        });
        this.addDrawableChild(options);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("tick-sync.reset"), button -> {
                    cfg.resetToDefaults();
                    this.clearAndInit();
                })
                .dimensions(this.width / 2 - 155, this.height - 28, 150, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(this.width / 2 + 5, this.height - 28, 150, 20)
                .build());
    }

    private SimpleOption<Boolean> booleanOption(String name, boolean value, Consumer<Boolean> onChange) {
        String key = "tick-sync.option." + name;
        return SimpleOption.ofBoolean(key, SimpleOption.constantTooltip(Text.translatable(key + ".tooltip")),
                value, onChange);
    }

    private SimpleOption<Integer> integerOption(String name, int value, int min, int max,
                                               IntFunction<Text> valueText, IntConsumer onChange) {
        String key = "tick-sync.option." + name;
        return new SimpleOption<>(key, SimpleOption.constantTooltip(Text.translatable(key + ".tooltip")),
                (text, number) -> Text.translatable("options.generic_value", text, valueText.apply(number)),
                new SimpleOption.ValidatingIntSliderCallbacks(min, max), value, onChange::accept);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void removed() {
        TickSyncConfig.INSTANCE.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
    }
}
