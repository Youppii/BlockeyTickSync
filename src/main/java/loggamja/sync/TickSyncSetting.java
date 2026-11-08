package loggamja.sync;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.gui.widget.OptionSliderWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;


public class TickSyncSetting extends Screen {

    static String[][] TOGGLE_OPTIONS = {
            { "TickSync: OFF", "TickSync: ON" },
            { "Debug: OFF", "Debug: ON" }
    };
    static String[] tooltips = {
            "Toggles all functions of TickSync.",
            "Toggles debug screen at the corner."
    };

    private final int[] toggleIndices = new int[TOGGLE_OPTIONS.length];

    public TickSyncSetting() {
        super(Text.literal("Setting"));
    }
    @Override
    protected void init() {
        super.init();

        int buttonWidth = 169;
        int buttonHeight = 20;
        int spacing = 25;

        int startX = this.width / 2 - buttonWidth / 2;
        int startY = this.height / 5;

        // Config load
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        toggleIndices[0] = cfg.isTickSyncOn ? 1 : 0;
        toggleIndices[1] = cfg.useDebugScreen ? 1 : 0;

        // Buttons
        for (int i = 0; i < TOGGLE_OPTIONS.length; i++) {
            final int index = i;
            ButtonWidget toggleButton = ButtonWidget.builder(
                            Text.literal(TOGGLE_OPTIONS[index][toggleIndices[index]]),
                            button -> {
                                toggleIndices[index] = (toggleIndices[index] + 1) % TOGGLE_OPTIONS[index].length;
                                button.setMessage(Text.literal(TOGGLE_OPTIONS[index][toggleIndices[index]]));
                                onButtonClick(index, toggleIndices[index]);

                                // Save config
                                TickSyncConfig.INSTANCE.save();
                            })
                    .position(startX, startY + i * spacing)
                    .size(buttonWidth, buttonHeight)
                    .tooltip(Tooltip.of(Text.literal(tooltips[i])))
                    .build();

            this.addDrawableChild(toggleButton);
        }
        this.addDrawableChild(new SliderWidget(startX, startY + 2 * spacing, 169, 20, ScreenTexts.EMPTY,  cfg.margin / 40F) {
            {
                updateMessage();
            }
            @Override
            protected void updateMessage() {
                if(cfg.margin == 0){
                    this.setMessage(Text.of("Tick Delay Clamp: Auto"));
                }
                else{
                    this.setMessage(Text.of("Tick Delay Clamp: " + cfg.margin + "ms"));

                }
            }

            @Override
            protected void applyValue() {
                cfg.margin = MathHelper.floor(MathHelper.clampedLerp(0.0, 40.0, this.value));
            }
        });

        // Exit button
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("OK"), button -> {
                            assert this.client != null;
                            this.client.setScreen(new GameMenuScreen(true));
                            TickSyncConfig.INSTANCE.save();
                        })
                        .position(startX, startY + 4 * spacing)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
    }
    void onButtonClick(int button, int index) {
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        if (button == 0) {
            cfg.isTickSyncOn = (index != 0);
        }
        else if (button == 1) {
            cfg.useDebugScreen = (index != 0);
        }
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        //this.renderBackground(context, mouseX, mouseY, delta); Renders a double-blurred background. Causes a bug in 21.6+.
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "Setting",
                this.width / 2,
                20,
                0xFFFFFF
        );
        super.render(context, mouseX, mouseY, delta);
    }
}
