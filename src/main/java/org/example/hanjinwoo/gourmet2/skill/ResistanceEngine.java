package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ClipPlayer;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resistance (抵抗). Two halves:
 *
 * <ul>
 *   <li><b>Passive</b> — every Cell level shaves a little more off all damage taken
 *       ({@link CellEvolution#resistanceFactor}); it is always on, whether or not the skill is unlocked.</li>
 *   <li><b>Active</b> — pressing the skill opens a short window in which incoming blows are shrugged off. The window
 *       holds a pool of damage ({@link CellEvolution#resistCapacity}) that grows with the Cell level. A blow the
 *       pool covers is negated outright; one it only half covers is still knocked aside; a much bigger one only
 *       loses that much. Any blow the pool at least half covers also throws the attacker off: their running skill
 *       (a nail-punch combo, a barrage) is cut short and their combo chain broken.</li>
 * </ul>
 *
 * State is kept per player on the server only; a window is a few dozen ticks long, so nothing is saved.
 */
public final class ResistanceEngine {
    /** Ticks a fresh window stays open. */
    private static final int WINDOW_TICKS = 14;
    /** Every blow shrugged off keeps the window open a little longer, up to this many ticks after it opened. */
    private static final int EXTEND_TICKS = 5;
    private static final int MAX_WINDOW_TICKS = 34;
    /** How long a thrown-off attacker cannot start another chain. */
    private static final int STAGGER_TICKS = 24;

    private static final class Window {
        long end;
        final long hardEnd;
        float pool;

        Window(long end, long hardEnd, float pool) {
            this.end = end;
            this.hardEnd = hardEnd;
            this.pool = pool;
        }
    }

    private static final Map<UUID, Window> WINDOWS = new HashMap<>();

    private ResistanceEngine() {}

    /** The skill key: opens (or refreshes) the resist window with a full pool for this Cell level. */
    public static void open(ServerPlayer player, TorikoData data) {
        long now = player.level().getGameTime();
        WINDOWS.put(player.getUUID(), new Window(now + WINDOW_TICKS, now + MAX_WINDOW_TICKS,
                CellEvolution.resistCapacity(data.cellLevel())));
    }

    public static boolean isOpen(ServerPlayer player) {
        Window window = WINDOWS.get(player.getUUID());
        return window != null && window.end >= player.level().getGameTime();
    }

    /** The share of a blow that still lands, from the passive alone. */
    public static float passiveFactor(TorikoData data) {
        return CellEvolution.resistanceFactor(data.cellLevel());
    }

    /**
     * Applies both halves to an incoming blow. Returns true when the blow was cancelled outright, so the caller
     * has nothing more to do with it.
     */
    public static boolean onIncomingDamage(ServerPlayer player, TorikoData data, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        event.setAmount(event.getAmount() * passiveFactor(data));

        Window window = WINDOWS.get(player.getUUID());
        if (window == null) {
            return false;
        }
        long now = player.level().getGameTime();
        if (window.end < now || event.getAmount() <= 0.0F) {
            if (window.end < now) {
                WINDOWS.remove(player.getUUID());
            }
            return false;
        }

        float damage = event.getAmount();
        float absorbed = Math.min(damage, window.pool);
        window.pool -= absorbed;
        float covered = absorbed / damage;
        boolean negated = covered >= 0.999F;
        if (negated) {
            event.setCanceled(true);
        } else {
            event.setAmount(damage - absorbed);
        }

        ServerLevel level = (ServerLevel) player.level();
        SkillContext ctx = new SkillContext(player, level, data);
        Hurt.playSound(ctx, player.position(), negated ? SoundEvents.ANVIL_LAND : SoundEvents.SHIELD_BLOCK,
                negated ? 0.7F : 0.9F, negated ? 1.5F : 0.8F);
        FxDispatch.at(level, SkillFx.COMBAT_IMPACT, player.getBoundingBox().getCenter(),
                0.5F + 0.6F * covered, player.getYRot(), 0.0F);

        if (covered >= 0.5F && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            throwOff(player, attacker);
        }

        if (window.pool <= 0.01F) {
            WINDOWS.remove(player.getUUID());
        } else {
            window.end = Math.min(window.hardEnd, Math.max(window.end, now + EXTEND_TICKS));
        }
        return negated;
    }

    /** Shrugs the attacker off: knocks them back, and cuts short whatever they were in the middle of. */
    private static void throwOff(ServerPlayer player, LivingEntity attacker) {
        Hurt.knockAway(attacker, player.position(), 0.9);
        if (attacker instanceof ServerPlayer other) {
            SkillEngine.abortActive(other);
            TorikoData theirs = ModAttachments.of(other);
            theirs.resetGroupChains();
            theirs.setCombatCooldown(Math.max(theirs.combatCooldown(), STAGGER_TICKS));
            SkillEngine.sync(other, theirs);
        } else if (attacker instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STAGGER_TICKS, 3, false, false));
            if (mob instanceof ClipPlayer fighter && fighter.fighter().hasTwin()) {
                fighter.fighter().shutdown();
                TorikoData theirs = fighter.fighter().data();
                theirs.resetGroupChains();
                theirs.setCombatCooldown(Math.max(theirs.combatCooldown(), STAGGER_TICKS));
            }
        }
        Vec3 at = attacker.getBoundingBox().getCenter();
        FxDispatch.at((ServerLevel) attacker.level(), SkillFx.COMBAT_IMPACT, at, 0.9F, player.getYRot(), 0.0F);
    }
}
