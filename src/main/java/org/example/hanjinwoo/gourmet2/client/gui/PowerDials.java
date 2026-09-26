package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.network.C2SUpdateSkillSettings;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * The player-tuned power dials (see {@link CellEvolution}): the values being edited, and the slider-plus-box widgets
 * that edit them. Shared by the skill tree, which shows the dials of whichever skill is picked, and by the general
 * settings screen, which keeps the two that are not a skill's own (attack damage and leap reach).
 *
 * <p>Every dial runs both ways. The level sets its ceiling, the setting's own floor is how far back it may be held,
 * and the default a skill has always had sits inside that range. Turning a dial up raises the Appetite cost of the
 * skills it belongs to, so this is a power/cost trade-off, not a free lunch.
 */
public final class PowerDials {
    /** Every tunable there is. */
    public enum Dial {
        NAIL_COMBO, FORK_PROJECTILES, NAIL_GUN_SHOTS, KNIFE_WAVES, FLYING_DAMAGE, FLYING_SIZE, KI_OUTPUT,
        ATTACK_DAMAGE, LEAP_DISTANCE, RANGE
    }

    private static final int BOX_WIDTH = 52;
    private static final int BOX_GAP = 4;
    /** What a label multiplies the slider's own value by when it shows a percentage. */
    private static final double PERCENT = 100.0;

    private int nailCombo = ClientTorikoData.nailComboSetting();
    private int forkProjectiles = ClientTorikoData.forkProjectileSetting();
    private int nailGunShots = ClientTorikoData.nailGunShotSetting();
    private int knifeWaves = ClientTorikoData.knifeWaveSetting();
    private float flyingDamage = ClientTorikoData.flyingDamageSetting();
    private float flyingSize = ClientTorikoData.flyingSizeSetting();
    private int kiOutput = ClientTorikoData.kiOutputSetting();
    private float attackDamage = ClientTorikoData.attackDamageSetting();
    private float leapDistance = ClientTorikoData.leapDistanceSetting();
    private float range = ClientTorikoData.rangeSetting();

    /** Width of a slider and its box together, for laying rows out. */
    public static int rowWidth(int sliderWidth) {
        return sliderWidth + BOX_GAP + BOX_WIDTH;
    }

    public C2SUpdateSkillSettings toPacket() {
        return new C2SUpdateSkillSettings(nailCombo, forkProjectiles, knifeWaves, flyingDamage, flyingSize, kiOutput,
                attackDamage, leapDistance, range, nailGunShots);
    }

    /** Adds the slider and the typing box for {@code dial} at ({@code x}, {@code y}). */
    public void add(Dial dial, int x, int y, int sliderWidth, int height, Font font, Consumer<AbstractWidget> adder) {
        int level = ClientTorikoData.cellLevel();
        switch (dial) {
            case NAIL_COMBO -> addInt(x, y, sliderWidth, height, font, adder, "nail_combo",
                    CellEvolution.NAIL_COMBO_FLOOR, CellEvolution.nailComboCap(level), nailCombo, v -> nailCombo = v);
            case FORK_PROJECTILES -> addInt(x, y, sliderWidth, height, font, adder, "fork_projectiles",
                    CellEvolution.FORK_PROJECTILE_FLOOR, CellEvolution.forkProjectileCap(level), forkProjectiles,
                    v -> forkProjectiles = v);
            case NAIL_GUN_SHOTS -> addInt(x, y, sliderWidth, height, font, adder, "nail_gun_shots",
                    CellEvolution.NAIL_GUN_SHOT_FLOOR, CellEvolution.nailGunShotCap(level), nailGunShots,
                    v -> nailGunShots = v);
            case KNIFE_WAVES -> addInt(x, y, sliderWidth, height, font, adder, "knife_waves",
                    CellEvolution.KNIFE_WAVE_FLOOR, CellEvolution.knifeWaveCap(level), knifeWaves, v -> knifeWaves = v);
            case FLYING_DAMAGE -> addScaled(x, y, sliderWidth, height, font, adder, "flying_damage",
                    CellEvolution.DAMAGE_MULT_FLOOR, CellEvolution.damageMultCap(level), flyingDamage,
                    ClientTorikoData.flyingDamageDialBase(), v -> flyingDamage = v);
            case FLYING_SIZE -> addPercent(x, y, sliderWidth, height, font, adder, "flying_size",
                    CellEvolution.SIZE_MULT_FLOOR, CellEvolution.sizeMultCap(level), flyingSize, v -> flyingSize = v);
            case KI_OUTPUT -> addInt(x, y, sliderWidth, height, font, adder, "ki_output",
                    CellEvolution.KI_OUTPUT_FLOOR, CellEvolution.kiOutputCap(level), kiOutput, v -> kiOutput = v);
            // Throttles, not boosts: their own ends are 100%, so the whole range is how far back the player may hold
            // what they already earn.
            case ATTACK_DAMAGE -> addScaled(x, y, sliderWidth, height, font, adder, "attack_damage",
                    CellEvolution.ATTACK_DAMAGE_FLOOR, CellEvolution.ATTACK_DAMAGE_BASE, attackDamage,
                    ClientTorikoData.attackDialBase(), v -> attackDamage = v);
            case LEAP_DISTANCE -> addScaled(x, y, sliderWidth, height, font, adder, "leap_distance",
                    CellEvolution.LEAP_DISTANCE_FLOOR, CellEvolution.LEAP_DISTANCE_BASE, leapDistance,
                    ClientTorikoData.leapDialBase(), v -> leapDistance = v);
            case RANGE -> addScaled(x, y, sliderWidth, height, font, adder, "range",
                    CellEvolution.RANGE_FLOOR, CellEvolution.RANGE_BASE, range,
                    ClientTorikoData.rangeDialBase(), v -> range = v);
        }
    }

    private void addInt(int x, int y, int w, int h, Font font, Consumer<AbstractWidget> adder, String key,
                        int floor, int cap, int initial, IntConsumer onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key, Math.round(mapped), cap);
        PowerSlider slider = new PowerSlider(x, y, w, h, floor, cap, 1.0, initial, label,
                mapped -> onChange.accept((int) Math.round(mapped)));
        adder.accept(slider);
        addValueBox(x, y, w, h, font, adder, slider, 1.0);
    }

    private void addPercent(int x, int y, int w, int h, Font font, Consumer<AbstractWidget> adder, String key,
                            float floor, float cap, float initial, Consumer<Float> onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key,
                Math.round(mapped * 100), Math.round(cap * 100));
        PowerSlider slider = new PowerSlider(x, y, w, h, floor, cap, 0.01, initial, label,
                mapped -> onChange.accept((float) mapped));
        adder.accept(slider);
        addValueBox(x, y, w, h, font, adder, slider, PERCENT);
    }

    /**
     * A slider over a ratio, shown and typed in the quantity that ratio actually produces: a damage dial in damage, a
     * reach dial in blocks. {@code basis} is what 100% is worth in those units, worked out by the server with the
     * very formulas the skills use.
     */
    private void addScaled(int x, int y, int w, int h, Font font, Consumer<AbstractWidget> adder, String key,
                           float floor, float cap, float initial, double basis, Consumer<Float> onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key,
                format(mapped * basis), format(cap * basis));
        PowerSlider slider = new PowerSlider(x, y, w, h, floor, cap, 0.01, initial, label,
                mapped -> onChange.accept((float) mapped));
        adder.accept(slider);
        addValueBox(x, y, w, h, font, adder, slider, basis);
    }

    /**
     * The box beside a slider, where the same value can be typed instead of dragged to. It is typed in the very
     * number the slider's label shows; typing moves the slider (clamped to its range) and dragging rewrites the box,
     * so the two never disagree.
     */
    private static void addValueBox(int x, int y, int sliderWidth, int height, Font font,
                                    Consumer<AbstractWidget> adder, PowerSlider slider, double shownPerUnit) {
        EditBox box = new EditBox(font, x + sliderWidth + BOX_GAP, y, BOX_WIDTH, height,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.value"));
        box.setMaxLength(6);
        box.setValue(format(slider.mappedValue() * shownPerUnit));
        box.setResponder(text -> slider.accept(parse(text) / shownPerUnit));
        slider.setMirror(value -> box.setValue(format(value * shownPerUnit)));
        adder.accept(box);
    }

    /** The typed text as a number, or NaN while it is not one yet. */
    private static double parse(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
    }

    /** Whole numbers stay whole; a fraction is kept to a tenth. */
    private static String format(double value) {
        double tenths = Math.round(value * 10.0) / 10.0;
        return tenths == Math.rint(tenths) ? String.valueOf((long) tenths) : String.valueOf(tenths);
    }

    /**
     * A slider over an arbitrary [floor, cap] range. The bottom of its travel is the dial's own floor, so the whole of
     * the bar is usable. Inactive (but still visible) only when there is nothing between the two ends at all.
     */
    private static final class PowerSlider extends AbstractSliderButton {
        private final double floor;
        private final double cap;
        private final double step;
        private final Function<Double, Component> labelFactory;
        private final DoubleConsumer onChange;
        /** Told the new value when the slider is dragged, so the box beside it can be rewritten. */
        private DoubleConsumer mirror = value -> {};

        PowerSlider(int x, int y, int width, int height, double floor, double cap, double step, double initial,
                    Function<Double, Component> labelFactory, DoubleConsumer onChange) {
            super(x, y, width, height, Component.empty(), normalize(initial, floor, cap));
            this.floor = floor;
            this.cap = cap;
            this.step = step;
            this.labelFactory = labelFactory;
            this.onChange = onChange;
            this.active = cap > floor;
            updateMessage();
        }

        void setMirror(DoubleConsumer mirror) {
            this.mirror = mirror;
        }

        void accept(double typed) {
            if (Double.isNaN(typed)) {
                return;
            }
            double clamped = Mth.clamp(typed, Math.min(floor, cap), Math.max(floor, cap));
            this.value = normalize(clamped, floor, cap);
            updateMessage();
            onChange.accept(mappedValue());
        }

        private static double normalize(double v, double floor, double cap) {
            return cap <= floor ? 0.0 : Mth.clamp((v - floor) / (cap - floor), 0.0, 1.0);
        }

        double mappedValue() {
            double raw = floor + value * (cap - floor);
            if (step > 0) {
                raw = Math.round(raw / step) * step;
            }
            return Mth.clamp(raw, floor, Math.max(floor, cap));
        }

        @Override
        protected void updateMessage() {
            setMessage(labelFactory.apply(mappedValue()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(mappedValue());
            mirror.accept(mappedValue());
        }
    }
}
