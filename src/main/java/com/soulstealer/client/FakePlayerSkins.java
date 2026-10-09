package com.soulstealer.client;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import com.soulstealer.entity.FakePlayerEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

/** Turns the skin data of a fake player into a texture the renderer can use. */
@OnlyIn(Dist.CLIENT)
public final class FakePlayerSkins {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Skin(ResourceLocation texture, boolean slim) {
    }

    private static final Map<String, Skin> CACHE = new HashMap<>();

    private FakePlayerSkins() {
    }

    public static Skin get(FakePlayerEntity entity) {
        String value = entity.getSkinTexture();
        if (value.isEmpty()) {
            return fallback(entity);
        }
        Skin skin = CACHE.get(value);
        if (skin == null) {
            skin = load(entity, value);
            CACHE.put(value, skin);
        }
        return skin;
    }

    private static Skin fallback(FakePlayerEntity entity) {
        UUID id = entity.getUUID();
        return new Skin(DefaultPlayerSkin.getDefaultSkin(id), "slim".equals(DefaultPlayerSkin.getSkinModelName(id)));
    }

    private static Skin load(FakePlayerEntity entity, String value) {
        try {
            UUID id = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
            GameProfile profile = new GameProfile(id, "SoulStealerFake");
            profile.getProperties().put("textures", new Property("textures", value));

            SkinManager skinManager = Minecraft.getInstance().getSkinManager();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = skinManager.getInsecureSkinInformation(profile);
            MinecraftProfileTexture texture = textures.get(MinecraftProfileTexture.Type.SKIN);
            if (texture == null) {
                return fallback(entity);
            }
            ResourceLocation location = skinManager.registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
            return new Skin(location, "slim".equals(texture.getMetadata("model")));
        } catch (Throwable t) {
            LOGGER.warn("[Soul Stealer] Could not load a fake player skin, using a default one.", t);
            return fallback(entity);
        }
    }
}
