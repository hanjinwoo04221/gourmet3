package org.example.hanjinwoo.gourmet2.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.CellGrowth;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;

/**
 * The {@code /gourmet2 celllevel} command: reads and moves a player's Gourmet Cell level, for testing the
 * evolution curve without having to eat a mountain of food first.
 *
 * <p>The level is not stored — it is worked out from Cell XP ({@link CellEvolution}) — so setting it means
 * setting the XP to whatever that level starts at, and adding to it means finding the XP of the level that
 * many above. That keeps the command out of the save file entirely: there is no second number to drift.
 *
 * <p>It acts on whoever runs it, so a gamemaster moves another player's with {@code /execute as <player>}.
 */
public final class CellLevelCommand {
    /**
     * Highest level the command will take. Not a ceiling on the game — a level has none — but the body that
     * goes with a level is worked out by replaying that many of them, and a slipped zero should not be able to
     * stall the server for a second.
     */
    private static final int MAX_ARGUMENT = 10000;

    private CellLevelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Gourmet2.MODID)
                .then(Commands.literal("celllevel")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> report(context.getSource()))
                        .then(Commands.literal("get")
                                .executes(context -> report(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, MAX_ARGUMENT))
                                        .executes(context -> move(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "level"), true))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("levels", IntegerArgumentType.integer(1, MAX_ARGUMENT))
                                        .executes(context -> move(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "levels"), false))))));
    }

    /** Says what level the player is on and how far into the next one they are. */
    private static int report(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TorikoData data = ModAttachments.of(player);
        int level = data.cellLevel();
        long need = CellEvolution.xpForNextLevel(level);
        long into = CellEvolution.xpIntoLevel(data.cellXp(), level);
        source.sendSuccess(() -> Component.translatable(
                "command." + Gourmet2.MODID + ".cell_level", level, into, need), false);
        return level;
    }

    /**
     * Moves the player to the level {@code value}, either absolutely or as a number of levels to add.
     *
     * @return the level the player ends up on, which is what the command reports as its result
     */
    private static int move(CommandSourceStack source, int value, boolean absolute) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TorikoData data = ModAttachments.of(player);
        int before = data.cellLevel();
        int target = absolute ? value : before + value;

        data.setCellXp(CellEvolution.xpForLevel(target));
        // A level is a body as well as a ceiling on what the skills may be tuned to: hand over whatever the
        // jump earned (or take back what a drop no longer pays for), say what changed, and pull any setting
        // the old level allowed back inside the caps of the new one.
        Component grown = CellGrowth.grow(player, before, target);
        data.clampSettingsToLevel();
        SkillEngine.sync(player, data);

        int after = data.cellLevel();
        source.sendSuccess(() -> Component.translatable(
                "command." + Gourmet2.MODID + ".cell_level_set", after, before), true);
        if (grown != null) {
            player.displayClientMessage(grown, false);
        }
        return after;
    }
}
