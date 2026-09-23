package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.network.C2SUpdateSkillSettings;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;

import java.util.function.DoubleConsumer;
import java.util.function.Function;

/**
 * Lets a player spend their Gourmet Cell evolution headroom: each tunable is an independent slider
 * capped by {@link CellEvolution} at the player's current cell level, so dialling one up never
 * costs another. Turning any of them up raises the Appetite cost of the skill it belongs to (see
 * each skill's {@code extraAppetiteCost}), so this is a power/cost trade-off, not a free lunch.
 */
public class SkillSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int SLIDER_WIDTH = 220;
    private static final int SLIDER_HEIGHT = 20;

    private int nailCombo;
    private int forkProjectiles;
    private int knifeWaves;
    private float flyingDamage;
    private float flyingSize;
    private int kiOutput;

    public SkillSettingsScreen() {
        super(Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.title"));
        this.nailCombo = ClientTorikoData.nailComboSetting();
        this.forkProjectiles = ClientTorikoData.forkProjectileSetting();
        this.knifeWaves = ClientTorikoData.knifeWaveSetting();
        this.flyingDamage = ClientTorikoData.flyingDamageSetting();
        this.flyingSize = ClientTorikoData.flyingSizeSetting();
        this.kiOutput = ClientTorikoData.kiOutputSetting();
    }

    @Override
    protected void init() {
        int level = ClientTorikoData.cellLevel();
        int x = width / 2 - SLIDER_WIDTH / 2;
        int y = height / 2 - 102;

        addIntSlider(x, y, "nail_combo", CellEvolution.NAIL_COMBO_BASE, CellEvolution.nailComboCap(level),
                nailCombo, v -> nailCombo = v);
        y += ROW_HEIGHT;
        addIntSlider(x, y, "fork_projectiles", CellEvolution.FORK_PROJECTILE_BASE, CellEvolution.forkProjectileCap(level),
                forkProjectiles, v -> forkProjectiles = v);
        y += ROW_HEIGHT;
        addIntSlider(x, y, "knife_waves", CellEvolution.KNIFE_WAVE_BASE, CellEvolution.knifeWaveCap(level),
                knifeWaves, v -> knifeWaves = v);
        y += ROW_HEIGHT;
        addPercentSlider(x, y, "flying_damage", CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(level),
                flyingDamage, v -> flyingDamage = v);
        y += ROW_HEIGHT;
        addPercentSlider(x, y, "flying_size", CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(level),
                flyingSize, v -> flyingSize = v);
        y += ROW_HEIGHT;
        addIntSlider(x, y, "ki_output", CellEvolution.KI_OUTPUT_BASE, CellEvolution.kiOutputCap(level),
                kiOutput, v -> kiOutput = v);
        y += ROW_HEIGHT + 12;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onDone())
                .bounds(width / 2 - 100, y, 200, 20).build());
    }

    private void addIntSlider(int x, int y, String key, int base, int cap, int initial, java.util.function.IntConsumer onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key, Math.round(mapped), cap);
        PowerSlider slider = new PowerSlider(x, y, SLIDER_WIDTH, SLIDER_HEIGHT, base, cap, 1.0, initial, label,
                mapped -> onChange.accept((int) Math.round(mapped)));
        addRenderableWidget(slider);
    }

    private void addPercentSlider(int x, int y, String key, float base, float cap, float initial, java.util.function.Consumer<Float> onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key,
                Math.round(mapped * 100), Math.round(cap * 100));
        PowerSlider slider = new PowerSlider(x, y, SLIDER_WIDTH, SLIDER_HEIGHT, base, cap, 0.01, initial, label,
                mapped -> onChange.accept((float) mapped));
        addRenderableWidget(slider);
    }

    private void onDone() {
        PacketDistributor.sendToServer(new C2SUpdateSkillSettings(
                nailCombo, forkProjectiles, knifeWaves, flyingDamage, flyingSize, kiOutput));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 122, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.cell_level", ClientTorikoData.cellLevel()),
                width / 2, height / 2 - 110, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A slider over an arbitrary [base, cap] range, inactive (but still visible) when cap == base. */
    private static final class PowerSlider extends AbstractSliderButton {
        private final double base;
        private final double cap;
        private final double step;
        private final Function<Double, Component> labelFactory;
        private final DoubleConsumer onChange;

        PowerSlider(int x, int y, int width, int height, double base, double cap, double step, double initial,
                    Function<Double, Component> labelFactory, DoubleConsumer onChange) {
            super(x, y, width, height, Component.empty(), normalize(initial, base, cap));
            this.base = base;
            this.cap = cap;
            this.step = step;
            this.labelFactory = labelFactory;
            this.onChange = onChange;
            this.active = cap > base;
            updateMessage();
        }

        private static double normalize(double v, double base, double cap) {
            return cap <= base ? 0.0 : Mth.clamp((v - base) / (cap - base), 0.0, 1.0);
        }

        private double mappedValue() {
            double raw = base + value * (cap - base);
            if (step > 0) {
                raw = Math.round(raw / step) * step;
            }
            return Mth.clamp(raw, base, Math.max(base, cap));
        }

        @Override
        protected void updateMessage() {
            setMessage(labelFactory.apply(mappedValue()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(mappedValue());
        }
    }
}
