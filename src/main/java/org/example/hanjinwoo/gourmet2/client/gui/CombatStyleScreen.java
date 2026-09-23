package org.example.hanjinwoo.gourmet2.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.client.ModKeys;
import org.example.hanjinwoo.gourmet2.network.C2SSelectCombatStyle;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyle;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyles;

import java.util.List;

/**
 * Picks the combat style used in combat mode. Every registered style gets a row; the choice applies at
 * once. Under the description, the hovered style's attack groups are listed with the key that presses
 * each of them, since a style's moves are split over one key per form.
 */
public class CombatStyleScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 200;
    /** Room left under the list for the description plus one line per attack group. */
    private static final int GROUP_LINE_HEIGHT = 11;

    private String hovered;

    public CombatStyleScreen() {
        super(Component.translatable("gui." + Gourmet2.MODID + ".combat_style.title"));
    }

    @Override
    protected void init() {
        List<CombatStyle> styles = CombatStyles.ALL;
        int top = height / 2 - (styles.size() * ROW_HEIGHT) / 2 - 20;
        int x = width / 2 - LIST_WIDTH / 2;
        for (int i = 0; i < styles.size(); i++) {
            CombatStyle style = styles.get(i);
            boolean current = style.id().equals(ClientTorikoData.combatStyle());
            Component label = current
                    ? Component.literal("> ").append(style.displayName()).append(" <")
                    : style.displayName();
            addRenderableWidget(Button.builder(label, b -> {
                ClientTorikoData.setCombatStyleLocally(style.id());
                PacketDistributor.sendToServer(new C2SSelectCombatStyle(style.id()));
                rebuildWidgets();
            }).bounds(x, top + i * ROW_HEIGHT, LIST_WIDTH, 20).build());
        }
        int textTop = top + styles.size() * ROW_HEIGHT + 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, textTop + 30 + CombatStyles.MAX_GROUPS * GROUP_LINE_HEIGHT, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        List<CombatStyle> styles = CombatStyles.ALL;
        int top = height / 2 - (styles.size() * ROW_HEIGHT) / 2 - 20;
        graphics.drawCenteredString(font, title, width / 2, top - 22, 0xFFFFFF);

        // The description shown is the hovered style's, or the current one when nothing is hovered.
        CombatStyle shown = CombatStyles.get(ClientTorikoData.combatStyle());
        int x = width / 2 - LIST_WIDTH / 2;
        for (int i = 0; i < styles.size(); i++) {
            int y = top + i * ROW_HEIGHT;
            if (mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= y && mouseY < y + 20) {
                shown = styles.get(i);
            }
        }
        int textY = top + styles.size() * ROW_HEIGHT + 2;
        graphics.drawCenteredString(font, shown.description(), width / 2, textY, 0xAAAAAA);

        // Which key presses which of the style's attack groups, and how many moves each group has.
        int lineY = textY + 14;
        for (int i = 0; i < shown.groups().size() && i < CombatStyles.MAX_GROUPS; i++) {
            Component line = Component.literal(attackKeyName(i) + "  ").withStyle(net.minecraft.ChatFormatting.YELLOW)
                    .append(shown.groups().get(i).displayName().copy().withStyle(net.minecraft.ChatFormatting.WHITE))
                    .append(Component.literal("  x" + shown.groups().get(i).moves().size())
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            graphics.drawCenteredString(font, line, width / 2, lineY, 0xAAAAAA);
            lineY += GROUP_LINE_HEIGHT;
        }
    }

    /** The key that presses one of a style's attack groups: group 0 is the attack button, the rest the extra keys. */
    private static String attackKeyName(int group) {
        return switch (group) {
            case 0 -> Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage().getString();
            case 1 -> ModKeys.ATTACK_GROUP_2.getTranslatedKeyMessage().getString();
            default -> ModKeys.ATTACK_GROUP_3.getTranslatedKeyMessage().getString();
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
