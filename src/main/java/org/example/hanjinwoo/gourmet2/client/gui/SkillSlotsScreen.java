package org.example.hanjinwoo.gourmet2.client.gui;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.client.ClientMinorityWorld;
import org.example.hanjinwoo.gourmet2.network.C2SMinoritySettings;
import org.example.hanjinwoo.gourmet2.network.C2SSetSkillSlots;
import org.example.hanjinwoo.gourmet2.skill.MinorityWorld;
import org.example.hanjinwoo.gourmet2.skill.SkillTree;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.hanjinwoo.gourmet2.client.gui.PowerDials.Dial;

/**
 * The skill tree. Every tree spreads out from one origin (the Gourmet Cell), one skill growing out of the one before
 * it, and a skill lights up as the cell level reaches it. Drag to move around, scroll to zoom. Click a lit skill to
 * pick it up, then click a hotbar slot along the bottom to put it there; click a slot with nothing picked to empty
 * it. Those slots are the hotbar used in combat mode.
 *
 * <p>The picked skill's own power dials appear in a panel on the right (see {@link PowerDials}); every change is saved
 * with Done, together with the hotbar.
 */
public class SkillSlotsScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int SLOT_WIDTH = 50;
    private static final int NODE = 26;
    private static final int LOCKED_COLOR = 0xFF4A4A5A;
    private static final int LOCKED_LINE = 0xFF3A3A48;
    private static final int PANEL_WIDTH = 250;
    private static final int SLIDER_WIDTH = 168;
    private static final int DIAL_HEIGHT = 20;
    private static final int DIAL_ROW = 26;

    /** The power dials each skill owns. A skill missing from here has none to tune. */
    private static final Map<SkillType, List<Dial>> SKILL_DIALS = new EnumMap<>(SkillType.class);

    static {
        SKILL_DIALS.put(SkillType.NAIL_PUNCH, List.of(Dial.NAIL_COMBO));
        SKILL_DIALS.put(SkillType.NAIL_GUN, List.of(Dial.NAIL_GUN_SHOTS));
        SKILL_DIALS.put(SkillType.FLYING_FORK,
                List.of(Dial.FORK_PROJECTILES, Dial.FLYING_DAMAGE, Dial.FLYING_SIZE, Dial.RANGE));
        SKILL_DIALS.put(SkillType.FLYING_KNIFE,
                List.of(Dial.KNIFE_WAVES, Dial.FLYING_DAMAGE, Dial.FLYING_SIZE, Dial.RANGE));
        SKILL_DIALS.put(SkillType.KI_RELEASE, List.of(Dial.KI_OUTPUT));
        SKILL_DIALS.put(SkillType.CHOPSTICKS, List.of(Dial.FLYING_SIZE));
        SKILL_DIALS.put(SkillType.CHOPSTICK_FIST,
                List.of(Dial.FORK_PROJECTILES, Dial.FLYING_DAMAGE, Dial.FLYING_SIZE, Dial.RANGE));
        SKILL_DIALS.put(SkillType.CHOPSTICK_STAB, List.of(Dial.FLYING_DAMAGE, Dial.FLYING_SIZE, Dial.RANGE));
        SKILL_DIALS.put(SkillType.CHOPSTICK_FLURRY,
                List.of(Dial.NAIL_COMBO, Dial.FLYING_DAMAGE, Dial.FLYING_SIZE, Dial.RANGE));
        SKILL_DIALS.put(SkillType.CHOPSTICK_SINGLE, List.of(Dial.FLYING_DAMAGE, Dial.FLYING_SIZE, Dial.RANGE));
        SKILL_DIALS.put(SkillType.CHOPSTICK_ASURA,
                List.of(Dial.FORK_PROJECTILES, Dial.FLYING_DAMAGE, Dial.FLYING_SIZE, Dial.RANGE));
        SKILL_DIALS.put(SkillType.MINORITY_WORLD, List.of(Dial.FLYING_SIZE));
    }

    private final PowerDials dials = new PowerDials();

    /** Minority World's rules as they are being edited here; saved with Done. */
    private int minorityFlags = ClientMinorityWorld.myFlags();
    private static final int[] MINORITY_BITS = {
            MinorityWorld.VISION, MinorityWorld.HEAL, MinorityWorld.ATTACK_DIRECTION, MinorityWorld.FLIGHT,
            MinorityWorld.SOLID_TO_LIQUID, MinorityWorld.LIQUID_TO_SOLID,
            MinorityWorld.AFFECT_SELF, MinorityWorld.AFFECT_OTHERS};
    private static final String[] MINORITY_IDS = {
            "vision", "heal", "attack_direction", "flight", "solid_to_liquid", "liquid_to_solid",
            "affect_self", "affect_others"};
    private static final int TOGGLE_HEIGHT = 16;
    private static final int TOGGLE_ROW = 17;

    private final int[] slots = new int[TorikoData.SLOT_COUNT];
    private final List<SkillTreeLayout.Node> nodes = SkillTreeLayout.build();
    private final Map<SkillType, SkillTreeLayout.Node> byId = new HashMap<>();
    private int picked = -1;

    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean dragged;
    private double pressX;
    private double pressY;

    public SkillSlotsScreen() {
        super(Component.translatable("gui." + Gourmet2.MODID + ".skill_slots.title"));
        for (int i = 0; i < slots.length; i++) {
            slots[i] = ClientTorikoData.skillSlot(i);
        }
        for (SkillTreeLayout.Node node : nodes) {
            byId.put(node.skill(), node);
        }
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        int total = slots.length * (SLOT_WIDTH + 2) - 2;
        int left = width / 2 - total / 2;
        int y = height - BUTTON_HEIGHT - 26;
        for (int i = 0; i < slots.length; i++) {
            final int index = i;
            Component name = slots[i] < 0
                    ? Component.translatable("gui." + Gourmet2.MODID + ".skill_slots.empty")
                    : SkillType.byIndex(slots[i]).displayName();
            addRenderableWidget(Button.builder(Component.literal((i + 1) + " ").append(name), b -> {
                slots[index] = picked;
                rebuildWidgets();
            }).bounds(left + i * (SLOT_WIDTH + 2), y, SLOT_WIDTH, BUTTON_HEIGHT).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            PacketDistributor.sendToServer(dials.toPacket());
            PacketDistributor.sendToServer(new C2SMinoritySettings(minorityFlags));
            PacketDistributor.sendToServer(new C2SSetSkillSlots(slots.clone()));
            onClose();
        }).bounds(width / 2 - 50, height - BUTTON_HEIGHT - 4, 100, BUTTON_HEIGHT).build());

        if (picked >= 0) {
            SkillType skill = SkillType.byIndex(picked);
            int x = width - PANEL_WIDTH + (PANEL_WIDTH - PowerDials.rowWidth(SLIDER_WIDTH)) / 2;
            int dialY = 50;
            for (Dial dial : SKILL_DIALS.getOrDefault(skill, List.of())) {
                dials.add(dial, x, dialY, SLIDER_WIDTH, DIAL_HEIGHT, font, this::addRenderableWidget);
                dialY += DIAL_ROW;
            }
            if (skill == SkillType.MINORITY_WORLD) {
                for (int i = 0; i < MINORITY_BITS.length; i++) {
                    if (i == MINORITY_BITS.length - 2) {
                        dialY += 6;
                    }
                    addMinorityToggle(x, dialY, MINORITY_BITS[i], MINORITY_IDS[i]);
                    dialY += TOGGLE_ROW;
                }
            }
        }
    }

    private void addMinorityToggle(int x, int y, int bit, String id) {
        boolean on = (minorityFlags & bit) != 0;
        Component state = Component.translatable("gui." + Gourmet2.MODID + (on ? ".minority.on" : ".minority.off"));
        Component label = Component.translatable("gui." + Gourmet2.MODID + ".minority." + id).append(": ").append(state);
        addRenderableWidget(Button.builder(label, b -> {
            minorityFlags ^= bit;
            rebuildWidgets();
        }).bounds(x, y, PowerDials.rowWidth(SLIDER_WIDTH), TOGGLE_HEIGHT).build());
    }

    private boolean panelOpen() {
        return picked >= 0;
    }

    /** The width the tree has to itself: all of it, or what the dial panel leaves. */
    private int viewWidth() {
        return panelOpen() ? width - PANEL_WIDTH : width;
    }

    // ------------------------------------------------------------ coordinates

    private double originX() {
        return viewWidth() / 2.0 + panX;
    }

    private double originY() {
        return (height - 60) / 2.0 + panY;
    }

    private double screenX(double x) {
        return originX() + x * zoom;
    }

    private double screenY(double y) {
        return originY() + y * zoom;
    }

    private double nodeHalf() {
        return NODE * zoom / 2.0;
    }

    private SkillTreeLayout.Node nodeAt(double mouseX, double mouseY) {
        double half = nodeHalf();
        for (SkillTreeLayout.Node node : nodes) {
            if (Math.abs(mouseX - screenX(node.x())) <= half && Math.abs(mouseY - screenY(node.y())) <= half) {
                return node;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        dragged = false;
        pressX = mouseX;
        pressY = mouseY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3.0) {
            dragged = true;
        }
        panX += dragX;
        panY += dragY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (!dragged && button == 0) {
            SkillTreeLayout.Node node = nodeAt(mouseX, mouseY);
            if (node != null && node.skill().isUnlocked(ClientTorikoData.cellLevel())) {
                int ordinal = node.skill().ordinal();
                picked = picked == ordinal ? -1 : ordinal;
                rebuildWidgets();
                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double next = Mth.clamp(zoom * (scrollY > 0 ? 1.1 : 1.0 / 1.1), 0.5, 2.0);
        // Zoom about the cursor so what is under it stays put.
        double ratio = next / zoom;
        panX = mouseX - viewWidth() / 2.0 - (mouseX - originX()) * ratio;
        panY = mouseY - (height - 60) / 2.0 - (mouseY - originY()) * ratio;
        zoom = next;
        return true;
    }

    // -------------------------------------------------------------- rendering

    /**
     * The tree is drawn as the background: the base screen paints its background again at the start of its own render,
     * and its blur would otherwise sit over a tree drawn before it and leave everything looking hazy. A flat dark
     * fill is used instead of the blur, and the widgets (slots, dials) are drawn on top of the tree afterwards.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101018);
        int level = ClientTorikoData.cellLevel();
        drawBranches(graphics, level);
        drawTreeLabels(graphics);
        drawOrigin(graphics, level);
        for (SkillTreeLayout.Node node : nodes) {
            drawNode(graphics, node, level, node.skill().ordinal() == picked);
        }
        if (panelOpen()) {
            graphics.fill(width - PANEL_WIDTH, 0, width, height, 0xFF181822);
            graphics.renderOutline(width - PANEL_WIDTH, 0, PANEL_WIDTH, height, 0xFF3A3A4A);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int level = ClientTorikoData.cellLevel();
        super.render(graphics, mouseX, mouseY, partialTick);

        if (panelOpen()) {
            SkillType skill = SkillType.byIndex(picked);
            int cx = width - PANEL_WIDTH / 2;
            graphics.drawCenteredString(font,
                    Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.tuning", skill.displayName()),
                    cx, 14, 0xFF000000 | skill.color());
            if (SKILL_DIALS.getOrDefault(skill, List.of()).isEmpty() && skill != SkillType.MINORITY_WORLD) {
                graphics.drawCenteredString(font,
                        Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.no_dials"), cx, 50, 0x888899);
            }
        }

        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.hint"), width / 2, 18, 0xAAAAAA);

        SkillTreeLayout.Node hovered = nodeAt(mouseX, mouseY);
        if (hovered != null && mouseY < height - BUTTON_HEIGHT - 30) {
            SkillType skill = hovered.skill();
            boolean unlocked = skill.isUnlocked(level);
            graphics.renderTooltip(font, List.of(
                    skill.displayName().getVisualOrderText(),
                    Component.translatable(skill.descriptionKey()).getVisualOrderText(),
                    (unlocked
                            ? Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.unlocked", skill.unlockLevel())
                            : Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.requires", skill.unlockLevel(), level))
                            .getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void drawBranches(GuiGraphics graphics, int level) {
        for (SkillTreeLayout.Node node : nodes) {
            boolean lit = node.skill().isUnlocked(level);
            int color = lit ? 0xFF000000 | node.skill().tree().color() : LOCKED_LINE;
            double fromX;
            double fromY;
            if (node.parent() == null) {
                fromX = originX();
                fromY = originY();
            } else {
                SkillTreeLayout.Node parent = byId.get(node.parent());
                fromX = screenX(parent.x());
                fromY = screenY(parent.y());
            }
            line(graphics, fromX, fromY, screenX(node.x()), screenY(node.y()), color);
        }
    }

    private static void line(GuiGraphics graphics, double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        graphics.pose().pushPose();
        graphics.pose().translate((float) x1, (float) y1, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        graphics.fill(0, -1, (int) length, 1, color);
        graphics.pose().popPose();
    }

    private void drawTreeLabels(GuiGraphics graphics) {
        for (SkillTree tree : SkillTree.VALUES) {
            double[] spoke = SkillTreeLayout.spoke(tree);
            int x = (int) (originX() + spoke[0] * 44 * zoom);
            int y = (int) (originY() + spoke[1] * 44 * zoom);
            Component name = tree.displayName();
            graphics.drawString(font, name, x - font.width(name) / 2, y - 4, 0xFF000000 | tree.color(), true);
        }
    }

    private void drawOrigin(GuiGraphics graphics, int level) {
        int r = (int) (16 * zoom);
        int cx = (int) originX();
        int cy = (int) originY();
        graphics.fill(cx - r, cy - r, cx + r, cy + r, 0xFF20202C);
        graphics.renderOutline(cx - r, cy - r, r * 2, r * 2, 0xFFFFE066);
        Component label = Component.translatable("gui." + Gourmet2.MODID + ".skill_tree.origin", level);
        graphics.drawCenteredString(font, label, cx, cy - 4, 0xFFFFE066);
    }

    private void drawNode(GuiGraphics graphics, SkillTreeLayout.Node node, int level, boolean picked) {
        SkillType skill = node.skill();
        boolean unlocked = skill.isUnlocked(level);
        int half = (int) nodeHalf();
        int cx = (int) screenX(node.x());
        int cy = (int) screenY(node.y());
        int color = unlocked ? 0xFF000000 | skill.color() : LOCKED_COLOR;

        graphics.fill(cx - half - 2, cy - half - 2, cx + half + 2, cy + half + 2, unlocked ? 0xFF101018 : 0xFF181820);
        graphics.fill(cx - half, cy - half, cx + half, cy + half, color);
        if (unlocked) {
            // A lit node has a lighter core so it reads as switched on.
            graphics.fill(cx - half / 2, cy - half / 2, cx + half / 2, cy + half / 2, 0x66FFFFFF);
        }
        graphics.renderOutline(cx - half - 2, cy - half - 2, half * 2 + 4, half * 2 + 4,
                picked ? 0xFFFFFFFF : unlocked ? 0xFF000000 | skill.tree().color() : 0xFF2A2A34);

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy + half + 4, 0.0F);
        graphics.pose().scale(0.75F, 0.75F, 1.0F);
        Component name = skill.displayName();
        graphics.drawString(font, name, -font.width(name) / 2, 0, unlocked ? 0xFFFFFF : 0x777788, true);
        if (!unlocked) {
            Component need = Component.literal("Lv " + skill.unlockLevel());
            graphics.drawString(font, need, -font.width(need) / 2, 10, 0x888899, false);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
