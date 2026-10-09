package com.soulstealer.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.soulstealer.Disguises;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Draws a player as a mob. For every morphed player a "puppet" copy of the mob is created on the client
 * (it is never added to the world) and every frame it is moved / rotated / animated like the player.
 */
@OnlyIn(Dist.CLIENT)
public final class MorphRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Puppet(Disguises.Entry entry, Entity entity) {
    }

    private static final Map<UUID, Puppet> PUPPETS = new HashMap<>();

    private MorphRenderer() {
    }

    public static void invalidate(UUID playerId) {
        PUPPETS.remove(playerId);
    }

    public static void clear() {
        PUPPETS.clear();
    }

    private static Entity puppetFor(Player player, Disguises.Entry entry) {
        Puppet puppet = PUPPETS.get(player.getUUID());
        if (puppet != null && puppet.entry() == entry
                && (puppet.entity() == null || puppet.entity().level() == player.level())) {
            return puppet.entity(); // may be null when creating it failed, we do not retry every frame
        }
        Entity created = create(player, entry);
        PUPPETS.put(player.getUUID(), new Puppet(entry, created));
        return created;
    }

    private static Entity create(Player player, Disguises.Entry entry) {
        try {
            if (entry.mobType() == null || !ForgeRegistries.ENTITY_TYPES.containsKey(entry.mobType())) {
                return null;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entry.mobType());
            if (type == null) {
                return null;
            }
            Entity entity = type.create(player.level());
            if (entity == null) {
                return null;
            }
            if (entry.mobData() != null) {
                try {
                    // Restores colour, age, variant and so on, so a morphed sheep has the colour of the sheep you hit.
                    CompoundTag copy = entry.mobData().copy();
                    entity.load(copy);
                } catch (Throwable t) {
                    LOGGER.warn("[Soul Stealer] Could not copy the mob data, using a plain mob instead.", t);
                    Entity plain = type.create(player.level());
                    if (plain != null) {
                        entity = plain;
                    }
                }
            }
            return entity;
        } catch (Throwable t) {
            LOGGER.error("[Soul Stealer] Could not create the morph puppet.", t);
            return null;
        }
    }

    /** Called every client tick: keeps the walking animation of the puppet in sync with the player. */
    public static void tick(Player player, Disguises.Entry entry) {
        Entity entity = puppetFor(player, entry);
        if (entity instanceof LivingEntity living) {
            // factor 1.0 = take over the speed of the player, the position then keeps adding up like the player's
            living.walkAnimation.update(player.walkAnimation.speed(), 1.0F);
        }
    }

    /**
     * Renders the player as the mob.
     *
     * @return true if the mob was drawn (the normal player model must then be skipped)
     */
    public static boolean render(Player player, Disguises.Entry entry, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int packedLight) {
        if (player.isInvisible()) {
            return false;
        }
        Entity entity = puppetFor(player, entry);
        if (entity == null) {
            return false;
        }

        try {
            sync(player, entity);

            EntityRenderer<? super Entity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
            // The player renderer lowers a crouching player by 0.125, that is already part of the pose stack.
            // A mob does not crouch, so it is moved back up (fixes the sinking-into-the-ground crouch).
            if (player.isCrouching()) {
                poseStack.translate(0.0D, 0.125D, 0.0D);
            }
            // The pose stack is already moved to the position of the player, so the renderer is called directly.
            renderer.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
            return true;
        } catch (Throwable t) {
            LOGGER.error("[Soul Stealer] Rendering the morph failed, showing the normal player instead.", t);
            PUPPETS.put(player.getUUID(), new Puppet(entry, null));
            return false;
        }
    }

    private static void sync(Player player, Entity entity) {
        entity.setPos(player.getX(), player.getY(), player.getZ());
        entity.xo = player.xo;
        entity.yo = player.yo;
        entity.zo = player.zo;
        entity.xOld = player.xOld;
        entity.yOld = player.yOld;
        entity.zOld = player.zOld;
        entity.setYRot(player.getYRot());
        entity.yRotO = player.yRotO;
        entity.setXRot(player.getXRot());
        entity.xRotO = player.xRotO;
        entity.tickCount = player.tickCount;
        entity.setOnGround(player.onGround());
        entity.setShiftKeyDown(player.isCrouching());
        entity.setPose(player.isCrouching() ? Pose.CROUCHING : Pose.STANDING);

        if (entity instanceof LivingEntity living) {
            living.yBodyRot = player.yBodyRot;
            living.yBodyRotO = player.yBodyRotO;
            living.yHeadRot = player.yHeadRot;
            living.yHeadRotO = player.yHeadRotO;
            living.swinging = player.swinging;
            living.swingTime = player.swingTime;
            living.swingingArm = player.swingingArm;
            living.attackAnim = player.attackAnim;
            living.oAttackAnim = player.oAttackAnim;
            living.hurtTime = player.hurtTime;
            living.hurtDuration = player.hurtDuration;

            // Shows what the player really holds / wears (and nothing else, so a tool can never get "stuck").
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack real = player.getItemBySlot(slot);
                if (!net.minecraft.world.item.ItemStack.matches(living.getItemBySlot(slot), real)) {
                    living.setItemSlot(slot, real.copy());
                }
            }
        }

        // Name tag: the (maybe stolen) nickname of the player. You do not see your own name tag.
        boolean self = player == Minecraft.getInstance().player;
        Component name = player.getDisplayName();
        if (entity.getCustomName() == null || !entity.getCustomName().getString().equals(name.getString())) {
            entity.setCustomName(name);
        }
        entity.setCustomNameVisible(!self);
    }

    /** Draws the player as a block. */
    public static boolean renderBlock(Player player, Disguises.Entry entry, float partialTick, PoseStack poseStack,
                                      MultiBufferSource buffers, int packedLight) {
        if (player.isInvisible()) {
            return false;
        }
        try {
            BlockState state = Block.stateById(entry.stateId());
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                return false;
            }
            poseStack.pushPose();
            if (player.isCrouching()) {
                poseStack.translate(0.0D, 0.125D, 0.0D);
            }
            poseStack.translate(-0.5D, 0.0D, -0.5D);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers, packedLight,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            return true;
        } catch (Throwable t) {
            LOGGER.error("[Wand Wizards] Rendering the block morph failed.", t);
            return false;
        }
    }
}
