package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.KiAuraEntity;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

/**
 * 気の放出 Ki Release — a toggle that radiates the caster's Gourmet Cell ki. While it is on, a
 * cell-coloured aura plays around the caster and everything close is pressed back and lightly
 * hurt every second. The output level (power settings GUI) scales the aura, its reach and its
 * pressure and the Appetite it burns, all of them together — turning the dial up is turning the whole
 * technique up, not only its damage.
 */
public class KiReleaseSkill implements SkillBehavior {
    private static final double BASE_RADIUS = 2.5;
    private static final double RADIUS_PER_OUTPUT = 0.75;
    private static final float DAMAGE_PER_OUTPUT = 0.5F;
    private static final double PUSH_BASE = 0.35;
    private static final double PUSH_PER_OUTPUT = 0.08;
    /** Extra activation cost when the output is dialled to the current cap. */
    private static final int MAX_EXTRA_COST = 10;
    /** How much faster Appetite burns at a full output than at the lowest one. */
    private static final double MAX_DRAIN_MULTIPLE = 10.0;

    @Override
    public int extraAppetiteCost(TorikoData data) {
        return Math.round(MAX_EXTRA_COST * outputFraction(data));
    }

    /**
     * How far up the player's own Ki output ceiling the dial is, 0..1. What the toggle <i>costs</i> is drawn
     * from this rather than from the raw output number: that number only ever goes up with the level, so
     * charging by it would mean a level 30 caster burning through a full tank in seconds for a setting they
     * never touched, while a level 1 caster standing right beside them paid a tenth of it for the same dial.
     */
    private static float outputFraction(TorikoData data) {
        return CellEvolution.powerFraction(data.kiOutputSetting(), CellEvolution.KI_OUTPUT_BASE,
                CellEvolution.kiOutputCap(data.cellLevel()));
    }

    /**
     * Appetite burned per second while the toggle is on: an absolute count, running from the config's
     * per-second figure at the lowest output to {@link #MAX_DRAIN_MULTIPLE} times that at the player's ceiling.
     */
    public static int drainPerSecond(TorikoData data) {
        return (int) Math.round(Config.kiDrainPerSecond * (1.0 + (MAX_DRAIN_MULTIPLE - 1.0) * outputFraction(data)));
    }

    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        KiAuraEntity aura = new KiAuraEntity(ctx.level());
        aura.setOwner(player);
        aura.setOutput(ctx.data().kiOutputSetting());
        ctx.level().addFreshEntity(aura);
        ctx.data().setKiAuraId(aura.getId());
        Hurt.playSound(ctx, player.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
        return true;
    }

    @Override
    public void tickToggle(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        int output = ctx.data().kiOutputSetting();
        // Output can be retuned while the aura is up.
        if (ctx.level().getEntity(ctx.data().kiAuraId()) instanceof KiAuraEntity aura && aura.output() != output) {
            aura.setOutput(output);
        }
        double radius = BASE_RADIUS + RADIUS_PER_OUTPUT * output;
        Vec3 center = player.position().add(0.0, 1.0, 0.0);
        for (LivingEntity victim : Targeting.inSphere(player, center, radius)) {
            Hurt.apply(ctx, victim, ModDamageTypes.INTIMIDATION, ctx.damage(DAMAGE_PER_OUTPUT * output));
            Vec3 away = victim.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0E-4) {
                Hurt.launch(victim, away.normalize().add(0.0, 0.25, 0.0), PUSH_BASE + PUSH_PER_OUTPUT * output);
            }
        }
    }

    @Override
    public void deactivateToggle(SkillContext ctx) {
        Entity aura = ctx.level().getEntity(ctx.data().kiAuraId());
        if (aura != null) {
            aura.discard();
        }
        ctx.data().setKiAuraId(-1);
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
    }
}
