package org.example.hanjinwoo.gourmet2.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.skill.CaptureLevel;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * Bottom-left HUD: the Appetite bar, the selected technique, and a cast/cooldown bar. Cycling
 * skills briefly expands it into the full list so the player can see what they are scrolling
 * through without a dedicated screen.
 */
public class SkillHudLayer implements LayeredDraw.Layer {
    private static final int MARGIN_X = 6;
    private static final int BOTTOM_OFFSET = 46;
    private static final int ROW_HEIGHT = 11;
    private static final int SWATCH = 6;
    private static final int BAR_WIDTH = 104;
    private static final int BAR_HEIGHT = 5;

    private static final int COLOR_PANEL = 0x90000000;
    private static final int COLOR_BORDER = 0xFF2B2B2B;
    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_TEXT_DIM = 0xFF8A8A8A;
    private static final int COLOR_APPETITE = 0xFFFF7A2B;
    private static final int COLOR_APPETITE_LOW = 0xFFB4231C;
    private static final int COLOR_CAST = 0xFF66FF9E;
    private static final int COLOR_AWAKENED = 0xFF66FF9E;
    private static final int COLOR_SELECTION = 0x60FFFFFF;
    private static final int COLOR_CHARGE = 0xFFFFD24B;
    private static final int COLOR_INTIMIDATION = 0xFFB44CFF;

    /** Below this fraction the Appetite bar turns red as a starvation warning. */
    private static final float LOW_APPETITE = 0.25F;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldRender(minecraft)) {
            return;
        }

        if (ClientTorikoData.isCombatMode()) {
            renderCombatHotbar(graphics, minecraft);
        }

        int bottom = graphics.guiHeight() - BOTTOM_OFFSET;
        if (ClientTorikoData.isSelectionHighlighted()) {
            renderSkillList(graphics, minecraft, bottom - ROW_HEIGHT - 4);
        }
        renderSelected(graphics, minecraft, bottom);
        renderAppetiteBar(graphics, bottom + ROW_HEIGHT);
        renderActivity(graphics, minecraft, bottom + ROW_HEIGHT + BAR_HEIGHT + 3);
        renderCaptureLevels(graphics, minecraft, bottom + ROW_HEIGHT + BAR_HEIGHT + 15);
    }

    /** The player's own Capture Level below the bars, and the level of whatever they are looking at. */
    private void renderCaptureLevels(GuiGraphics graphics, Minecraft minecraft, int y) {
        Player self = minecraft.player;
        int own = CaptureLevel.of(self, ClientTorikoData.cellLevel());
        graphics.drawString(minecraft.font, Component.translatable("hud." + Gourmet2.MODID + ".capture_level", own),
                MARGIN_X, y, COLOR_TEXT_DIM, true);

        if (ClientTorikoData.isCombatMode()) {
            graphics.drawString(minecraft.font, Component.translatable("hud." + Gourmet2.MODID + ".combat_mode"),
                    MARGIN_X, y + 12, COLOR_CHARGE, true);
        }

        if (minecraft.crosshairPickEntity instanceof LivingEntity target) {
            // Other players' Gourmet Cell levels are server-side, so only their attributes count here.
            Component label = Component.translatable("hud." + Gourmet2.MODID + ".capture_level_target",
                    CaptureLevel.of(target, 0));
            int x = (graphics.guiWidth() - minecraft.font.width(label)) / 2;
            graphics.drawString(minecraft.font, label, x, graphics.guiHeight() / 2 + 14, COLOR_TEXT, true);
        }
    }

    /** The nine-slot skill hotbar, above the vanilla item hotbar (which stays visible and unchanged). */
    private void renderCombatHotbar(GuiGraphics graphics, Minecraft minecraft) {
        int slotSize = 20;
        int left = graphics.guiWidth() / 2 - slotSize * 9 / 2;
        int top = graphics.guiHeight() - 22 - slotSize - 46;
        int selectedSlot = ClientTorikoData.skillCursor();

        graphics.fill(left - 2, top - 2, left + slotSize * 9 + 2, top + slotSize + 2, COLOR_PANEL);
        for (int i = 0; i < 9; i++) {
            int x = left + i * slotSize;
            int skillIndex = ClientTorikoData.skillSlot(i);
            graphics.renderOutline(x, top, slotSize, slotSize, i == selectedSlot ? 0xFFFFFFFF : COLOR_BORDER);
            graphics.drawString(minecraft.font, String.valueOf(i + 1), x + 2, top + 2, COLOR_TEXT_DIM, false);
            if (skillIndex >= 0) {
                SkillType skill = SkillType.byIndex(skillIndex);
                drawSwatch(graphics, x + 7, top + 8, skill, ClientTorikoData.isReady(skill));
            }
        }
        int skillIndex = ClientTorikoData.skillSlot(selectedSlot);
        if (skillIndex >= 0) {
            Component name = SkillType.byIndex(skillIndex).displayName();
            graphics.drawCenteredString(minecraft.font, name, graphics.guiWidth() / 2, top - 12, COLOR_TEXT);
        }
    }

    private boolean shouldRender(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || player.isSpectator()) {
            return false;
        }
        return !minecraft.options.hideGui && minecraft.screen == null;
    }

    /** The expanded list, drawn upwards from {@code bottom} so it grows away from the hotbar. */
    private void renderSkillList(GuiGraphics graphics, Minecraft minecraft, int bottom) {
        int count = SkillType.COUNT;
        int height = count * ROW_HEIGHT + 4;
        int top = bottom - height;
        int width = BAR_WIDTH;

        graphics.fill(MARGIN_X - 2, top, MARGIN_X + width, bottom, COLOR_PANEL);
        graphics.renderOutline(MARGIN_X - 2, top, width + 2, height, COLOR_BORDER);

        for (int i = 0; i < count; i++) {
            SkillType skill = SkillType.VALUES[i];
            int y = top + 2 + i * ROW_HEIGHT;
            boolean isSelected = i == ClientTorikoData.selectedIndex();
            if (isSelected) {
                graphics.fill(MARGIN_X - 1, y - 1, MARGIN_X + width - 1, y + ROW_HEIGHT - 2, COLOR_SELECTION);
            }
            drawSwatch(graphics, MARGIN_X, y + 1, skill, ClientTorikoData.isReady(skill));
            graphics.drawString(minecraft.font, skill.displayName(),
                    MARGIN_X + SWATCH + 4, y,
                    ClientTorikoData.isReady(skill) ? COLOR_TEXT : COLOR_TEXT_DIM, true);
        }
    }

    private void renderSelected(GuiGraphics graphics, Minecraft minecraft, int y) {
        SkillType skill = ClientTorikoData.selectedSkill();
        boolean ready = ClientTorikoData.isReady(skill);

        drawSwatch(graphics, MARGIN_X, y + 1, skill, ready);
        graphics.drawString(minecraft.font, skill.displayName(),
                MARGIN_X + SWATCH + 4, y, ready ? COLOR_TEXT : COLOR_TEXT_DIM, true);

        int cooldown = ClientTorikoData.cooldown(skill);
        if (cooldown > 0) {
            Component label = Component.literal(String.format("%.1fs", cooldown / 20.0F));
            int labelWidth = minecraft.font.width(label);
            graphics.drawString(minecraft.font, label,
                    MARGIN_X + BAR_WIDTH - labelWidth, y, COLOR_TEXT_DIM, true);
        }
    }

    private void renderAppetiteBar(GuiGraphics graphics, int y) {
        float fraction = ClientTorikoData.appetiteFraction();
        int filled = Math.round(BAR_WIDTH * fraction);
        int color = fraction <= LOW_APPETITE ? COLOR_APPETITE_LOW : COLOR_APPETITE;

        graphics.fill(MARGIN_X, y, MARGIN_X + BAR_WIDTH, y + BAR_HEIGHT, COLOR_PANEL);
        if (filled > 0) {
            graphics.fill(MARGIN_X, y, MARGIN_X + filled, y + BAR_HEIGHT, color);
        }
        graphics.renderOutline(MARGIN_X - 1, y - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2, COLOR_BORDER);
    }

    /** The running skill's bar, a charge-up bar, the Intimidation indicator, or the awakening timer. */
    private void renderActivity(GuiGraphics graphics, Minecraft minecraft, int y) {
        SkillType active = ClientTorikoData.activeSkill();
        if (active != null) {
            int filled = Math.round(BAR_WIDTH * ClientTorikoData.activeProgress());
            graphics.fill(MARGIN_X, y, MARGIN_X + BAR_WIDTH, y + 2, COLOR_PANEL);
            if (filled > 0) {
                graphics.fill(MARGIN_X, y, MARGIN_X + filled, y + 2, COLOR_CAST);
            }
            return;
        }
        if (ClientTorikoData.isCharging()) {
            int filled = Math.round(BAR_WIDTH * ClientTorikoData.chargeProgress());
            graphics.fill(MARGIN_X, y, MARGIN_X + BAR_WIDTH, y + 2, COLOR_PANEL);
            if (filled > 0) {
                graphics.fill(MARGIN_X, y, MARGIN_X + filled, y + 2, COLOR_CHARGE);
            }
            return;
        }
        if (ClientTorikoData.isIntimidationActive()) {
            Component label = Component.translatable(
                    "hud." + Gourmet2.MODID + ".intimidation_active", ClientTorikoData.intimidationStage());
            graphics.drawString(minecraft.font, label, MARGIN_X, y, COLOR_INTIMIDATION, true);
            return;
        }
        if (ClientTorikoData.isAwakened()) {
            Component label = Component.translatable("hud." + Gourmet2.MODID + ".awakened",
                    ClientTorikoData.awakenedTicks() / 20);
            graphics.drawString(minecraft.font, label, MARGIN_X, y, COLOR_AWAKENED, true);
        }
    }

    private void drawSwatch(GuiGraphics graphics, int x, int y, SkillType skill, boolean ready) {
        int color = 0xFF000000 | skill.color();
        graphics.fill(x, y, x + SWATCH, y + SWATCH, ready ? color : dim(color));
    }

    /** Halves each channel so a skill on cooldown reads as unavailable without changing hue. */
    private static int dim(int argb) {
        int r = (argb >> 16 & 0xFF) / 2;
        int g = (argb >> 8 & 0xFF) / 2;
        int b = (argb & 0xFF) / 2;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
