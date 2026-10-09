package com.soulstealer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Swaps the displayed nickname (above the head and in chat) of players who stole another player's soul.
 * This runs on both sides, each side looks into its own map.
 */
@Mod.EventBusSubscriber(modid = SoulStealer.MODID)
public final class NameEvents {

    private NameEvents() {
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        Disguises.Entry entry = Disguises.forSide(player.level().isClientSide()).get(player.getUUID());
        if (entry != null && entry.kind() == Disguises.Kind.PLAYER) {
            event.setDisplayname(Component.literal(entry.victimName()));
            return;
        }
        if (entry == null || entry.kind() == Disguises.Kind.BLOCK) {
            String nick = Disguises.nicks(player.level().isClientSide()).get(player.getUUID());
            if (nick != null) {
                event.setDisplayname(Component.literal(nick));
            }
        }
    }
}
