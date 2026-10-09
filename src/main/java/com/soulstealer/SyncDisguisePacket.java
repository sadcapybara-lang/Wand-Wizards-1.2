package com.soulstealer;

import java.util.UUID;
import java.util.function.Supplier;

import com.soulstealer.client.ClientDisguiseHandler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: "this player is now disguised as that" (entry == null means the disguise is gone). */
public class SyncDisguisePacket {
    public final UUID thief;
    public final Disguises.Entry entry;

    public SyncDisguisePacket(UUID thief, Disguises.Entry entry) {
        this.thief = thief;
        this.entry = entry;
    }

    public static void encode(SyncDisguisePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.thief);
        buf.writeBoolean(packet.entry != null);
        if (packet.entry == null) {
            return;
        }
        Disguises.Entry entry = packet.entry;
        buf.writeByte(entry.kind().ordinal());
        if (entry.kind() == Disguises.Kind.BLOCK) {
            buf.writeVarInt(entry.stateId());
        } else if (entry.kind() == Disguises.Kind.MOB) {
            buf.writeResourceLocation(entry.mobType());
            buf.writeNbt(entry.mobData());
        } else {
            buf.writeUUID(entry.victimId());
            buf.writeUtf(entry.victimName(), 64);
        }
    }

    public static SyncDisguisePacket decode(FriendlyByteBuf buf) {
        UUID thief = buf.readUUID();
        if (!buf.readBoolean()) {
            return new SyncDisguisePacket(thief, null);
        }
        int kind = buf.readByte();
        if (kind == Disguises.Kind.BLOCK.ordinal()) {
            return new SyncDisguisePacket(thief, Disguises.Entry.block(buf.readVarInt()));
        }
        if (kind == Disguises.Kind.MOB.ordinal()) {
            ResourceLocation type = buf.readResourceLocation();
            CompoundTag data = buf.readNbt();
            return new SyncDisguisePacket(thief, Disguises.Entry.mob(type, data));
        }
        UUID victim = buf.readUUID();
        String name = buf.readUtf(64);
        return new SyncDisguisePacket(thief, Disguises.Entry.player(victim, name));
    }

    public static void handle(SyncDisguisePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDisguiseHandler.handle(packet)));
        ctx.get().setPacketHandled(true);
    }
}
