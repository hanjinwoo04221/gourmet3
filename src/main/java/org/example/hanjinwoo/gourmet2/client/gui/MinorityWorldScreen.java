package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientMinorityWorld;
import org.example.hanjinwoo.gourmet2.network.C2SMinoritySettings;
import org.example.hanjinwoo.gourmet2.skill.MinorityWorld;

/**
 * Minority World settings: each ability, and who it reaches, is an on/off button. The choice is saved with Done and
 * applies to the zone right away if it is standing.
 */
public class MinorityWorldScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 240;

    private static final int[] ABILITIES = {
            MinorityWorld.VISION, MinorityWorld.HEAL, MinorityWorld.ATTACK_DIRECTION, MinorityWorld.FLIGHT,
            MinorityWorld.SOLID_TO_LIQUID, MinorityWorld.LIQUID_TO_SOLID};
    private static final String[] ABILITY_IDS = {
            "vision", "heal", "attack_direction", "flight", "solid_to_liquid", "liquid_to_solid"};

    private int flags;

    public MinorityWorldScreen() {
        super(Component.translatable("gui." + Gourmet2.MODID + ".minority.title"));
        this.flags = ClientMinorityWorld.myFlags();
    }

    @Override
    protected void init() {
        int left = width / 2 - BUTTON_WIDTH / 2;
        int top = height / 2 - 6 * (BUTTON_HEIGHT + 2);
        int row = 0;
        for (int i = 0; i < ABILITIES.length; i++) {
            addToggle(left, top + row++ * (BUTTON_HEIGHT + 2), ABILITIES[i], ABILITY_IDS[i]);
        }
        row++;
        addToggle(left, top + row++ * (BUTTON_HEIGHT + 2), MinorityWorld.AFFECT_SELF, "affect_self");
        addToggle(left, top + row++ * (BUTTON_HEIGHT + 2), MinorityWorld.AFFECT_OTHERS, "affect_others");
        row++;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            PacketDistributor.sendToServer(new C2SMinoritySettings(flags));
            onClose();
        }).bounds(width / 2 - 100, top + row * (BUTTON_HEIGHT + 2), 200, BUTTON_HEIGHT).build());
    }

    private void addToggle(int x, int y, int bit, String id) {
        boolean on = (flags & bit) != 0;
        Component state = Component.translatable("gui." + Gourmet2.MODID + (on ? ".minority.on" : ".minority.off"));
        Component label = Component.translatable("gui." + Gourmet2.MODID + ".minority." + id).append(": ").append(state);
        addRenderableWidget(Button.builder(label, b -> {
            flags ^= bit;
            rebuildWidgets();
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = height / 2 - 6 * (BUTTON_HEIGHT + 2);
        graphics.drawCenteredString(font, title, width / 2, top - 22, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
