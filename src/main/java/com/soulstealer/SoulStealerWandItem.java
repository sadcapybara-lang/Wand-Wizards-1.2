package com.soulstealer;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class SoulStealerWandItem extends Item {

    public SoulStealerWandItem(Properties properties) {
        super(properties);
    }

    /**
     * Left click on an entity while holding the wand. Returning true cancels the normal hit,
     * so the wand does not deal damage or knockback - it is only for pranks.
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof Player) {
            if (!player.level().isClientSide()
                    && player instanceof ServerPlayer thief
                    && entity instanceof ServerPlayer victim) {
                Disguises.steal(thief, victim);
            }
            return true;
        }
        if (entity instanceof Mob mob && !(entity instanceof EnderDragon)) {
            if (!player.level().isClientSide() && player instanceof ServerPlayer thief) {
                Disguises.morph(thief, mob);
            }
            return true;
        }
        return false;
    }

    /** Shift + right click gives you your own skin, name and body back. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                Disguises.restore(serverPlayer, true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Hit a player to steal their skin and nickname").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Hit an animal / mob to morph into it").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift + right click to get yourself back").withStyle(ChatFormatting.DARK_GRAY));
    }
}
