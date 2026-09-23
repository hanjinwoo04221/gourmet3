package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.network.C2SSetSkillSlots;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * Skill selection window: pick a skill on the left, then click a hotbar slot on the right to put it
 * there; click a slot with nothing picked to empty it. Those slots are the hotbar used in combat mode.
 */
public class SkillSlotsScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_WIDTH = 140;
    private static final int SLOT_WIDTH = 130;

    private final int[] slots = new int[TorikoData.SLOT_COUNT];
    private int picked = -1;

    public SkillSlotsScreen() {
        super(Component.translatable("gui." + Gourmet2.MODID + ".skill_slots.title"));
        for (int i = 0; i < slots.length; i++) {
            slots[i] = ClientTorikoData.skillSlot(i);
        }
    }

    @Override
    protected void init() {
        int top = height / 2 - 6 * (BUTTON_HEIGHT + 2);
        int listX = width / 2 - LIST_WIDTH - 10;
        int slotX = width / 2 + 10;

        for (SkillType skill : SkillType.VALUES) {
            int row = skill.ordinal();
            Component label = skill.displayName();
            if (picked == row) {
                label = Component.literal("> ").append(label);
            }
            addRenderableWidget(Button.builder(label, b -> {
                picked = picked == skill.ordinal() ? -1 : skill.ordinal();
                rebuildWidgets();
            }).bounds(listX, top + row * (BUTTON_HEIGHT + 2), LIST_WIDTH, BUTTON_HEIGHT).build());
        }

        for (int i = 0; i < slots.length; i++) {
            final int index = i;
            Component name = slots[i] < 0
                    ? Component.translatable("gui." + Gourmet2.MODID + ".skill_slots.empty")
                    : SkillType.byIndex(slots[i]).displayName();
            addRenderableWidget(Button.builder(Component.literal((i + 1) + "  ").append(name), b -> {
                slots[index] = picked;
                rebuildWidgets();
            }).bounds(slotX, top + i * (BUTTON_HEIGHT + 2), SLOT_WIDTH, BUTTON_HEIGHT).build());
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            PacketDistributor.sendToServer(new C2SSetSkillSlots(slots.clone()));
            onClose();
        }).bounds(width / 2 - 100, top + 13 * (BUTTON_HEIGHT + 2) - 10, 200, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = height / 2 - 6 * (BUTTON_HEIGHT + 2);
        graphics.drawCenteredString(font, title, width / 2, top - 26, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_slots.hint"), width / 2, top - 14, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
