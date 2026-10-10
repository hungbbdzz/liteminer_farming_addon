package com.velorise.veinfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.*;

/**
 * Handles mass harvesting, auto-replanting, left-click mass destruction, and harvest BFS searches.
 */
public class HarvestManager {

    /**
     * Handles AOE mass harvesting and replanting using positions strictly defined by LiteMiner's Walker or fallback BFS.
     */
    public static boolean handleMassHarvest(ServerPlayer player, InteractionHand hand, ItemStack heldItem, BlockPos clickedCropPos) {
        if (!FarmingConfig.ENABLE_MASS_HARVEST.get()) {
            return false;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Collection<BlockPos> selected = null;
        boolean isFarmlandTarget = PlantClassifier.isFarmland(serverLevel.getBlockState(clickedCropPos))
                || PlantClassifier.isFarmland(serverLevel.getBlockState(clickedCropPos.below()))
                || PlantClassifier.isFarmlandCrop(serverLevel.getBlockState(clickedCropPos));

        // When harvesting farmland crops, bypass external mining mod walkers (LiteMiner/FTB Ultimine)
        // so our specialized smart BFS can harvest ALL mature crops across intercropped rows (Wheat, Carrot, Potato, etc.).
        if (!isFarmlandTarget) {
            selected = FarmingManager.getSelectedPositions(player, clickedCropPos);
            if (selected == null || selected.isEmpty()) {
                selected = FarmingManager.getSelectedPositions(player, clickedCropPos.below());
            }
            if (selected == null || selected.isEmpty()) {
                selected = FarmingManager.getSelectedPositions(player, clickedCropPos.above());
            }
        }

        if (selected == null || selected.isEmpty()) {
            selected = fallbackHarvestSearch(serverLevel, clickedCropPos);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        final BlockPos originPos = clickedCropPos;
        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(originPos)))
                .toList();

        int maxLimit = FarmingManager.getEffectiveBlockLimit(player);
        boolean preventBreaking = FarmingManager.shouldPreventToolBreaking(player);
        boolean replant = FarmingConfig.REPLANT_CROPS.get();
        boolean damageHoe = FarmingConfig.DAMAGE_HOE_ON_HARVEST.get();
        boolean collectAtTarget = FarmingConfig.COLLECT_DROPS_AT_TARGET.get();
        boolean harvestToInventory = FarmingConfig.HARVEST_TO_INVENTORY.get();
        boolean allowColumnCrops = FarmingConfig.HARVEST_SUGAR_CANE.get();
        float exhaustion = FarmingManager.getFoodExhaustion(player);

        int harvestedCount = 0;
        SoundType lastSoundType = null;
        BlockState originState = serverLevel.getBlockState(clickedCropPos);

        List<ItemStack> allDrops = new ArrayList<>();
        Set<BlockPos> processedColumnRoots = new HashSet<>();

        for (BlockPos pos : sorted) {
            if (harvestedCount >= maxLimit) {
                break;
            }

            // Check tool breaking safety if using a damageable tool/hoe
            if (!player.isCreative() && !heldItem.isEmpty() && heldItem.isDamageableItem()) {
                if (preventBreaking && heldItem.getDamageValue() >= heldItem.getMaxDamage() - 1) {
                    break;
                }
            }

            // 1. Column Crops handling (Sugar Cane, Cactus, Bamboo, Kelp - preserves bottom root)
            if (allowColumnCrops) {
                BlockPos colPos = null;
                if (PlantClassifier.isColumnCrop(serverLevel.getBlockState(pos))) {
                    colPos = pos;
                } else if (PlantClassifier.isColumnCrop(serverLevel.getBlockState(pos.above()))) {
                    colPos = pos.above();
                }

                if (colPos != null) {
                    BlockPos rootPos = PlantClassifier.getColumnCropRoot(serverLevel, colPos);
                    if (!processedColumnRoots.contains(rootPos)) {
                        processedColumnRoots.add(rootPos);

                        // Harvest stalks strictly ABOVE rootPos
                        BlockPos stalkPos = rootPos.above();
                        BlockState rootState = serverLevel.getBlockState(rootPos);
                        while (PlantClassifier.isSameColumnType(rootState, serverLevel.getBlockState(stalkPos))) {
                            if (harvestedCount >= maxLimit) {
                                break;
                            }
                            if (!player.isCreative() && !heldItem.isEmpty() && heldItem.isDamageableItem()) {
                                if (preventBreaking && heldItem.getDamageValue() >= heldItem.getMaxDamage() - 1) {
                                    break;
                                }
                            }

                            BlockState stalkState = serverLevel.getBlockState(stalkPos);
                            lastSoundType = stalkState.getSoundType(serverLevel, stalkPos, player);

                            List<ItemStack> stalkDrops = new ArrayList<>(Block.getDrops(stalkState, serverLevel, stalkPos, null, player, heldItem));
                            if (stalkDrops.isEmpty()) {
                                stalkDrops.add(new ItemStack(stalkState.getBlock().asItem()));
                            }

                            if (collectAtTarget || harvestToInventory) {
                                allDrops.addAll(stalkDrops);
                            } else {
                                for (ItemStack drop : stalkDrops) {
                                    if (!drop.isEmpty()) {
                                        Block.popResource(serverLevel, stalkPos, drop);
                                    }
                                }
                            }

                            serverLevel.destroyBlock(stalkPos, false, player);
                            harvestedCount++;
                            applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);

                            stalkPos = stalkPos.above();
                        }
                    }
                    continue;
                }
            }

            // 2. Fruit Crops & Stems handling (Melon, Pumpkin - strictly protects stems!)
            BlockState curState = serverLevel.getBlockState(pos);
            if (PlantClassifier.isFruitCrop(curState)) {
                lastSoundType = curState.getSoundType(serverLevel, pos, player);
                List<ItemStack> fruitDrops = new ArrayList<>(Block.getDrops(curState, serverLevel, pos, null, player, heldItem));
                if (collectAtTarget || harvestToInventory) {
                    allDrops.addAll(fruitDrops);
                } else {
                    for (ItemStack drop : fruitDrops) {
                        if (!drop.isEmpty()) {
                            Block.popResource(serverLevel, pos, drop);
                        }
                    }
                }
                serverLevel.destroyBlock(pos, false, player);
                harvestedCount++;
                applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                continue;
            } else if (PlantClassifier.isStem(curState)) {
                // 100% Protect stem: never harvest, never break, never reset!
                continue;
            }

            // 2b. Chorus Plant & Flower handling
            if (PlantClassifier.isChorus(curState)) {
                lastSoundType = curState.getSoundType(serverLevel, pos, player);
                List<ItemStack> chorusDrops = new ArrayList<>(Block.getDrops(curState, serverLevel, pos, null, player, heldItem));

                boolean isRootOnEndStone = serverLevel.getBlockState(pos.below()).is(Blocks.END_STONE);
                if (replant && isRootOnEndStone) {
                    serverLevel.setBlock(pos, Blocks.CHORUS_FLOWER.defaultBlockState(), 3);
                    serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
                } else {
                    serverLevel.destroyBlock(pos, false, player);
                }

                if (collectAtTarget || harvestToInventory) {
                    allDrops.addAll(chorusDrops);
                } else {
                    for (ItemStack drop : chorusDrops) {
                        if (!drop.isEmpty()) {
                            Block.popResource(serverLevel, pos, drop);
                        }
                    }
                }
                harvestedCount++;
                applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                continue;
            }

            // 3. Pitcher Crop (2 blocks tall)
            if (curState.is(Blocks.PITCHER_CROP) || serverLevel.getBlockState(pos.above()).is(Blocks.PITCHER_CROP)) {
                BlockPos pPos = curState.is(Blocks.PITCHER_CROP) ? pos : pos.above();
                BlockState pState = serverLevel.getBlockState(pPos);
                if (PlantClassifier.isMatureCrop(pState)) {
                    BlockPos lowerPos = pPos;
                    for (Property<?> prop : pState.getProperties()) {
                        if (prop.getName().equalsIgnoreCase("half") && pState.getValue(prop).toString().equalsIgnoreCase("upper")) {
                            lowerPos = pPos.below();
                            break;
                        }
                    }
                    BlockPos upperPos = lowerPos.above();
                    BlockState lowerState = serverLevel.getBlockState(lowerPos);
                    List<ItemStack> pitcherDrops = new ArrayList<>(Block.getDrops(lowerState, serverLevel, lowerPos, null, player, heldItem));

                    boolean canReplant = player.isCreative();
                    Item seedItem = Items.PITCHER_POD;
                    if (!canReplant && replant) {
                        for (Iterator<ItemStack> it = pitcherDrops.iterator(); it.hasNext(); ) {
                            ItemStack drop = it.next();
                            if (!drop.isEmpty() && drop.is(seedItem)) {
                                drop.shrink(1);
                                if (drop.isEmpty()) it.remove();
                                canReplant = true;
                                break;
                            }
                        }
                        if (!canReplant && FarmingConfig.PULL_FROM_INVENTORY.get()) {
                            canReplant = FarmingManager.consumeSeed(player, hand, seedItem);
                        }
                    }

                    if (canReplant && replant) {
                        if (serverLevel.getBlockState(upperPos).is(Blocks.PITCHER_CROP)) {
                            serverLevel.setBlock(upperPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                        serverLevel.setBlock(lowerPos, Blocks.PITCHER_CROP.defaultBlockState(), 3);
                        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, lowerPos);
                    } else {
                        serverLevel.destroyBlock(lowerPos, false, player);
                        if (serverLevel.getBlockState(upperPos).is(Blocks.PITCHER_CROP)) {
                            serverLevel.destroyBlock(upperPos, false, player);
                        }
                    }

                    if (collectAtTarget || harvestToInventory) {
                        allDrops.addAll(pitcherDrops);
                    } else {
                        for (ItemStack drop : pitcherDrops) {
                            if (!drop.isEmpty()) {
                                Block.popResource(serverLevel, lowerPos, drop);
                            }
                        }
                    }
                    serverLevel.playSound(null, lowerPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
                    harvestedCount++;
                    applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                    continue;
                }
            }

            // 4. Standard crop / Sweet Berry Bush / Cave Vines handling
            BlockPos cropPos = pos;
            BlockState cropState = serverLevel.getBlockState(cropPos);

            // If pos is farmland or not mature, check pos.above()
            if (!PlantClassifier.isMatureCrop(cropState)) {
                BlockPos above = pos.above();
                if (PlantClassifier.isMatureCrop(serverLevel.getBlockState(above))) {
                    cropPos = above;
                    cropState = serverLevel.getBlockState(cropPos);
                } else {
                    continue;
                }
            }

            Block cropBlock = cropState.getBlock();
            lastSoundType = cropState.getSoundType(serverLevel, cropPos, player);

            // Cave Vines (Glow Berries)
            if (cropBlock instanceof CaveVines || cropState.is(Blocks.CAVE_VINES) || cropState.is(Blocks.CAVE_VINES_PLANT)) {
                if (CaveVines.hasGlowBerries(cropState)) {
                    BlockState resetVines = cropState.setValue(CaveVines.BERRIES, false);
                    serverLevel.setBlock(cropPos, resetVines, 2);
                    serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, cropPos);
                    ItemStack berryDrop = new ItemStack(Items.GLOW_BERRIES, 1);
                    if (collectAtTarget || harvestToInventory) {
                        allDrops.add(berryDrop);
                    } else {
                        Block.popResource(serverLevel, cropPos, berryDrop);
                    }
                    serverLevel.playSound(null, cropPos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
                    harvestedCount++;
                    applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                }
                continue;
            }

            // Sweet Berry Bush: harvest berries without breaking or consuming seeds
            if (cropBlock instanceof SweetBerryBushBlock) {
                int age = cropState.getValue(SweetBerryBushBlock.AGE);
                int berryCount = 1 + serverLevel.random.nextInt(2) + (age >= 3 ? 1 : 0);
                BlockState resetBush = cropState.setValue(SweetBerryBushBlock.AGE, 1);
                serverLevel.setBlock(cropPos, resetBush, 2);
                serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, cropPos);

                ItemStack berryDrop = new ItemStack(Items.SWEET_BERRIES, berryCount);
                if (collectAtTarget || harvestToInventory) {
                    allDrops.add(berryDrop);
                } else {
                    Block.popResource(serverLevel, cropPos, berryDrop);
                }
                serverLevel.playSound(null, cropPos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);

                harvestedCount++;
                applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                continue;
            }

            // Standard crops: calculate drops
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(cropState, serverLevel, cropPos, null, player, heldItem));

            // Farmer's Delight Knife Compatibility: Straw drops
            if (PlantClassifier.isKnife(heldItem)) {
                Item strawItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "straw"));
                if (strawItem != null && strawItem != Items.AIR) {
                    boolean alreadyHasStraw = drops.stream().anyMatch(s -> s.is(strawItem));
                    if (!alreadyHasStraw) {
                        int fortune = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                .get(Enchantments.FORTUNE)
                                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, heldItem))
                                .orElse(0);
                        if (serverLevel.random.nextFloat() < (0.20F + 0.10F * fortune)) {
                            drops.add(new ItemStack(strawItem));
                        }
                    }
                }
            }

            if (replant) {
                Item seedItem = cropBlock.asItem();
                if (seedItem == Items.AIR) {
                    ItemStack pickStack = cropBlock.getCloneItemStack(serverLevel, cropPos, cropState);
                    if (!pickStack.isEmpty()) {
                        seedItem = pickStack.getItem();
                    }
                }
                boolean canReplant = player.isCreative();

                if (!canReplant && seedItem != Items.AIR) {
                    // Try to deduct 1 seed from harvested drops
                    for (Iterator<ItemStack> it = drops.iterator(); it.hasNext(); ) {
                        ItemStack drop = it.next();
                        if (!drop.isEmpty() && drop.is(seedItem)) {
                            drop.shrink(1);
                            if (drop.isEmpty()) {
                                it.remove();
                            }
                            canReplant = true;
                            break;
                        }
                    }

                    // If drops didn't provide a seed, try consuming from player inventory
                    if (!canReplant && FarmingConfig.PULL_FROM_INVENTORY.get()) {
                        canReplant = FarmingManager.consumeSeed(player, hand, seedItem);
                    }
                }

                if (canReplant) {
                    BlockState resetState = PlantClassifier.getResetCropState(cropState);
                    serverLevel.setBlock(cropPos, resetState, 3);
                    serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, cropPos);
                } else {
                    serverLevel.destroyBlock(cropPos, false, player);
                }
            } else {
                serverLevel.destroyBlock(cropPos, false, player);
            }

            // Drop items
            if (collectAtTarget || harvestToInventory) {
                allDrops.addAll(drops);
            } else {
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        Block.popResource(serverLevel, cropPos, drop);
                    }
                }
            }

            boolean audioCascade = FarmingConfig.SATISFYING_AUDIO_CASCADE.get();
            int soundInterval = audioCascade ? 2 : 4;
            if (harvestedCount % soundInterval == 0 && lastSoundType != null) {
                float pitch = audioCascade
                        ? (0.75F + Math.min(0.55F, (harvestedCount / (float) Math.max(1, sorted.size())) * 0.55F))
                        : (lastSoundType.getPitch() * 0.8F);
                serverLevel.playSound(null, cropPos, lastSoundType.getBreakSound(), SoundSource.BLOCKS,
                        (lastSoundType.getVolume() + 1.0F) / 2.0F, pitch);
            }

            if (audioCascade) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, cropPos.getX() + 0.5, cropPos.getY() + 0.3, cropPos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.02);
            }

            harvestedCount++;
            applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
        }

        if (harvestedCount > 0) {
            if (harvestToInventory && !allDrops.isEmpty()) {
                List<ItemStack> mergedDrops = FarmingManager.mergeItemStacks(allDrops);
                for (ItemStack drop : mergedDrops) {
                    if (drop.isEmpty()) continue;
                    boolean added = player.getInventory().add(drop);
                    if (!added || !drop.isEmpty()) {
                        // Inventory full: drop overflow at target position
                        Block.popResource(serverLevel, clickedCropPos, drop);
                    }
                }
                try {
                    if (player.containerMenu != null) {
                        player.containerMenu.broadcastChanges();
                    }
                } catch (Throwable ignored) {
                }
            } else if (collectAtTarget && !allDrops.isEmpty()) {
                List<ItemStack> mergedDrops = FarmingManager.mergeItemStacks(allDrops);
                for (ItemStack drop : mergedDrops) {
                    if (!drop.isEmpty()) {
                        Block.popResource(serverLevel, clickedCropPos, drop);
                    }
                }
            }

            if (FarmingConfig.SATISFYING_AUDIO_CASCADE.get()) {
                serverLevel.playSound(null, clickedCropPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.25F);
            } else if (lastSoundType != null) {
                serverLevel.playSound(null, clickedCropPos, lastSoundType.getBreakSound(), SoundSource.BLOCKS,
                        (lastSoundType.getVolume() + 1.0F) / 2.0F, lastSoundType.getPitch() * 0.8F);
            } else if (originState != null) {
                SoundType st = originState.getSoundType(serverLevel, clickedCropPos, player);
                serverLevel.playSound(null, clickedCropPos, st.getBreakSound(), SoundSource.BLOCKS,
                        (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
            }
            player.swing(hand, true);
            if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
                FTBUltimineCompat.applyPostUltimineCosts(player, harvestedCount);
            }
            ModAdvancements.award(player, ModAdvancements.ROOT);
            if (harvestedCount >= 64) {
                ModAdvancements.award(player, ModAdvancements.LAZY_FARMER);
            }
            if (!player.isCreative() && preventBreaking && !heldItem.isEmpty() && heldItem.isDamageableItem() && heldItem.getDamageValue() >= heldItem.getMaxDamage() - 1) {
                ModAdvancements.award(player, ModAdvancements.LIVING_ON_EDGE);
            }
            return true;
        }

        return false;
    }

    private static void applyHarvestCosts(ServerPlayer player, InteractionHand hand, ItemStack heldItem, boolean damageHoe, float exhaustion) {
        if (player.isCreative()) {
            return;
        }
        boolean isFarmingTool = FarmingEventHandler.isHoe(heldItem) || PlantClassifier.isKnife(heldItem);
        if (damageHoe && !heldItem.isEmpty() && isFarmingTool) {
            heldItem.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        if (exhaustion > 0) {
            player.causeFoodExhaustion(exhaustion);
        }
    }

    /**
     * Left-Click Mass Destruction: Completely wipes connected agricultural crops and plants (including root blocks of column crops),
     * without replanting, leaving clean soil for replanting new crops.
     * Strictly restricted to agricultural crops/plants, leaving regular stone/dirt/ores untouched.
     */
    public static boolean handleMassDestroy(ServerPlayer player, InteractionHand hand, ItemStack heldItem, BlockPos clickedPos, BlockState clickedState) {
        if (!FarmingConfig.ENABLE_MASS_HARVEST.get()) {
            return false;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Collection<BlockPos> targets = findDestroyTargets(serverLevel, clickedPos, clickedState);
        if (targets == null || targets.isEmpty()) {
            return false;
        }

        List<BlockPos> sorted = new ArrayList<>(targets);
        boolean isColumn = PlantClassifier.isColumnCrop(clickedState);
        if (isColumn) {
            sorted.sort((a, b) -> {
                int cmpY = Integer.compare(b.getY(), a.getY());
                if (cmpY != 0) return cmpY;
                return Integer.compare(a.distManhattan(clickedPos), b.distManhattan(clickedPos));
            });
        } else {
            sorted.sort(Comparator.comparingInt(p -> p.distManhattan(clickedPos)));
        }

        int maxLimit = FarmingManager.getEffectiveBlockLimit(player);
        boolean preventBreaking = FarmingManager.shouldPreventToolBreaking(player);
        float exhaustion = FarmingManager.getFoodExhaustion(player);
        boolean harvestToInventory = FarmingConfig.HARVEST_TO_INVENTORY.get();
        boolean collectAtTarget = FarmingConfig.COLLECT_DROPS_AT_TARGET.get();
        boolean gatherDrops = harvestToInventory || collectAtTarget;

        int destroyedCount = 0;
        List<ItemStack> allDrops = new ArrayList<>();
        SoundType lastSoundType = null;

        for (BlockPos pos : sorted) {
            if (destroyedCount >= maxLimit) {
                break;
            }

            if (!player.isCreative() && !heldItem.isEmpty() && heldItem.isDamageableItem()) {
                if (preventBreaking && heldItem.getDamageValue() >= heldItem.getMaxDamage() - 1) {
                    break;
                }
            }

            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            lastSoundType = state.getSoundType(serverLevel, pos, player);

            if (gatherDrops) {
                List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, serverLevel, pos, null, player, heldItem));
                if (PlantClassifier.isKnife(heldItem)) {
                    Item strawItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "straw"));
                    if (strawItem != null && strawItem != Items.AIR && serverLevel.random.nextFloat() < 0.20F) {
                        drops.add(new ItemStack(strawItem));
                    }
                }
                allDrops.addAll(drops);
                serverLevel.destroyBlock(pos, false, player);
            } else {
                serverLevel.destroyBlock(pos, true, player);
            }

            destroyedCount++;

            if (!player.isCreative()) {
                if (!heldItem.isEmpty() && heldItem.isDamageableItem()) {
                    heldItem.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
                if (exhaustion > 0) {
                    player.causeFoodExhaustion(exhaustion);
                }
            }
        }

        if (destroyedCount > 0) {
            if (harvestToInventory && !allDrops.isEmpty()) {
                List<ItemStack> merged = FarmingManager.mergeItemStacks(allDrops);
                for (ItemStack drop : merged) {
                    if (drop.isEmpty()) continue;
                    boolean added = player.getInventory().add(drop);
                    if (!added || !drop.isEmpty()) {
                        Block.popResource(serverLevel, clickedPos, drop);
                    }
                }
                try {
                    if (player.containerMenu != null) {
                        player.containerMenu.broadcastChanges();
                    }
                } catch (Throwable ignored) {
                }
            } else if (collectAtTarget && !allDrops.isEmpty()) {
                List<ItemStack> merged = FarmingManager.mergeItemStacks(allDrops);
                for (ItemStack drop : merged) {
                    if (!drop.isEmpty()) {
                        Block.popResource(serverLevel, clickedPos, drop);
                    }
                }
            }

            if (FarmingConfig.SATISFYING_AUDIO_CASCADE.get()) {
                serverLevel.playSound(null, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.25F);
            } else if (lastSoundType != null) {
                serverLevel.playSound(null, clickedPos, lastSoundType.getBreakSound(), SoundSource.BLOCKS,
                        (lastSoundType.getVolume() + 1.0F) / 2.0F, lastSoundType.getPitch() * 0.8F);
            }

            player.swing(hand, true);
            if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
                FTBUltimineCompat.applyPostUltimineCosts(player, destroyedCount);
            }
            ModAdvancements.award(player, ModAdvancements.ROOT);
            if (!player.isCreative() && preventBreaking && !heldItem.isEmpty() && heldItem.isDamageableItem() && heldItem.getDamageValue() >= heldItem.getMaxDamage() - 1) {
                ModAdvancements.award(player, ModAdvancements.LIVING_ON_EDGE);
            }
            return true;
        }

        return false;
    }

    public static Collection<BlockPos> findDestroyTargets(Level level, BlockPos clickedPos, BlockState clickedState) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // Rice (Farmer's Delight): wipe both submerged crops and upper panicles
        if (PlantClassifier.isRiceCrop(clickedState) || PlantClassifier.isRiceCrop(level.getBlockState(clickedPos.above()))) {
            BlockPos start = PlantClassifier.isRiceCrop(clickedState) ? clickedPos : clickedPos.above();
            queue.add(start);
            visited.add(start);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos curr = queue.poll();
                BlockState currState = level.getBlockState(curr);
                if (PlantClassifier.isRiceCrop(currState) && !result.contains(curr)) {
                    result.add(curr);
                }
                BlockPos below = curr.below();
                if (PlantClassifier.isRiceCrop(level.getBlockState(below)) && !result.contains(below)) {
                    result.add(below);
                }
                BlockPos above = curr.above();
                if (PlantClassifier.isRiceCrop(level.getBlockState(above)) && !result.contains(above)) {
                    result.add(above);
                }

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - start.getX()) > radius
                                    || Math.abs(next.getZ() - start.getZ()) > radius
                                    || Math.abs(next.getY() - start.getY()) > 2) {
                                continue;
                            }

                            BlockState nextState = level.getBlockState(next);
                            if (PlantClassifier.isRiceCrop(nextState) || PlantClassifier.isRiceCrop(level.getBlockState(next.below())) || PlantClassifier.isRiceCrop(level.getBlockState(next.above()))) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            return result;
        }

        if (PlantClassifier.isColumnCrop(clickedState)) {
            BlockPos startRoot = PlantClassifier.getColumnCropRoot(level, clickedPos);
            queue.add(startRoot);
            visited.add(startRoot);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos currentRoot = queue.poll();
                BlockState rootState = level.getBlockState(currentRoot);

                // For mass destruction, collect the root itself!
                result.add(currentRoot);

                // Collect all stalks above currentRoot!
                BlockPos stalk = currentRoot.above();
                while (PlantClassifier.isSameColumnType(rootState, level.getBlockState(stalk))) {
                    if (result.size() >= maxLimit) break;
                    result.add(stalk);
                    stalk = stalk.above();
                }

                // Check 8 horizontal neighbors for adjacent column roots
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos neighborPos = currentRoot.offset(dx, dy, dz);
                            if (visited.contains(neighborPos)) continue;
                            if (Math.abs(neighborPos.getX() - startRoot.getX()) > radius
                                    || Math.abs(neighborPos.getZ() - startRoot.getZ()) > radius
                                    || Math.abs(neighborPos.getY() - startRoot.getY()) > 3) {
                                continue;
                            }

                            BlockState neighborState = level.getBlockState(neighborPos);
                            if (PlantClassifier.isSameColumnType(clickedState, neighborState)) {
                                BlockPos neighborRoot = PlantClassifier.getColumnCropRoot(level, neighborPos);
                                if (!visited.contains(neighborRoot)) {
                                    visited.add(neighborRoot);
                                    queue.add(neighborRoot);
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }

        // Fruit crops / stems
        if (PlantClassifier.isFruitCrop(clickedState) || PlantClassifier.isStem(clickedState)) {
            queue.add(clickedPos);
            visited.add(clickedPos);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos curr = queue.poll();
                result.add(curr);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - clickedPos.getX()) > radius
                                    || Math.abs(next.getZ() - clickedPos.getZ()) > radius
                                    || Math.abs(next.getY() - clickedPos.getY()) > 2) {
                                continue;
                            }

                            BlockState nextState = level.getBlockState(next);
                            if (PlantClassifier.isFruitCrop(nextState) || PlantClassifier.isStem(nextState)) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            return result;
        }

        // Nether Wart
        if (clickedState.getBlock() instanceof NetherWartBlock) {
            queue.add(clickedPos);
            visited.add(clickedPos);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos curr = queue.poll();
                result.add(curr);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - clickedPos.getX()) > radius
                                    || Math.abs(next.getZ() - clickedPos.getZ()) > radius
                                    || Math.abs(next.getY() - clickedPos.getY()) > 2) {
                                continue;
                            }

                            BlockState nextState = level.getBlockState(next);
                            if (nextState.getBlock() instanceof NetherWartBlock) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            return result;
        }

        // Chorus Plant & Flower: complete 3D tree destruction
        if (PlantClassifier.isChorus(clickedState)) {
            queue.add(clickedPos);
            visited.add(clickedPos);
            List<BlockPos> flowers = new ArrayList<>();
            List<BlockPos> plants = new ArrayList<>();

            while (!queue.isEmpty() && (flowers.size() + plants.size()) < maxLimit) {
                BlockPos curr = queue.poll();
                BlockState currState = level.getBlockState(curr);
                if (currState.is(Blocks.CHORUS_FLOWER)) {
                    flowers.add(curr);
                } else if (currState.is(Blocks.CHORUS_PLANT)) {
                    plants.add(curr);
                }

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - clickedPos.getX()) > 16
                                    || Math.abs(next.getZ() - clickedPos.getZ()) > 16
                                    || Math.abs(next.getY() - clickedPos.getY()) > 24) {
                                continue;
                            }

                            BlockState nextState = level.getBlockState(next);
                            if (PlantClassifier.isChorus(nextState)) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            result.addAll(flowers);
            result.addAll(plants);
            return result;
        }

        // Flowers: clear contiguous flower patch
        if (PlantClassifier.isFlowerBlock(clickedState)) {
            queue.add(clickedPos);
            visited.add(clickedPos);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos curr = queue.poll();
                result.add(curr);

                BlockState currState = level.getBlockState(curr);
                if (level.getBlockState(curr.above()).is(currState.getBlock()) && !result.contains(curr.above())) {
                    result.add(curr.above());
                }
                if (level.getBlockState(curr.below()).is(currState.getBlock()) && !result.contains(curr.below())) {
                    result.add(curr.below());
                }

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - clickedPos.getX()) > radius
                                    || Math.abs(next.getZ() - clickedPos.getZ()) > radius
                                    || Math.abs(next.getY() - clickedPos.getY()) > 3) {
                                continue;
                            }

                            BlockState nextState = level.getBlockState(next);
                            if (PlantClassifier.isFlowerBlock(nextState)) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            return result;
        }

        // Mushrooms & Fungi: clear contiguous mushroom patch
        if (PlantClassifier.isMushroomBlock(clickedState)) {
            queue.add(clickedPos);
            visited.add(clickedPos);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos curr = queue.poll();
                result.add(curr);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - clickedPos.getX()) > radius
                                    || Math.abs(next.getZ() - clickedPos.getZ()) > radius
                                    || Math.abs(next.getY() - clickedPos.getY()) > 3) {
                                continue;
                            }

                            BlockState nextState = level.getBlockState(next);
                            if (PlantClassifier.isMushroomBlock(nextState)) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            return result;
        }

        // Cocoa
        if (clickedState.getBlock() instanceof CocoaBlock) {
            queue.add(clickedPos);
            visited.add(clickedPos);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos curr = queue.poll();
                if (level.getBlockState(curr).getBlock() instanceof CocoaBlock) {
                    if (!result.contains(curr)) {
                        result.add(curr);
                    }
                }

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - clickedPos.getX()) > radius
                                    || Math.abs(next.getZ() - clickedPos.getZ()) > radius
                                    || Math.abs(next.getY() - clickedPos.getY()) > 16) {
                                continue;
                            }

                            BlockState nextState = level.getBlockState(next);
                            if (nextState.getBlock() instanceof CocoaBlock || PlantClassifier.isJungleLog(nextState)) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            return result;
        }

        // Farmland crops: wipe all crops across contiguous farmland
        BlockPos actualCropPos = clickedPos;
        if (PlantClassifier.isFarmland(clickedState)) {
            actualCropPos = clickedPos.above();
        }

        queue.add(actualCropPos);
        visited.add(actualCropPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();
            BlockState curState = level.getBlockState(current);

            BlockPos cropPos = current;
            BlockState cropState = curState;
            if (PlantClassifier.isFarmland(cropState)) {
                cropPos = current.above();
                cropState = level.getBlockState(cropPos);
            }

            if (PlantClassifier.isCrop(cropState) && !result.contains(cropPos)) {
                result.add(cropPos);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - actualCropPos.getX()) > radius
                                || Math.abs(next.getZ() - actualCropPos.getZ()) > radius
                                || Math.abs(next.getY() - actualCropPos.getY()) > 2) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        boolean canTraverse = false;

                        if (PlantClassifier.isFarmland(nextState)) {
                            BlockState above = level.getBlockState(next.above());
                            if (above.isAir() || PlantClassifier.isCrop(above)) {
                                canTraverse = true;
                            }
                        } else if (PlantClassifier.isCrop(nextState)) {
                            if (PlantClassifier.isFarmland(level.getBlockState(next.below()))) {
                                canTraverse = true;
                            }
                        }

                        if (canTraverse) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        return result;
    }

    public static Collection<BlockPos> fallbackHarvestSearch(Level level, BlockPos startPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        BlockState startState = level.getBlockState(startPos);
        BlockPos actualCropPos = startPos;
        if (PlantClassifier.isFarmland(startState) || PlantClassifier.isSoulSand(startState)) {
            actualCropPos = startPos.above();
            startState = level.getBlockState(actualCropPos);
        } else if (PlantClassifier.isRiceCrop(startState) && PlantClassifier.isRiceCrop(level.getBlockState(startPos.above()))) {
            actualCropPos = startPos.above();
            startState = level.getBlockState(actualCropPos);
        }

        boolean targetColumn = PlantClassifier.isColumnCrop(startState);
        boolean targetFruit = PlantClassifier.isFruitCrop(startState);
        boolean targetBerry = startState.getBlock() instanceof SweetBerryBushBlock;
        boolean targetVines = startState.getBlock() instanceof CaveVines || startState.is(Blocks.CAVE_VINES) || startState.is(Blocks.CAVE_VINES_PLANT);
        boolean targetCocoa = startState.getBlock() instanceof CocoaBlock;
        boolean targetNetherWart = startState.getBlock() instanceof NetherWartBlock;
        boolean targetRice = PlantClassifier.isRiceCrop(startState);

        // Chorus Plant & Flower handling: traverse connected tree
        if (PlantClassifier.isChorus(startState)) {
            queue.add(actualCropPos);
            visited.add(actualCropPos);
            List<BlockPos> flowers = new ArrayList<>();
            List<BlockPos> plants = new ArrayList<>();

            while (!queue.isEmpty() && (flowers.size() + plants.size()) < maxLimit) {
                BlockPos curr = queue.poll();
                BlockState s = level.getBlockState(curr);
                if (s.is(Blocks.CHORUS_FLOWER)) {
                    flowers.add(curr);
                } else if (s.is(Blocks.CHORUS_PLANT)) {
                    plants.add(curr);
                }

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = curr.offset(dx, dy, dz);
                            if (visited.contains(next)) continue;
                            if (Math.abs(next.getX() - actualCropPos.getX()) > 16
                                    || Math.abs(next.getZ() - actualCropPos.getZ()) > 16
                                    || Math.abs(next.getY() - actualCropPos.getY()) > 24) {
                                continue;
                            }
                            if (PlantClassifier.isChorus(level.getBlockState(next))) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            result.addAll(flowers);
            result.addAll(plants);
            return result;
        }

        // If column crop, normalize root and traverse horizontally across adjacent column roots
        if (targetColumn) {
            BlockPos startRoot = PlantClassifier.getColumnCropRoot(level, actualCropPos);
            queue.add(startRoot);
            visited.add(startRoot);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos currentRoot = queue.poll();

                // Add stalks strictly ABOVE currentRoot to result (protecting root)
                BlockPos stalk = currentRoot.above();
                BlockState rootState = level.getBlockState(currentRoot);
                while (PlantClassifier.isSameColumnType(rootState, level.getBlockState(stalk))) {
                    if (result.size() >= maxLimit) break;
                    result.add(stalk);
                    stalk = stalk.above();
                }

                // Check 8 horizontal adjacent columns (including diagonals)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos neighborPos = currentRoot.offset(dx, dy, dz);
                            if (visited.contains(neighborPos)) continue;
                            if (Math.abs(neighborPos.getX() - startRoot.getX()) > radius
                                    || Math.abs(neighborPos.getZ() - startRoot.getZ()) > radius
                                    || Math.abs(neighborPos.getY() - startRoot.getY()) > 3) {
                                continue;
                            }

                            BlockState neighborState = level.getBlockState(neighborPos);
                            if (PlantClassifier.isSameColumnType(startState, neighborState)) {
                                BlockPos neighborRoot = PlantClassifier.getColumnCropRoot(level, neighborPos);
                                if (!visited.contains(neighborRoot)) {
                                    visited.add(neighborRoot);
                                    queue.add(neighborRoot);
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }

        // For all other crops: BFS directly on contiguous harvestable targets
        queue.add(actualCropPos);
        visited.add(actualCropPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();

            BlockState curState = level.getBlockState(current);
            if (targetFruit) {
                if (PlantClassifier.isFruitCrop(curState)) {
                    result.add(current);
                }
            } else if (targetBerry) {
                if (curState.getBlock() instanceof SweetBerryBushBlock && PlantClassifier.isMatureCrop(curState)) {
                    result.add(current);
                }
            } else if (targetVines) {
                if ((curState.getBlock() instanceof CaveVines || curState.is(Blocks.CAVE_VINES) || curState.is(Blocks.CAVE_VINES_PLANT)) && CaveVines.hasGlowBerries(curState)) {
                    result.add(current);
                }
            } else if (targetCocoa) {
                if (curState.getBlock() instanceof CocoaBlock && PlantClassifier.isMatureCrop(curState)) {
                    result.add(current);
                }
            } else if (targetNetherWart) {
                if (curState.getBlock() instanceof NetherWartBlock && PlantClassifier.isMatureCrop(curState)) {
                    result.add(current);
                }
            } else if (targetRice) {
                if (PlantClassifier.isRiceCrop(curState) && PlantClassifier.isMatureCrop(curState)) {
                    if (!result.contains(current)) {
                        result.add(current);
                    }
                }
            } else {
                // Farmland crops (Wheat, Carrot, Potato, Beetroot, Pitcher Crop, Torchflower, and modded CropBlocks)
                BlockPos harvestPos = current;
                BlockState harvestState = curState;
                if (PlantClassifier.isFarmland(harvestState)) {
                    harvestPos = current.above();
                    harvestState = level.getBlockState(harvestPos);
                }
                if (PlantClassifier.isFarmlandCrop(harvestState) && PlantClassifier.isMatureCrop(harvestState)) {
                    if (!result.contains(harvestPos)) {
                        result.add(harvestPos);
                    }
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        int maxY = targetCocoa ? 16 : 2;
                        if (Math.abs(next.getX() - actualCropPos.getX()) > radius
                                || Math.abs(next.getZ() - actualCropPos.getZ()) > radius
                                || Math.abs(next.getY() - actualCropPos.getY()) > maxY) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        boolean canTraverse = false;

                        if (targetFruit) {
                            // Only traverse connected fruits of the same type! Never traverse stems!
                            if (nextState.is(startState.getBlock())) {
                                canTraverse = true;
                            }
                        } else if (targetBerry) {
                            if (nextState.getBlock() instanceof SweetBerryBushBlock) {
                                canTraverse = true;
                            }
                        } else if (targetVines) {
                            if (nextState.getBlock() instanceof CaveVines || nextState.is(Blocks.CAVE_VINES) || nextState.is(Blocks.CAVE_VINES_PLANT)) {
                                canTraverse = true;
                            }
                        } else if (targetCocoa) {
                            if (nextState.getBlock() instanceof CocoaBlock || PlantClassifier.isJungleLog(nextState)) {
                                canTraverse = true;
                            }
                        } else if (targetNetherWart) {
                            if (nextState.getBlock() instanceof NetherWartBlock || PlantClassifier.isSoulSand(nextState)) {
                                canTraverse = true;
                            }
                        } else if (targetRice) {
                            if (PlantClassifier.isRiceCrop(nextState) || PlantClassifier.isRiceCrop(level.getBlockState(next.below())) || PlantClassifier.isRiceCrop(level.getBlockState(next.above()))) {
                                canTraverse = true;
                            }
                        } else {
                            // Farmland crops: traverse connected Farmland or crops planted on Farmland!
                            if (PlantClassifier.isFarmland(nextState)) {
                                BlockState aboveFarmland = level.getBlockState(next.above());
                                if (aboveFarmland.isAir() || PlantClassifier.isFarmlandCrop(aboveFarmland)) {
                                    canTraverse = true;
                                }
                            } else if (PlantClassifier.isFarmlandCrop(nextState)) {
                                if (PlantClassifier.isFarmland(level.getBlockState(next.below())) || PlantClassifier.isRiceCrop(nextState) || PlantClassifier.isRiceCrop(level.getBlockState(next.below()))) {
                                    canTraverse = true;
                                }
                            }
                        }

                        if (canTraverse) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        return result;
    }
}

