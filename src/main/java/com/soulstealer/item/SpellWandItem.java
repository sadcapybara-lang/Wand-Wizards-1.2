package com.soulstealer.item;

import java.util.List;

import org.joml.Vector3f;

import com.soulstealer.entity.MagicBolt;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One class for all the spell wands, every {@link Spell} is a different wand with its own power. */
public class SpellWandItem extends Item {

    public enum Spell {
        EMBER("ember_wand", "Ember Wand", "Shoots a burning beam that sets enemies on fire", 20, ChatFormatting.GOLD),
        FROST("frost_wand", "Frost Wand", "Shoots a freezing beam that slows and chills", 25, ChatFormatting.AQUA),
        ARCANE("arcane_wand", "Arcane Wand", "A piercing arcane beam that goes through many enemies", 30, ChatFormatting.LIGHT_PURPLE),
        WITHER("wither_wand", "Wither Wand", "A dark beam that rots enemies with the wither", 35, ChatFormatting.DARK_GRAY),
        VAMPIRIC("vampiric_wand", "Vampiric Wand", "A crimson beam that drains life and heals you", 30, ChatFormatting.DARK_RED),
        STORM("storm_wand", "Storm Wand", "Calls down lightning where you aim", 80, ChatFormatting.YELLOW),
        METEOR("meteor_wand", "Meteor Wand", "A meteor crashes down where you aim (does not break blocks)", 120, ChatFormatting.RED),
        FANG("fang_wand", "Fang Wand", "Summons a line of snapping fangs from the ground", 45, ChatFormatting.GREEN),
        LIFEBLOOM("lifebloom_wand", "Lifebloom Wand", "A healing beam: heals who you hit, or yourself", 40, ChatFormatting.GREEN),
        GRAVITON("graviton_wand", "Graviton Wand", "Lifts the target you hit up into the air", 70, ChatFormatting.DARK_AQUA),
        BLINK("blink_wand", "Blink Wand", "Teleports you forward", 40, ChatFormatting.DARK_PURPLE),
        GALE("gale_wand", "Gale Wand", "Launches you through the air and blasts enemies away", 60, ChatFormatting.WHITE),
        CHRONO("chrono_wand", "Chrono Wand", "Freezes time around you: nearby enemies are slowed and weakened", 240, ChatFormatting.BLUE),
        AEGIS("aegis_wand", "Aegis Wand", "Wraps you in a magical shield", 600, ChatFormatting.GOLD),
        VERDANT("verdant_wand", "Verdant Wand", "Right click the ground to make plants grow around you", 30, ChatFormatting.DARK_GREEN);

        public final String id;
        public final String displayName;
        public final String description;
        public final int cooldown;
        public final ChatFormatting color;

        Spell(String id, String displayName, String description, int cooldown, ChatFormatting color) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.cooldown = cooldown;
            this.color = color;
        }
    }

    private final Spell spell;

    public SpellWandItem(Properties properties, Spell spell) {
        super(properties);
        this.spell = spell;
    }

    public Spell spell() {
        return this.spell;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(this.spell.description).withStyle(this.spell.color));
        tooltip.add(Component.literal("Cooldown: " + (this.spell.cooldown / 20.0D) + "s").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------ casting

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            if (this.cast(server, player)) {
                player.getCooldowns().addCooldown(this, this.spell.cooldown);
                player.swing(hand, true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (this.spell != Spell.VERDANT) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level instanceof ServerLevel server && player != null) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResult.FAIL;
            }
            int grown = 0;
            BlockPos center = context.getClickedPos();
            for (int dx = -2; dx <= 2 && grown < 14; dx++) {
                for (int dz = -2; dz <= 2 && grown < 14; dz++) {
                    for (int dy = -1; dy <= 1 && grown < 14; dy++) {
                        BlockPos p = center.offset(dx, dy, dz);
                        if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), server, p)) {
                            BoneMealItem.addGrowthParticles(server, p, 8);
                            grown++;
                        }
                    }
                }
            }
            server.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getCooldowns().addCooldown(this, this.spell.cooldown);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void sound(ServerLevel server, Player p, SoundEvent ev, float vol, float pitch) {
        server.playSound(null, p.getX(), p.getY(), p.getZ(), ev, SoundSource.PLAYERS, vol, pitch);
    }

    private static Vec3 muzzle(Player p) {
        return p.getEyePosition().add(p.getLookAngle().scale(1.0D)).subtract(0.0D, 0.2D, 0.0D);
    }

    private static final java.util.function.Predicate<LivingEntity> ANY = e -> true;

    private boolean cast(ServerLevel server, Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 start = muzzle(player);
        switch (this.spell) {
            case EMBER -> {
                sound(server, player, SoundEvents.BLAZE_SHOOT, 1.0F, 1.0F);
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 26.0D, ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME, false, 1, ANY);
                for (LivingEntity e : r.hits()) {
                    e.hurt(server.damageSources().indirectMagic(player, player), 6.0F);
                    e.setSecondsOnFire(6);
                }
                return true;
            }
            case FROST -> {
                sound(server, player, SoundEvents.GLASS_BREAK, 1.0F, 1.6F);
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 26.0D, ParticleTypes.SNOWFLAKE, ParticleTypes.ITEM_SNOWBALL, false, 1, ANY);
                for (LivingEntity e : r.hits()) {
                    e.hurt(server.damageSources().indirectMagic(player, player), 5.0F);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2));
                    e.setTicksFrozen(220);
                }
                return true;
            }
            case ARCANE -> {
                sound(server, player, SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.2F);
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 30.0D, ParticleTypes.WITCH, ParticleTypes.END_ROD, true, 6, ANY);
                for (LivingEntity e : r.hits()) {
                    e.hurt(server.damageSources().indirectMagic(player, player), 8.0F);
                }
                return true;
            }
            case WITHER -> {
                sound(server, player, SoundEvents.WITHER_SHOOT, 0.7F, 1.4F);
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 26.0D, ParticleTypes.SMOKE, ParticleTypes.SOUL, false, 1, ANY);
                for (LivingEntity e : r.hits()) {
                    e.hurt(server.damageSources().indirectMagic(player, player), 4.0F);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
                }
                return true;
            }
            case VAMPIRIC -> {
                sound(server, player, SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.8F);
                ParticleOptions blood = new DustParticleOptions(new Vector3f(0.8F, 0.0F, 0.1F), 1.2F);
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 24.0D, blood, ParticleTypes.DAMAGE_INDICATOR, false, 1, ANY);
                for (LivingEntity e : r.hits()) {
                    e.hurt(server.damageSources().indirectMagic(player, player), 6.0F);
                    player.heal(6.0F);
                    server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8D, player.getZ(), 5, 0.4D, 0.3D, 0.4D, 0.0D);
                }
                return true;
            }
            case STORM -> {
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 40.0D, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, false, 1, ANY);
                Vec3 end = r.hits().isEmpty() ? r.end() : r.hits().get(0).position();
                if (end.distanceTo(player.position()) < 4.0D) {
                    ((Player) player).displayClientMessage(Component.literal("Too close!").withStyle(ChatFormatting.RED), true);
                    return false;
                }
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                if (bolt != null) {
                    bolt.moveTo(end);
                    if (player instanceof ServerPlayer sp) {
                        bolt.setCause(sp);
                    }
                    server.addFreshEntity(bolt);
                }
                return true;
            }
            case METEOR -> {
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 45.0D, ParticleTypes.FLAME, ParticleTypes.SMOKE, false, 1, ANY);
                Vec3 end = r.hits().isEmpty() ? r.end() : r.hits().get(0).position();
                if (end.distanceTo(player.position()) < 6.5D) {
                    player.displayClientMessage(Component.literal("Too close!").withStyle(ChatFormatting.RED), true);
                    return false;
                }
                for (int i = 0; i < 22; i++) {
                    server.sendParticles(ParticleTypes.FLAME, end.x, end.y + i, end.z, 3, 0.2D, 0.2D, 0.2D, 0.02D);
                    server.sendParticles(ParticleTypes.LAVA, end.x, end.y + i, end.z, 1, 0.2D, 0.2D, 0.2D, 0.0D);
                }
                sound(server, player, SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
                server.explode(player, end.x, end.y, end.z, 3.0F, Level.ExplosionInteraction.NONE);
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y + 0.5D, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                server.sendParticles(ParticleTypes.FLAME, end.x, end.y + 0.5D, end.z, 60, 1.5D, 0.6D, 1.5D, 0.1D);
                AABB box = new AABB(end.x - 4, end.y - 2, end.z - 4, end.x + 4, end.y + 3, end.z + 4);
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
                    e.setSecondsOnFire(5);
                }
                return true;
            }
            case FANG -> {
                sound(server, player, SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
                float yaw = (float) Mth.atan2(look.z, look.x);
                double minY = player.getY() - 1.0D;
                double maxY = player.getY() + 1.0D;
                for (int i = 0; i < 12; i++) {
                    double dist = 1.5D + 1.25D * i;
                    fang(server, player, player.getX() + Mth.cos(yaw) * dist, player.getZ() + Mth.sin(yaw) * dist, minY, maxY, yaw, i * 2);
                }
                return true;
            }
            case LIFEBLOOM -> {
                sound(server, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.4F);
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 20.0D, ParticleTypes.HAPPY_VILLAGER, ParticleTypes.HEART, false, 1, e -> !(e instanceof Enemy));
                if (r.hits().isEmpty()) {
                    player.heal(4.0F);
                    server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8D, player.getZ(), 6, 0.4D, 0.3D, 0.4D, 0.0D);
                } else {
                    for (LivingEntity e : r.hits()) {
                        e.heal(8.0F);
                        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                        server.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + 1.5D, e.getZ(), 8, 0.4D, 0.4D, 0.4D, 0.0D);
                    }
                }
                return true;
            }
            case GRAVITON -> {
                sound(server, player, SoundEvents.SHULKER_SHOOT, 1.0F, 0.8F);
                MagicBolt.Result r = MagicBolt.beam(player, start, look, 28.0D, ParticleTypes.REVERSE_PORTAL, ParticleTypes.END_ROD, false, 1, ANY);
                for (LivingEntity e : r.hits()) {
                    e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 1));
                    e.setDeltaMovement(e.getDeltaMovement().add(0.0D, 0.7D, 0.0D));
                    e.hurtMarked = true;
                }
                return true;
            }
            case BLINK -> {
                return blink(server, player, look);
            }
            case GALE -> {
                sound(server, player, SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 1.2F);
                server.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2D, player.getZ(), 40, 0.6D, 0.2D, 0.6D, 0.1D);
                AABB box = player.getBoundingBox().inflate(4.0D);
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
                    Vec3 away = e.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D).normalize().scale(1.2D);
                    e.setDeltaMovement(e.getDeltaMovement().add(away.x, 0.5D, away.z));
                    e.hurtMarked = true;
                }
                player.setDeltaMovement(look.scale(1.7D).add(0.0D, 0.5D, 0.0D));
                player.hurtMarked = true;
                player.fallDistance = 0.0F;
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
                return true;
            }
            case CHRONO -> {
                sound(server, player, SoundEvents.BELL_RESONATE, 2.0F, 0.6F);
                AABB box = player.getBoundingBox().inflate(12.0D);
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof Player))) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 4));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140, 0));
                }
                for (int i = 0; i < 48; i++) {
                    double a = i * (Math.PI * 2.0D / 48.0D);
                    server.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(a) * 12.0D, player.getY() + 0.3D,
                            player.getZ() + Math.sin(a) * 12.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    server.sendParticles(ParticleTypes.ENCHANT, player.getX() + Math.cos(a) * 6.0D, player.getY() + 1.0D,
                            player.getZ() + Math.sin(a) * 6.0D, 2, 0.1D, 0.5D, 0.1D, 0.2D);
                }
                return true;
            }
            case AEGIS -> {
                sound(server, player, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 2));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
                server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 40, 0.5D, 0.9D, 0.5D, 0.3D);
                return true;
            }
            case VERDANT -> {
                sound(server, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0D, player.getZ(), 25, 0.5D, 0.8D, 0.5D, 0.0D);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean blink(ServerLevel server, Player player, Vec3 look) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(look.scale(16.0D));
        BlockHitResult hit = server.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double dist = hit.getType() == HitResult.Type.MISS ? 16.0D : Math.max(0.0D, eye.distanceTo(hit.getLocation()) - 0.7D);
        for (double d = dist; d >= 1.0D; d -= 1.0D) {
            Vec3 pos = eye.add(look.scale(d)).subtract(0.0D, player.getEyeHeight(), 0.0D);
            AABB moved = player.getBoundingBox().move(pos.x - player.getX(), pos.y - player.getY(), pos.z - player.getZ());
            if (server.noCollision(player, moved)) {
                server.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.4D);
                sound(server, player, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                if (player instanceof ServerPlayer sp) {
                    sp.connection.teleport(pos.x, pos.y, pos.z, sp.getYRot(), sp.getXRot());
                } else {
                    player.moveTo(pos.x, pos.y, pos.z);
                }
                player.fallDistance = 0.0F;
                server.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0D, pos.z, 40, 0.4D, 0.8D, 0.4D, 0.4D);
                sound(server, player, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.3F);
                return true;
            }
        }
        player.displayClientMessage(Component.literal("No room to blink.").withStyle(ChatFormatting.RED), true);
        return false;
    }

    private static void fang(ServerLevel server, Player owner, double x, double z, double minY, double maxY, float yaw, int warmup) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        boolean found = false;
        double yOffset = 0.0D;
        do {
            BlockPos below = pos.below();
            BlockState belowState = server.getBlockState(below);
            if (belowState.isFaceSturdy(server, below, Direction.UP)) {
                BlockState here = server.getBlockState(pos);
                VoxelShape shape = here.getCollisionShape(server, pos);
                if (!shape.isEmpty()) {
                    yOffset = shape.max(Direction.Axis.Y);
                }
                found = true;
                break;
            }
            pos = pos.below();
        } while (pos.getY() >= Mth.floor(minY) - 1);
        if (found) {
            server.addFreshEntity(new EvokerFangs(server, x, pos.getY() + yOffset, z, yaw, warmup, owner));
        }
    }
}
