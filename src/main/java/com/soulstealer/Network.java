package com.soulstealer;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SoulStealer.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private Network() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, SyncDisguisePacket.class,
                SyncDisguisePacket::encode,
                SyncDisguisePacket::decode,
                SyncDisguisePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(1, SpawnFakePlayerPacket.class,
                SpawnFakePlayerPacket::encode,
                SpawnFakePlayerPacket::decode,
                SpawnFakePlayerPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(2, NamePackets.Change.class,
                NamePackets.Change::encode, NamePackets.Change::decode, NamePackets.Change::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(3, NamePackets.Sync.class,
                NamePackets.Sync::encode, NamePackets.Sync::decode, NamePackets.Sync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
