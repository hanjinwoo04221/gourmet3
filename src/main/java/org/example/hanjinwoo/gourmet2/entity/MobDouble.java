package org.example.hanjinwoo.gourmet2.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The player a mob fights as. The mod's systems (skills, combat styles, leaping, terrain breaking, the damage and dial
 * maths) are written against a ServerPlayer; rather than fork them for mobs, a mob gets one of these, moved to where
 * the mob is every tick, and drives the very same engines through it. Anything new added for players works for the
 * mob too, which is the point of it being a test fighter.
 *
 * <p>It shares the mob's UUID, so anything that stores who fired it as a UUID (projectiles) resolves back to the
 * mob, and damage is attributed to the mob rather than to this never-spawned stand-in (see ModDamageTypes).
 */
public class MobDouble extends FakePlayer {
    private final Mob owner;

    public MobDouble(ServerLevel level, Mob owner) {
        super(level, new GameProfile(owner.getUUID(), owner.getName().getString()));
        this.owner = owner;
    }

    public Mob owner() {
        return owner;
    }

    @Override
    public boolean isAlive() {
        return owner.isAlive();
    }

    /** The animation systems name a clip; the mob decides what it looks like. */
    public void playClip(String clip) {
        if (owner instanceof ClipPlayer player) {
            player.playClip(clip);
        }
    }
}
