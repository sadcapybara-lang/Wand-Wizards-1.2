package com.soulstealer;

import com.soulstealer.entity.FakePlayerEntity;
import com.soulstealer.entity.MaxWizardEntity;
import com.soulstealer.entity.RebelWizardEntity;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod bus events that are needed on both client and server. */
@Mod.EventBusSubscriber(modid = SoulStealer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {

    private ModEvents() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SoulStealer.FAKE_PLAYER.get(), FakePlayerEntity.createAttributes().build());
        event.put(SoulStealer.REBEL_WIZARD.get(), RebelWizardEntity.createAttributes().build());
        event.put(SoulStealer.MAX_WIZARD.get(), MaxWizardEntity.createAttributes().build());
    }
}
