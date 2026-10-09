package com.soulstealer.item;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.soulstealer.client.WizardHatModel;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** A wizard hat (helmet) that gives a small perk while worn. */
public class WizardHatItem extends ArmorItem {

    public WizardHatItem(ArmorMaterial material, Item.Properties properties) {
        super(material, ArmorItem.Type.HELMET, properties);
    }

    @Override
    public void onArmorTick(ItemStack stack, Level level, Player player) {
        if (level instanceof ServerLevel server && player.tickCount % 12 == 0) {
            boolean elemental = this.getMaterial() == WizardArmorMaterial.ELEMENTAL_WIZARD;
            server.sendParticles(elemental ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 2.2D, player.getZ(), 2, 0.25D, 0.1D, 0.25D, 0.02D);
        }
        if (level.isClientSide() || player.tickCount % 40 != 0) {
            return;
        }
        if (this.getMaterial() == WizardArmorMaterial.ELEMENTAL_WIZARD) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 120, 0, true, false, true));
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, 0, true, false, true));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (this.getMaterial() == WizardArmorMaterial.ELEMENTAL_WIZARD) {
            tooltip.add(Component.literal("Worn: Fire Resistance and Water Breathing").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Worn: Haste").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("A hat fit for a wizard.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private WizardHatModel model;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (this.model == null) {
                    this.model = new WizardHatModel(Minecraft.getInstance().getEntityModels().bakeLayer(WizardHatModel.LAYER));
                }
                return this.model;
            }
        });
    }
}
