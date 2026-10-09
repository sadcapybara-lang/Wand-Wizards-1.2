package com.soulstealer.client;

import com.soulstealer.Disguises;
import com.soulstealer.SyncDisguisePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Applies a {@link SyncDisguisePacket} on the client. */
@OnlyIn(Dist.CLIENT)
public final class ClientDisguiseHandler {

    private ClientDisguiseHandler() {
    }

    public static void handle(SyncDisguisePacket packet) {
        Minecraft mc = Minecraft.getInstance();

        PlayerInfo info = null;
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            info = connection.getPlayerInfo(packet.thief);
        }

        if (packet.entry != null) {
            Disguises.CLIENT.put(packet.thief, packet.entry);
            if (packet.entry.kind() != Disguises.Kind.PLAYER) {
                // no longer a stolen skin, e.g. in the tab list
                SkinSwapper.reset(packet.thief, info);
            }
        } else {
            Disguises.CLIENT.remove(packet.thief);
            SkinSwapper.reset(packet.thief, info);
        }
        MorphRenderer.invalidate(packet.thief);

        // Makes the nickname above the head update right away.
        if (mc.level != null) {
            Player player = mc.level.getPlayerByUUID(packet.thief);
            if (player != null) {
                player.refreshDisplayName();
            }
        }
    }
}
