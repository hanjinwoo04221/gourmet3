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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.data.HiddenBlocks;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
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
    /** About 3 s, counting the last piece to go: the last of them only starts fading at {@code SETTLE_FROM} + the
     *  spread + RETURN_TICKS, and then takes SHRINK_TICKS to go. */
    public static final int LIFETIME = 66;
    /**
     * Ticks of coming apart, then the window pieces start going in. They do not all leave together: each one
     * picks its own moment in that window, so the patch disappears piece by piece rather than in one blink.
     */
    private static final int FLY_TICKS = 12;
    private static final int SETTLE_FROM = 6;
    private static final int SETTLE_SPREAD = 10;
    private static final int RETURN_TICKS = 11;
    /**
     * Ticks a piece takes to fade out once its own block is back under it. Deliberately unhurried: the ground is
     * already there by the time this starts, and a piece that shrinks away in a blink reads as one that was simply
     * deleted, especially when it is a slab several blocks across — it should look like the ground taking it back.
     */
    private static final int SHRINK_TICKS = 24;
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
    /**
     * Up to this wide, a crater is the small ball around the mark it always was — a bowl dug out of ground that is
     * not flat, following it up and down. Past it a ball stops being a crater and starts being a hole through
     * whatever the strike passed near, and a wider one is cut as a disc across the face the blow opened instead
     * (see {@link #faceGround}). That covers the plain blows and the lighter techniques — everything that ever
     * had a crater before ranges opened up — while the heavy techniques, whose reach is measured in the tens of
     * blocks, get the disc.
     */
    private static final double BALL_RADIUS = 6.0;
    /**
     * How far off level a blow has to be before it counts as aimed at a floor or a ceiling rather than at a wall:
     * about seventeen degrees. A punch at a wall is thrown nearly level; a punch at the ground is not, and the two
     * have to be told apart because the crater lies the other way round for each.
     */
    private static final double WALL_ANGLE = 0.30;
    /**
     * Pieces one burst may have, and the one thing about its size that is bounded at all. A piece is what a burst
     * costs: an entity each, with a spawn and a dozen updates behind it. There is no ceiling on how wide a crater is
     * cut — see {@link #PIECE_SPREAD} for how a bounded number of pieces covers an unbounded crater — and every
     * piece is a block the crater took out, shown coming out of the hole it left. The ground in between is never
     * hidden at all: it simply stands there, under the pieces, so there is no cell that is empty and then is not,
     * which is what the wider craters used to look like — ground appearing out of nowhere.
     */
    private static final int MAX_PIECES = 250;
    /**
     * How far past the nearest piece beside it a piece is drawn, as a share of the distance to it (see
     * {@link #pieceSizes}).
     */
    private static final double PIECE_SPREAD = 1.4;
    /** Safety on that: a piece with nothing else anywhere near it still only grows so far. */
    private static final float MAX_PIECE_SCALE = 8.0F;
    /**
     * How thick the ground a wide burst takes is, in blocks either side of the mark along the axis the blow travels.
     * The crater is a disc across the face the blow opened: thin along the axis the blow travels and round in the
     * two it does not, so a crater too wide for a ball does not reach into the ground under it or out around behind
     * anything the blow merely passed near — ground with nothing to do with the hit, thrown up as if it had. Being
     * a slab a few blocks thick, it can be walked block by block however wide it gets.
     */
    private static final int CRATER_DEPTH = 3;

    /**
     * What it takes to break ground up at all, and how much moving makes a difference: anything under this
     * power leaves the ground alone, and speed counts up to a point — a leap carries more of it than a punch,
     * but it should not dwarf one.
     */
    private static final double MIN_POWER = 4.0;
    private static final double SPEED_WEIGHT = 4.0;
    private static final double MAX_SPEED_FACTOR = 1.0;
    /**
     * How wide, how high and how many blocks a burst gets, as a base plus a share of the power over the bar.
     * Deliberately without a ceiling on any of them: ground broken by a blow worth more of it gets more of it,
     * all the way up. A blow big enough makes a crater big enough that its pieces are spread thin over it, which
     * is what a huge amount of force landing in one place should look like.
     */
    private static final double BASE_RADIUS = 2.5;
    /**
     * Blocks of crater radius per point of power over the bar. The crater grows with the blow at this rate all the
     * way up: a blow twice as hard leaves a crater twice as wide, and there is nothing here to stop it. What keeps a
     * very wide one readable is that its pieces grow to keep covering it (see {@link #PIECE_SPREAD}).
     */
    private static final double RADIUS_PER_POWER = 0.20;
    private static final double BASE_LIFT = 1.0;
    private static final double LIFT_PER_POWER = 0.12;
    /**
     * The one ceiling left: how high the pieces come up. Lifting them scales with the blow too, but only a little
     * — this is ground breaking apart, not blocks being thrown, and beyond a certain power lifting them further
     * stops reading as a crater and starts reading as debris.
     */
    private static final double MAX_LIFT = 4.0;
    private static final double BASE_BLOCKS = 6.0;
    private static final double BLOCKS_PER_POWER = 0.8;
    /**
     * How tall a piece is ever drawn, however wide the crater. A chunk is grown to cover ground, not to stand up:
     * past a couple of blocks, every extra block of width would otherwise add a block of height as well, and a
     * crater of big cubes stacks up a hill of them where it should be laying ground over ground — most of which is
     * buried anyway, since a piece is sunk until its top sits level with the surface. Flattening them that way is
     * also what keeps the drawing cheap when the crater gets wide: the fill on screen follows the footprint of the
     * slabs, and that is what it needs to be to cover the crater.
     */
    private static final float MAX_PIECE_HEIGHT = 2.0F;
    /**
     * How far proud of the ground a piece stands, on top of whatever its own size needs to sit level in its cell.
     * Slightly above it, deliberately: a piece here is covering the ground it stands over as well as showing a block
     * that came out of it, and one sunk below the surface is hidden by the very ground it was meant to hide — the
     * ground between the pieces would all be on show with it. Standing just proud of the surface is still a good
     * deal lower than these pieces sat before they had that job.
     */
    private static final float PIECE_REST = 0.1F;
    /** How close a new burst may land to one already going, and how many may be going at once nearby. */
    private static final double MIN_SPACING = 2.0;
    private static final double NEARBY_RANGE = 12.0;
    private static final int MAX_NEARBY = 3;

    /**
     * Throws a burst up at {@code at}, if the blow was worth one. How wide, how high and how many blocks come
     * from the damage of the blow and the speed it was thrown at — moving fast tears up more ground than
     * standing still — and anything under {@link #MIN_POWER} leaves the ground alone. The attacks, the skills
     * and the leap all come through here, so ground broken by any of them reads the same.
     *
     * @return whether one was thrown up
     */
    public static boolean burst(ServerLevel level, BlockPos at, Vec3 direction, double damage, double speed) {
        double power = damage * (1.0 + Math.min(MAX_SPEED_FACTOR, Math.max(0.0, speed)) * SPEED_WEIGHT);
        if (power < MIN_POWER) {
            return false;
        }
        // Nothing where one is already breaking up, and only a few going at once: a technique sweeping through
        // terrain would otherwise leave a burst every couple of blocks.
        List<UpheavalEntity> live = level.getEntitiesOfClass(UpheavalEntity.class, new AABB(at).inflate(NEARBY_RANGE));
        if (live.size() >= MAX_NEARBY) {
            return false;
        }
        for (UpheavalEntity near : live) {
            if (near.blockPosition().closerThan(at, MIN_SPACING)) {
                return false;
            }
        }
        double over = power - MIN_POWER;
        double radius = BASE_RADIUS + over * RADIUS_PER_POWER;
        double lift = Math.min(MAX_LIFT, BASE_LIFT + over * LIFT_PER_POWER);
        int blocks = (int) Math.round(BASE_BLOCKS + over * BLOCKS_PER_POWER);

        UpheavalEntity burst = new UpheavalEntity(level);
        burst.moveTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 0.0F, 0.0F);
        burst.setBurst(radius, lift, blocks, direction);
        level.addFreshEntity(burst);
        return true;
    }

    /**
     * Which way the surface the blow broke faces: flat along the two axes a floor or a ceiling spans, or flat along
     * the axis a wall is thin in.
     *
     * <p>Read off the angle of the blow, and only off that. Reading it off the ground instead — which way the mark
     * has earth on one side and air on the other — sounds better but answers the same way for a wall as for the
     * ground under a step or the side of a hill, and whichever way those ties fell, half of them came out as a disc
     * standing on end, which cuts a slot through the ground instead of a crater. Only a blow sent in within
     * {@link #WALL_ANGLE} of level is a blow at a wall; anything steeper than that is aimed at the floor or the
     * ceiling it was thrown from, and breaks that as a round patch however it was thrown.
     */
    private static Direction.Axis surfaceAxis(Vec3 direction) {
        if (Math.abs(direction.y) >= WALL_ANGLE) {
            return Direction.Axis.Y;
        }
        return Math.abs(direction.x) >= Math.abs(direction.z) ? Direction.Axis.X : Direction.Axis.Z;
    }

    /**
     * The ground a ball of this radius around the mark breaks up: a bowl dug out around the blow, following ground
     * that is not flat up and down, which is how every crater was cut before ranges opened up. Looked at block by
     * block rather than at a lattice of every third one, because what a crater is made of is the surface the blow
     * actually left.
     */
    private static List<BlockPos> ballGround(ServerLevel level, BlockPos centre, double radius, int r) {
        List<BlockPos> ground = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.getX() - r, centre.getY() - r, centre.getZ() - r,
                centre.getX() + r, centre.getY() + r, centre.getZ() + r)) {
            double dx = pos.getX() - centre.getX();
            double dy = pos.getY() - centre.getY();
            double dz = pos.getZ() - centre.getZ();
            if (dx * dx + dy * dy + dz * dz > radius * radius) {
                continue;
            }
            if (isLooseGround(level, pos)) {
                ground.add(pos.immutable());
            }
        }
        return ground;
    }

    /**
     * The ground a disc across the surface the blow broke breaks up: round in the two axes that surface spans and
     * thin along the one it faces, so hitting the floor breaks up the floor, hitting a wall breaks up the wall, and
     * a crater already too wide for a ball does not reach into the ground under it or out around behind anything
     * the blow merely passed near. Looked at block by block, wherever it is cut.
     */
    private static List<BlockPos> faceGround(ServerLevel level, BlockPos centre, double radius, int r,
            Vec3 direction) {
        Direction.Axis facing = surfaceAxis(direction);
        boolean thinX = facing == Direction.Axis.X;
        boolean thinY = facing == Direction.Axis.Y;
        double radiusSq = radius * radius;
        List<BlockPos> ground = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                centre.getX() - (thinX ? CRATER_DEPTH : r), centre.getY() - (thinY ? CRATER_DEPTH : r),
                centre.getZ() - (thinX || thinY ? r : CRATER_DEPTH),
                centre.getX() + (thinX ? CRATER_DEPTH : r), centre.getY() + (thinY ? CRATER_DEPTH : r),
                centre.getZ() + (thinX || thinY ? r : CRATER_DEPTH))) {
            double dx = pos.getX() - centre.getX();
            double dy = pos.getY() - centre.getY();
            double dz = pos.getZ() - centre.getZ();
            double flat = thinX ? dy * dy + dz * dz : thinY ? dx * dx + dz * dz : dx * dx + dy * dy;
            double thin = thinX ? dx : thinY ? dy : dz;
            if (flat > radiusSq || Math.abs(thin) > CRATER_DEPTH) {
                continue;
            }
            if (isLooseGround(level, pos)) {
                ground.add(pos.immutable());
            }
        }
        return ground;
    }

    /**
     * How big each piece is drawn, one per block taken out: big enough to meet the piece nearest to it, and no
     * bigger. Where pieces are thick on the ground they stay at their own block size and the ground between them is
     * never seen at all; where one stands more or less alone it swells until it covers what is around it — the fewer
     * neighbours a piece has, the bigger it comes out. That is what lets a crater be as wide as the blow is worth
     * while still costing only a bounded number of pieces.
     */
    private static float[] pieceSizes(List<BlockPos> at) {
        float[] sizes = new float[at.size()];
        for (int i = 0; i < at.size(); i++) {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < at.size(); j++) {
                if (i != j) {
                    nearest = Math.min(nearest, at.get(i).distSqr(at.get(j)));
                }
            }
            sizes[i] = nearest == Double.MAX_VALUE ? MAX_PIECE_SCALE
                    : (float) Mth.clamp(Math.sqrt(nearest) * PIECE_SPREAD, 1.0, MAX_PIECE_SCALE);
        }
        return sizes;
    }

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
        /** How big this one is drawn: enough to meet the pieces around it (see {@code throwUp}). */
        private final float scale;
        private float spin;
        private float rattle = (float) JITTER;
        /** Whether this piece's own block was taken out of the world to be shown lifted instead. */
        private boolean hidden;
        private boolean settling;
        private boolean settled;

        private Piece(Display.BlockDisplay display, BlockState state, BlockPos pos, Quaternionf turn,
                      Vector3f spinAxis, float spin, Vector3f target, int settleAt, float scale) {
            this.display = display;
            this.state = state;
            this.pos = pos;
            this.turn = turn;
            this.spinAxis = spinAxis;
            this.spin = spin;
            this.target = target;
            this.settleAt = settleAt;
            this.scale = scale;
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
        this.wanted = Math.max(4, blocks);
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
                send(piece, piece.turn, piece.offset, piece.scale, STEP_BLEND);
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
                send(piece, new Quaternionf(), new Vector3f(), piece.scale, RETURN_TICKS);
                dust(piece, 1, 1);
            }
            if (piece.settling && !piece.settled && tickCount >= piece.settleAt + RETURN_TICKS) {
                piece.settled = true;
                // Home again and about to fade: the ground goes back now, so there is never a moment where the
                // piece has gone and the cell it came from is still empty.
                restore(piece);
                send(piece, new Quaternionf(), new Vector3f(), 0.001F, SHRINK_TICKS);
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide()) {
            for (Piece piece : pieces) {
                // Whatever is left over when the effect is done, put the ground back.
                restore(piece);
                piece.display.discard();
            }
            pieces.clear();
        }
    }

    /** Breaks the ground around the mark apart: every block of the crater is copied out over the block it came from. */
    private void throwUp() {
        ServerLevel level = (ServerLevel) level();
        int centreX = Mth.floor(getX());
        int centreY = Mth.floor(getY() - 0.5);
        int centreZ = Mth.floor(getZ());
        int r = Mth.ceil(radius);

        // How a crater is cut depends on how wide it is: a small one the way it always was, a wide one as a disc
        // across the face the blow opened instead. See the two below.
        BlockPos centre = new BlockPos(centreX, centreY, centreZ);
        List<BlockPos> ground = radius <= BALL_RADIUS
                ? ballGround(level, centre, radius, r)
                : faceGround(level, centre, radius, r, direction);

        Random random = new Random(level.random.nextLong());
        // Nearest first: if there is more ground here than one burst has pieces for (see MAX_PIECES), what falls off
        // the end is the rim of the crater rather than blocks scattered through the middle of it.
        ground.sort(Comparator.comparingDouble(pos -> pos.distSqr(centre)));
        // The way the blow was thrown: pieces come off the face it opened, and skid the way it was going.
        Vector3f strike = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        if (strike.lengthSquared() < 1.0E-4F) {
            strike.set(0.0F, 0.0F, 1.0F);
        }
        strike.normalize();

        // Every piece is a block the crater takes out, shown coming out of the hole it leaves: one piece per hole, no
        // exceptions. A hole with nothing standing over it is a block missing from the world with nothing to say
        // what became of it, and the moment it comes back that is a block appearing out of nowhere — the ground
        // between the pieces is never hidden at all, so nothing is ever seen filling back in.
        List<BlockPos> shown = ground.subList(0, Math.min(ground.size(), MAX_PIECES));
        float[] sizes = pieceSizes(shown);
        for (int index = 0; index < shown.size(); index++) {
            BlockPos pos = shown.get(index);
            BlockState state = level.getBlockState(pos);
            // How big this one is drawn, and therefore how much of a move is a move: the offsets below are divided by
            // it, because the same lift on a slab six blocks across would tear it out of the ground it is covering
            // instead of shifting it in place.
            float scale = sizes[index];
            float motion = 1.0F / scale;
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
                    * (0.5F + random.nextFloat() * 0.7F) * heave * motion;
            float along = (float) (OUT_BASE + rise * OUT_PER_RISE)
                    * (0.6F + random.nextFloat() * 0.8F) * heave * motion;
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
            display.load(displayTag(state, pos, lightOf(level, pos), pose(turn, new Vector3f(), scale), 0));
            level.addFreshEntity(display);
            Piece piece = new Piece(display, state, pos.immutable(), turn, axis, spin, target, settleAt, scale);
            // Take the real block out of the world and leave nothing visible in its place, so the display standing
            // over that cell reads as the block itself lifting out of the ground. A barrier rather than air: it
            // keeps the light and the collision of the cell for the moment it is standing in for, and is put back
            // in remove() whatever happens to the effect.
            piece.hidden = hide(level, pos, state);
            pieces.add(piece);
        }
        // The hit itself throws dust: what the blow knocked off the ground it landed on.
        for (Piece piece : pieces) {
            dust(piece, 2, 1);
        }
    }

    /**
     * Takes a block out of the world and leaves nothing visible in its place — a barrier rather than air, so the
     * cell keeps its light and its collision for the moment it is standing in for. Written down as well as done:
     * a save landing mid-burst would otherwise leave the barrier in the world with nothing left to undo it.
     */
    private boolean hide(ServerLevel level, BlockPos pos, BlockState state) {
        boolean hidden = level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
        if (hidden) {
            HiddenBlocks.of(level).add(pos, state);
        }
        return hidden;
    }

    /**
     * Puts a piece's own block back exactly as it was. Only if that cell still holds the placeholder this left
     * there: anything a player has done to it since is theirs and is left alone.
     */
    private void restore(Piece piece) {
        if (!piece.hidden) {
            return;
        }
        if (level().getBlockState(piece.pos).is(Blocks.BARRIER)) {
            level().setBlock(piece.pos, piece.state, 3);
        }
        // Off the list either way: if the cell is no longer the placeholder, the block there is somebody
        // else's now and there is nothing left for this to put back.
        HiddenBlocks.of((ServerLevel) level()).remove(piece.pos);
        piece.hidden = false;
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
        // As wide as the crater wants, and never taller than a couple of blocks (see MAX_PIECE_HEIGHT).
        float height = Math.min(scale, MAX_PIECE_HEIGHT);
        Vector3f middle = turn.transform(new Vector3f(0.5F * scale, 0.5F * height, 0.5F * scale));
        // A piece grown to stand for more than its own block is centred lower by half of what it was grown by, so it
        // sits in that block's cell with its top level with the ground instead of hanging over it — and then a little
        // above that again, so it stands over the ground it is covering rather than being buried in it (see
        // PIECE_REST). A piece on its way out is left where it is: standing proud is for standing, not for vanishing.
        float sink = Math.max(0.0F, (height - 1.0F) * 0.5F) - (scale < 0.01F ? 0.0F : PIECE_REST);
        return new Transformation(
                new Vector3f(0.5F - middle.x + offset.x, 0.5F - middle.y + offset.y - sink,
                        0.5F - middle.z + offset.z),
                new Quaternionf(turn),
                new Vector3f(scale, height, scale),
                new Quaternionf());
    }

    /** Ground a blow could shake loose: solid, not a container, not bedrock, and with a face out in the open. */
    private static boolean isLooseGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK)
                || state.hasBlockEntity() || state.getCollisionShape(level, pos).isEmpty()
                || state.getLightEmission() > 0 || state.is(Blocks.BARRIER)) {
            // Light sources are left out: taking one out of the world would darken the spot for as long as the
            // piece is up, which reads as a bug rather than as a block being lifted.
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
