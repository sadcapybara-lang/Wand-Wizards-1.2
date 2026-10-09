package com.soulstealer.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared helpers of the wizard entities: the server-side "magic bolt" raycast and the wizard hat lookup. */
public final class MagicBolt {

    private MagicBolt() {
    }

    public static boolean isWizard(Entity entity) {
        return entity instanceof RebelWizardEntity || entity instanceof MaxWizardEntity;
    }

    /** The wizard hat from the registry (no compile-time dependency on the main class), or an empty stack. */
    static ItemStack wizardHat() {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("soulstealer", "wizard_hat"));
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    /**
     * Fires an invisible bolt from {@code start} along {@code dir}. It stops at the first solid block or at the first
     * living entity that is not a wizard (which takes {@code damage} magic damage). Returns true when something was hit.
     */
    static boolean bolt(LivingEntity caster, Vec3 start, Vec3 dir, double range, float damage,
                        ParticleOptions trail, ParticleOptions sparkle) {
        Level level = caster.level();
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        Vec3 d = dir.normalize();
        int step = 0;
        for (double t = 0.5D; t <= range; t += 0.5D) {
            step++;
            Vec3 p = start.add(d.scale(t));
            BlockPos bp = BlockPos.containing(p.x, p.y, p.z);
            BlockState state = level.getBlockState(bp);
            if (!state.getCollisionShape(level, bp).isEmpty()) {
                server.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 3, 0.1D, 0.1D, 0.1D, 0.01D);
                return false;
            }
            server.sendParticles(trail, p.x, p.y, p.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
            if (step % 2 == 0) {
                server.sendParticles(sparkle, p.x, p.y, p.z, 1, 0.08D, 0.08D, 0.08D, 0.0D);
            }
            AABB box = new AABB(p.x - 0.3D, p.y - 0.3D, p.z - 0.3D, p.x + 0.3D, p.y + 0.3D, p.z + 0.3D);
            List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != caster && e.isAlive() && !e.isSpectator() && !isWizard(e));
            if (!hits.isEmpty()) {
                LivingEntity hit = hits.get(0);
                hit.hurt(level.damageSources().indirectMagic(caster, caster), damage);
                server.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 12, 0.3D, 0.3D, 0.3D, 0.05D);
                return true;
            }
        }
        return false;
    }

    /** Result of a player beam: everything it touched and where it ended. */
    public record Result(List<LivingEntity> hits, Vec3 end) {
    }

    /**
     * A visible beam for the wands. Unlike {@link #bolt} it does not hurt anybody itself: the caller decides what the
     * hit entities get. It can pierce (up to {@code maxHits} entities), stops at solid blocks and never hits the caster,
     * spectators or pets of the caster.
     */
    public static Result beam(LivingEntity caster, Vec3 start, Vec3 dir, double range, ParticleOptions trail, ParticleOptions sparkle,
                              boolean pierce, int maxHits, Predicate<LivingEntity> filter) {
        Level level = caster.level();
        List<LivingEntity> hits = new ArrayList<>();
        if (!(level instanceof ServerLevel server)) {
            return new Result(hits, start);
        }
        Vec3 d = dir.normalize();
        Set<LivingEntity> seen = new HashSet<>();
        Vec3 last = start;
        int step = 0;
        for (double t = 0.5D; t <= range; t += 0.5D) {
            step++;
            Vec3 p = start.add(d.scale(t));
            BlockPos bp = BlockPos.containing(p.x, p.y, p.z);
            BlockState state = level.getBlockState(bp);
            if (!state.getCollisionShape(level, bp).isEmpty()) {
                server.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 3, 0.1D, 0.1D, 0.1D, 0.01D);
                break;
            }
            last = p;
            server.sendParticles(trail, p.x, p.y, p.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
            if (step % 2 == 0) {
                server.sendParticles(sparkle, p.x, p.y, p.z, 1, 0.08D, 0.08D, 0.08D, 0.0D);
            }
            AABB box = new AABB(p.x - 0.35D, p.y - 0.35D, p.z - 0.35D, p.x + 0.35D, p.y + 0.35D, p.z + 0.35D);
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != caster && e.isAlive() && !e.isSpectator() && filter.test(e)
                            && !(e instanceof TamableAnimal pet && pet.isOwnedBy(caster)));
            for (LivingEntity e : found) {
                if (seen.add(e)) {
                    hits.add(e);
                    server.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 10, 0.3D, 0.3D, 0.3D, 0.05D);
                }
            }
            if (!hits.isEmpty() && (!pierce || hits.size() >= maxHits)) {
                last = p;
                break;
            }
        }
        return new Result(hits, last);
    }
}
