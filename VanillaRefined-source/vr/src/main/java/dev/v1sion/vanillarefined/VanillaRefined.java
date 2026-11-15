package dev.v1sion.vanillarefined;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Gameplay side of Vanilla Refined: things that need the (integrated or dedicated) server. */
public final class VanillaRefined implements ModInitializer {
    public static final String ID = "vanillarefined";
    public static final Logger LOG = LoggerFactory.getLogger("Vanilla Refined");

    @Override
    public void onInitialize() {
        Config.load(FabricLoader.getInstance().getConfigDir());
        UseBlockCallback.EVENT.register(VanillaRefined::onUseBlock);
        ServerTickEvents.END_SERVER_TICK.register(VanillaRefined::onServerTick);
        LOG.info("Vanilla Refined by v1sion loaded");
    }

    // ------------------------------------------------------------------ right-click

    static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = player.getItemInHand(hand);

        // right-click harvest + replant (empty hand or not holding a placeable/usable item)
        if (Config.I.rightClickHarvest && !player.isShiftKeyDown() && (held.isEmpty() || !held.is(Items.BONE_MEAL))) {
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                if (level instanceof ServerLevel sl) harvest(sl, player, pos, state, crop.getStateForAge(0));
                return InteractionResult.SUCCESS;
            }
            if (state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= 3) {
                if (level instanceof ServerLevel sl) harvest(sl, player, pos, state, state.setValue(NetherWartBlock.AGE, 0));
                return InteractionResult.SUCCESS;
            }
        }

        // bone meal "pop": extra sparkle + chime on top of vanilla growth
        if (Config.I.boneMealPop && held.is(Items.BONE_MEAL) && state.getBlock() instanceof BonemealableBlock && level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + .5, pos.getY() + .6, pos.getZ() + .5, 14, .35, .35, .35, 0);
            sl.sendParticles(ParticleTypes.WAX_ON, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 6, .3, .3, .3, 0.05);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .6f, 1.6f + sl.getRandom().nextFloat() * .3f);
        }

        // double doors open together
        if (Config.I.doubleDoors && !player.isShiftKeyDown() && state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && !level.isClientSide()) {
            BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockState ls = level.getBlockState(lower);
            if (ls.getBlock() instanceof DoorBlock) {
                Direction facing = ls.getValue(DoorBlock.FACING);
                DoorHingeSide hinge = ls.getValue(DoorBlock.HINGE);
                BlockPos other = lower.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
                BlockState os = level.getBlockState(other);
                if (os.getBlock() == ls.getBlock() && os.getValue(DoorBlock.FACING) == facing && os.getValue(DoorBlock.HINGE) != hinge
                        && os.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && os.getValue(DoorBlock.OPEN) == ls.getValue(DoorBlock.OPEN)) {
                    boolean open = !ls.getValue(DoorBlock.OPEN);
                    ((DoorBlock) os.getBlock()).setOpen(player, level, os, other, open);
                }
            }
        }
        return InteractionResult.PASS;
    }

    static void harvest(ServerLevel level, Player player, BlockPos pos, BlockState state, BlockState replanted) {
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, player, player.getMainHandItem()));
        // keep one seed back for the replant
        net.minecraft.world.item.Item seed = state.getBlock().asItem();
        for (ItemStack s : drops) if (s.is(seed)) { s.shrink(1); break; }
        for (ItemStack s : drops) if (!s.isEmpty()) Block.popResource(level, pos, s);
        level.setBlock(pos, replanted, 3);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + .5, pos.getY() + .4, pos.getZ() + .5, 6, .3, .2, .3, 0);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    // ------------------------------------------------------------------ ticking

    static final Map<UUID, Boolean> sheared = new HashMap<>();
    static final Map<UUID, Integer> snackCooldown = new HashMap<>();

    static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            Set<Animal> seen = new HashSet<>();
            for (ServerPlayer p : level.players()) seen.addAll(level.getEntitiesOfClass(Animal.class, p.getBoundingBox().inflate(32)));
            for (Animal a : seen) {
                if (Config.I.animalsEatDroppedFood) eatDroppedFood(level, a);
                if (Config.I.sheepRegrowFx && a instanceof Sheep sheep) {
                    Boolean before = sheared.put(sheep.getUUID(), sheep.isSheared());
                    if (before != null && before && !sheep.isSheared()) {
                        level.sendParticles(ParticleTypes.CLOUD, sheep.getX(), sheep.getY() + 1, sheep.getZ(), 10, .4, .3, .4, .02);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sheep.getX(), sheep.getY() + 1.2, sheep.getZ(), 6, .4, .3, .4, 0);
                        level.playSound(null, sheep.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, .8f, 1.3f);
                    }
                }
            }
        }
        if (server.getTickCount() % 1200 == 0) { sheared.keySet().removeIf(id -> false); if (sheared.size() > 5000) sheared.clear(); }
    }

    /** Animals walk to food lying on the ground and eat it: heals them and lets them breed. */
    static void eatDroppedFood(ServerLevel level, Animal a) {
        Integer cd = snackCooldown.get(a.getUUID());
        if (cd != null && cd > level.getServer().getTickCount()) return;
        List<ItemEntity> food = level.getEntitiesOfClass(ItemEntity.class, a.getBoundingBox().inflate(6), i -> i.isAlive() && a.isFood(i.getItem()) && i.tickCount > 20);
        if (food.isEmpty()) return;
        ItemEntity target = food.stream().min(Comparator.comparingDouble(a::distanceToSqr)).get();
        if (a.distanceToSqr(target) > 2.2) { a.getNavigation().moveTo(target, 1.15); return; }
        target.getItem().shrink(1);
        if (target.getItem().isEmpty()) target.discard();
        a.heal(2);
        if (!a.isBaby() && a.canFallInLove() && !a.isInLove()) a.setInLove(null);
        level.playSound(null, a.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, .7f, 1f + level.getRandom().nextFloat() * .2f);
        level.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + a.getBbHeight() + .3, a.getZ(), 2, .3, .1, .3, 0);
        snackCooldown.put(a.getUUID(), level.getServer().getTickCount() + 60);
    }
}
