package com.soulstealer.item;

import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Armor materials of the wizard hats. The name decides the worn texture: assets/soulstealer/textures/models/armor/<name>_layer_1.png */
public enum WizardArmorMaterial implements ArmorMaterial {
    WIZARD("soulstealer:wizard", 15, 2, 25, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F,
            () -> Ingredient.of(Items.AMETHYST_SHARD)),
    ELEMENTAL_WIZARD("soulstealer:elemental_wizard", 25, 3, 28, SoundEvents.ARMOR_EQUIP_DIAMOND, 0.5F, 0.0F,
            () -> Ingredient.of(Items.PRISMARINE_CRYSTALS));

    private final String name;
    private final int durabilityMultiplier;
    private final int helmetProtection;
    private final int enchantability;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    WizardArmorMaterial(String name, int durabilityMultiplier, int helmetProtection, int enchantability,
                        SoundEvent equipSound, float toughness, float knockbackResistance,
                        Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.helmetProtection = helmetProtection;
        this.enchantability = enchantability;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        int base;
        switch (type) {
            case HELMET -> base = 11;
            case CHESTPLATE -> base = 16;
            case LEGGINGS -> base = 15;
            default -> base = 13;
        }
        return base * this.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return type == ArmorItem.Type.HELMET ? this.helmetProtection : 0;
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this.repairIngredient.get();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public float getToughness() {
        return this.toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }
}
