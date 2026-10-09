package com.soulstealer;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Packets of the Name Changer. */
public final class NamePackets {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_ ]{1,16}");
    private static final String SAVE_KEY = "soulstealer_nick";

    private NamePackets() {
    }

    /** Client -> server: "call me this" (empty = my real name again). */
    public static class Change {
        public final String name;

        public Change(String name) {
            this.name = name;
        }

        public static void encode(Change p, FriendlyByteBuf buf) {
            buf.writeUtf(p.name, 16);
        }

        public static Change decode(FriendlyByteBuf buf) {
            return new Change(buf.readUtf(16));
        }

        public static void handle(Change p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player == null || !(player.getMainHandItem().getItem() instanceof NameChangerItem
                        || player.getOffhandItem().getItem() instanceof NameChangerItem)) {
                    return;
                }
                String name = p.name.trim();
                if (!name.isEmpty() && !VALID.matcher(name).matches()) {
                    player.displayClientMessage(Component.literal("Only letters, numbers, _ and spaces (max 16).")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                setNick(player, name.isEmpty() ? null : name);
                player.displayClientMessage(Component.literal(name.isEmpty()
                        ? "Your real name is back." : "You are now called " + name + ".")
                        .withStyle(ChatFormatting.GREEN), true);
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: the nickname of a player (empty = none). */
    public static class Sync {
        public final UUID player;
        public final String name;

        public Sync(UUID player, String name) {
            this.player = player;
            this.name = name;
        }

        public static void encode(Sync p, FriendlyByteBuf buf) {
            buf.writeUUID(p.player);
            buf.writeUtf(p.name, 16);
        }

        public static Sync decode(FriendlyByteBuf buf) {
            return new Sync(buf.readUUID(), buf.readUtf(16));
        }

        public static void handle(Sync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (p.name.isEmpty()) {
                    Disguises.NICKS_CLIENT.remove(p.player);
                } else {
                    Disguises.NICKS_CLIENT.put(p.player, p.name);
                }
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level != null) {
                    Player pl = mc.level.getPlayerByUUID(p.player);
                    if (pl != null) {
                        pl.refreshDisplayName();
                    }
                }
            }));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Sets (or clears) the nickname, saves it with the player and tells everybody. */
    public static void setNick(ServerPlayer player, String nick) {
        CompoundTag persisted = persisted(player);
        if (nick == null) {
            Disguises.NICKS_SERVER.remove(player.getUUID());
            persisted.remove(SAVE_KEY);
        } else {
            Disguises.NICKS_SERVER.put(player.getUUID(), nick);
            persisted.putString(SAVE_KEY, nick);
        }
        player.refreshDisplayName();
        Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new Sync(player.getUUID(), nick == null ? "" : nick));
    }

    /** Loads the saved nickname when the player joins. */
    public static void loadNick(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (persisted.contains(SAVE_KEY)) {
            Disguises.NICKS_SERVER.put(player.getUUID(), persisted.getString(SAVE_KEY));
            player.refreshDisplayName();
            Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new Sync(player.getUUID(), persisted.getString(SAVE_KEY)));
        }
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains("PlayerPersisted", 10)) {
            data.put("PlayerPersisted", new CompoundTag());
        }
        return data.getCompound("PlayerPersisted");
    }
}
