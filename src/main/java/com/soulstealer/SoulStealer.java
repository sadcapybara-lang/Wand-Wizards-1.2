package com.soulstealer;

import com.soulstealer.entity.FakePlayerEntity;
import com.soulstealer.entity.MaxWizardEntity;
import com.soulstealer.entity.RebelWizardEntity;
import com.soulstealer.item.WizardArmorMaterial;
import com.soulstealer.item.WizardHatItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(SoulStealer.MODID)
public class SoulStealer {
    public static final String MODID = "soulstealer";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> BOSS_THEME = SOUNDS.register("boss_theme",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "boss_theme")));
    public static final RegistryObject<SoundEvent> BOSS_RAGE_THEME = SOUNDS.register("boss_rage_theme",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "boss_rage_theme")));

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> SOUL_STEALER_WAND = ITEMS.register("soul_stealer_wand",
            () -> new SoulStealerWandItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> SOUL_SPAWNER = ITEMS.register("soul_spawner",
            () -> new SoulSpawnerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> ELEMENTAL_WAND = ITEMS.register("elemental_wand",
            () -> new ElementalWandItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> LIFE_STEALER_WAND = ITEMS.register("life_stealer_wand",
            () -> new LifeStealerWandItem(new Item.Properties().durability(5).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> NAME_CHANGER = ITEMS.register("name_changer",
            () -> new NameChangerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> WIZARD_HAT = ITEMS.register("wizard_hat",
            () -> new WizardHatItem(WizardArmorMaterial.WIZARD, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ELEMENTAL_WIZARD_HAT = ITEMS.register("elemental_wizard_hat",
            () -> new WizardHatItem(WizardArmorMaterial.ELEMENTAL_WIZARD, new Item.Properties().rarity(Rarity.RARE)));


    // ---- spell wands
    public static final java.util.List<RegistryObject<Item>> SPELL_WANDS = new java.util.ArrayList<>();
    private static RegistryObject<Item> spell(com.soulstealer.item.SpellWandItem.Spell spell, Rarity rarity) {
        RegistryObject<Item> o = ITEMS.register(spell.id, () -> new com.soulstealer.item.SpellWandItem(new Item.Properties().stacksTo(1).rarity(rarity), spell));
        SPELL_WANDS.add(o);
        return o;
    }
    public static final RegistryObject<Item> EMBER_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.EMBER, Rarity.UNCOMMON);
    public static final RegistryObject<Item> FROST_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.FROST, Rarity.UNCOMMON);
    public static final RegistryObject<Item> ARCANE_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.ARCANE, Rarity.RARE);
    public static final RegistryObject<Item> WITHER_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.WITHER, Rarity.RARE);
    public static final RegistryObject<Item> VAMPIRIC_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.VAMPIRIC, Rarity.RARE);
    public static final RegistryObject<Item> STORM_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.STORM, Rarity.RARE);
    public static final RegistryObject<Item> METEOR_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.METEOR, Rarity.EPIC);
    public static final RegistryObject<Item> FANG_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.FANG, Rarity.UNCOMMON);
    public static final RegistryObject<Item> LIFEBLOOM_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.LIFEBLOOM, Rarity.RARE);
    public static final RegistryObject<Item> GRAVITON_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.GRAVITON, Rarity.RARE);
    public static final RegistryObject<Item> BLINK_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.BLINK, Rarity.UNCOMMON);
    public static final RegistryObject<Item> GALE_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.GALE, Rarity.UNCOMMON);
    public static final RegistryObject<Item> CHRONO_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.CHRONO, Rarity.EPIC);
    public static final RegistryObject<Item> AEGIS_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.AEGIS, Rarity.EPIC);
    public static final RegistryObject<Item> VERDANT_WAND = spell(com.soulstealer.item.SpellWandItem.Spell.VERDANT, Rarity.UNCOMMON);

    public static final RegistryObject<EntityType<RebelWizardEntity>> REBEL_WIZARD = ENTITIES.register("rebel_wizard",
            () -> EntityType.Builder.<RebelWizardEntity>of(RebelWizardEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10)
                    .build(new ResourceLocation(MODID, "rebel_wizard").toString()));

    public static final RegistryObject<EntityType<MaxWizardEntity>> MAX_WIZARD = ENTITIES.register("max_wizard",
            () -> EntityType.Builder.<MaxWizardEntity>of(MaxWizardEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.1F).fireImmune().clientTrackingRange(10)
                    .build(new ResourceLocation(MODID, "max_wizard").toString()));

    public static final RegistryObject<Item> REBEL_WIZARD_SPAWN_EGG = ITEMS.register("rebel_wizard_spawn_egg",
            () -> new ForgeSpawnEggItem(REBEL_WIZARD, 0x5A1A1A, 0x2A2A2A, new Item.Properties()));
    public static final RegistryObject<Item> MAX_WIZARD_SPAWN_EGG = ITEMS.register("max_wizard_spawn_egg",
            () -> new ForgeSpawnEggItem(MAX_WIZARD, 0x111111, 0xC00000, new Item.Properties()));

    public static final RegistryObject<EntityType<FakePlayerEntity>> FAKE_PLAYER = ENTITIES.register("fake_player",
            () -> EntityType.Builder.<FakePlayerEntity>of(FakePlayerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(MODID, "fake_player").toString()));

    /** The own creative tab ("category") of the mod. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("soulstealer",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.soulstealer"))
                    .icon(() -> new ItemStack(SOUL_STEALER_WAND.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SOUL_STEALER_WAND.get());
                        output.accept(ELEMENTAL_WAND.get());
                        output.accept(LIFE_STEALER_WAND.get());
                        for (RegistryObject<Item> w : SPELL_WANDS) {
                            output.accept(w.get());
                        }
                        output.accept(NAME_CHANGER.get());
                        output.accept(SOUL_SPAWNER.get());
                        output.accept(WIZARD_HAT.get());
                        output.accept(ELEMENTAL_WIZARD_HAT.get());
                        output.accept(REBEL_WIZARD_SPAWN_EGG.get());
                        output.accept(MAX_WIZARD_SPAWN_EGG.get());
                    })
                    .build());

    public SoulStealer() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
    }
}
