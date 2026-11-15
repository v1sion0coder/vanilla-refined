package dev.v1sion.vanillarefined.client;

import dev.v1sion.vanillarefined.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side particles and sounds that make the world feel alive. */
final class WorldFx {
    static SoundEvent snd(String id) { return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("vanillarefined", id)); }
    static final SoundEvent CRICKETS = snd("ambient.crickets"), BIRDS = snd("ambient.birds"), WIND = snd("ambient.wind"),
            DRIP = snd("ambient.cave_drip"), CREAK = snd("block.ladder_creak"), WET = snd("player.wet_step"), SPARKLE = snd("ui.sparkle");

    static final Map<Integer, Vec3> falling = new HashMap<>();
    static final Map<Integer, BlockState> fallingState = new HashMap<>();
    static final Map<Integer, Integer> frameRot = new HashMap<>();
    static final Map<UUID, Integer> wetUntil = new HashMap<>();
    static final Map<UUID, Integer> lastStep = new HashMap<>();
    static int nextBird, nextCricket, nextWind, nextDrip, nextCreak;

    static void tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        ClientLevel level = mc.level;
        if (me == null || level == null || mc.isPaused()) return;
        RandomSource r = level.getRandom();
        int t = me.tickCount;
        scanBlocks(level, me, r);
        entities(level, me, r, t);
        ambience(level, me, r, t);
        ladder(level, me, r, t);
    }

    // ------------------------------------------------------------------ blocks around the player

    static void scanBlocks(ClientLevel level, LocalPlayer me, RandomSource r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos c = me.blockPosition();
        for (int i = 0; i < 260; i++) {
            m.set(c.getX() + r.nextInt(33) - 16, c.getY() + r.nextInt(17) - 8, c.getZ() + r.nextInt(33) - 16);
            BlockState s = level.getBlockState(m);
            Block b = s.getBlock();
            double x = m.getX() + .5, y = m.getY(), z = m.getZ() + .5;
            if (Config.I.animatedContainers) {
                if ((b instanceof AbstractFurnaceBlock) && s.getValue(BlockStateProperties.LIT)) {
                    // a strong, busy fire while smelting
                    level.addParticle(ParticleTypes.FLAME, x + (r.nextDouble() - .5) * .5, y + .2 + r.nextDouble() * .3, z + (r.nextDouble() - .5) * .5, 0, .01, 0);
                    if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x, y + 1.05, z, 0, .04, 0);
                } else if (b instanceof BrewingStandBlock && (s.getValue(BrewingStandBlock.HAS_BOTTLE[0]) || s.getValue(BrewingStandBlock.HAS_BOTTLE[1]) || s.getValue(BrewingStandBlock.HAS_BOTTLE[2]))) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, x + (r.nextDouble() - .5) * .4, y + .55, z + (r.nextDouble() - .5) * .4, 0, .03, 0);
                    if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.WITCH, x, y + .9, z, 0, 0, 0);
                } else if (b instanceof LecternBlock && s.getValue(LecternBlock.HAS_BOOK)) {
                    // pages "turning": magic letters lifting off the book
                    level.addParticle(ParticleTypes.ENCHANT, x, y + 1.6, z, (r.nextDouble() - .5) * .6, -.6, (r.nextDouble() - .5) * .6);
                } else if (b instanceof HopperBlock && s.getValue(HopperBlock.ENABLED) && level.getBlockEntity(m) != null && r.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.WAX_OFF, x, y + .75, z, 0, -.02, 0);
                }
            }
            if (Config.I.flickeringLights) flicker(level, me, r, s, b, x, y, z);
            if (Config.I.cropSparkle && b instanceof CropBlock crop && crop.isMaxAge(s) && r.nextInt(2) == 0)
                level.addParticle(ParticleTypes.WAX_ON, x + (r.nextDouble() - .5) * .6, y + .5 + r.nextDouble() * .4, z + (r.nextDouble() - .5) * .6, 0, .02, 0);
        }
    }

    /** Torches, lanterns, candles and campfires flicker, and the flame leans away when you walk past. */
    static void flicker(ClientLevel level, LocalPlayer me, RandomSource r, BlockState s, Block b, double x, double y, double z) {
        double fy;
        double fx = x, fz = z;
        if (b instanceof TorchBlock && !(b instanceof RedstoneTorchBlock)) fy = y + .72;
        else if (b instanceof WallTorchBlock && !(b instanceof RedstoneWallTorchBlock)) {
            var f = s.getValue(WallTorchBlock.FACING).getOpposite();
            fx += f.getStepX() * .27; fz += f.getStepZ() * .27; fy = y + .92;
        } else if (b instanceof LanternBlock) fy = y + (s.getValue(LanternBlock.HANGING) ? .45 : .4);
        else if (b instanceof CandleBlock && s.getValue(CandleBlock.LIT)) fy = y + .6;
        else return;
        Vec3 vel = me.getDeltaMovement();
        double dist = me.position().distanceToSqr(fx, fy, fz);
        double lean = dist < 6 ? Math.min(.04, vel.horizontalDistance() * .2) : 0;
        level.addParticle(ParticleTypes.SMALL_FLAME, fx + (r.nextDouble() - .5) * .04, fy, fz + (r.nextDouble() - .5) * .04, vel.x * lean * 20, .005 + r.nextDouble() * .01, vel.z * lean * 20);
        if (r.nextInt(5) == 0) level.addParticle(ParticleTypes.SMOKE, fx, fy + .12, fz, 0, .02, 0);
    }

    // ------------------------------------------------------------------ entities

    static void entities(ClientLevel level, LocalPlayer me, RandomSource r, int t) {
        Map<Integer, Vec3> stillFalling = new HashMap<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e.distanceToSqr(me) > 64 * 64) continue;
            if (Config.I.fallingBlockDust && e instanceof FallingBlockEntity fb) {
                BlockState st = fb.getBlockState();
                stillFalling.put(e.getId(), e.position());
                fallingState.put(e.getId(), st);
                // trail of dust while it drops
                level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, st), e.getX() + (r.nextDouble() - .5) * .8, e.getY() + r.nextDouble(), e.getZ() + (r.nextDouble() - .5) * .8, 0, 0, 0);
            }
            if (Config.I.itemFrameEffects && e instanceof ItemFrame frame) {
                int rot = frame.getRotation();
                Integer before = frameRot.put(e.getId(), rot);
                if (before != null && before != rot && !frame.getItem().isEmpty())
                    for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.WAX_ON, frame.getX() + (r.nextDouble() - .5) * .6, frame.getY() + (r.nextDouble() - .5) * .6, frame.getZ() + (r.nextDouble() - .5) * .6, 0, .02, 0);
                if (frame instanceof GlowItemFrame && r.nextInt(14) == 0) // gentle glowing pulse
                    level.addParticle(ParticleTypes.GLOW, frame.getX() + (r.nextDouble() - .5) * .7, frame.getY() + (r.nextDouble() - .5) * .7, frame.getZ() + (r.nextDouble() - .5) * .7, 0, 0, 0);
            }
            if (e instanceof Player p) players(level, me, r, t, p);
            if (Config.I.deathEffects && e instanceof LivingEntity le && !(e instanceof Player) && le.deathTime > 0) {
                // the body falls over (vanilla) while its soul drifts up and the body dissolves into dust
                level.addParticle(ParticleTypes.SOUL, le.getX() + (r.nextDouble() - .5) * le.getBbWidth(), le.getY() + r.nextDouble() * le.getBbHeight(), le.getZ() + (r.nextDouble() - .5) * le.getBbWidth(), 0, .03, 0);
                if (le.deathTime > 12) level.addParticle(ParticleTypes.WHITE_SMOKE, le.getX() + (r.nextDouble() - .5) * le.getBbWidth(), le.getY() + r.nextDouble() * le.getBbHeight() * .5, le.getZ() + (r.nextDouble() - .5) * le.getBbWidth(), 0, .01, 0);
            }
            if (Config.I.villagerEmotes && e instanceof Villager v && r.nextInt(260) == 0 && v.distanceToSqr(me) < 16 * 16) {
                var held = me.getMainHandItem();
                var p = held.is(Items.EMERALD) ? ParticleTypes.HEART : v.getHealth() < v.getMaxHealth() * .5 ? ParticleTypes.ANGRY_VILLAGER : r.nextBoolean() ? ParticleTypes.NOTE : ParticleTypes.HAPPY_VILLAGER;
                level.addParticle(p, v.getX(), v.getY() + v.getBbHeight() + .45, v.getZ(), r.nextDouble(), 0, 0);
            }
        }
        // falling blocks that just landed: puff of dust
        for (var it = falling.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (stillFalling.containsKey(en.getKey())) continue;
            Vec3 p = en.getValue();
            BlockState st = fallingState.remove(en.getKey());
            if (st != null) {
                for (int i = 0; i < 18; i++) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), p.x + (r.nextDouble() - .5), p.y + .1, p.z + (r.nextDouble() - .5), (r.nextDouble() - .5) * .2, .1, (r.nextDouble() - .5) * .2);
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.POOF, p.x + (r.nextDouble() - .5), p.y + .1, p.z + (r.nextDouble() - .5), (r.nextDouble() - .5) * .05, .02, (r.nextDouble() - .5) * .05);
            }
            it.remove();
        }
        falling.putAll(stillFalling);
        if (t % 200 == 0) { frameRot.keySet().removeIf(id -> level.getEntity(id) == null); }
    }

    static void players(ClientLevel level, LocalPlayer me, RandomSource r, int t, Player p) {
        // cold breath
        if (Config.I.coldBreath && (t + p.getId()) % 45 == 0) {
            BlockPos at = p.blockPosition();
            if (level.getBiome(at).value().coldEnoughToSnow(at, level.getSeaLevel()) && (!p.isInvisible())) {
                Vec3 look = p.getLookAngle();
                for (int i = 0; i < 3; i++)
                    level.addParticle(ParticleTypes.WHITE_SMOKE, p.getX() + look.x * .4, p.getEyeY() - .12, p.getZ() + look.z * .4, look.x * .03, .005, look.z * .03);
            }
        }
        // wet footsteps after leaving water
        if (Config.I.wetFootsteps) {
            if (p.isInWater()) wetUntil.put(p.getUUID(), t + 200);
            Integer until = wetUntil.get(p.getUUID());
            if (until != null && until > t && !p.isInWater() && p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() > 1e-3) {
                int last = lastStep.getOrDefault(p.getUUID(), 0);
                if (t - last >= (p.isSprinting() ? 6 : 9)) {
                    lastStep.put(p.getUUID(), t);
                    float fade = (until - t) / 200f;
                    level.addParticle(ParticleTypes.SPLASH, p.getX(), p.getY() + .05, p.getZ(), 0, .05, 0);
                    level.addParticle(ParticleTypes.FALLING_WATER, p.getX() + (r.nextDouble() - .5) * .4, p.getY() + 1, p.getZ() + (r.nextDouble() - .5) * .4, 0, 0, 0);
                    level.playLocalSound(p.getX(), p.getY(), p.getZ(), WET, SoundSource.PLAYERS, .35f * fade + .1f, .9f + r.nextFloat() * .2f, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ ambience

    static void ambience(ClientLevel level, LocalPlayer me, RandomSource r, int t) {
        if (!Config.I.ambientSounds) return;
        float v = Config.I.ambientVolume;
        BlockPos at = me.blockPosition();
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        int sky = level.getBrightness(LightLayer.SKY, at);
        long time = level.getOverworldClockTime() % 24000;
        boolean day = time < 12300 || time > 23500;
        boolean raining = level.isRaining();
        if (overworld && sky >= 10 && !raining) {
            boolean green = false;
            for (int i = 0; i < 12 && !green; i++) {
                BlockState s = level.getBlockState(at.offset(r.nextInt(17) - 8, r.nextInt(9) - 3, r.nextInt(17) - 8));
                green = s.getBlock() instanceof LeavesBlock || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS);
            }
            if (green && day && t >= nextBird) {
                nextBird = t + 160 + r.nextInt(400);
                level.playLocalSound(me.getX() + r.nextInt(21) - 10, me.getY() + 4 + r.nextInt(6), me.getZ() + r.nextInt(21) - 10, BIRDS, SoundSource.AMBIENT, .35f * v, .9f + r.nextFloat() * .25f, false);
            }
            if (green && !day && t >= nextCricket) {
                nextCricket = t + 120 + r.nextInt(240);
                level.playLocalSound(me.getX() + r.nextInt(17) - 8, me.getY(), me.getZ() + r.nextInt(17) - 8, CRICKETS, SoundSource.AMBIENT, .3f * v, .95f + r.nextFloat() * .1f, false);
            }
        }
        // wind up high / on mountains
        if (overworld && sky >= 14 && me.getY() > 110 && t >= nextWind) {
            nextWind = t + 140 + r.nextInt(200);
            level.playLocalSound(me.getX(), me.getY() + 2, me.getZ(), WIND, SoundSource.AMBIENT, (float) Math.min(.6, (me.getY() - 100) / 80) * v, .8f + r.nextFloat() * .3f, false);
        }
        // cave drips with echo when underground
        if (sky == 0 && me.getY() < 62 && t >= nextDrip) {
            nextDrip = t + 60 + r.nextInt(180);
            BlockPos ceiling = at.offset(r.nextInt(13) - 6, 3 + r.nextInt(4), r.nextInt(13) - 6);
            if (!level.getBlockState(ceiling).isAir()) {
                level.addParticle(ParticleTypes.DRIPPING_WATER, ceiling.getX() + r.nextDouble(), ceiling.getY() - .05, ceiling.getZ() + r.nextDouble(), 0, 0, 0);
                level.playLocalSound(ceiling.getX(), ceiling.getY(), ceiling.getZ(), DRIP, SoundSource.AMBIENT, .45f * v, .85f + r.nextFloat() * .3f, false);
            }
        }
    }

    static void ladder(ClientLevel level, LocalPlayer me, RandomSource r, int t) {
        if (!Config.I.ladderCreak || !me.onClimbable() || Math.abs(me.getDeltaMovement().y) < .05 || t < nextCreak) return;
        if (!(level.getBlockState(me.blockPosition()).getBlock() instanceof LadderBlock)) return;
        nextCreak = t + 14 + r.nextInt(8);
        level.playLocalSound(me.getX(), me.getY(), me.getZ(), CREAK, SoundSource.BLOCKS, .35f, .85f + r.nextFloat() * .3f, false);
    }
}
