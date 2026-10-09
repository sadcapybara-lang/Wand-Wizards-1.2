package com.soulstealer.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

/**
 * Copies the skin (and cape) textures of one player's {@link PlayerInfo} into another one.
 *
 * Forge has no event for changing skins, so the three private fields of PlayerInfo are found by their
 * type (the only Map = textures, the only String = skin model, the only boolean = "textures requested").
 * Looking them up by type instead of by name keeps this working both in the dev environment and in a
 * normal game where all names are obfuscated.
 */
@OnlyIn(Dist.CLIENT)
public final class SkinSwapper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Field texturesField;
    private static Field modelField;
    private static Field pendingField;

    static {
        try {
            Field textures = null;
            Field model = null;
            Field pending = null;
            int maps = 0;
            int strings = 0;
            int bools = 0;

            for (Field field : PlayerInfo.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> type = field.getType();
                if (Map.class.isAssignableFrom(type)) {
                    textures = field;
                    maps++;
                } else if (type == String.class) {
                    model = field;
                    strings++;
                } else if (type == boolean.class) {
                    pending = field;
                    bools++;
                }
            }

            if (maps == 1) {
                textures.setAccessible(true);
                texturesField = textures;
            }
            if (strings == 1) {
                model.setAccessible(true);
                modelField = model;
            }
            if (bools == 1) {
                pending.setAccessible(true);
                pendingField = pending;
            }

            if (texturesField == null) {
                LOGGER.warn("[Soul Stealer] Could not find the skin textures field, skin swapping is disabled (nicknames still work).");
            }
        } catch (Throwable t) {
            texturesField = null;
            LOGGER.error("[Soul Stealer] Could not set up skin swapping, skin swapping is disabled.", t);
        }
    }

    private SkinSwapper() {
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> textures(PlayerInfo info) throws IllegalAccessException {
        return (Map<Object, Object>) texturesField.get(info);
    }

    private record Snap(Map<Object, Object> textures, Object model) {
    }

    /** The untouched skins of players, so a two-way swap never copies an already swapped skin. */
    private static final Map<UUID, Snap> SNAPS = new HashMap<>();
    private static final Set<UUID> MODIFIED = new HashSet<>();

    /** Remembers the real skin of a player (only while it has not been changed by us). */
    public static void snapshot(UUID id, PlayerInfo info) {
        if (texturesField == null || MODIFIED.contains(id) || SNAPS.containsKey(id)) {
            return;
        }
        try {
            info.getSkinLocation(); // starts loading
            Map<Object, Object> map = textures(info);
            if (map == null || map.isEmpty()) {
                return;
            }
            SNAPS.put(id, new Snap(new HashMap<>(map), modelField == null ? null : modelField.get(info)));
        } catch (Throwable t) {
            LOGGER.error("[Wand Wizards] Could not remember a skin.", t);
        }
    }

    /** Makes {@code thief} look like the real skin of {@code victimId}. Cheap enough to call every tick. */
    public static void apply(UUID thiefId, PlayerInfo thief, UUID victimId) {
        if (texturesField == null) {
            return;
        }
        Snap from = SNAPS.get(victimId);
        if (from == null) {
            return; // victim's skin is not loaded yet, try again next tick
        }
        try {
            Map<Object, Object> to = textures(thief);
            if (to == null) {
                return;
            }
            // Stop the thief's own textures from being requested and overwriting ours.
            if (pendingField != null) {
                pendingField.setBoolean(thief, true);
            }
            MODIFIED.add(thiefId);
            if (!to.equals(from.textures())) {
                to.clear();
                to.putAll(from.textures());
            }
            if (modelField != null) {
                modelField.set(thief, from.model());
            }
        } catch (Throwable t) {
            texturesField = null;
            LOGGER.error("[Wand Wizards] Skin swapping failed and was disabled.", t);
        }
    }

    /** Gives the player their own skin back (it gets downloaded / loaded again). */
    public static void reset(UUID id, PlayerInfo info) {
        MODIFIED.remove(id);
        SNAPS.remove(id);
        if (texturesField == null || info == null) {
            return;
        }
        try {
            textures(info).clear();
            if (modelField != null) {
                modelField.set(info, null);
            }
            if (pendingField != null) {
                pendingField.setBoolean(info, false);
            }
        } catch (Throwable t) {
            LOGGER.error("[Wand Wizards] Could not restore the original skin.", t);
        }
    }

    public static void clearAll() {
        SNAPS.clear();
        MODIFIED.clear();
    }
}
