package com.soulstealer;

import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = SoulStealer.MODID)
public final class ServerEvents {

    private ServerEvents() {
    }

    /** Players who join late get the current disguises, and a disguised player who rejoins is announced again. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined)) {
            return;
        }

        for (Map.Entry<UUID, Disguises.Entry> e : Disguises.SERVER.entrySet()) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> joined),
                    new SyncDisguisePacket(e.getKey(), e.getValue()));
        }

        for (Map.Entry<UUID, String> e : Disguises.NICKS_SERVER.entrySet()) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> joined),
                    new NamePackets.Sync(e.getKey(), e.getValue()));
        }
        NamePackets.loadNick(joined);

        Disguises.Entry mine = Disguises.SERVER.get(joined.getUUID());
        if (mine != null) {
            joined.refreshDisplayName();
            Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncDisguisePacket(joined.getUUID(), mine));
        }
    }

    /** Logging out ends a swap, so nobody stays stuck in somebody else's skin. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Disguises.restore(player, false);
            Disguises.NICKS_SERVER.remove(player.getUUID());
        }
    }

    /** Dying ends the disguise. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Disguises.restore(player, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Disguises.SERVER.clear();
        Disguises.SWAPS.clear();
        Disguises.NICKS_SERVER.clear();
    }
}
