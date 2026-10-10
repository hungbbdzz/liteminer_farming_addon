package com.velorise.veinfarming.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.velorise.veinfarming.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StandaloneHighlightRenderer {

    private BlockPos lastClickedPos = null;
    private BlockState lastClickedState = null;
    private ItemStack lastHeldItem = ItemStack.EMPTY;
    private ItemStack lastOffItem = ItemStack.EMPTY;
    private long lastCacheTime = 0L;
    private Collection<BlockPos> cachedPreviewBlocks = Collections.emptyList();
    private float cachedR = 1.0f, cachedG = 0.82f, cachedB = 0.2f, cachedA = 0.8f;
    private boolean cachedIsPlanting = false;
    private boolean cachedIsHoe = false;
    private BlockState cachedGhostPlantState = null;
    private BlockState cachedGhostOffState = null;
    private List<BlockPos> cachedPlannedWaterHoles = Collections.emptyList();
    private boolean lastSmartPlantEnabled = true;

    private boolean lastWasAttacking = false;

    private void clearCache() {
        lastClickedPos = null;
        lastClickedState = null;
        lastHeldItem = ItemStack.EMPTY;
        lastOffItem = ItemStack.EMPTY;
        lastWasAttacking = false;
        lastCacheTime = 0L;
        cachedPreviewBlocks = Collections.emptyList();
        cachedIsHoe = false;
        cachedGhostPlantState = null;
        cachedGhostOffState = null;
        cachedPlannedWaterHoles = Collections.emptyList();
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // If LiteMiner or FTB Ultimine is loaded, let them handle their own highlight rendering!
        if (LiteMinerCompat.isLiteMinerLoaded() || FTBUltimineCompat.isFTBUltimineLoaded()) {
            return;
        }

        if (!FarmingConfig.STANDALONE_PREVIEW.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            clearCache();
            return;
        }

        boolean currentSmartPlant = ModKeyMappings.isSmartPlantEnabled();
        if (currentSmartPlant != lastSmartPlantEnabled) {
            clearCache();
            lastSmartPlantEnabled = currentSmartPlant;
        }

        // Check activation condition: FTB Ultimine keybind, or dedicated key / Shift fallback in standalone mode
        boolean active = false;
        if (FTBUltimineCompat.isFTBUltimineLoaded()) {
            active = FTBUltimineCompat.isUltimineClientActive();
        } else {
            active = ModKeyMappings.isKeyActive();
        }

        if (!active) {
            clearCache();
            return;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            clearCache();
            return;
        }

        BlockPos clickedPos = blockHit.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack heldItem = player.getMainHandItem();
        ItemStack offItem = player.getOffhandItem();
        InteractionHand hand = InteractionHand.MAIN_HAND;

        // Check if cached search result is still valid (throttle search to at most once per 250ms when looking at same block)
        long now = System.currentTimeMillis();
        boolean isAttacking = mc.options.keyAttack.isDown();
        boolean cacheValid = lastClickedPos != null
                && lastClickedPos.equals(clickedPos)
                && clickedState.equals(lastClickedState)
                && ItemStack.matches(heldItem, lastHeldItem)
                && ItemStack.matches(offItem, lastOffItem)
                && (isAttacking == lastWasAttacking)
                && (now - lastCacheTime < 250);

        Collection<BlockPos> previewBlocks;
        List<BlockPos> plannedWaterHoles;
        float r, g, b, a;
        boolean isPlanting;
        boolean isHoe;
        BlockState ghostPlantState;
        BlockState ghostOffState;

        if (cacheValid) {
            previewBlocks = cachedPreviewBlocks;
            plannedWaterHoles = cachedPlannedWaterHoles;
            r = cachedR; g = cachedG; b = cachedB; a = cachedA;
            isPlanting = cachedIsPlanting;
            isHoe = cachedIsHoe;
            ghostPlantState = cachedGhostPlantState;
            ghostOffState = cachedGhostOffState;
        } else {
            previewBlocks = Collections.emptyList();
            plannedWaterHoles = Collections.emptyList();
            r = 1.0f; g = 0.82f; b = 0.2f; a = 0.8f; // Default golden harvest
            isPlanting = false;
            isHoe = false;
            ghostPlantState = null;
            ghostOffState = null;

            BlockPos targetCrop = clickedPos;
            if (FarmingManager.isRiceCrop(clickedState)) {
                if (FarmingManager.isRiceCrop(level.getBlockState(clickedPos.above()))) {
                    targetCrop = clickedPos.above();
                }
            } else if (!FarmingManager.isCrop(clickedState) && !FarmingManager.isColumnCrop(clickedState) && !FarmingManager.isFruitCrop(clickedState) && !FarmingManager.isHarvestablePlant(clickedState)) {
                targetCrop = clickedPos.above();
            }
            BlockState targetCropState = level.getBlockState(targetCrop);

            boolean isHarvestable = FarmingManager.isMatureCrop(targetCropState)
                    || FarmingManager.isFruitCrop(targetCropState)
                    || FarmingManager.isFruitCrop(clickedState)
                    || FarmingManager.isColumnCrop(targetCropState)
                    || FarmingManager.isChorus(targetCropState)
                    || FarmingManager.isChorus(clickedState)
                    || (FarmingManager.isRiceCrop(targetCropState) && FarmingManager.isMatureCrop(targetCropState));

            boolean isPlant = FarmingManager.isHarvestablePlant(targetCropState)
                    || FarmingManager.isHarvestablePlant(clickedState)
                    || FarmingManager.isRiceCrop(targetCropState)
                    || FarmingManager.isRiceCrop(clickedState);

            boolean isTool = FarmingManager.isDestructionTool(heldItem);

            if (isPlant && (isAttacking || isTool || !isHarvestable)) {
                // DESTROY PREVIEW (Left-click / Breaking Mode) - Vivid Crimson Red
                previewBlocks = FarmingManager.findDestroyTargets(level, targetCrop, targetCropState);
                r = 1.0f; g = 0.22f; b = 0.22f; a = 0.85f;
            } else if (isHarvestable) {
                // HARVEST PREVIEW (Right-click Harvest Mode) - Golden Autumn Harvest
                previewBlocks = FarmingManager.fallbackHarvestSearch(level, targetCrop);
                r = 1.0f; g = 0.82f; b = 0.2f; a = 0.8f;
            } else if (clickedState.is(Blocks.COMPOSTER) && FarmingConfig.BATCH_COMPOSTER.get() && FarmingManager.isCompostable(heldItem)) {
                previewBlocks = Collections.singletonList(clickedPos);
                r = 0.45f; g = 0.75f; b = 0.25f; // Compost Green
            } else if (FarmingEventHandler.isHoe(heldItem) && FarmingManager.isTillable(level, player, hand, clickedPos)) {
                previewBlocks = FarmingManager.fallbackHoeSearch(player, hand, clickedPos);
                r = 0.65f; g = 0.45f; b = 0.25f; // Earth Farmland Brown
                isHoe = true;
                if (PlantClassifier.isWaterContainer(offItem) && FarmingConfig.SMART_WATER_BUCKET_IRRIGATION.get() && previewBlocks.size() >= 2) {
                    int availableWater = FarmingManager.getAvailableWaterCount(player);
                    plannedWaterHoles = FarmingManager.calculateOptimalWaterHoles(level, previewBlocks, clickedPos, availableWater);
                }
            } else if (FarmingManager.isPlantableSeed(heldItem) && (
                    FarmingManager.isCocoaBean(heldItem)
                        ? (FarmingManager.isJungleLog(clickedState) || clickedState.getBlock() instanceof CocoaBlock)
                        : ((FarmingManager.isValidSoilForSeed(heldItem, clickedState, level, clickedPos) && (level.getBlockState(clickedPos.above()).isAir() || level.getBlockState(clickedPos.above()).canBeReplaced()))
                           || (FarmingManager.isValidSoilForSeed(heldItem, level.getBlockState(clickedPos.below()), level, clickedPos.below()) && (clickedState.isAir() || clickedState.canBeReplaced())))
            )) {
                previewBlocks = FarmingManager.fallbackPlantingSearch(level, clickedPos, heldItem, ModKeyMappings.isSmartPlantEnabled());
                r = 0.4f; g = 0.85f; b = 0.3f; // Sprout Green
                isPlanting = true;
                if (FarmingConfig.GHOST_PLANT_PREVIEW.get()) {
                    ghostPlantState = getPlantedBlockState(heldItem);
                    if (ModKeyMappings.isSmartPlantEnabled()
                            && FarmingConfig.SMART_INTERCROPPING.get()
                            && FarmingManager.isIntercroppableCrop(heldItem)
                            && FarmingManager.isIntercroppableCrop(offItem)
                            && !heldItem.is(offItem.getItem())) {
                        ghostOffState = getPlantedBlockState(offItem);
                    }
                }
            } else if (heldItem.is(Items.BONE_MEAL)) {
                if (FarmingManager.isCrop(clickedState) || FarmingManager.isFarmland(clickedState)
                        || FarmingManager.isBonemealCrop(clickedState)
                        || FarmingManager.isBonemealCrop(level.getBlockState(clickedPos.above()))
                        || FarmingManager.isSmallFlower(clickedState)) {
                    previewBlocks = FarmingManager.fallbackCropSearch(level, clickedPos);
                    if (FarmingManager.isSmallFlower(clickedState)) {
                        r = 0.95f; g = 0.55f; b = 0.85f; // Flora Blossom Pink
                    } else {
                        r = 0.3f; g = 0.9f; b = 0.4f; // Emerald Jade
                    }
                }
            }

            // Update cache
            lastClickedPos = clickedPos;
            lastClickedState = clickedState;
            lastHeldItem = heldItem.copy();
            lastOffItem = offItem.copy();
            lastWasAttacking = isAttacking;
            lastCacheTime = now;
            cachedPreviewBlocks = previewBlocks;
            cachedPlannedWaterHoles = plannedWaterHoles;
            cachedR = r; cachedG = g; cachedB = b; cachedA = a;
            cachedIsPlanting = isPlanting;
            cachedIsHoe = isHoe;
            cachedGhostPlantState = ghostPlantState;
            cachedGhostOffState = ghostOffState;
        }

        if (previewBlocks == null || previewBlocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();

        Map<BlockPos, Block> plannedIntercrop = null;
        if (isPlanting && ghostOffState != null && ModKeyMappings.isSmartPlantEnabled()) {
            BlockPos originSoilPos = FarmingManager.isValidSoilForSeed(heldItem, clickedState, level, clickedPos) ? clickedPos : clickedPos.below();
            Block mainCropBlock = FarmingManager.getCropBlock(heldItem);
            Block offCropBlock = FarmingManager.getCropBlock(offItem);
            boolean evenIsMain = FarmingManager.determineIntercropPhase(level, originSoilPos, player.getDirection(), mainCropBlock, offCropBlock);
            boolean alternateOnX = (player.getDirection().getAxis() == Direction.Axis.Z);

            plannedIntercrop = new HashMap<>();
            for (BlockPos p : previewBlocks) {
                int rowCoord = alternateOnX ? (p.getX() - originSoilPos.getX()) : (p.getZ() - originSoilPos.getZ());
                boolean isMainRow = (Math.floorMod(rowCoord, 2) == 0) ? evenIsMain : !evenIsMain;
                plannedIntercrop.put(p.above(), isMainRow ? mainCropBlock : offCropBlock);
            }
        }

        float lineAlpha = ((isPlanting && ghostPlantState != null) || (isHoe && FarmingConfig.GHOST_FARMLAND_PREVIEW.get())) ? 0.35f : a;
        boolean isCocoaPlanting = isPlanting && FarmingManager.isCocoaBean(heldItem);
        for (BlockPos pos : previewBlocks) {
            BlockPos renderPos = (isPlanting && !isCocoaPlanting) ? pos.above() : pos;
            net.minecraft.world.phys.AABB aabb;
            if (isPlanting) {
                aabb = new net.minecraft.world.phys.AABB(renderPos);
            } else {
                BlockState state = level.getBlockState(renderPos);
                VoxelShape shape = state.getShape(level, renderPos);
                aabb = shape.isEmpty()
                        ? new net.minecraft.world.phys.AABB(renderPos)
                        : shape.bounds().move(renderPos);
            }
            net.minecraft.world.phys.AABB camRelative = aabb.move(-camPos.x, -camPos.y, -camPos.z);

            float boxR = r, boxG = g, boxB = b;
            float currentAlpha = lineAlpha;
            if (isHoe && FarmingConfig.SMART_IRRIGATION_PREVIEW.get()) {
                if (plannedWaterHoles.contains(pos)) {
                    boxR = 0.15f; boxG = 0.65f; boxB = 1.0f; // Azure Water Blue
                    currentAlpha = 0.85f;
                } else {
                    boolean hasWater = FarmingManager.isNearWater(level, pos) || FarmingManager.isHydratedByHoles(pos, plannedWaterHoles);
                    if (hasWater) {
                        boxR = 0.45f; boxG = 0.65f; boxB = 0.35f; // Hydrated Farmland Green-Brown
                        currentAlpha = 0.40f;
                    } else {
                        boxR = 0.95f; boxG = 0.45f; boxB = 0.15f; // Warning Dry Amber/Orange
                        currentAlpha = 0.65f;
                    }
                }
            } else if (isPlanting && plannedIntercrop != null && FarmingConfig.GROWTH_PENALTY_WARNING.get()) {
                Block plannedCrop = plannedIntercrop.get(renderPos);
                boolean hasPenalty = FarmingManager.hasGrowthPenalty(level, renderPos, plannedCrop, plannedIntercrop);
                if (hasPenalty) {
                    boxR = 1.0f; boxG = 0.28f; boxB = 0.2f; // Warning Coral Crimson
                    currentAlpha = 0.85f;
                } else {
                    boxR = 0.35f; boxG = 0.85f; boxB = 0.3f; // Full 200% growth speed Sprout Green
                    currentAlpha = 0.35f;
                }
            }

            LevelRenderer.renderLineBox(poseStack, consumer, camRelative, boxR, boxG, boxB, currentAlpha);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());

        // Render faded 3D ghost block preview of Farmland (and planned Water Holes) when holding a hoe
        if (isHoe && FarmingConfig.GHOST_FARMLAND_PREVIEW.get() && !previewBlocks.isEmpty()) {
            VertexConsumer translucentConsumer = bufferSource.getBuffer(RenderType.translucent());
            for (BlockPos pos : previewBlocks) {
                BlockState targetState = level.getBlockState(pos);
                VoxelShape shape = targetState.getShape(level, pos);
                double blockTop = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);

                if (plannedWaterHoles.contains(pos)) {
                    // Render translucent water hole preview (using Light Blue Stained Glass model for fluid rendering compatibility)
                    double yOffset = (blockTop - 1.0) + 0.003;
                    BlockState waterGhost = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
                    FadedVertexConsumer fadedConsumer = new FadedVertexConsumer(translucentConsumer, 0.65f);
                    MultiBufferSource fadedBuffer = type -> fadedConsumer;

                    poseStack.pushPose();
                    poseStack.translate(pos.getX() - camPos.x + 0.5, pos.getY() - camPos.y + yOffset, pos.getZ() - camPos.z + 0.5);
                    poseStack.scale(1.002f, 1.0f, 1.002f);
                    poseStack.translate(-0.5, 0.0, -0.5);
                    int light = LevelRenderer.getLightColor(level, pos.above());
                    mc.getBlockRenderer().renderSingleBlock(
                            waterGhost,
                            poseStack,
                            fadedBuffer,
                            light,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                            RenderType.translucent()
                    );
                    poseStack.popPose();
                } else {
                    // Farmland model is 0.9375 (15/16) tall. Elevate so top face sits 0.003 above the block surface (eliminating grass occlusion and dirt path z-fighting)
                    double yOffset = (blockTop - 0.9375) + 0.003;

                    boolean hasWater = FarmingManager.isNearWater(level, pos) || FarmingManager.isHydratedByHoles(pos, plannedWaterHoles);
                    BlockState farmlandState = Blocks.FARMLAND.defaultBlockState()
                            .setValue(FarmBlock.MOISTURE, hasWater ? 7 : 0);
                    float alpha = hasWater ? 0.70f : 0.50f;
                    FadedVertexConsumer fadedConsumer = new FadedVertexConsumer(translucentConsumer, alpha);
                    MultiBufferSource fadedBuffer = type -> fadedConsumer;

                    poseStack.pushPose();
                    poseStack.translate(pos.getX() - camPos.x + 0.5, pos.getY() - camPos.y + yOffset, pos.getZ() - camPos.z + 0.5);
                    poseStack.scale(1.002f, 1.0f, 1.002f);
                    poseStack.translate(-0.5, 0.0, -0.5);
                    int light = LevelRenderer.getLightColor(level, pos.above());
                    mc.getBlockRenderer().renderSingleBlock(
                            farmlandState,
                            poseStack,
                            fadedBuffer,
                            light,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                            RenderType.translucent()
                    );
                    poseStack.popPose();
                }
            }
            bufferSource.endBatch(RenderType.translucent());
        }

        // Render faded ghost block preview of the exact plant/sapling
        if (ghostPlantState != null && !previewBlocks.isEmpty()) {
            VertexConsumer translucentConsumer = bufferSource.getBuffer(RenderType.translucent());
            FadedVertexConsumer fadedConsumer = new FadedVertexConsumer(translucentConsumer, 0.45f);
            MultiBufferSource fadedBuffer = type -> fadedConsumer;
            BlockPos originSoilPos = FarmingManager.isValidSoilForSeed(heldItem, clickedState, level, clickedPos) ? clickedPos : clickedPos.below();
            Block mainCropBlock = FarmingManager.getCropBlock(heldItem);
            Block offCropBlock = FarmingManager.getCropBlock(offItem);

            boolean evenIsMain = true;
            if (ghostOffState != null) {
                evenIsMain = FarmingManager.determineIntercropPhase(level, originSoilPos, player.getDirection(), mainCropBlock, offCropBlock);
            }
            boolean alternateOnX = (player.getDirection().getAxis() == Direction.Axis.Z);
            for (BlockPos pos : previewBlocks) {
                BlockState stateToRender = ghostPlantState;
                if (isCocoaPlanting) {
                    Direction facing = Direction.NORTH;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (FarmingManager.isJungleLog(level.getBlockState(pos.relative(d)))) {
                            facing = d;
                            break;
                        }
                    }
                    stateToRender = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, facing).setValue(CocoaBlock.AGE, 0);
                } else if (ghostOffState != null) {
                    int rowCoord = alternateOnX ? (pos.getX() - originSoilPos.getX()) : (pos.getZ() - originSoilPos.getZ());
                    boolean isMainRow = (Math.floorMod(rowCoord, 2) == 0) ? evenIsMain : !evenIsMain;
                    stateToRender = isMainRow ? ghostPlantState : ghostOffState;
                }
                if (stateToRender == null) continue;

                BlockPos plantPos = isCocoaPlanting ? pos : pos.above();
                poseStack.pushPose();
                poseStack.translate(plantPos.getX() - camPos.x, plantPos.getY() - camPos.y, plantPos.getZ() - camPos.z);
                int light = LevelRenderer.getLightColor(level, plantPos);
                mc.getBlockRenderer().renderSingleBlock(
                        stateToRender,
                        poseStack,
                        fadedBuffer,
                        light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                        RenderType.translucent()
                );
                poseStack.popPose();
            }
            bufferSource.endBatch(RenderType.translucent());
        }

        // Render translucent 3D ghost fruits (Melon / Pumpkin) on the reserved fruit spots
        if (isPlanting && FarmingManager.isFruitSeed(heldItem) && !previewBlocks.isEmpty()) {
            Block fruitBlock = FarmingManager.getFruitForSeed(heldItem);
            if (fruitBlock != null) {
                BlockState fruitState = fruitBlock.defaultBlockState();
                Set<BlockPos> fruitSpots = new HashSet<>();
                Set<BlockPos> stemSoilSet = new HashSet<>(previewBlocks);

                for (BlockPos soil : previewBlocks) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos neighborSoil = soil.relative(dir);
                        if (!stemSoilSet.contains(neighborSoil)) {
                            BlockState neighborSoilState = level.getBlockState(neighborSoil);
                            if (FarmingManager.isFarmland(neighborSoilState) || neighborSoilState.is(net.minecraft.tags.BlockTags.DIRT)) {
                                BlockPos fruitPos = neighborSoil.above();
                                BlockState fruitPosState = level.getBlockState(fruitPos);
                                if (fruitPosState.isAir() || fruitPosState.canBeReplaced()) {
                                    fruitSpots.add(fruitPos);
                                }
                            }
                        }
                    }
                }

                if (!fruitSpots.isEmpty()) {
                    VertexConsumer translucentConsumer = bufferSource.getBuffer(RenderType.translucent());
                    FadedVertexConsumer fadedFruitConsumer = new FadedVertexConsumer(translucentConsumer, 0.35f);
                    MultiBufferSource fadedFruitBuffer = type -> fadedFruitConsumer;

                    for (BlockPos fruitPos : fruitSpots) {
                        poseStack.pushPose();
                        poseStack.translate(fruitPos.getX() - camPos.x, fruitPos.getY() - camPos.y, fruitPos.getZ() - camPos.z);
                        int light = LevelRenderer.getLightColor(level, fruitPos);
                        mc.getBlockRenderer().renderSingleBlock(
                                fruitState,
                                poseStack,
                                fadedFruitBuffer,
                                light,
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                                net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                                RenderType.translucent()
                        );
                        poseStack.popPose();
                    }
                    bufferSource.endBatch(RenderType.translucent());
                }
            }
        }
    }

    public static BlockState getPlantedBlockState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.BAMBOO)) {
            return Blocks.BAMBOO_SAPLING.defaultBlockState();
        }
        if (stack.is(Items.SWEET_BERRIES)) {
            return Blocks.SWEET_BERRY_BUSH.defaultBlockState();
        }
        if (stack.is(Items.NETHER_WART)) {
            return Blocks.NETHER_WART.defaultBlockState();
        }
        if (stack.is(Items.COCOA_BEANS)) {
            return Blocks.COCOA.defaultBlockState();
        }
        if (stack.is(Items.CHORUS_FLOWER)) {
            return Blocks.CHORUS_FLOWER.defaultBlockState();
        }
        if (stack.is(Items.RED_MUSHROOM)) {
            return Blocks.RED_MUSHROOM.defaultBlockState();
        }
        if (stack.is(Items.BROWN_MUSHROOM)) {
            return Blocks.BROWN_MUSHROOM.defaultBlockState();
        }
        if (stack.is(Items.CRIMSON_FUNGUS)) {
            return Blocks.CRIMSON_FUNGUS.defaultBlockState();
        }
        if (stack.is(Items.WARPED_FUNGUS)) {
            return Blocks.WARPED_FUNGUS.defaultBlockState();
        }
        if (stack.getItem() instanceof BlockItem bi) {
            Block b = bi.getBlock();
            if (b == Blocks.BAMBOO) {
                return Blocks.BAMBOO_SAPLING.defaultBlockState();
            }
            return b.defaultBlockState();
        }
        return null;
    }

    private static class FadedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        public FadedVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { return delegate.addVertex(x, y, z); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return delegate.setColor(r, g, b, (int) (a * alpha)); }
        @Override public VertexConsumer setUv(float u, float v) { return delegate.setUv(u, v); }
        @Override public VertexConsumer setUv1(int u, int v) { return delegate.setUv1(u, v); }
        @Override public VertexConsumer setUv2(int u, int v) { return delegate.setUv2(u, v); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return delegate.setNormal(x, y, z); }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad, float r, float g, float b, float a, int light, int overlay, boolean readExistingColor) {
            delegate.putBulkData(pose, quad, r, g, b, a * alpha, light, overlay, readExistingColor);
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad, float[] baseColor, float r, float g, float b, float a, int[] lightmap, int overlay, boolean readExistingColor) {
            delegate.putBulkData(pose, quad, baseColor, r, g, b, a * alpha, lightmap, overlay, readExistingColor);
        }
    }
}
