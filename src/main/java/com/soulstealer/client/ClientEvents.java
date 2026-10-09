package com.soulstealer.client;

import java.util.Map;
import java.util.UUID;

import com.soulstealer.Disguises;
import com.soulstealer.SoulStealer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulStealer.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    /** Keeps the stolen skins and the morph animations up to date. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Disguises.CLIENT.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.level == null) {
            return;
        }
        // first remember the real skins of everybody involved, then swap
        for (Map.Entry<UUID, Disguises.Entry> e : Disguises.CLIENT.entrySet()) {
            if (e.getValue().kind() == Disguises.Kind.PLAYER) {
                PlayerInfo a = connection.getPlayerInfo(e.getKey());
                PlayerInfo b = connection.getPlayerInfo(e.getValue().victimId());
                if (a != null) {
                    SkinSwapper.snapshot(e.getKey(), a);
                }
                if (b != null) {
                    SkinSwapper.snapshot(e.getValue().victimId(), b);
                }
            }
        }
        for (Map.Entry<UUID, Disguises.Entry> e : Disguises.CLIENT.entrySet()) {
            Disguises.Entry entry = e.getValue();
            if (entry.kind() == Disguises.Kind.PLAYER) {
                applySkin(connection, e.getKey(), entry);
            } else if (entry.kind() == Disguises.Kind.MOB) {
                Player player = mc.level.getPlayerByUUID(e.getKey());
                if (player != null) {
                    MorphRenderer.tick(player, entry);
                }
            }
        }
    }

    /** Draws morphed players as their mob and applies stolen skins right before drawing. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (Disguises.CLIENT.isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        Disguises.Entry entry = Disguises.CLIENT.get(player.getUUID());
        if (entry == null) {
            return;
        }

        if (entry.kind() == Disguises.Kind.MOB) {
            boolean drawn = MorphRenderer.render(player, entry, event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
            if (drawn) {
                event.setCanceled(true);
            }
        } else if (entry.kind() == Disguises.Kind.BLOCK) {
            if (MorphRenderer.renderBlock(player, entry, event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight())) {
                event.setCanceled(true);
            }
        } else {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                applySkin(connection, player.getUUID(), entry);
            }
        }
    }

    /** Disguises only live inside one world, so forget them when leaving it. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Disguises.CLIENT.clear();
        Disguises.NICKS_CLIENT.clear();
        MorphRenderer.clear();
        SkinSwapper.clearAll();
    }

    private static void applySkin(ClientPacketListener connection, UUID thiefId, Disguises.Entry entry) {
        PlayerInfo thief = connection.getPlayerInfo(thiefId);
        if (thief != null) {
            SkinSwapper.apply(thiefId, thief, entry.victimId());
        }
    }
}
