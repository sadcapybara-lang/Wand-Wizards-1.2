package com.soulstealer.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A dummy that looks like a player (skin + name tag) but is a normal mob.
 * It wanders around and does random "player things" (see {@link FakeActivityGoal}).
 */
public class FakePlayerEntity extends PathfinderMob {

    /** The "textures" property (base64 json) of the skin, resolved on the server, used by the client to draw the skin. */
    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.STRING);

    public FakePlayerEntity(EntityType<? extends FakePlayerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SKIN, "");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FakeActivityGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    public String getSkinTexture() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkinTexture(String value) {
        this.entityData.set(DATA_SKIN, value == null ? "" : value);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinTexture", this.getSkinTexture());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSkinTexture(tag.getString("SkinTexture"));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    /** Dies like a real player: the death message shows up in chat. */
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            MinecraftServer server = this.level().getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(this.getCombatTracker().getDeathMessage(), false);
            }
        }
        super.die(source);
    }

    /** "Name joined the game" - exactly like the real message. */
    public static void broadcastJoin(MinecraftServer server, String name) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("multiplayer.player.joined", Component.literal(name)).withStyle(ChatFormatting.YELLOW),
                false);
    }

    /** Removes the dummy and sends a "left the game" message. */
    public void leaveGame() {
        if (this.level().isClientSide()) {
            return;
        }
        MinecraftServer server = this.level().getServer();
        if (server != null && this.hasCustomName()) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("multiplayer.player.left", this.getCustomName()).withStyle(ChatFormatting.YELLOW),
                    false);
        }
        this.discard();
    }
}
