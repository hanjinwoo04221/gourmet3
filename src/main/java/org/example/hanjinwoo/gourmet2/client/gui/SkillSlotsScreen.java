package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.network.C2SSetSkillSlots;
import org.example.hanjinwoo.gourmet2.skill.SkillTree;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * Skill tree window: pick a tree tab, pick an unlocked skill in it (skills unlock with Gourmet Cell level), then
 * click a hotbar slot on the right to put it there; click a slot with nothing picked to empty it. Those slots
 * are the hotbar used in combat mode.
 */
public class SkillSlotsScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_WIDTH = 140;
    private static final int SLOT_WIDTH = 130;

    private final int[] slots = new int[TorikoData.SLOT_COUNT];
    private int picked = -1;
    private static SkillTree currentTree = SkillTree.TECHNIQUE;

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

        int level = ClientTorikoData.cellLevel();
        int tabWidth = LIST_WIDTH / 2 - 1;
        for (SkillTree tree : SkillTree.VALUES) {
            int col = tree.ordinal() % 2;
            int tabRow = tree.ordinal() / 2;
            Component tabLabel = tree == currentTree ? Component.literal("> ").append(tree.displayName()) : tree.displayName();
            addRenderableWidget(Button.builder(tabLabel, b -> {
                currentTree = tree;
                picked = -1;
                rebuildWidgets();
            }).bounds(listX + col * (tabWidth + 2), top + tabRow * (BUTTON_HEIGHT + 2), tabWidth, BUTTON_HEIGHT).build());
        }

        int tabRows = (SkillTree.VALUES.length + 1) / 2;
        int skillTop = top + tabRows * (BUTTON_HEIGHT + 2) + 6;
        int row = 0;
        for (SkillType skill : currentTree.skills()) {
            boolean unlocked = skill.isUnlocked(level);
            Component label;
            if (!unlocked) {
                label = Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.locked",
                        skill.displayName(), skill.unlockLevel());
            } else if (picked == skill.ordinal()) {
                label = Component.literal("> ").append(skill.displayName());
            } else {
                label = skill.displayName();
            }
            Button button = Button.builder(label, b -> {
                picked = picked == skill.ordinal() ? -1 : skill.ordinal();
                rebuildWidgets();
            }).bounds(listX, skillTop + row * (BUTTON_HEIGHT + 2), LIST_WIDTH, BUTTON_HEIGHT).build();
            button.active = unlocked;
            button.setTooltip(Tooltip.create(Component.translatable(skill.descriptionKey())));
            addRenderableWidget(button);
            row++;
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
        graphics.drawCenteredString(font,
                title.copy().append(" - ").append(
                        Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.level", ClientTorikoData.cellLevel())),
                width / 2, top - 26, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_slots.hint"), width / 2, top - 14, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
