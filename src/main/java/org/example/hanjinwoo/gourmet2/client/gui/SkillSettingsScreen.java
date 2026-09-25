package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
 *
 * <p>Every dial runs both ways. The level sets its ceiling, the setting's own floor ({@code *_FLOOR}) is how far
 * back it may be held, and the bar between the two is the player's to place the handle on: the {@code *_BASE}
 * default a skill has always had sits inside that range rather than at the bottom of it, so a technique that has
 * outgrown what the player wanted to fight with can be reeled back in without giving up the level that earned it.
 * A dial with nothing to spend is not a dead one either — on a fresh character the ceiling is the default and the
 * floor is still below it, so there is always somewhere for the handle to go.
 *
 * <p>The three that are throttles first — attack damage, leap reach and the reach of the ranged techniques — are
 * the same shape seen from the other end: their ceiling is 100% of whatever the player's own progress already
 * earns them, and their floor is nothing at all ({@link CellEvolution#ATTACK_DAMAGE_FLOOR}), so a technique can be
 * wound off entirely without ever leaving the hotbar. Nothing about the level gates them, because there is nothing
 * to unlock — the growth has already happened, and this is only how to run below it.
 */
public class SkillSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int SLIDER_WIDTH = 168;
    private static final int SLIDER_HEIGHT = 20;
    /** The box beside each slider, where the same value can be typed in exactly. */
    private static final int BOX_WIDTH = 52;
    private static final int BOX_GAP = 4;
    /** How many sliders there are, and the tightest they may be squeezed into when the screen is short. */
    private static final int ROWS = 10;
    private static final int MIN_ROW_HEIGHT = 16;
    /** Where the rows may start at the very least, so the two header lines above them stay on screen. */
    private static final int MIN_PANEL_TOP = 30;

    /** Where the rows were actually laid out, so the header can sit above them on any screen height. */
    private int panelTop;
    private int rowHeight = ROW_HEIGHT;
    private int sliderHeight = SLIDER_HEIGHT;

    private int nailCombo;
    private int forkProjectiles;
    private int nailGunShots;
    private int knifeWaves;
    private float flyingDamage;
    private float flyingSize;
    private int kiOutput;
    private float attackDamage;
    private float leapDistance;
    private float range;

    public SkillSettingsScreen() {
        super(Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.title"));
        this.nailCombo = ClientTorikoData.nailComboSetting();
        this.forkProjectiles = ClientTorikoData.forkProjectileSetting();
        this.nailGunShots = ClientTorikoData.nailGunShotSetting();
        this.knifeWaves = ClientTorikoData.knifeWaveSetting();
        this.flyingDamage = ClientTorikoData.flyingDamageSetting();
        this.flyingSize = ClientTorikoData.flyingSizeSetting();
        this.kiOutput = ClientTorikoData.kiOutputSetting();
        this.attackDamage = ClientTorikoData.attackDamageSetting();
        this.leapDistance = ClientTorikoData.leapDistanceSetting();
        this.range = ClientTorikoData.rangeSetting();
    }

    @Override
    protected void init() {
        int level = ClientTorikoData.cellLevel();
        // The rows tighten up rather than run off the bottom: this has to fit an auto-scaled 1080p window, which is
        // only 270 pixels of screen height, and it grew another row when the ranged reach dial moved in.
        this.rowHeight = Math.min(ROW_HEIGHT, Math.max(MIN_ROW_HEIGHT, (height - 120) / ROWS));
        this.panelTop = height / 2 - (ROWS * rowHeight) / 2 - 4;
        if (panelTop < MIN_PANEL_TOP) {
            // Too short to centre the panel without pushing the header off the top: start below the header and
            // squeeze the rows (and with them every slider) into whatever is left above the DONE button.
            this.rowHeight = Mth.clamp((height - MIN_PANEL_TOP - 34) / ROWS, 1, rowHeight);
            this.panelTop = MIN_PANEL_TOP;
        }
        this.sliderHeight = Math.min(SLIDER_HEIGHT, rowHeight - 2);
        int x = width / 2 - (SLIDER_WIDTH + BOX_GAP + BOX_WIDTH) / 2;
        int y = panelTop;

        // Every dial runs from its own floor up to the cap the Cell level has opened, with the default the skill
        // has always had somewhere inside that range (see CellEvolution): a technique can be wound back below its
        // default as well as pushed up, and on a fresh character — where the cap is the default — it is that floor
        // below it that leaves the handle somewhere to go.
        addIntSlider(x, y, "nail_combo", CellEvolution.NAIL_COMBO_FLOOR, CellEvolution.nailComboCap(level),
                nailCombo, v -> nailCombo = v);
        y += rowHeight;
        addIntSlider(x, y, "fork_projectiles", CellEvolution.FORK_PROJECTILE_FLOOR, CellEvolution.forkProjectileCap(level),
                forkProjectiles, v -> forkProjectiles = v);
        y += rowHeight;
        addIntSlider(x, y, "nail_gun_shots", CellEvolution.NAIL_GUN_SHOT_FLOOR, CellEvolution.nailGunShotCap(level),
                nailGunShots, v -> nailGunShots = v);
        y += rowHeight;
        addIntSlider(x, y, "knife_waves", CellEvolution.KNIFE_WAVE_FLOOR, CellEvolution.knifeWaveCap(level),
                knifeWaves, v -> knifeWaves = v);
        y += rowHeight;
        addScaledSlider(x, y, "flying_damage", CellEvolution.DAMAGE_MULT_FLOOR, CellEvolution.damageMultCap(level),
                flyingDamage, ClientTorikoData.flyingDamageDialBase(), v -> flyingDamage = v);
        y += rowHeight;
        addPercentSlider(x, y, "flying_size", CellEvolution.SIZE_MULT_FLOOR, CellEvolution.sizeMultCap(level),
                flyingSize, v -> flyingSize = v);
        y += rowHeight;
        addIntSlider(x, y, "ki_output", CellEvolution.KI_OUTPUT_FLOOR, CellEvolution.kiOutputCap(level),
                kiOutput, v -> kiOutput = v);
        y += rowHeight;
        // Throttles, not boosts: their own ends are 100%, so the whole range is how far back the player may hold
        // the damage they already hit for, the reach their strength and speed already earn them, and how far
        // their thrown techniques already carry.
        addScaledSlider(x, y, "attack_damage", CellEvolution.ATTACK_DAMAGE_FLOOR,
                CellEvolution.ATTACK_DAMAGE_BASE, attackDamage, ClientTorikoData.attackDialBase(),
                v -> attackDamage = v);
        y += rowHeight;
        addScaledSlider(x, y, "leap_distance", CellEvolution.LEAP_DISTANCE_FLOOR,
                CellEvolution.LEAP_DISTANCE_BASE, leapDistance, ClientTorikoData.leapDialBase(),
                v -> leapDistance = v);
        y += rowHeight;
        addScaledSlider(x, y, "range", CellEvolution.RANGE_FLOOR,
                CellEvolution.RANGE_BASE, range, ClientTorikoData.rangeDialBase(), v -> range = v);
        y += rowHeight + 10;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onDone())
                .bounds(width / 2 - 100, y, 200, 20).build());
    }

    /**
     * A slider over a count — how many hits, shots or waves a technique puts out — running from the dial's own
     * floor up to the cap this player's level has opened (see {@link CellEvolution}). The initial value is
     * wherever the setting stands now, which may be anywhere in that range, its default included.
     */
    private void addIntSlider(int x, int y, String key, int floor, int cap, int initial, java.util.function.IntConsumer onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key, Math.round(mapped), cap);
        PowerSlider slider = new PowerSlider(x, y, SLIDER_WIDTH, sliderHeight, floor, cap, 1.0, initial, label,
                mapped -> onChange.accept((int) Math.round(mapped)));
        addRenderableWidget(slider);
        // A count, so the box shows the count itself.
        addValueBox(x, y, slider, 1.0);
    }

    /** A slider over a multiplier, shown and typed as a whole percentage of its own scale. */
    private void addPercentSlider(int x, int y, String key, float floor, float cap, float initial, java.util.function.Consumer<Float> onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key,
                Math.round(mapped * 100), Math.round(cap * 100));
        PowerSlider slider = new PowerSlider(x, y, SLIDER_WIDTH, sliderHeight, floor, cap, 0.01, initial, label,
                mapped -> onChange.accept((float) mapped));
        addRenderableWidget(slider);
        // A percentage on the label, so the box is typed as the same whole percent: 1.85 reads 185% and takes 185.
        addValueBox(x, y, slider, PERCENT);
    }

    /** What a label multiplies the slider's own value by when it shows a percentage. */
    private static final double PERCENT = 100.0;

    /**
     * A slider over a ratio, shown and typed in the quantity that ratio actually produces: a damage dial in
     * damage, a reach dial in blocks. {@code basis} is what 100% is worth in those units, worked out by the
     * server with the very formulas the skills themselves use, so the numbers here are ones the player can hold
     * against the world rather than shares of a figure they never see.
     *
     * @param basis what the dial's 100% is worth in the units its label is written in
     */
    private void addScaledSlider(int x, int y, String key, float floor, float cap, float initial, double basis,
            java.util.function.Consumer<Float> onChange) {
        Function<Double, Component> label = mapped -> Component.translatable(
                "gui." + Gourmet2.MODID + ".skill_settings." + key,
                format(mapped * basis), format(cap * basis));
        PowerSlider slider = new PowerSlider(x, y, SLIDER_WIDTH, sliderHeight, floor, cap, 0.01, initial, label,
                mapped -> onChange.accept((float) mapped));
        addRenderableWidget(slider);
        addValueBox(x, y, slider, basis);
    }

    /**
     * The box beside a slider, where the same value can be typed instead of dragged to — a slider is fine for
     * rough tuning and hopeless for landing on 185 exactly. It is typed in the very number the slider's own label
     * shows, so a damage multiplier reading 185% takes 185 rather than the 1.85 the slider works in behind it, and
     * a dial that counts hits takes the count. Typing moves the slider (clamped to the same range it has) and
     * dragging rewrites the box, so the two never disagree.
     *
     * @param shownPerUnit what the label multiplies the slider's own value by — 100 where it shows a percentage
     */
    private void addValueBox(int x, int y, PowerSlider slider, double shownPerUnit) {
        EditBox box = new EditBox(font, x + SLIDER_WIDTH + BOX_GAP, y, BOX_WIDTH, sliderHeight,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.value"));
        box.setMaxLength(6);
        box.setValue(format(slider.mappedValue() * shownPerUnit));
        box.setResponder(text -> slider.accept(parse(text) / shownPerUnit));
        slider.setMirror(value -> box.setValue(format(value * shownPerUnit)));
        addRenderableWidget(box);
    }

    /** The typed text as a number, or NaN while it is not one yet — mid-keystroke it rarely is. */
    private static double parse(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
    }

    /**
     * The number as the boxes and labels both show it. Whole numbers stay whole — a count dial is one, and so is
     * every percent — but a fraction is kept to a tenth instead of being rounded away: the dials read in damage
     * and blocks, where a whole unit can be a fifth of the whole travel, and the box feeds back whatever it is
     * showing. Rounded to whole numbers, the first point under one damage displayed as 0 and was sent back as 0,
     * so the handle could not be put below that first whole point at all: drag it any lower and the slider
     * snapped to the end of its travel, with nothing in between to set. A tenth of a unit is fine enough for
     * every bar here, and still reads as a plain number.
     */
    private static String format(double value) {
        double tenths = Math.round(value * 10.0) / 10.0;
        return tenths == Math.rint(tenths) ? String.valueOf((long) tenths) : String.valueOf(tenths);
    }

    private void onDone() {
        PacketDistributor.sendToServer(new C2SUpdateSkillSettings(
                nailCombo, forkProjectiles, knifeWaves, flyingDamage, flyingSize, kiOutput,
                attackDamage, leapDistance, range, nailGunShots));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        // Sitting on the rows themselves rather than on the screen, so the header follows them down on a short one.
        graphics.drawCenteredString(font, title, width / 2, panelTop - 26, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.cell_level", ClientTorikoData.cellLevel()),
                width / 2, panelTop - 15, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * A slider over an arbitrary [floor, cap] range. The bottom of its travel is the dial's own floor rather than
     * the default the setting starts at, so the whole of the bar is usable — the handle sits where the setting
     * stands and can be moved either way from there. Inactive (but still visible) only in the one case where
     * there is nothing between the two ends at all.
     */
    private static final class PowerSlider extends AbstractSliderButton {
        private final double floor;
        private final double cap;
        private final double step;
        private final Function<Double, Component> labelFactory;
        private final DoubleConsumer onChange;
        /** Told the new value when the slider is *dragged*, so the box beside it can be rewritten; a typed value
         *  moves the slider without echoing back into the box, which would eat the keystrokes being typed. */
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

        private void setMirror(DoubleConsumer mirror) {
            this.mirror = mirror;
        }

        /** Puts the slider where a typed number says, as though it had been dragged there. */
        private void accept(double typed) {
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

        private double mappedValue() {
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
