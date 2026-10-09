package com.soulstealer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.network.PacketDistributor;

/**
 * Keeps track of who is currently disguised as what.
 * The server map is the real one, the client map is a copy synced through {@link SyncDisguisePacket}.
 * Nothing is saved to disk, so everything resets when the world / server is closed.
 */
public final class Disguises {

    public enum Kind {
        /** Looks like another player (skin + nickname). */
        PLAYER,
        /** Looks like a mob / animal. */
        MOB,
        /** Looks like a block. */
        BLOCK
    }

    public record Entry(Kind kind, UUID victimId, String victimName, ResourceLocation mobType, CompoundTag mobData, int stateId) {
        public static Entry player(UUID id, String name) {
            return new Entry(Kind.PLAYER, id, name, null, null, 0);
        }

        public static Entry mob(ResourceLocation type, CompoundTag data) {
            return new Entry(Kind.MOB, null, "", type, data, 0);
        }

        public static Entry block(int stateId) {
            return new Entry(Kind.BLOCK, null, "", null, null, stateId);
        }
    }

    /** Players who swapped skins with the Life Stealer Wand (both directions are stored). */
    public static final Map<UUID, UUID> SWAPS = new ConcurrentHashMap<>();
    /** Custom nicknames set with the Name Changer. */
    public static final Map<UUID, String> NICKS_SERVER = new ConcurrentHashMap<>();
    public static final Map<UUID, String> NICKS_CLIENT = new ConcurrentHashMap<>();

    public static Map<UUID, String> nicks(boolean clientSide) {
        return clientSide ? NICKS_CLIENT : NICKS_SERVER;
    }

    public static final Map<UUID, Entry> SERVER = new ConcurrentHashMap<>();
    public static final Map<UUID, Entry> CLIENT = new ConcurrentHashMap<>();

    /** NBT keys that must not be copied to the client copy of the mob. */
    private static final String[] STRIP = {
            "Pos", "Motion", "Rotation", "UUID", "Passengers", "Leash", "Brain", "Offers", "Inventory", "Items",
            "Attributes", "ActiveEffects", "active_effects", "FallDistance", "Fire", "Air", "OnGround", "Health",
            "HurtTime", "DeathTime", "HurtByTimestamp", "AbsorptionAmount", "Invulnerable",
            "HandItems", "ArmorItems", "HandDropChances", "ArmorDropChances", "SaddleItem", "ArmorItem", "Saddle", "Item", "CustomName", "CustomNameVisible"
    };

    private Disguises() {
    }

    public static Map<UUID, Entry> forSide(boolean clientSide) {
        return clientSide ? CLIENT : SERVER;
    }

    /** Take the skin and nickname of another player. */
    public static void steal(ServerPlayer thief, ServerPlayer victim) {
        unswapIfNeeded(thief);
        Entry entry = Entry.player(victim.getUUID(), victim.getGameProfile().getName());
        apply(thief, entry);
        thief.displayClientMessage(
                Component.literal("You stole the soul of " + entry.victimName() + "!").withStyle(ChatFormatting.DARK_PURPLE),
                true);
    }

    /** Turn into a mob / animal. */
    public static void morph(ServerPlayer thief, Mob target) {
        unswapIfNeeded(thief);
        CompoundTag data = new CompoundTag();
        target.saveWithoutId(data);
        for (String key : STRIP) {
            data.remove(key);
        }
        Entry entry = Entry.mob(EntityType.getKey(target.getType()), data);
        apply(thief, entry);
        thief.displayClientMessage(
                Component.literal("You morphed into a ").append(target.getType().getDescription()).append("!")
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);
    }

    /** Turn into a block. */
    public static void morphBlock(ServerPlayer thief, net.minecraft.world.level.block.state.BlockState state) {
        unswapIfNeeded(thief);
        Entry entry = Entry.block(net.minecraft.world.level.block.Block.getId(state));
        apply(thief, entry);
        thief.displayClientMessage(
                Component.literal("You turned into ").append(state.getBlock().getName()).append("!")
                        .withStyle(ChatFormatting.AQUA),
                true);
    }

    public static boolean isSwapped(UUID a, UUID b) {
        return b.equals(SWAPS.get(a));
    }

    /** Exchange skins and names of two players. */
    public static void swap(ServerPlayer a, ServerPlayer b) {
        unswapIfNeeded(a);
        unswapIfNeeded(b);
        SWAPS.put(a.getUUID(), b.getUUID());
        SWAPS.put(b.getUUID(), a.getUUID());
        apply(a, Entry.player(b.getUUID(), b.getGameProfile().getName()));
        apply(b, Entry.player(a.getUUID(), a.getGameProfile().getName()));
        a.displayClientMessage(Component.literal("You swapped lives with " + b.getGameProfile().getName() + "!")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        b.displayClientMessage(Component.literal(a.getGameProfile().getName() + " stole your life!")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Gives both players of a swap their own life back. */
    public static void unswap(ServerPlayer a, ServerPlayer b) {
        SWAPS.remove(a.getUUID());
        SWAPS.remove(b.getUUID());
        restore(a, true);
        restore(b, true);
    }

    private static void unswapIfNeeded(ServerPlayer player) {
        UUID other = SWAPS.remove(player.getUUID());
        if (other != null) {
            SWAPS.remove(other);
            ServerPlayer partner = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(other);
            if (partner != null) {
                restore(partner, true);
            } else if (SERVER.remove(other) != null) {
                Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncDisguisePacket(other, null));
            }
        }
    }

    private static void effects(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(), 50, 0.4D, 0.8D, 0.4D, 0.3D);
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.5D, 0.9D, 0.5D, 0.4D);
            level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static void apply(ServerPlayer thief, Entry entry) {
        effects(thief);
        SERVER.put(thief.getUUID(), entry);
        thief.refreshDisplayName();
        Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncDisguisePacket(thief.getUUID(), entry));
    }

    /** Gives the player their own skin, nickname and body back. */
    public static void restore(ServerPlayer thief, boolean tellPlayer) {
        UUID partnerId = SWAPS.remove(thief.getUUID());
        if (partnerId != null) {
            SWAPS.remove(partnerId);
            ServerPlayer partner = thief.getServer() == null ? null : thief.getServer().getPlayerList().getPlayer(partnerId);
            if (partner != null) {
                restore(partner, true);
            } else if (SERVER.remove(partnerId) != null) {
                Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncDisguisePacket(partnerId, null));
            }
        }
        if (SERVER.remove(thief.getUUID()) == null) {
            return;
        }
        thief.refreshDisplayName();
        effects(thief);
        Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncDisguisePacket(thief.getUUID(), null));
        if (tellPlayer) {
            thief.displayClientMessage(Component.literal("Your soul is back.").withStyle(ChatFormatting.GRAY), true);
        }
    }
}
