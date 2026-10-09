package com.soulstealer;

import java.util.List;

import com.soulstealer.client.ClientHooks;
import com.soulstealer.entity.FakePlayerEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class SoulSpawnerItem extends Item {

    public SoulSpawnerItem(Properties properties) {
        super(properties);
    }

    /** Right click opens the menu where you choose the name and the skin of the fake player. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openSpawnerScreen());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Shift + right click on a fake player makes it "leave the game". */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof FakePlayerEntity fake && player.isShiftKeyDown()) {
            if (!player.level().isClientSide()) {
                fake.leaveGame();
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click: spawn a fake player").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift + right click on one: make it leave").withStyle(ChatFormatting.DARK_GRAY));
    }
}
