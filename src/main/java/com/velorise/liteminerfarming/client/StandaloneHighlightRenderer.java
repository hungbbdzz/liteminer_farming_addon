package com.velorise.liteminerfarming.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.velorise.liteminerfarming.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collection;
import java.util.Collections;

public class StandaloneHighlightRenderer {

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // If LiteMiner is loaded, let LiteMiner handle its own highlight rendering!
        if (LiteMinerCompat.isLiteMinerLoaded()) {
            return;
        }

        if (!FarmingConfig.STANDALONE_PREVIEW.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
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
            return;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos clickedPos = blockHit.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack heldItem = player.getMainHandItem();
        InteractionHand hand = InteractionHand.MAIN_HAND;

        // Determine preview blocks and action color
        Collection<BlockPos> previewBlocks = Collections.emptyList();
        float r = 1.0f, g = 0.82f, b = 0.2f, a = 0.8f; // Default golden harvest

        if (FarmingManager.isCrop(clickedState) || FarmingManager.isFarmland(clickedState)
                || FarmingManager.isCrop(level.getBlockState(clickedPos.above()))) {
            if (heldItem.is(Items.BONE_MEAL)) {
                previewBlocks = FarmingManager.fallbackCropSearch(level, clickedPos);
                r = 0.3f; g = 0.9f; b = 0.4f; // Emerald Jade
            } else if (FarmingManager.isFarmland(clickedState) && level.getBlockState(clickedPos.above()).isAir() && FarmingManager.isPlantableCrop(heldItem)) {
                previewBlocks = FarmingManager.fallbackPlantingSearch(level, clickedPos);
                r = 0.4f; g = 0.85f; b = 0.3f; // Sprout Green
            } else if (!heldItem.is(Items.BONE_MEAL)) {
                BlockPos targetCrop = (FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState)) ? clickedPos : clickedPos.above();
                previewBlocks = FarmingManager.fallbackHarvestSearch(level, targetCrop);
                r = 1.0f; g = 0.82f; b = 0.2f; // Golden Autumn Harvest
            }
        } else if (FarmingEventHandler.isHoe(heldItem) && FarmingManager.isTillable(level, player, hand, clickedPos)) {
            previewBlocks = FarmingManager.fallbackHoeSearch(player, hand, clickedPos);
            r = 0.65f; g = 0.45f; b = 0.25f; // Earth Farmland Brown
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

        for (BlockPos pos : previewBlocks) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);
            net.minecraft.world.phys.AABB aabb = shape.isEmpty()
                    ? new net.minecraft.world.phys.AABB(pos)
                    : shape.bounds().move(pos);
            net.minecraft.world.phys.AABB camRelative = aabb.move(-camPos.x, -camPos.y, -camPos.z);
            LevelRenderer.renderLineBox(poseStack, consumer, camRelative, r, g, b, a);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }
}
