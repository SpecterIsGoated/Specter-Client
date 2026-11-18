package com.jfant.specter.core.utils;

import com.jfant.specter.core.utils.render.color.Color;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector.ParticleGroupRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class OutlineRenderCommandQueue extends SubmitNodeStorage {
   private int color;
   private int[] tints;

   public void setColor(Color color) {
      this.color = color.getPacked();
   }

   @Override
   public SubmitNodeCollection order(int i) {
      return (SubmitNodeCollection)this.submitsPerOrder.computeIfAbsent(i, order -> new OutlineRenderCommandQueue.OutlineBatchingRenderCommandQueue(this));
   }

   private class OutlineBatchingRenderCommandQueue extends SubmitNodeCollection {
      public OutlineBatchingRenderCommandQueue(SubmitNodeStorage orderedQueueImpl) {
         super(orderedQueueImpl);
      }

      @Override
      public void submitShadow(PoseStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
      }

      @Override
      public void submitNameTag(
         PoseStack matrices,
         @Nullable Vec3 nameLabelPos,
         int y,
         Component label,
         boolean notSneaking,
         int light,
         double squaredDistanceToCamera,
         CameraRenderState cameraState
      ) {
      }

      @Override
      public void submitText(
         PoseStack matrices,
         float x,
         float y,
         FormattedCharSequence text,
         boolean dropShadow,
         DisplayMode layerType,
         int light,
         int color,
         int backgroundColor,
         int outlineColor
      ) {
      }

      @Override
      public void submitFlame(PoseStack matrices, EntityRenderState renderState, Quaternionf rotation) {
      }

      @Override
      public void submitLeash(PoseStack matrices, LeashState leashData) {
      }

      @Override
      public <S> void submitModel(
         Model<? super S> model,
         S state,
         PoseStack matrices,
         RenderType renderLayer,
         int light,
         int overlay,
         int tintedColor,
         @Nullable TextureAtlasSprite sprite,
         int outlineColor,
         @Nullable CrumblingOverlay crumblingOverlay
      ) {
         super.submitModel(model, state, matrices, renderLayer, light, overlay, OutlineRenderCommandQueue.this.color, sprite, 0, crumblingOverlay);
      }

      @Override
      public void submitModelPart(
         ModelPart part,
         PoseStack matrices,
         RenderType renderLayer,
         int light,
         int overlay,
         @Nullable TextureAtlasSprite sprite,
         boolean sheeted,
         boolean hasGlint,
         int tintedColor,
         @Nullable CrumblingOverlay crumblingOverlay,
         int i
      ) {
         super.submitModelPart(
            part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, OutlineRenderCommandQueue.this.color, crumblingOverlay, i
         );
      }

      @Override
      public void submitBlock(PoseStack matrices, BlockState state, int light, int overlay, int outlineColor) {
      }

      @Override
      public void submitMovingBlock(PoseStack matrices, MovingBlockRenderState state) {
      }

      @Override
      public void submitBlockModel(
         PoseStack matrices, RenderType renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
      ) {
         r = Color.toRGBAR(OutlineRenderCommandQueue.this.color) / 255.0F;
         g = Color.toRGBAG(OutlineRenderCommandQueue.this.color) / 255.0F;
         b = Color.toRGBAB(OutlineRenderCommandQueue.this.color) / 255.0F;
         super.submitBlockModel(matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
      }

      @Override
      public void submitItem(
         PoseStack matrices,
         ItemDisplayContext displayContext,
         int light,
         int overlay,
         int outlineColors,
         int[] tintLayers,
         List<BakedQuad> quads,
         RenderType renderLayer,
         FoilType glintType
      ) {
         if (OutlineRenderCommandQueue.this.tints == null || OutlineRenderCommandQueue.this.tints[0] != OutlineRenderCommandQueue.this.color) {
            OutlineRenderCommandQueue.this.tints = new int[]{
               OutlineRenderCommandQueue.this.color,
               OutlineRenderCommandQueue.this.color,
               OutlineRenderCommandQueue.this.color,
               OutlineRenderCommandQueue.this.color
            };
         }

         super.submitItem(matrices, displayContext, light, overlay, outlineColors, OutlineRenderCommandQueue.this.tints, quads, renderLayer, glintType);
      }

      @Override
      public void submitCustomGeometry(PoseStack matrices, RenderType renderLayer, CustomGeometryRenderer customRenderer) {
      }

      @Override
      public void submitParticleGroup(ParticleGroupRenderer customRenderer) {
      }
   }
}
