package com.soulstealer.entity;

import java.util.EnumSet;


import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A hostile rebel wizard living in the evil towers: shoots magic bolts from a distance and dodges by teleporting. */
public class RebelWizardEntity extends Monster {

    private static final int HOME_RADIUS = 14;

    private BlockPos homePos;
    private boolean homeSet;
    private int lastTeleportTick = -1000;
    /** tickCount at which the next bolt may be cast. */
    int nextCastTick;

    public RebelWizardEntity(EntityType<? extends RebelWizardEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        this.setPersistenceRequired();
        ItemStack hat = MagicBolt.wizardHat();
        if (!hat.isEmpty()) {
            this.setItemSlot(EquipmentSlot.HEAD, hat);
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WizardCastGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RebelWizardEntity.class, MaxWizardEntity.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                (LivingEntity e) -> e.distanceToSqr(this) <= 14.0D * 14.0D));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!this.homeSet) {
            this.homeSet = true;
            if (this.homePos == null) {
                this.homePos = this.blockPosition();
            }
            this.restrictTo(this.homePos, HOME_RADIUS);
        }
    }

    /** Fires one magic bolt at the target. */
    void castBolt(LivingEntity target) {
        Vec3 start = this.getEyePosition();
        Vec3 aim = new Vec3(target.getX(), target.getY(0.5D), target.getZ());
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
        MagicBolt.bolt(this, start, aim.subtract(start), 16.0D, 5.0F, ParticleTypes.WITCH, ParticleTypes.SOUL_FIRE_FLAME);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide() && this.isAlive()
                && this.getHealth() < this.getMaxHealth() * 0.5F
                && this.tickCount - this.lastTeleportTick >= 200) {
            if (this.tryTeleportAway()) {
                this.lastTeleportTick = this.tickCount;
            }
        }
        return result;
    }

    /** Teleports 4-6 blocks away to a safe spot. Returns false when no spot was found. */
    private boolean tryTeleportAway() {
        if (!(this.level() instanceof ServerLevel server)) {
            return false;
        }
        int[] dys = {0, 1, -1, 2, -2, 3, -3};
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double dist = 4.0D + this.random.nextDouble() * 2.0D;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            double x = this.getX() + dx;
            double z = this.getZ() + dz;
            for (int dy : dys) {
                double y = this.getY() + dy;
                AABB box = this.getBoundingBox().move(dx, dy, dz);
                BlockPos below = BlockPos.containing(x, y - 0.5D, z);
                if (this.level().noCollision(this, box)
                        && !this.level().containsAnyLiquid(box)
                        && this.level().getBlockState(below).blocksMotion()
                        && this.isWithinRestriction(BlockPos.containing(x, y, z))) {
                    server.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0D, this.getZ(), 30, 0.4D, 0.8D, 0.4D, 0.3D);
                    this.moveTo(x, y, z);
                    this.getNavigation().stop();
                    server.sendParticles(ParticleTypes.PORTAL, x, y + 1.0D, z, 30, 0.4D, 0.8D, 0.4D, 0.3D);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        if (MagicBolt.isWizard(other)) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.homePos != null) {
            tag.putInt("HomeX", this.homePos.getX());
            tag.putInt("HomeY", this.homePos.getY());
            tag.putInt("HomeZ", this.homePos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            this.homePos = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
            this.homeSet = true;
            this.restrictTo(this.homePos, HOME_RADIUS);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.EVOKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    /** Ranged behaviour: keeps 5-10 blocks away and casts a bolt every ~45 ticks. */
    static class WizardCastGoal extends Goal {
        private final RebelWizardEntity mob;
        private int nextNav;

        WizardCastGoal(RebelWizardEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distSqr = this.mob.distanceToSqr(target);
            return distSqr <= 14.0D * 14.0D && distSqr > 3.5D * 3.5D && this.mob.getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distSqr = this.mob.distanceToSqr(target);
            return distSqr <= 18.0D * 18.0D && distSqr > 2.5D * 2.5D;
        }

        @Override
        public void start() {
            this.nextNav = 0;
            this.mob.nextCastTick = Math.max(this.mob.nextCastTick, this.mob.tickCount + 15);
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }
            double dist = Math.sqrt(this.mob.distanceToSqr(target));
            boolean los = this.mob.getSensing().hasLineOfSight(target);
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (dist < 5.0D) {
                if (this.mob.tickCount >= this.nextNav) {
                    Vec3 away = DefaultRandomPos.getPosAway(this.mob, 8, 4, target.position());
                    if (away != null) {
                        this.mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
                    }
                    this.nextNav = this.mob.tickCount + 10;
                }
            } else if (dist > 10.0D || !los) {
                if (this.mob.tickCount >= this.nextNav) {
                    this.mob.getNavigation().moveTo(target, 1.0D);
                    this.nextNav = this.mob.tickCount + 10;
                }
            } else {
                this.mob.getNavigation().stop();
            }

            if (los && dist <= 14.0D && this.mob.tickCount >= this.mob.nextCastTick) {
                this.mob.castBolt(target);
                this.mob.nextCastTick = this.mob.tickCount + 45;
            }
        }
    }
}
