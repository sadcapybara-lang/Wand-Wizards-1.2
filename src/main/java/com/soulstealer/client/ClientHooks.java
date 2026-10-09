package com.soulstealer.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Keeps client-only classes away from the server (items are loaded on both sides). */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void openSpawnerScreen() {
        Minecraft.getInstance().setScreen(new SoulSpawnerScreen());
    }

    public static void openNameScreen() {
        Minecraft.getInstance().setScreen(new NameChangerScreen());
    }
}
