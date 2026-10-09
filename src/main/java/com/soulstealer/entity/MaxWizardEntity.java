package com.soulstealer.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.soulstealer.SoulStealer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Clearable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Max the evil wizard: the boss of the evil tower. Three attacks, plus a fourth one once enraged below 50% health. */
public class MaxWizardEntity extends Monster {

    private static final int HOME_RADIUS = 20;
    private static final int STORM_WARNING_TICKS = 30;
    private static final int BARRAGE_WINDUP_TICKS = 10;
    private static final UUID RAGE_SPEED_ID = UUID.fromString("5c1f6a52-0d1e-4a3b-9f0a-3b6c2e7a1d01");
    private static final UUID RAGE_DAMAGE_ID = UUID.fromString("5c1f6a52-0d1e-4a3b-9f0a-3b6c2e7a1d02");
    private static final String NAME = "Max the evil wizard";

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.literal(NAME),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private BlockPos homePos;
    private boolean homeSet;
    boolean rage;

    // Cooldown timestamps (compared with tickCount).
    int nextAttackTick = 60;
    int nextBarrageTick = 60;
    int nextFangTick = 100;
    int nextSummonTick = 200;
    int nextStormTick = 0;

    // ---- rage set piece: countdown, rumble, roof explosion, collapse
    /** How long the players have after Max enrages (ticks). */
    public static final int RAGE_LIMIT_TICKS = 20 * 150;
    private static final double SAFE_RADIUS = 38.0D;
    private static final String SUMMON_TAG = "max_summon";
    private int rageTimer = -1;
    private boolean doomed;
    private int doomTicks;
    private final List<BlockPos> roofQueue = new ArrayList<>();
    private int roofIndex;
    private int rageCharge = -1;
    private BlockPos holePos;
    private String holeDir = "north";

    private int barrageAt = -1;
    private LivingEntity barrageTarget;
    private final List<Strike> strikes = new ArrayList<>();

    private static final class Strike {
        final Vec3 pos;
        final int fireTick;

        Strike(Vec3 pos, int fireTick) {
            this.pos = pos;
            this.fireTick = fireTick;
        }
    }

    public MaxWizardEntity(EntityType<? extends MaxWizardEntity> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        this.setPersistenceRequired();
        this.setCustomName(Component.literal(NAME));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        // Max sleeps (invisible and untouchable) until a player walks into his hall.
        this.setDormant();
    }

    // ---------------------------------------------------------------- waking up / music

    public static final int THEME_TICKS = 20 * 76;
    public static final int RAGE_THEME_TICKS = 20 * 56;
    private static final ResourceLocation THEME_ID = new ResourceLocation("soulstealer", "boss_theme");
    private static final ResourceLocation RAGE_THEME_ID = new ResourceLocation("soulstealer", "boss_rage_theme");

    private boolean awake;
    private int awakeningTicks = -1;
    private int musicEndTick;

    private void setDormant() {
        this.awake = false;
        this.setInvisible(false);          // Max is always visible, he just stands still until somebody enters his hall
        this.setInvulnerable(true);
        this.setNoAi(true);
        this.setSilent(true);
        this.setCustomNameVisible(false);
        this.dress();
    }

    private void dress() {
        ItemStack hat = MagicBolt.wizardHat();
        if (!hat.isEmpty()) {
            this.setItemSlot(EquipmentSlot.HEAD, hat);
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.END_ROD));
    }

    private void becomeActive() {
        this.awake = true;
        this.awakeningTicks = -1;
        this.setInvisible(false);
        this.dress();
        this.setInvulnerable(false);
        this.setNoAi(false);
        this.setSilent(false);
        this.setCustomNameVisible(true);
        this.musicEndTick = 0;
        if (this.level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.players()) {
                if (player.distanceToSqr(this) <= 48.0D * 48.0D) {
                    this.bossEvent.addPlayer(player);
                }
            }
        }
    }

    /** Called when a player enters the boss room. */
    private void beginAwakening(ServerLevel server, Player intruder) {
        this.awakeningTicks = 0;
        this.setInvisible(false);
        this.dress();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(this.position());
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.8F);
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(this) <= 24.0D * 24.0D) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
            }
        }
        Component msg = Component.literal("Max the evil wizard: ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal("You dare enter my hall?!").withStyle(ChatFormatting.RED));
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(this) <= 40.0D * 40.0D) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private void stopMusic() {
        if (this.level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.players()) {
                player.connection.send(new ClientboundStopSoundPacket(THEME_ID, SoundSource.RECORDS));
                player.connection.send(new ClientboundStopSoundPacket(RAGE_THEME_ID, SoundSource.RECORDS));
            }
        }
    }

    private void playMusic(ServerLevel server) {
        if (this.tickCount < this.musicEndTick) {
            return;
        }
        SoundEvent track = this.rage ? SoulStealer.BOSS_RAGE_THEME.get() : SoulStealer.BOSS_THEME.get();
        this.musicEndTick = this.tickCount + (this.rage ? RAGE_THEME_TICKS : THEME_TICKS);
        server.playSound(null, this.getX(), this.getY(), this.getZ(), track, SoundSource.RECORDS, 4.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server) || !this.isAlive()) {
            return;
        }
        if (!this.awake) {
            if (this.awakeningTicks < 0) {
                Player near = server.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 14.0D,
                        p -> !p.isSpectator());
                if (near != null && near.getY() >= this.getY() - 1.5D && near.getY() <= this.getY() + 9.0D
                        && Math.hypot(near.getX() - this.getX(), near.getZ() - this.getZ()) <= 13.0D) {
                    this.beginAwakening(server, near);
                }
            } else {
                this.awakeningTicks++;
                if (this.awakeningTicks % 3 == 0) {
                    server.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0D, this.getZ(), 20, 0.6D, 1.0D, 0.6D, 0.4D);
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.2D, this.getZ(), 6, 1.2D, 0.1D, 1.2D, 0.02D);
                }
                if (this.awakeningTicks == 40) {
                    server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 3.0F, 0.7F);
                }
                if (this.awakeningTicks >= 70) {
                    this.becomeActive();
                }
            }
            return;
        }
        if (this.doomed) {
            this.processDoom(server);
            return;
        }
        this.playMusic(server);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.stopMusic();
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 1.2D, this.getZ(), 120, 0.8D, 1.2D, 0.8D, 0.5D);
            server.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.2D, this.getZ(), 60, 1.0D, 1.5D, 1.0D, 0.2D);
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 3.0F, 0.8F);
            Component msg = Component.literal("Max the evil wizard has been defeated!").withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : server.players()) {
                if (player.distanceToSqr(this) <= 60.0D * 60.0D) {
                    player.sendSystemMessage(msg);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.awake || this.doomed || (this.rageCharge >= 0 && !this.rage)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (!this.awake && target != null) {
            return;
        }
        super.setTarget(target);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 220.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MaxAttackGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RebelWizardEntity.class, MaxWizardEntity.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ---------------------------------------------------------------- boss bar / naming

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (this.bossEvent != null) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.awake && !this.doomed) {
            this.bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.awake) {
            this.stopMusic();
        }
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    // ---------------------------------------------------------------- ticking

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel server) || !this.awake) {
            return;
        }
        if (!this.homeSet) {
            this.homeSet = true;
            if (this.homePos == null) {
                this.homePos = this.blockPosition();
            }
            this.restrictTo(this.homePos, HOME_RADIUS);
        }

        if (this.doomed) {
            return;
        }

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (!this.rage) {
            this.chargeRage(server);
        }

        if (this.rage && this.tickCount % 2 == 0) {
            server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 1.4F),
                    this.getX(), this.getY() + 1.0D, this.getZ(), 6, 0.5D, 0.9D, 0.5D, 0.0D);
            if (this.random.nextInt(6) == 0) {
                server.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 1.0D, this.getZ(), 3, 0.4D, 0.8D, 0.4D, 0.02D);
            }
            if (this.tickCount % 100 == 0) {
                LightningBolt flash = EntityType.LIGHTNING_BOLT.create(server);
                if (flash != null) {
                    flash.moveTo(this.getX() + (this.random.nextDouble() - 0.5D) * 14.0D, this.getY(),
                            this.getZ() + (this.random.nextDouble() - 0.5D) * 14.0D);
                    flash.setVisualOnly(true);
                    server.addFreshEntity(flash);
                }
            }
            if (this.random.nextInt(10) == 0) {
                server.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 2.1D, this.getZ(), 1, 0.3D, 0.2D, 0.3D, 0.0D);
            }
        }

        // delayed arcane barrage
        if (this.barrageAt >= 0 && this.tickCount >= this.barrageAt) {
            this.barrageAt = -1;
            LivingEntity target = this.barrageTarget;
            this.barrageTarget = null;
            if (target != null && target.isAlive()) {
                this.fireBarrage(target);
            }
        }

        this.processStrikes(server);
        if (this.rage) {
            this.rageTick(server);
        }
    }

    private void enterRage(boolean announce) {
        this.rage = true;
        if (announce) {
            this.stopMusic();
            this.musicEndTick = 0;
        }
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.applyRageModifiers();
        if (announce) {
            // a short breather before the first rage attacks, and the countdown starts
            this.nextAttackTick = this.tickCount + 80;
            this.nextStormTick = this.tickCount + 140;
            this.rageTimer = RAGE_LIMIT_TICKS;
        }
        if (announce && this.level() instanceof ServerLevel server) {
            this.blowRoof(server);
            this.openEscapeHole(server);
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 1.0F);
            Component msg = Component.literal("Max the evil wizard is enraged!").withStyle(ChatFormatting.RED);
            Component msg2 = Component.literal("The castle is tearing itself apart! Defeat Max within "
                    + (RAGE_LIMIT_TICKS / 1200) + " minutes and 30 seconds or everything will collapse!").withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : server.players()) {
                if (player.distanceToSqr(this) <= 40.0D * 40.0D) {
                    player.sendSystemMessage(msg);
                    player.sendSystemMessage(msg2);
                    if (this.holePos != null) {
                        player.sendSystemMessage(Component.literal("A hole was blasted into the " + this.holeDir + " wall of the hall! "
                                + "Follow the glowing particles, jump out and run far away from the castle to survive!").withStyle(ChatFormatting.AQUA));
                    }
                }
            }
        }
    }

    private void applyRageModifiers() {
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(RAGE_SPEED_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(RAGE_SPEED_ID, "Max rage speed", 0.3D, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getModifier(RAGE_DAMAGE_ID) == null) {
            damage.addTransientModifier(new AttributeModifier(RAGE_DAMAGE_ID, "Max rage damage", 0.3D, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    /** Cooldown scaled by the rage state. */
    int cooldown(int base) {
        return this.rage ? Math.round(base * 0.9F) : base;
    }

    // ---------------------------------------------------------------- attack 1: arcane barrage

    void startBarrage(LivingEntity target) {
        this.nextBarrageTick = this.tickCount + this.cooldown(70);
        this.barrageAt = this.tickCount + BARRAGE_WINDUP_TICKS;
        this.barrageTarget = target;
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 1.0F);
    }

    private void fireBarrage(LivingEntity target) {
        Vec3 start = this.getEyePosition();
        Vec3 aim = new Vec3(target.getX(), target.getY(0.5D), target.getZ());
        Vec3 dir = aim.subtract(start).normalize();
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.9F);
        float[] yawOffsets = {-10.0F, 0.0F, 10.0F};
        for (float deg : yawOffsets) {
            Vec3 d = dir.yRot((float) Math.toRadians(deg));
            MagicBolt.bolt(this, start, d, 20.0D, 6.0F, ParticleTypes.WITCH, ParticleTypes.ENCHANT);
        }
    }

    // ---------------------------------------------------------------- attack 2: fang line

    void castFangLine(LivingEntity target) {
        this.nextFangTick = this.tickCount + this.cooldown(120);
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 1.0F);
        double minY = Math.min(target.getY(), this.getY());
        double maxY = Math.max(target.getY(), this.getY()) + 1.0D;
        float yaw = (float) Mth.atan2(target.getZ() - this.getZ(), target.getX() - this.getX());
        for (int i = 0; i < 10; i++) {
            double dist = 1.25D * (i + 1);
            this.createFang(this.getX() + Mth.cos(yaw) * dist, this.getZ() + Mth.sin(yaw) * dist, minY, maxY, yaw, i * 2);
        }
    }

    private void createFang(double x, double z, double minY, double maxY, float yaw, int warmup) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        boolean found = false;
        double yOffset = 0.0D;
        do {
            BlockPos below = pos.below();
            BlockState belowState = this.level().getBlockState(below);
            if (belowState.isFaceSturdy(this.level(), below, Direction.UP)) {
                BlockState here = this.level().getBlockState(pos);
                VoxelShape shape = here.getCollisionShape(this.level(), pos);
                if (!shape.isEmpty()) {
                    yOffset = shape.max(Direction.Axis.Y);
                }
                found = true;
                break;
            }
            pos = pos.below();
        } while (pos.getY() >= Mth.floor(minY) - 1);

        if (found) {
            this.level().addFreshEntity(new EvokerFangs(this.level(), x, pos.getY() + yOffset, z, yaw, warmup, this));
        }
    }

    // ---------------------------------------------------------------- attack 3: summon rebels

    /** Only the rebels Max summoned himself count (the castle guards do not). */
    int countRebels() {
        return this.level().getEntitiesOfClass(RebelWizardEntity.class, this.getBoundingBox().inflate(40.0D),
                e -> e.isAlive() && e.getTags().contains(SUMMON_TAG)).size();
    }

    void summonRebels(LivingEntity target) {
        this.nextSummonTick = this.tickCount + this.cooldown(900);
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 1.0F);
        for (int i = 0; i < 1; i++) {
            RebelWizardEntity rebel = SoulStealer.REBEL_WIZARD.get().create(server);
            if (rebel == null) {
                continue;
            }
            boolean placed = false;
            for (int attempt = 0; attempt < 12 && !placed; attempt++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0D;
                double dist = 2.0D + this.random.nextDouble() * 2.0D;
                double x = this.getX() + Math.cos(angle) * dist;
                double z = this.getZ() + Math.sin(angle) * dist;
                for (int dy = 0; dy <= 2 && !placed; dy++) {
                    rebel.moveTo(x, this.getY() + dy, z, this.random.nextFloat() * 360.0F, 0.0F);
                    if (server.noCollision(rebel)) {
                        placed = true;
                    }
                }
            }
            if (!placed) {
                rebel.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
            }
            rebel.addTag(SUMMON_TAG);
            rebel.setTarget(target);
            server.addFreshEntity(rebel);
            server.sendParticles(ParticleTypes.POOF, rebel.getX(), rebel.getY() + 1.0D, rebel.getZ(), 20, 0.3D, 0.6D, 0.3D, 0.05D);
        }
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.8F);
    }

    // ---------------------------------------------------------------- attack 4 (rage): storm call

    void castStormCall(LivingEntity target) {
        this.nextStormTick = this.tickCount + this.cooldown(220);
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 0.7F);
        int fireTick = this.tickCount + STORM_WARNING_TICKS;
        this.strikes.add(new Strike(target.position(), fireTick));
        for (int i = 0; i < 1; i++) {
            double ox = (this.random.nextDouble() * 2.0D - 1.0D) * 4.0D;
            double oz = (this.random.nextDouble() * 2.0D - 1.0D) * 4.0D;
            this.strikes.add(new Strike(new Vec3(target.getX() + ox, target.getY(), target.getZ() + oz), fireTick));
        }
    }

    private void processStrikes(ServerLevel server) {
        if (this.strikes.isEmpty()) {
            return;
        }
        Iterator<Strike> it = this.strikes.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            Vec3 p = strike.pos;
            if (this.tickCount >= strike.fireTick) {
                it.remove();
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                if (bolt != null) {
                    bolt.moveTo(p);
                    bolt.setVisualOnly(true);
                    server.addFreshEntity(bolt);
                }
                AABB box = new AABB(p.x - 2.5D, p.y - 2.0D, p.z - 2.5D, p.x + 2.5D, p.y + 4.0D, p.z + 2.5D);
                List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e.isAlive() && !MagicBolt.isWizard(e)
                                && Math.hypot(e.getX() - p.x, e.getZ() - p.z) <= 2.5D);
                for (LivingEntity victim : victims) {
                    victim.hurt(server.damageSources().lightningBolt(), 7.0F);
                }
            } else if (this.tickCount % 2 == 0) {
                DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.0F);
                for (int i = 0; i < 16; i++) {
                    double a = i * (Math.PI * 2.0D / 16.0D);
                    server.sendParticles(dust, p.x + Math.cos(a) * 2.5D, p.y + 0.1D, p.z + Math.sin(a) * 2.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }


    // ---------------------------------------------------------------- rage set piece

    /** Charging up: a few seconds of build-up before the rage really starts. */
    private void chargeRage(ServerLevel server) {
        if (this.rageCharge < 0) {
            if (this.getHealth() > this.getMaxHealth() * 0.5F) {
                return;
            }
            this.rageCharge = 0;
            this.stopMusic();
            this.musicEndTick = this.tickCount + 400;
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.6F);
            Component msg = Component.literal("Max the evil wizard: ").withStyle(ChatFormatting.DARK_PURPLE)
                    .append(Component.literal("ENOUGH! Feel my true power!").withStyle(ChatFormatting.RED));
            for (ServerPlayer p : server.players()) {
                if (p.distanceToSqr(this) <= 40.0D * 40.0D) {
                    p.sendSystemMessage(msg);
                }
            }
        }
        this.rageCharge++;
        this.nextAttackTick = this.tickCount + 30;
        this.getNavigation().stop();
        double a = this.rageCharge * 0.5D;
        double r = 1.8D - this.rageCharge * 0.012D;
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 1.3F);
        for (int i = 0; i < 3; i++) {
            double aa = a + i * 2.094D;
            server.sendParticles(red, this.getX() + Math.cos(aa) * r, this.getY() + (this.rageCharge % 40) * 0.05D + i * 0.5D,
                    this.getZ() + Math.sin(aa) * r, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (this.rageCharge % 10 == 0) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, 0.4F + this.rageCharge * 0.004F);
            server.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.3D, this.getZ(), 8, 0.8D, 0.1D, 0.8D, 0.04D);
        }
        if (this.rageCharge == 70) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.moveTo(this.position());
                bolt.setVisualOnly(true);
                server.addFreshEntity(bolt);
            }
        }
        if (this.rageCharge >= 100) {
            this.rageCharge = -1;
            this.enterRage(true);
        }
    }

    /** Blasts a doorway-sized hole into the hall wall on the outer side of the castle, for the players to escape through. */
    private void openEscapeHole(ServerLevel server) {
        BlockPos o = this.origin();
        Direction toward = Direction.SOUTH;
        int best = -1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int count = 0;
            for (int dist = 2; dist <= 36; dist++) {
                for (int dy = -24; dy <= -14; dy += 2) {
                    if (!server.getBlockState(o.relative(d, dist).above(dy)).isAir()) {
                        count++;
                    }
                }
            }
            if (count > best) {
                best = count;
                toward = d;
            }
        }
        Direction out = toward.getOpposite();      // Max stands near the outer side of the castle
        Direction side = out.getClockWise();
        this.holeDir = out.getName();
        List<BlockPos> hole = new ArrayList<>();
        for (int dist = 2; dist <= 10; dist++) {
            for (int lat = -2; lat <= 2; lat++) {
                for (int up = 0; up <= 5; up++) {
                    BlockPos p = o.relative(out, dist).relative(side, lat).above(up);
                    BlockState st = server.getBlockState(p);
                    if (!st.isAir() && st.getDestroySpeed(server, p) >= 0.0F) {
                        hole.add(p);
                    }
                }
            }
        }
        this.holePos = o.relative(out, 6).above(2);
        this.roofQueue.addAll(0, hole);
        this.roofIndex = 0;
    }

    private BlockPos origin() {
        return this.homePos != null ? this.homePos : this.blockPosition();
    }

    /** Collects the roof above the boss hall (everything connected above hall height) and queues it for exploding. */
    private void blowRoof(ServerLevel server) {
        BlockPos o = this.origin();
        int minY = o.getY() + 11;
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int y = minY; y <= minY + 12; y++) {
                    BlockPos p = new BlockPos(o.getX() + dx, y, o.getZ() + dz);
                    if (!server.getBlockState(p).isAir() && seen.add(p)) {
                        open.add(p);
                    }
                }
            }
        }
        List<BlockPos> found = new ArrayList<>();
        while (!open.isEmpty() && found.size() < 6000) {
            BlockPos p = open.poll();
            BlockState st = server.getBlockState(p);
            if (st.isAir() || st.getDestroySpeed(server, p) < 0.0F) {
                continue;
            }
            found.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos n = p.offset(dx, dy, dz);
                        if (n.getY() < minY || Math.abs(n.getX() - o.getX()) > 18 || Math.abs(n.getZ() - o.getZ()) > 18) {
                            continue;
                        }
                        if (seen.add(n) && !server.getBlockState(n).isAir()) {
                            open.add(n);
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(o)));
        this.roofQueue.clear();
        this.roofQueue.addAll(found);   // the escape hole is added in front of this by openEscapeHole
        this.roofIndex = 0;
        server.playSound(null, o.getX(), o.getY() + 8, o.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6.0F, 0.6F);
    }

    private void processRoof(ServerLevel server) {
        if (this.roofIndex >= this.roofQueue.size()) {
            if (!this.roofQueue.isEmpty()) {
                this.roofQueue.clear();
                this.roofIndex = 0;
            }
            return;
        }
        int end = Math.min(this.roofQueue.size(), this.roofIndex + 40);
        for (; this.roofIndex < end; this.roofIndex++) {
            BlockPos p = this.roofQueue.get(this.roofIndex);
            BlockState st = server.getBlockState(p);
            if (st.isAir()) {
                continue;
            }
            if (this.roofIndex % 4 == 0) {
                server.sendParticles(ParticleTypes.EXPLOSION, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, 1, 0.3D, 0.3D, 0.3D, 0.0D);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                        14, 0.5D, 0.5D, 0.5D, 0.15D);
            }
            if (this.roofIndex % 30 == 0) {
                server.playSound(null, p, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.7F + this.random.nextFloat() * 0.5F);
                server.sendParticles(ParticleTypes.LAVA, p.getX() + 0.5D, p.getY() + 1.0D, p.getZ() + 0.5D, 6, 0.6D, 0.6D, 0.6D, 0.0D);
            }
            server.removeBlock(p, false);
        }
    }

    private static String clock(int ticks) {
        int secs = (ticks + 19) / 20;
        return (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
    }

    /** Every tick of the rage: roof explosion, countdown on the boss bar, rumbling. */
    private void rageTick(ServerLevel server) {
        this.processRoof(server);
        if (this.rageTimer < 0) {
            return;
        }
        BlockPos o = this.origin();
        if (this.holePos != null && this.tickCount % 6 == 0) {
            for (int i = 0; i < 6; i++) {
                server.sendParticles(ParticleTypes.END_ROD, this.holePos.getX() + 0.5D, this.holePos.getY() - 2 + i * 0.8D,
                        this.holePos.getZ() + 0.5D, 2, 1.2D, 0.2D, 1.2D, 0.01D);
            }
        }
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : server.players()) {
            if (!p.isSpectator() && p.distanceToSqr(o.getX() + 0.5D, o.getY(), o.getZ() + 0.5D) <= 45.0D * 45.0D) {
                near.add(p);
            }
        }
        if (near.isEmpty()) {
            return;   // the countdown only runs while somebody is there
        }
        if (this.rageTimer > 0) {
            this.rageTimer--;
        }
        int secs = (this.rageTimer + 19) / 20;
        boolean intense = secs <= 30;
        if (this.tickCount % 10 == 0) {
            this.bossEvent.setName(Component.literal(NAME + "   -   Castle collapses in " + clock(this.rageTimer))
                    .withStyle(intense ? ChatFormatting.DARK_RED : ChatFormatting.RED));
        }
        if (this.rageTimer % 20 == 0 && (secs == 120 || secs == 60 || secs == 30 || secs == 10 || (secs <= 5 && secs > 0))) {
            Component warn = Component.literal("The castle collapses in " + secs + " seconds!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer p : near) {
                p.displayClientMessage(warn, true);
                p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1.5F, secs <= 5 ? 1.6F : 0.8F);
            }
        }
        if (this.tickCount % 20 == 0) {
            for (ServerPlayer p : near) {
                if (p.getY() > o.getY() - 8) {
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));   // so jumping out of the hole is safe
                }
            }
        }
        if (this.tickCount % (intense ? 8 : 16) == 0) {
            this.rumble(server, near, intense);
        }
        if (this.rageTimer <= 0) {
            this.startDoom(server);
        }
    }

    private void rumble(ServerLevel server, List<ServerPlayer> near, boolean intense) {
        BlockPos o = this.origin();
        server.playSound(null, o.getX(), o.getY() + 2, o.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                intense ? 1.6F : 0.9F, 0.4F + this.random.nextFloat() * 0.2F);
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState());
        for (ServerPlayer p : near) {
            for (int i = 0; i < 6; i++) {
                server.sendParticles(debris, p.getX() + (this.random.nextDouble() - 0.5D) * 12.0D, p.getY() + 4.0D + this.random.nextDouble() * 4.0D,
                        p.getZ() + (this.random.nextDouble() - 0.5D) * 12.0D, 5, 0.3D, 0.3D, 0.3D, 0.0D);
            }
            if (this.random.nextInt(4) == 0) {
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 2.5D, p.getZ(), 2, 3.0D, 0.5D, 3.0D, 0.01D);
            }
            if (p.onGround() && this.random.nextInt(intense ? 2 : 4) == 0) {
                p.push((this.random.nextDouble() - 0.5D) * 0.12D, 0.16D, (this.random.nextDouble() - 0.5D) * 0.12D);
                p.hurtMarked = true;
            }
            if (intense && this.random.nextInt(14) == 0) {
                p.hurt(server.damageSources().fallingBlock(this), 3.0F);
            }
        }
    }

    /** The timer ran out: everybody in the castle dies, nothing is rewarded and the castle crumbles away. */
    private void startDoom(ServerLevel server) {
        this.doomed = true;
        this.doomTicks = 0;
        this.stopMusic();
        this.bossEvent.removeAllPlayers();
        this.setInvulnerable(true);
        this.setNoAi(true);
        this.setInvisible(true);
        this.setSilent(true);
        this.setCustomNameVisible(false);
        this.getNavigation().stop();
        BlockPos o = this.origin();
        server.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 8.0F, 0.5F);
        Component msg = Component.literal("The castle crumbles into dust... Max wins.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(o.getX() + 0.5D, o.getY(), o.getZ() + 0.5D) <= 50.0D * 50.0D) {
                p.sendSystemMessage(msg);
                double hd = Math.hypot(p.getX() - (o.getX() + 0.5D), p.getZ() - (o.getZ() + 0.5D));
                if (hd > SAFE_RADIUS) {
                    p.sendSystemMessage(Component.literal("You escaped the collapsing castle!").withStyle(ChatFormatting.GREEN));
                } else if (!p.isCreative() && !p.isSpectator()) {
                    p.hurt(server.damageSources().genericKill(), Float.MAX_VALUE);
                }
            }
        }
    }

    private void processDoom(ServerLevel server) {
        this.doomTicks++;
        BlockPos o = this.origin();
        int top = o.getY() + 34;
        int bottom = o.getY() - 26;                 // the courtyard floor stays
        int y = top - (this.doomTicks - 1);
        if (this.doomTicks % 4 == 1) {
            server.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5.0F, 0.5F + this.random.nextFloat() * 0.3F);
        }
        if (y < bottom) {
            AABB box = new AABB(o.getX() - 37, bottom, o.getZ() - 37, o.getX() + 38, top + 2, o.getZ() + 38);
            for (Entity e : server.getEntities((Entity) null, box, e -> e instanceof ItemEntity || e instanceof ArmorStand
                    || e instanceof ItemFrame || e instanceof RebelWizardEntity)) {
                e.discard();
            }
            this.discard();
            return;
        }
        final int radius = 37;
        int removed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos p = new BlockPos(o.getX() + dx, y, o.getZ() + dz);
                BlockState st = server.getBlockState(p);
                if (st.isAir() || st.getDestroySpeed(server, p) < 0.0F || st.is(BlockTags.DIRT) || st.is(BlockTags.SAND)
                        || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)
                        || st.is(Blocks.GRAVEL) || st.is(Blocks.WATER) || st.is(Blocks.LAVA)) {
                    continue;
                }
                BlockEntity be = server.getBlockEntity(p);
                if (be instanceof Clearable clearable) {
                    clearable.clearContent();
                }
                removed++;
                if (removed % 25 == 0) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                            10, 0.5D, 0.5D, 0.5D, 0.1D);
                    server.sendParticles(ParticleTypes.EXPLOSION, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                server.removeBlock(p, false);
            }
        }
    }

    // ---------------------------------------------------------------- misc

    @Override
    public boolean isAlliedTo(Entity other) {
        if (MagicBolt.isWizard(other)) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Rage", this.rage);
        tag.putBoolean("Awake", this.awake);
        tag.putInt("RageTimer", this.rageTimer);
        tag.putBoolean("Doomed", this.doomed);
        if (this.holePos != null) {
            tag.putInt("HoleX", this.holePos.getX());
            tag.putInt("HoleY", this.holePos.getY());
            tag.putInt("HoleZ", this.holePos.getZ());
            tag.putString("HoleDir", this.holeDir);
        }
        if (this.homePos != null) {
            tag.putInt("HomeX", this.homePos.getX());
            tag.putInt("HomeY", this.homePos.getY());
            tag.putInt("HomeZ", this.homePos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!this.hasCustomName()) {
            this.setCustomName(Component.literal(NAME));
        } else {
            this.bossEvent.setName(this.getDisplayName());
        }
        if (tag.getBoolean("Awake")) {
            this.dress();
            this.setInvisible(false);
            this.becomeActive();
        } else {
            this.setDormant();
        }
        if (tag.getBoolean("Rage")) {
            this.enterRage(false);
            this.rageTimer = tag.contains("RageTimer") ? tag.getInt("RageTimer") : RAGE_LIMIT_TICKS;
        }
        if (tag.contains("HoleX")) {
            this.holePos = new BlockPos(tag.getInt("HoleX"), tag.getInt("HoleY"), tag.getInt("HoleZ"));
            this.holeDir = tag.getString("HoleDir");
        }
        if (tag.getBoolean("Doomed")) {
            this.doomed = true;
            this.setInvisible(true);
            this.setInvulnerable(true);
            this.setNoAi(true);
            this.setSilent(true);
            this.setCustomNameVisible(false);
        }
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

    // ---------------------------------------------------------------- the attack goal

    /** Chooses between the attacks, keeps ~6 blocks distance (strafing) and melees when very close. */
    static class MaxAttackGoal extends Goal {
        private final MaxWizardEntity mob;
        private int nextNav;
        private int nextMelee;
        private int strafeDir = 1;
        private int nextStrafeSwitch;

        MaxAttackGoal(MaxWizardEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive() && this.mob.distanceToSqr(target) <= 40.0D * 40.0D;
        }

        @Override
        public void start() {
            this.nextNav = 0;
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
            int now = this.mob.tickCount;
            double dist = Math.sqrt(this.mob.distanceToSqr(target));
            boolean los = this.mob.getSensing().hasLineOfSight(target);
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // movement
            if (dist <= 3.0D) {
                this.mob.getNavigation().stop();
                if (now >= this.nextMelee) {
                    this.nextMelee = now + 20;
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    this.mob.doHurtTarget(target);
                }
            } else if (dist < 5.0D) {
                if (now >= this.nextNav) {
                    Vec3 away = DefaultRandomPos.getPosAway(this.mob, 8, 4, target.position());
                    if (away != null) {
                        this.mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
                    }
                    this.nextNav = now + 10;
                }
            } else if (dist > 8.0D || !los) {
                if (now >= this.nextNav) {
                    this.mob.getNavigation().moveTo(target, 1.1D);
                    this.nextNav = now + 10;
                }
            } else {
                this.mob.getNavigation().stop();
                if (now >= this.nextStrafeSwitch) {
                    this.strafeDir = -this.strafeDir;
                    this.nextStrafeSwitch = now + 40 + this.mob.random.nextInt(40);
                }
                this.mob.getMoveControl().strafe(0.0F, 0.5F * this.strafeDir);
            }

            // attacks
            if (now < 40 || now < this.mob.nextAttackTick || dist > 28.0D) {
                return;
            }
            List<Integer> options = new ArrayList<>();
            if (this.mob.rage && now >= this.mob.nextStormTick) {
                options.add(4);
            }
            if (now >= this.mob.nextSummonTick && this.mob.countRebels() < (this.mob.rage ? 1 : 2)) {
                options.add(3);
            }
            if (now >= this.mob.nextFangTick && dist <= 20.0D) {
                options.add(2);
            }
            if (now >= this.mob.nextBarrageTick && los) {
                options.add(1);
            }
            if (options.isEmpty()) {
                return;
            }
            int choice = options.get(this.mob.random.nextInt(options.size()));
            switch (choice) {
                case 1 -> this.mob.startBarrage(target);
                case 2 -> this.mob.castFangLine(target);
                case 3 -> this.mob.summonRebels(target);
                default -> this.mob.castStormCall(target);
            }
            this.mob.nextAttackTick = now + (this.mob.rage ? 60 : 40);
        }
    }
}
