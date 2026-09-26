package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;

/**
 * The power dials that are not a skill's own: how hard the caster's own blows hit and how far their leap carries. Every
 * dial that belongs to a skill lives on the skill tree instead, on the skill it tunes (see {@code SkillSlotsScreen}).
 * Both dials here are throttles: 100% is whatever the caster's progress already earns them, and they can only be
 * held back from it.
 */
public class SkillSettingsScreen extends Screen {
    private static final int SLIDER_WIDTH = 168;
    private static final int ROW_HEIGHT = 24;

    private final PowerDials dials = new PowerDials();
    private int panelTop;

    public SkillSettingsScreen() {
        super(Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.title"));
    }

    @Override
    protected void init() {
        int x = width / 2 - PowerDials.rowWidth(SLIDER_WIDTH) / 2;
        panelTop = height / 2 - 40;
        dials.add(PowerDials.Dial.ATTACK_DAMAGE, x, panelTop, SLIDER_WIDTH, 20, font, this::addRenderableWidget);
        dials.add(PowerDials.Dial.LEAP_DISTANCE, x, panelTop + ROW_HEIGHT, SLIDER_WIDTH, 20, font,
                this::addRenderableWidget);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            PacketDistributor.sendToServer(dials.toPacket());
            onClose();
        }).bounds(width / 2 - 100, panelTop + 2 * ROW_HEIGHT + 10, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelTop - 26, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_settings.cell_level", ClientTorikoData.cellLevel()),
                width / 2, panelTop - 15, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
