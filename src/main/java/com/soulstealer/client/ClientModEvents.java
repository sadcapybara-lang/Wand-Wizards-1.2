package com.soulstealer.client;

import com.soulstealer.SoulStealer;
import com.soulstealer.entity.MaxWizardEntity;
import com.soulstealer.entity.RebelWizardEntity;

import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod bus events that only exist on the client. */
@Mod.EventBusSubscriber(modid = SoulStealer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SoulStealer.FAKE_PLAYER.get(), FakePlayerRenderer::new);
        event.registerEntityRenderer(SoulStealer.REBEL_WIZARD.get(),
                ctx -> new WizardRenderer<RebelWizardEntity>(ctx, new ResourceLocation("soulstealer", "textures/entity/rebel_wizard.png"), 0.95F));
        event.registerEntityRenderer(SoulStealer.MAX_WIZARD.get(),
                ctx -> new WizardRenderer<MaxWizardEntity>(ctx, new ResourceLocation("soulstealer", "textures/entity/max_wizard.png"), 1.1F));
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WizardHatModel.LAYER, WizardHatModel::createLayer);
    }
}
