package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.LeapEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatAction;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatEngine;
import org.jetbrains.annotations.Nullable;

/**
 * Drives a mob through the player systems. Each server tick the mob's body is copied onto its {@link MobDouble}
 * (position, facing, motion, the attributes the maths reads), the ordinary engines tick that stand-in, and whatever
 * they did to its motion is copied back. The mob's AI does not reimplement any of it: it presses the same inputs a
 * client sends, through the methods below.
 */
public final class MobFighter {
    private final Mob mob;
    private @Nullable MobDouble twin;
    private @Nullable Vec3 aim;
    private static final int RESIST_RECOVERY = 90;
    private int resistReadyTick;

    public MobFighter(Mob mob) {
        this.mob = mob;
    }

    public boolean hasTwin() {
        return twin != null;
    }

    /** The stand-in player, created on first use and again if the mob changes dimension. Server side only. */
    public MobDouble twin() {
        ServerLevel level = (ServerLevel) mob.level();
        if (twin == null || twin.level() != level) {
            if (twin != null) {
                SkillEngine.abortActive(twin);
            }
            twin = new MobDouble(level, mob);
        }
        return twin;
    }

    public TorikoData data() {
        return ModAttachments.of(twin());
    }

    /** Gives the mob a Gourmet Cell level: everything gated on the level (skills, dial caps) follows from it. */
    public void setCellLevel(int level) {
        TorikoData data = data();
        data.setCellXp(CellEvolution.xpForLevel(Math.max(0, level)));
        data.setAppetite(data.maxAppetite());
    }

    /** Face this point on the next tick, pitch included (the leap and the aimed skills read the stand-in's look). */
    public void aimAt(Vec3 point) {
        this.aim = point;
    }

    /** Called once per server tick, after the mob has moved. */
    public void tick() {
        MobDouble t = twin();
        Vec3 eye = mob.getEyePosition();
        float yaw = mob.getYRot();
        float pitch = mob.getXRot();
        if (aim != null) {
            double dx = aim.x - eye.x;
            double dy = aim.y - eye.y;
            double dz = aim.z - eye.z;
            yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
            mob.yHeadRot = yaw;
            mob.setXRot(pitch);
        }
        t.tickCount = mob.tickCount;
        t.setPos(mob.getX(), mob.getY(), mob.getZ());
        t.setYRot(yaw);
        t.setXRot(pitch);
        t.yBodyRot = yaw;
        t.yHeadRot = yaw;
        t.setOnGround(mob.onGround());
        t.setDeltaMovement(mob.getDeltaMovement());
        t.fallDistance = mob.fallDistance;
        copyAttribute(t, Attributes.ATTACK_DAMAGE);
        copyAttribute(t, Attributes.MOVEMENT_SPEED);
        copyAttribute(t, Attributes.ARMOR);
        copyAttribute(t, Attributes.KNOCKBACK_RESISTANCE);

        Vec3 motionBefore = t.getDeltaMovement();
        Vec3 positionBefore = t.position();
        SkillEngine.tick(t);
        aim = null;

        if (!t.getDeltaMovement().equals(motionBefore)) {
            mob.setDeltaMovement(t.getDeltaMovement());
            mob.hasImpulse = true;
        }
        if (t.position().distanceToSqr(positionBefore) > 1.0E-6) {
            mob.setPos(t.getX(), t.getY(), t.getZ());
        }
        mob.fallDistance = t.fallDistance;
    }

    private void copyAttribute(MobDouble t, Holder<Attribute> attribute) {
        AttributeInstance from = mob.getAttribute(attribute);
        AttributeInstance to = t.getAttribute(attribute);
        if (from != null && to != null && to.getBaseValue() != from.getValue()) {
            to.setBaseValue(from.getValue());
        }
    }

    /** Stops whatever is still running, for a mob that is dying or leaving the level. */
    public void shutdown() {
        if (twin != null) {
            SkillEngine.abortActive(twin);
        }
    }

    // ---------------------------------------------------------------------------------------------- the inputs

    public void combatMode(boolean on) {
        if (data().isCombatMode() != on) {
            SkillEngine.setCombatMode(twin(), on);
        }
    }

    public void style(String id) {
        SkillEngine.selectCombatStyle(twin(), id);
    }

    /** One press of an attack group's key. */
    public void attack(int group) {
        CombatEngine.handle(twin(), CombatAction.ATTACK.ordinal(), group);
    }

    /** Jump + attack: a launcher from the ground or on the way up, a spike on the way down. */
    public void aerial() {
        CombatEngine.handle(twin(), CombatAction.AERIAL_ATTACK.ordinal(), 0);
    }

    /**
     * Braces with the Resistance active: opens the resist window at this body's Cell level (a mob does not need the
     * skill unlocked) and plays the guard clip. Refuses while it is still recovering from the last one.
     */
    public boolean resist() {
        if (mob.tickCount < resistReadyTick) {
            return false;
        }
        resistReadyTick = mob.tickCount + RESIST_RECOVERY;
        MobDouble t = twin();
        org.example.hanjinwoo.gourmet2.skill.ResistanceEngine.open(t, data());
        if (mob instanceof ClipPlayer player) {
            player.playClip("guard");
        }
        return true;
    }

    public void dodge() {
        CombatEngine.handle(twin(), CombatAction.DODGE.ordinal(), 0);
    }

    public void guard(boolean on) {
        CombatEngine.handle(twin(), (on ? CombatAction.GUARD_START : CombatAction.GUARD_END).ordinal(), 0);
    }

    public void select(SkillType skill) {
        SkillEngine.select(twin(), skill.ordinal());
    }

    /** The skill key going down. */
    public void useSkill() {
        SkillEngine.use(twin());
    }

    /** The skill key coming up (charged and held skills fire or stop here). */
    public void releaseSkill() {
        SkillEngine.release(twin());
    }

    public void leapCharge() {
        LeapEngine.charge(twin());
    }

    /** A short leap tap: the flash step, toward these movement keys (forward, strafe-left), relative to where it faces. */
    public boolean flashStep(float forward, float strafe) {
        return org.example.hanjinwoo.gourmet2.skill.LeapEngine.flash(twin(), data(), forward, strafe);
    }

    public void leapRelease() {
        LeapEngine.release(twin());
    }
}
