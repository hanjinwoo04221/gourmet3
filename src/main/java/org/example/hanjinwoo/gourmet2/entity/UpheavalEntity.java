package org.example.hanjinwoo.gourmet2.entity;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The burst a hard blow leaves where it lands on terrain: the ground blocks around the mark, copied in place
 * and blown apart — each one kicked away from the strike, tumbling, and then gone.
 *
 * <p>Every block of the patch is a vanilla {@code BlockDisplay} standing exactly where its own block is, so
 * what you see is that piece of ground coming apart over nothing at all: nothing is placed and nothing is
 * destroyed. A display draws its block from its own corner and turns about it too, so each piece carries the
 * offset that keeps its own middle on the mark while it turns and flies.
 *
 * <p>Two details that are easy to get wrong, and are handled here: a display sitting inside the block it
 * copies is lit by the light <i>in</i> that block, which is what makes them come out black, so each one is
 * given the light of its most open face; and everything a display has is NBT-only — the block, the rotation,
 * the transformation, the interpolation and the brightness all have no setters — so each one is loaded the way
 * {@code /summon} would write it. They are discarded with this entity, which is itself never saved.
 */
public class UpheavalEntity extends VisualEntity {
    /** About 2.5 s, counting the last piece to go. */
    public static final int LIFETIME = 50;
    /**
     * Ticks of coming apart, then the window pieces start going in. They do not all leave together: each one
     * picks its own moment in that window, so the patch disappears piece by piece rather than in one blink.
     */
    private static final int FLY_TICKS = 12;
    private static final int SETTLE_FROM = 6;
    private static final int SETTLE_SPREAD = 10;
    private static final int RETURN_TICKS = 11;
    private static final int SHRINK_TICKS = 8;
    /**
     * The pieces barely travel, and they do not fall back: this is ground breaking up, not blocks being
     * thrown. Each one comes up quickly to a place of its own and then stays there — no arc, no bounce — with
     * a nudge in a random direction on the way so nothing moves in a straight line or lands in a neat ring.
     */
    private static final double RISE_BASE = 0.08;
    private static final double RISE_PER_RISE = 0.035;
    private static final double OUT_BASE = 0.04;
    private static final double OUT_PER_RISE = 0.02;
    /** Share of the way to its place a piece covers each tick: quick to arrive, and it never overshoots. */
    private static final double APPROACH = 0.45;
    private static final double JITTER = 0.03;
    private static final double RATTLE_DECAY = 0.8;
    /** How fast a piece starts to tumble over, at most, and how quickly that is bled off to nothing. */
    private static final double SPIN = 0.35;
    private static final double SPIN_DECAY = 0.72;
    /** How far a piece starts tipped over, before the distance falloff is applied to it. */
    private static final double TILT = 0.07;
    /** Ticks the client eases into each step, so a moving piece is smooth rather than stepping.
     *  Kept short: every step is a fresh target and the interpolation restarts from it. */
    private static final int STEP_BLEND = 2;
    /** Hard cap on the blocks one burst may use. */
    public static final int MAX_BLOCKS = 28;

    /** One piece of ground coming apart: where it stays, how it is turned, and when it goes. */
    private static final class Piece {
        private final Display.BlockDisplay display;
        private final BlockState state;
        private final BlockPos pos;
        private final Quaternionf turn;
        private final Vector3f spinAxis;
        private final Vector3f offset = new Vector3f();
        private final Vector3f target;
        private final int settleAt;
        private float spin;
        private float rattle = (float) JITTER;
        private boolean settling;
        private boolean settled;

        private Piece(Display.BlockDisplay display, BlockState state, BlockPos pos, Quaternionf turn,
                      Vector3f spinAxis, float spin, Vector3f target, int settleAt) {
            this.display = display;
            this.state = state;
            this.pos = pos;
            this.turn = turn;
            this.spinAxis = spinAxis;
            this.spin = spin;
            this.target = target;
            this.settleAt = settleAt;
        }

        /**
         * One tick of coming apart. It moves a good share of the way to its own place every tick, so it is up
         * and done within a few tics, and it never overshoots — which is what stops it reading as a block
         * bouncing. The nudge on top is what keeps it from moving in a straight line, and both the nudge and
         * the tumble are bled off every tick, so a piece is still by the time it has finished breaking.
         */
        private void drift(Random random) {
            offset.x += (target.x - offset.x) * (float) APPROACH + (random.nextFloat() - 0.5F) * rattle;
            offset.y += (target.y - offset.y) * (float) APPROACH + (random.nextFloat() - 0.5F) * rattle;
            offset.z += (target.z - offset.z) * (float) APPROACH + (random.nextFloat() - 0.5F) * rattle;
            rattle *= (float) RATTLE_DECAY;
            turn.rotateAxis(spin, spinAxis.x, spinAxis.y, spinAxis.z);
            spin *= (float) SPIN_DECAY;
        }
    }

    private final List<Piece> pieces = new ArrayList<>();
    private double radius = 2.0;
    private double rise = 2.0;
    private int wanted = 12;
    private Vec3 direction = Vec3.ZERO;
    private boolean built;

    public UpheavalEntity(EntityType<? extends UpheavalEntity> type, Level level) {
        super(type, level);
    }

    public UpheavalEntity(Level level) {
        super(ModEntities.UPHEAVAL.get(), level);
    }

    /**
     * How big the burst should be: how wide around the mark, how hard it lifts, how many blocks, and which way
     * the blow was thrown — the direction the pieces come apart in follows it.
     */
    public void setBurst(double radius, double rise, int blocks, Vec3 direction) {
        this.radius = radius;
        this.rise = rise;
        this.wanted = Mth.clamp(blocks, 4, MAX_BLOCKS);
        this.direction = direction;
    }

    @Override
    public int lifetime() {
        return LIFETIME;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (!built) {
            built = true;
            throwUp();
            return;
        }
        if (tickCount <= FLY_TICKS) {
            Random random = new Random(level().random.nextLong());
            for (Piece piece : pieces) {
                piece.drift(random);
                send(piece, piece.turn, piece.offset, 1.0F, STEP_BLEND);
            }
            // Dust shaken loose as it comes apart, a little at a time rather than one cloud.
            if (tickCount % 4 == 0 && !pieces.isEmpty()) {
                for (int i = 0; i < 4; i++) {
                    dust(pieces.get(random.nextInt(pieces.size())), 1, 1);
                }
            }
            return;
        }
        // Then every piece is on its own clock. It sits there broken until its moment comes — nothing is sent
        // in between, so it simply stays where it came apart — eases back into its own cell and straightens up,
        // and shrinks away once that easing is done. Each one starts at a different tick, so the patch goes a
        // piece at a time rather than in one blink.
        for (Piece piece : pieces) {
            if (!piece.settling && tickCount >= piece.settleAt) {
                piece.settling = true;
                send(piece, new Quaternionf(), new Vector3f(), 1.0F, RETURN_TICKS);
                dust(piece, 1, 1);
            }
            if (piece.settling && !piece.settled && tickCount >= piece.settleAt + RETURN_TICKS) {
                piece.settled = true;
                send(piece, new Quaternionf(), new Vector3f(), 0.001F, SHRINK_TICKS);
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide()) {
            for (Piece piece : pieces) {
                piece.display.discard();
            }
            pieces.clear();
        }
    }

    /** Breaks the ground around the mark apart: every block in the ball is copied over the block it came from. */
    private void throwUp() {
        ServerLevel level = (ServerLevel) level();
        int centreX = Mth.floor(getX());
        int centreY = Mth.floor(getY() - 0.5);
        int centreZ = Mth.floor(getZ());
        int r = Mth.ceil(radius);

        // A small ball around the mark rather than a disc laid on the floor: hitting a wall breaks up the wall,
        // hitting the floor breaks up the floor, and neither drags in ground the blow never touched.
        List<BlockPos> ground = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centreX - r, centreY - r, centreZ - r,
                centreX + r, centreY + r, centreZ + r)) {
            double dx = pos.getX() - centreX;
            double dy = pos.getY() - centreY;
            double dz = pos.getZ() - centreZ;
            if (dx * dx + dy * dy + dz * dz > radius * radius) {
                continue;
            }
            if (isLooseGround(level, pos)) {
                ground.add(pos.immutable());
            }
        }

        Random random = new Random(level.random.nextLong());
        Collections.shuffle(ground, random);
        // The way the blow was thrown: pieces come off the face it opened, and skid the way it was going.
        Vector3f strike = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        if (strike.lengthSquared() < 1.0E-4F) {
            strike.set(0.0F, 0.0F, 1.0F);
        }
        strike.normalize();

        for (BlockPos pos : ground.subList(0, Math.min(wanted, ground.size()))) {
            BlockState state = level.getBlockState(pos);
            double dx = pos.getX() - centreX;
            double dy = pos.getY() - centreY;
            double dz = pos.getZ() - centreZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // Closest to the mark comes apart hardest: the falloff is squared, so the middle of the patch is a
            // mess, the halfway ring is a third of that, and the outermost blocks only just stir.
            float near = (float) Math.max(0.0, 1.0 - Math.min(1.0, distance / Math.max(0.5, radius)));
            float bite = 0.08F + 0.92F * near * near;
            // Off the face the blow opened — straight up where it hit the floor, out toward the player where it
            // hit a wall — with a skid along that face the way the strike was going. Which way a piece goes
            // depends on what the blow went through, so a wall and a floor break up differently.
            Vector3f open = openFace(level, pos);
            Vector3f skid = new Vector3f(strike).sub(new Vector3f(open).mul(strike.dot(open)));
            // How far it is thrown is the other way round from how far it turns: the middle of the patch is
            // barely lifted at all and the rim is what comes up, so it reads as ground pushed up around the
            // blow — a crater — rather than a bump under it.
            float heave = (float) Math.min(1.0, distance / Math.max(0.5, radius));
            heave *= heave;
            float lift = (float) (RISE_BASE + rise * RISE_PER_RISE)
                    * (0.5F + random.nextFloat() * 0.7F) * heave;
            float along = (float) (OUT_BASE + rise * OUT_PER_RISE)
                    * (0.6F + random.nextFloat() * 0.8F) * heave;
            Vector3f target = new Vector3f(
                    open.x * lift + skid.x * along,
                    open.y * lift + skid.y * along,
                    open.z * lift + skid.z * along);
            // Facing the mark: each block is turned so the face it was opened on looks back at the strike, spun
            // about that axis so they are not all identical, and tipped a little for unevenness. Most of the
            // patch ends up facing in, the way the inside of a bowl of broken ground would.
            Vector3f toCentre = new Vector3f((float) -dx, (float) -dy, (float) -dz);
            if (toCentre.lengthSquared() < 1.0E-4F) {
                toCentre.set(0.0F, 1.0F, 0.0F);
            }
            toCentre.normalize();
            // The least turn that puts a face on the mark: whichever of the six sides is already nearest to it
            // is the one brought onto it, so a block is not rolled all the way over to face in — some show the
            // top, some a side, and all of them look at the strike.
            Quaternionf facing = new Quaternionf().rotationTo(nearestFace(toCentre), toCentre);
            Vector3f tip = new Vector3f((float) (random.nextDouble() - 0.5), (float) (random.nextDouble() - 0.5),
                    (float) (random.nextDouble() - 0.5));
            if (tip.lengthSquared() < 1.0E-4F) {
                tip.set(1.0F, 0.0F, 0.0F);
            }
            tip.normalize();
            Quaternionf turn = new Quaternionf().rotateAxis(
                    (float) (random.nextDouble() * Math.PI * 2.0), toCentre.x, toCentre.y, toCentre.z)
                    .mul(facing)
                    .mul(new Quaternionf().rotateAxis((float) ((random.nextDouble() - 0.5) * TILT * bite),
                            tip.x, tip.y, tip.z));
            // Every piece turns about the way it faces, so it keeps looking at the mark while it spins.
            Vector3f axis = new Vector3f(toCentre).add(
                    new Vector3f((float) (random.nextDouble() - 0.5) * 0.35F,
                            (float) (random.nextDouble() - 0.5) * 0.35F,
                            (float) (random.nextDouble() - 0.5) * 0.35F)).normalize();
            float spin = (float) ((0.35 + random.nextDouble() * 0.65) * SPIN * bite);
            int settleAt = FLY_TICKS + SETTLE_FROM + random.nextInt(SETTLE_SPREAD);

            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            display.load(displayTag(state, pos, lightOf(level, pos), pose(turn, new Vector3f(), 1.0F), 0));
            level.addFreshEntity(display);
            pieces.add(new Piece(display, state, pos.immutable(), turn, axis, spin, target, settleAt));
        }
        // The hit itself throws dust: what the blow knocked off the ground it landed on.
        for (Piece piece : pieces) {
            dust(piece, 2, 1);
        }
    }

    /**
     * A puff of dust off one piece: crumbs of its own block, plus a little haze around them. Sent from the
     * server rather than spawned here, so only players near it pay for it.
     */
    private void dust(Piece piece, int crumbs, int haze) {
        ServerLevel level = (ServerLevel) level();
        double x = piece.pos.getX() + 0.5 + piece.offset.x;
        double y = piece.pos.getY() + 0.5 + piece.offset.y;
        double z = piece.pos.getZ() + 0.5 + piece.offset.z;
        if (crumbs > 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, piece.state), x, y, z,
                    crumbs, 0.3, 0.3, 0.3, 0.0);
        }
        if (haze > 0) {
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, haze, 0.35, 0.3, 0.35, 0.01);
        }
    }

    /** Updates one piece: the turn, the offset and the scale the phase asks for, for the client to ease into. */
    private void send(Piece piece, Quaternionf turn, Vector3f offset, float scale, int ticks) {
        piece.display.load(displayTag(piece.state, piece.pos, lightOf((ServerLevel) level(), piece.pos),
                pose(turn, offset, scale), ticks));
    }

    /**
     * The pose of a thrown block: turned about its own middle, moved by {@code offset}, and scaled. A display
     * turns about its own corner, so the corner is offset by whatever the middle ends up at — otherwise a
     * turned block swings a whole block out of the cell it belongs to.
     */
    private static Transformation pose(Quaternionf turn, Vector3f offset, float scale) {
        Vector3f middle = turn.transform(new Vector3f(0.5F * scale, 0.5F * scale, 0.5F * scale));
        return new Transformation(
                new Vector3f(0.5F - middle.x + offset.x, 0.5F - middle.y + offset.y, 0.5F - middle.z + offset.z),
                new Quaternionf(turn),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /** Ground a blow could shake loose: solid, not a container, not bedrock, and with a face out in the open. */
    private static boolean isLooseGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK)
                || state.hasBlockEntity() || state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Which of a block's six faces is closest to pointing this way: the one the least turning brings onto it. */
    private static Vector3f nearestFace(Vector3f direction) {
        Vector3f best = new Vector3f(0.0F, 1.0F, 0.0F);
        float bestDot = -2.0F;
        for (int axisIndex = 0; axisIndex < 3; axisIndex++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vector3f candidate = new Vector3f().setComponent(axisIndex, sign);
                float dot = direction.dot(candidate);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** The way out of a block: the average of the sides of it that are open to air. */
    private static Vector3f openFace(ServerLevel level, BlockPos pos) {
        Vector3f out = new Vector3f();
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).isAir()) {
                out.add((float) side.getStepX(), (float) side.getStepY(), (float) side.getStepZ());
            }
        }
        if (out.lengthSquared() < 1.0E-4F) {
            out.set(0.0F, 1.0F, 0.0F);
        }
        return out.normalize();
    }

    /**
     * The light of this block's most open face. A display gets its light where it stands, and a block display
     * stands inside the block it copies — which is dark, and is what made them come out black. Lit from the
     * face instead, a piece looks like the ground it came from.
     */
    private static Brightness lightOf(ServerLevel level, BlockPos pos) {
        int sky = 0;
        int block = 0;
        for (Direction side : Direction.values()) {
            BlockPos face = pos.relative(side);
            if (!level.getBlockState(face).isAir()) {
                continue;
            }
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, face));
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, face));
        }
        return new Brightness(block, sky);
    }

    /**
     * The NBT one of these displays is built from. Every setting a display has is NBT-only — the block, the
     * rotation, the transformation, the interpolation and the brightness all have no setters — so they are
     * loaded the way {@code /summon} would write them.
     */
    private static CompoundTag displayTag(BlockState state, BlockPos pos, Brightness brightness,
                                          Transformation pose, int interpolationTicks) {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", doubleList(pos.getX(), pos.getY(), pos.getZ()));
        tag.put("Motion", doubleList(0.0, 0.0, 0.0));
        tag.put("Rotation", floatList(0.0F, 0.0F));
        tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(state));
        Display.BillboardConstraints.CODEC
                .encodeStart(NbtOps.INSTANCE, Display.BillboardConstraints.FIXED)
                .result()
                .ifPresent(value -> tag.put(Display.TAG_BILLBOARD, value));
        Brightness.CODEC
                .encodeStart(NbtOps.INSTANCE, brightness)
                .result()
                .ifPresent(value -> tag.put(Display.TAG_BRIGHTNESS, value));
        Transformation.EXTENDED_CODEC
                .encodeStart(NbtOps.INSTANCE, pose)
                .result()
                .ifPresent(value -> tag.put(Display.TAG_TRANSFORMATION, value));
        tag.putInt(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, interpolationTicks);
        tag.putFloat(Display.TAG_VIEW_RANGE, 3.0F);
        tag.putFloat(Display.TAG_SHADOW_RADIUS, 0.0F);
        tag.putFloat(Display.TAG_SHADOW_STRENGTH, 0.0F);
        return tag;
    }

    private static ListTag doubleList(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }
}
