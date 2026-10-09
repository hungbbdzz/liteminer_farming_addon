package com.velorise.liteminerfarming.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.velorise.liteminerfarming.*;
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
import java.util.Map;

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

    private void clearCache() {
        lastClickedPos = null;
        lastClickedState = null;
        lastHeldItem = ItemStack.EMPTY;
        lastOffItem = ItemStack.EMPTY;
        lastCacheTime = 0L;
        cachedPreviewBlocks = Collections.emptyList();
        cachedIsHoe = false;
        cachedGhostPlantState = null;
        cachedGhostOffState = null;
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

        // Check activation condition: FTB Ultimine keybind, or Shift fallback in standalone mode
        boolean active = false;
        if (FTBUltimineCompat.isFTBUltimineLoaded()) {
            active = FTBUltimineCompat.isUltimineClientActive();
        } else {
            active = player.isShiftKeyDown();
            if (!active && !FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
                active = true;
            }
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
        boolean cacheValid = lastClickedPos != null
                && lastClickedPos.equals(clickedPos)
                && clickedState.equals(lastClickedState)
                && ItemStack.matches(heldItem, lastHeldItem)
                && ItemStack.matches(offItem, lastOffItem)
                && (now - lastCacheTime < 250);

        Collection<BlockPos> previewBlocks;
        float r, g, b, a;
        boolean isPlanting;
        boolean isHoe;
        BlockState ghostPlantState;
        BlockState ghostOffState;

        if (cacheValid) {
            previewBlocks = cachedPreviewBlocks;
            r = cachedR; g = cachedG; b = cachedB; a = cachedA;
            isPlanting = cachedIsPlanting;
            isHoe = cachedIsHoe;
            ghostPlantState = cachedGhostPlantState;
            ghostOffState = cachedGhostOffState;
        } else {
            previewBlocks = Collections.emptyList();
            r = 1.0f; g = 0.82f; b = 0.2f; a = 0.8f; // Default golden harvest
            isPlanting = false;
            isHoe = false;
            ghostPlantState = null;
            ghostOffState = null;

            BlockPos targetCrop = (FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState) || FarmingManager.isFruitCrop(clickedState)) 
                    ? clickedPos 
                    : clickedPos.above();
            BlockState targetCropState = level.getBlockState(targetCrop);

            boolean isHarvestable = FarmingManager.isMatureCrop(targetCropState)
                    || FarmingManager.isFruitCrop(targetCropState)
                    || FarmingManager.isFruitCrop(clickedState)
                    || FarmingManager.isColumnCrop(targetCropState);

            if (isHarvestable) {
                // Harvest mature crops with ANY item or bare hand!
                previewBlocks = FarmingManager.fallbackHarvestSearch(level, targetCrop);
                r = 1.0f; g = 0.82f; b = 0.2f; // Golden Autumn Harvest
            } else if (clickedState.is(Blocks.COMPOSTER) && FarmingConfig.BATCH_COMPOSTER.get() && FarmingManager.isCompostable(heldItem)) {
                previewBlocks = Collections.singletonList(clickedPos);
                r = 0.45f; g = 0.75f; b = 0.25f; // Compost Green
            } else if (FarmingEventHandler.isHoe(heldItem) && FarmingManager.isTillable(level, player, hand, clickedPos)) {
                previewBlocks = FarmingManager.fallbackHoeSearch(player, hand, clickedPos);
                r = 0.65f; g = 0.45f; b = 0.25f; // Earth Farmland Brown
                isHoe = true;
            } else if (FarmingManager.isPlantableSeed(heldItem) && (
                    (FarmingManager.isValidSoilForSeed(heldItem, clickedState, level, clickedPos) && (level.getBlockState(clickedPos.above()).isAir() || level.getBlockState(clickedPos.above()).canBeReplaced()))
                    || (FarmingManager.isValidSoilForSeed(heldItem, level.getBlockState(clickedPos.below()), level, clickedPos.below()) && (clickedState.isAir() || clickedState.canBeReplaced()))
            )) {
                previewBlocks = FarmingManager.fallbackPlantingSearch(level, clickedPos, heldItem);
                r = 0.4f; g = 0.85f; b = 0.3f; // Sprout Green
                isPlanting = true;
                if (FarmingConfig.GHOST_PLANT_PREVIEW.get()) {
                    ghostPlantState = getPlantedBlockState(heldItem);
                    if (FarmingConfig.SMART_INTERCROPPING.get()
                            && FarmingManager.isPlantableSeed(offItem)
                            && !heldItem.is(offItem.getItem())
                            && !FarmingManager.isSapling(heldItem)
                            && !FarmingManager.isSapling(offItem)) {
                        ghostOffState = getPlantedBlockState(offItem);
                    }
                }
            } else if (heldItem.is(Items.BONE_MEAL)) {
                if (FarmingManager.isCrop(clickedState) || FarmingManager.isFarmland(clickedState)
                        || FarmingManager.isBonemealCrop(clickedState)
                        || FarmingManager.isBonemealCrop(level.getBlockState(clickedPos.above()))) {
                    previewBlocks = FarmingManager.fallbackCropSearch(level, clickedPos);
                    r = 0.3f; g = 0.9f; b = 0.4f; // Emerald Jade
                }
            }

            // Update cache
            lastClickedPos = clickedPos;
            lastClickedState = clickedState;
            lastHeldItem = heldItem.copy();
            lastOffItem = offItem.copy();
            lastCacheTime = now;
            cachedPreviewBlocks = previewBlocks;
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
        if (isPlanting && ghostOffState != null) {
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

        float lineAlpha = (isPlanting && ghostPlantState != null) ? 0.35f : a;
        for (BlockPos pos : previewBlocks) {
            BlockPos renderPos = isPlanting ? pos.above() : pos;
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
                boolean hasWater = FarmingManager.isNearWater(level, pos);
                if (hasWater) {
                    boxR = 0.45f; boxG = 0.65f; boxB = 0.35f; // Hydrated Farmland Green-Brown
                } else {
                    boxR = 0.95f; boxG = 0.45f; boxB = 0.15f; // Warning Dry Amber/Orange
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
                if (ghostOffState != null) {
                    int rowCoord = alternateOnX ? (pos.getX() - originSoilPos.getX()) : (pos.getZ() - originSoilPos.getZ());
                    boolean isMainRow = (Math.floorMod(rowCoord, 2) == 0) ? evenIsMain : !evenIsMain;
                    stateToRender = isMainRow ? ghostPlantState : ghostOffState;
                }
                if (stateToRender == null) continue;

                BlockPos plantPos = pos.above();
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
