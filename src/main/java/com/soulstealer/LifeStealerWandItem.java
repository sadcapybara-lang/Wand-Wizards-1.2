package com.soulstealer;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Hit a player: you two swap skins and names. Hit them again to swap back. Only 5 uses. */
public class LifeStealerWandItem extends Item {

    public LifeStealerWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }
        if (!player.level().isClientSide() && player instanceof ServerPlayer thief && entity instanceof ServerPlayer victim) {
            if (Disguises.isSwapped(thief.getUUID(), victim.getUUID())) {
                Disguises.unswap(thief, victim);
            } else {
                Disguises.swap(thief, victim);
            }
            stack.hurtAndBreak(1, thief, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Hit a player: swap skins and names").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Hit them again to swap back").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Uses left: " + (stack.getMaxDamage() - stack.getDamageValue()))
                .withStyle(ChatFormatting.DARK_RED));
    }
}
