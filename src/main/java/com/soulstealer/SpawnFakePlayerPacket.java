package com.soulstealer;

import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.soulstealer.entity.FakePlayerEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: "spawn a fake player with this name and skin where I am looking". */
public class SpawnFakePlayerPacket {
    private static final Pattern VALID_ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int MAX_FAKE_PLAYERS = 50;

    public final String name;
    public final String skin;
    public final boolean joinMessage;

    public SpawnFakePlayerPacket(String name, String skin, boolean joinMessage) {
        this.name = name;
        this.skin = skin;
        this.joinMessage = joinMessage;
    }

    public static void encode(SpawnFakePlayerPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name, 32);
        buf.writeUtf(packet.skin, 16);
        buf.writeBoolean(packet.joinMessage);
    }

    public static SpawnFakePlayerPacket decode(FriendlyByteBuf buf) {
        return new SpawnFakePlayerPacket(buf.readUtf(32), buf.readUtf(16), buf.readBoolean());
    }

    public static void handle(SpawnFakePlayerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                spawn(sender, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void spawn(ServerPlayer sender, SpawnFakePlayerPacket packet) {
        // only with the Soul Spawner in hand
        boolean holding = sender.getMainHandItem().is(SoulStealer.SOUL_SPAWNER.get())
                || sender.getOffhandItem().is(SoulStealer.SOUL_SPAWNER.get());
        if (!holding) {
            return;
        }

        String name = packet.name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return;
        }
        String skinName = packet.skin.trim().isEmpty() ? name : packet.skin.trim();

        ServerLevel level = sender.serverLevel();

        int existing = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity) {
                existing++;
            }
        }
        if (existing >= MAX_FAKE_PLAYERS) {
            sender.displayClientMessage(Component.literal("Too many fake players already (" + MAX_FAKE_PLAYERS + ").")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        FakePlayerEntity fake = SoulStealer.FAKE_PLAYER.get().create(level);
        if (fake == null) {
            return;
        }

        // where the sender is looking
        Vec3 position;
        HitResult hit = sender.pick(64.0D, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos above = blockHit.getBlockPos().relative(blockHit.getDirection());
            position = Vec3.atBottomCenterOf(above);
        } else {
            Vec3 look = sender.getLookAngle();
            position = sender.position().add(look.x * 2.0D, 0.0D, look.z * 2.0D);
        }

        fake.moveTo(position.x, position.y, position.z, sender.getYRot() + 180.0F, 0.0F);
        fake.setCustomName(Component.literal(name));
        fake.setCustomNameVisible(true);
        fake.setPersistenceRequired();
        level.addFreshEntity(fake);

        level.sendParticles(ParticleTypes.SOUL, position.x, position.y + 1.0D, position.z, 20, 0.3D, 0.6D, 0.3D, 0.02D);

        if (packet.joinMessage) {
            FakePlayerEntity.broadcastJoin(level.getServer(), name);
        }

        resolveSkin(sender, fake, skinName);
    }

    /** Looks up the skin of the account online (async) and gives it to the dummy when it is found. */
    private static void resolveSkin(ServerPlayer sender, FakePlayerEntity fake, String skinName) {
        if (!VALID_ACCOUNT_NAME.matcher(skinName).matches()) {
            sender.displayClientMessage(Component.literal("\"" + skinName + "\" is not a valid Minecraft username, using a default skin.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        SkullBlockEntity.updateGameprofile(new GameProfile((java.util.UUID) null, skinName), profile -> {
            String texture = null;
            if (profile != null) {
                for (Property property : profile.getProperties().get("textures")) {
                    texture = property.getValue();
                    break;
                }
            }
            if (texture == null) {
                sender.displayClientMessage(Component.literal("No skin found for \"" + skinName + "\", using a default skin.")
                        .withStyle(ChatFormatting.YELLOW), true);
            } else if (fake.isAlive()) {
                fake.setSkinTexture(texture);
            }
        });
    }
}
