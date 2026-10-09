package com.soulstealer.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.soulstealer.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Makes the fake player do random things like a real player would:
 * chop a nearby tree, "craft" something (at a crafting table if there is one) or sneak-spam at the nearest player.
 *
 * Goals are only ticked every second game tick, so all counters below count goal ticks (= 2 game ticks each).
 */
public class FakeActivityGoal extends Goal {

    private enum Mode {
        MINE_WOOD, CRAFT, SNEAK
    }

    private static final int MINE_STEPS = 24;
    private static final int CRAFT_STEPS = 50;
    private static final int SNEAK_STEPS = 40;

    private static final Item[] AXES = {Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE};
    private static final Item[] PLANKS = {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.ACACIA_PLANKS};
    private static final Item[] RESULTS = {
            Items.STICK, Items.CRAFTING_TABLE, Items.CHEST, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE,
            Items.IRON_SWORD, Items.TORCH, Items.FURNACE, Items.BOW, Items.BOOK, Items.OAK_DOOR
    };

    private final FakePlayerEntity mob;

    private Mode mode;
    private boolean done;
    private int startTick;
    private int maxTicks;
    private int nextTick = 80;
    private int steps;
    private int lastNavTick;
    private int failedPaths;
    private BlockPos target;
    private BlockPos workstation;

    public FakeActivityGoal(FakePlayerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextTick) {
            return false;
        }
        Mode chosen = Mode.values()[this.mob.getRandom().nextInt(Mode.values().length)];
        if (!prepare(chosen)) {
            this.nextTick = this.mob.tickCount + 40 + this.mob.getRandom().nextInt(80);
            return false;
        }
        this.mode = chosen;
        return true;
    }

    private boolean prepare(Mode chosen) {
        switch (chosen) {
            case MINE_WOOD:
                this.target = findTree();
                return this.target != null;
            case CRAFT:
                this.workstation = findCraftingTable();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.done && this.mode != null && this.mob.tickCount - this.startTick < this.maxTicks;
    }

    @Override
    public void start() {
        this.done = false;
        this.steps = 0;
        this.mineProgress = 0;
        this.failedPaths = 0;
        this.lastNavTick = -100;
        this.startTick = this.mob.tickCount;
        switch (this.mode) {
            case MINE_WOOD:
                this.maxTicks = 20 * 25;
                this.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AXES[this.mob.getRandom().nextInt(AXES.length)]));
                break;
            case CRAFT:
                this.maxTicks = 20 * 15;
                break;
            default:
                this.maxTicks = 20 * 8;
                break;
        }
    }

    @Override
    public void stop() {
        if (this.target != null) {
            this.mob.level().destroyBlockProgress(this.mob.getId(), this.target, -1);
        }
        this.mob.getNavigation().stop();
        this.mob.setShiftKeyDown(false);
        this.mob.setPose(Pose.STANDING);
        this.target = null;
        this.workstation = null;
        this.mode = null;
        this.nextTick = this.mob.tickCount + 100 + this.mob.getRandom().nextInt(260);
    }

    @Override
    public void tick() {
        if (this.mode == null) {
            return;
        }
        this.steps++;
        switch (this.mode) {
            case MINE_WOOD:
                tickMine();
                break;
            case CRAFT:
                tickCraft();
                break;
            default:
                tickSneak();
                break;
        }
    }

    // ---------------------------------------------------------------- mining wood

    private void tickMine() {
        Level level = this.mob.level();
        BlockState state = level.getBlockState(this.target);
        if (!state.is(BlockTags.LOGS)) {
            this.done = true;
            return;
        }

        double cx = this.target.getX() + 0.5D;
        double cy = this.target.getY() + 0.5D;
        double cz = this.target.getZ() + 0.5D;

        if (this.mob.distanceToSqr(cx, cy, cz) > 9.0D) {
            // walk to the tree
            if (this.mob.tickCount - this.lastNavTick >= 10) {
                this.lastNavTick = this.mob.tickCount;
                boolean ok = this.mob.getNavigation().moveTo(cx, this.target.getY(), cz, 1.0D);
                if (!ok) {
                    this.failedPaths++;
                    if (this.failedPaths >= 3) {
                        this.done = true;
                    }
                }
            }
            this.mob.getLookControl().setLookAt(cx, cy, cz);
            return;
        }

        // chop
        this.mob.getNavigation().stop();
        this.mob.getLookControl().setLookAt(cx, cy, cz);
        this.mineProgress++;
        if (this.mineProgress % 3 == 1) {
            this.mob.swing(InteractionHand.MAIN_HAND);
        }
        if (this.mineProgress % 2 == 0) {
            level.playSound(null, this.target, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 0.8F);
        }
        level.destroyBlockProgress(this.mob.getId(), this.target, Math.min(9, this.mineProgress * 10 / MINE_STEPS));

        if (this.mineProgress >= MINE_STEPS) {
            level.destroyBlockProgress(this.mob.getId(), this.target, -1);
            if (Config.BREAK_TREES.get()) {
                level.destroyBlock(this.target, true, this.mob);
            }
            this.mineProgress = 0;
            this.done = true;
        }
    }

    private int mineProgress;

    private BlockPos findTree() {
        Level level = this.mob.level();
        BlockPos origin = this.mob.blockPosition();

        // do not touch chunks that are not loaded
        if (!level.hasChunkAt(origin.offset(12, 0, 12)) || !level.hasChunkAt(origin.offset(-12, 0, 12))
                || !level.hasChunkAt(origin.offset(12, 0, -12)) || !level.hasChunkAt(origin.offset(-12, 0, -12))) {
            return null;
        }

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -2, -12), origin.offset(12, 6, 12))) {
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                candidates.add(pos.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));

        int checked = 0;
        for (BlockPos pos : candidates) {
            if (isNaturalTree(level, pos)) {
                return pos;
            }
            if (++checked >= 24) {
                break;
            }
        }
        return null;
    }

    /** A log counts as a natural tree when there are leaves around it that were not placed by a player. */
    private static boolean isNaturalTree(Level level, BlockPos log) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 7; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockState state = level.getBlockState(log.offset(dx, dy, dz));
                    if (state.getBlock() instanceof LeavesBlock
                            && state.hasProperty(LeavesBlock.PERSISTENT)
                            && !state.getValue(LeavesBlock.PERSISTENT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- crafting

    private void tickCraft() {
        if (this.workstation != null) {
            Vec3 center = Vec3.atCenterOf(this.workstation);
            if (this.mob.distanceToSqr(center) > 6.25D) {
                if (this.mob.tickCount - this.lastNavTick >= 10) {
                    this.lastNavTick = this.mob.tickCount;
                    boolean ok = this.mob.getNavigation().moveTo(center.x, this.workstation.getY(), center.z, 1.0D);
                    if (!ok) {
                        this.failedPaths++;
                        if (this.failedPaths >= 3) {
                            this.workstation = null; // craft in the inventory instead
                        }
                    }
                }
                this.mob.getLookControl().setLookAt(center.x, center.y, center.z);
                this.steps = 0; // the crafting itself starts when the dummy has arrived
                return;
            }
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(center.x, center.y, center.z);
        } else {
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(this.mob.getX(), this.mob.getY(), this.mob.getZ() + 0.3D);
        }

        if (this.steps % 4 == 1) {
            this.mob.swing(InteractionHand.MAIN_HAND);
        }
        if (this.steps == 8) {
            hold(PLANKS[this.mob.getRandom().nextInt(PLANKS.length)]);
        } else if (this.steps == 22) {
            hold(Items.STICK);
        } else if (this.steps == 36) {
            hold(RESULTS[this.mob.getRandom().nextInt(RESULTS.length)]);
        } else if (this.steps >= CRAFT_STEPS) {
            this.done = true;
        }
    }

    private void hold(Item item) {
        this.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    private BlockPos findCraftingTable() {
        Level level = this.mob.level();
        BlockPos origin = this.mob.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-10, -3, -10), origin.offset(10, 3, 10))) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- sneak spam

    private void tickSneak() {
        this.mob.getNavigation().stop();
        Player nearest = this.mob.level().getNearestPlayer(this.mob, 16.0D);
        if (nearest != null) {
            this.mob.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
        }
        if (this.steps % 2 == 0) {
            boolean sneak = !this.mob.isShiftKeyDown();
            this.mob.setShiftKeyDown(sneak);
            this.mob.setPose(sneak ? Pose.CROUCHING : Pose.STANDING);
        }
        if (this.steps % 14 == 0) {
            this.mob.getJumpControl().jump();
        }
        if (this.steps >= SNEAK_STEPS) {
            this.done = true;
        }
    }
}
