package com.jfant.specter.core.utils.render;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.mixin.RenderLayerAccessor;
import com.jfant.specter.core.renderer.Renderer3D;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.utils.render.color.Color;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class WireframeEntityRenderer {
   private static final PoseStack matrices = new PoseStack();
   private static Renderer3D renderer;
   private static final SubmitNodeStorage renderCommandQueue = new SubmitNodeStorage();
   private static final FeatureRenderDispatcher renderDispatcher = new FeatureRenderDispatcher(
      renderCommandQueue,
      SpecterCore.mc.getBlockRenderer(),
      WireframeEntityRenderer.MyVertexConsumerProvider.INSTANCE,
      SpecterCore.mc.getAtlasManager(),
      NoopOutlineVertexConsumerProvider.INSTANCE,
      NoopImmediateVertexConsumerProvider.INSTANCE,
      SpecterCore.mc.font
   );
   private static Color sideColor;
   private static Color lineColor;
   private static ShapeMode shapeMode;
   private static double offsetX;
   private static double offsetY;
   private static double offsetZ;

   private WireframeEntityRenderer() {
   }

   public static void render(Render3DEvent event, Entity entity, double scale, Color sideColor, Color lineColor, ShapeMode shapeMode) {
      WireframeEntityRenderer.renderer = event.renderer;
      WireframeEntityRenderer.sideColor = sideColor;
      WireframeEntityRenderer.lineColor = lineColor;
      WireframeEntityRenderer.shapeMode = shapeMode;
      float tickDelta = SpecterCore.mc.level.tickRateManager().isFrozen() ? 1.0F : event.tickDelta;
      offsetX = Mth.lerp((double)tickDelta, entity.xOld, entity.getX());
      offsetY = Mth.lerp((double)tickDelta, entity.yOld, entity.getY());
      offsetZ = Mth.lerp((double)tickDelta, entity.zOld, entity.getZ());
      EntityRenderer<Entity, EntityRenderState> renderer = (EntityRenderer<Entity, EntityRenderState>)SpecterCore.mc
         .getEntityRenderDispatcher()
         .getRenderer(entity);
      EntityRenderState state = renderer.createRenderState(entity, tickDelta);
      Vec3 entityOffset = renderer.getRenderOffset(state);
      offsetX = offsetX + entityOffset.x;
      offsetY = offsetY + entityOffset.y;
      offsetZ = offsetZ + entityOffset.z;
      matrices.pushPose();
      matrices.scale((float)scale, (float)scale, (float)scale);
      renderer.submit(state, matrices, renderCommandQueue, SpecterCore.mc.gameRenderer.getLevelRenderState().cameraRenderState);
      matrices.popPose();
      renderDispatcher.renderAllFeatures();
      renderCommandQueue.endFrame();
   }

   private static class MyVertexConsumer implements VertexConsumer {
      private final float[] xs = new float[4];
      private final float[] ys = new float[4];
      private final float[] zs = new float[4];
      private int i = 0;

      @Override
      public VertexConsumer addVertex(float x, float y, float z) {
         this.xs[this.i] = x;
         this.ys[this.i] = y;
         this.zs[this.i] = z;
         this.i++;
         if (this.i == 4) {
            WireframeEntityRenderer.renderer
               .side(
                  WireframeEntityRenderer.offsetX + this.xs[0],
                  WireframeEntityRenderer.offsetY + this.ys[0],
                  WireframeEntityRenderer.offsetZ + this.zs[0],
                  WireframeEntityRenderer.offsetX + this.xs[1],
                  WireframeEntityRenderer.offsetY + this.ys[1],
                  WireframeEntityRenderer.offsetZ + this.zs[1],
                  WireframeEntityRenderer.offsetX + this.xs[2],
                  WireframeEntityRenderer.offsetY + this.ys[2],
                  WireframeEntityRenderer.offsetZ + this.zs[2],
                  WireframeEntityRenderer.offsetX + this.xs[3],
                  WireframeEntityRenderer.offsetY + this.ys[3],
                  WireframeEntityRenderer.offsetZ + this.zs[3],
                  WireframeEntityRenderer.sideColor,
                  WireframeEntityRenderer.lineColor,
                  WireframeEntityRenderer.shapeMode
               );
            this.i = 0;
         }

         return this;
      }

      @Override
      public VertexConsumer setColor(int red, int green, int blue, int alpha) {
         return this;
      }

      @Override
      public VertexConsumer setColor(int argb) {
         return this;
      }

      @Override
      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      @Override
      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setNormal(float x, float y, float z) {
         return this;
      }

      @Override
      public VertexConsumer setLineWidth(float width) {
         return this;
      }
   }

   private static class MyVertexConsumerProvider extends BufferSource {
      public static final WireframeEntityRenderer.MyVertexConsumerProvider INSTANCE = new WireframeEntityRenderer.MyVertexConsumerProvider();
      private final Object2ObjectOpenHashMap<RenderType, WireframeEntityRenderer.MyVertexConsumer> buffers = new Object2ObjectOpenHashMap();

      protected MyVertexConsumerProvider() {
         super(null, null);
      }

      @Override
      public VertexConsumer getBuffer(RenderType layer) {
         if (((RenderLayerAccessor)layer).getRenderSetup().outputTarget == OutputTarget.ITEM_ENTITY_TARGET) {
            return NoopVertexConsumer.INSTANCE;
         } else {
            WireframeEntityRenderer.MyVertexConsumer vertexConsumer = (WireframeEntityRenderer.MyVertexConsumer)this.buffers.get(layer);
            if (vertexConsumer == null) {
               vertexConsumer = new WireframeEntityRenderer.MyVertexConsumer();
               this.buffers.put(layer, vertexConsumer);
            }

            return vertexConsumer;
         }
      }

      @Override
      public void endBatch() {
         throw new RuntimeException();
      }

      @Override
      public void endBatch(RenderType layer) {
         throw new RuntimeException();
      }
   }
}
