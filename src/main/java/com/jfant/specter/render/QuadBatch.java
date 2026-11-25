package com.jfant.specter.render;

import com.jfant.specter.core.SpecterCore;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import com.jfant.specter.core.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public final class QuadBatch {
   private static final int STRIDE = 8;
   private final RenderPipeline pipeline;
   private final TextureSetup textures;
   private final boolean textured;
   private final Matrix3x2f pose;
   @Nullable
   private final ScreenRectangle scissor;
   private float[] data = new float[128];
   private int[] colors = new int[32];
   private int count;
   private float minX = 3.4028235E38F;
   private float minY = 3.4028235E38F;
   private float maxX = -3.4028235E38F;
   private float maxY = -3.4028235E38F;
   private static float alpha = 1.0F;
   // Grouping (see beginGroup): the merged batch waiting to be handed to the GUI, and what it may still take.
   private static int groupDepth;
   @Nullable
   private static QuadBatch pending;
   @Nullable
   private static GuiGraphicsExtractor pendingCtx;
   private static boolean flushing;
   private static final float[] alphaStack = new float[16];
   private static int alphaDepth;

   private QuadBatch(RenderPipeline pipeline, TextureSetup textures, boolean textured, Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
      this.pipeline = pipeline;
      this.textures = textures;
      this.textured = textured;
      this.pose = pose;
      this.scissor = scissor;
   }

   public static QuadBatch colored(GuiGraphicsExtractor ctx) {
      return new QuadBatch(RenderPipelines.GUI, TextureSetup.noTexture(), false, new Matrix3x2f(ctx.pose()), ((GuiGraphicsExtractorAccessor)ctx).getScissorStack().peek());
   }

   @Nullable
   public static QuadBatch textured(GuiGraphicsExtractor ctx, Identifier texture) {
      AbstractTexture tex = SpecterCore.mc.getTextureManager().getTexture(texture);
      return tex != null && tex.getTextureView() != null
         ? new QuadBatch(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()),
            true,
            new Matrix3x2f(ctx.pose()),
            ((GuiGraphicsExtractorAccessor)ctx).getScissorStack().peek()
         )
         : null;
   }

   public void fill(float x0, float y0, float x1, float y1, int color) {
      this.add(x0, y0, x1, y1, 0.0F, 0.0F, 0.0F, 0.0F, color, color);
   }

   public void gradient(float x0, float y0, float x1, float y1, int topColor, int bottomColor) {
      this.add(x0, y0, x1, y1, 0.0F, 0.0F, 0.0F, 0.0F, topColor, bottomColor);
   }

   public void textured(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int topColor, int bottomColor) {
      this.add(x0, y0, x1, y1, u0, v0, u1, v1, topColor, bottomColor);
   }

   public static void pushAlpha(float factor) {
      if (alphaDepth < alphaStack.length) {
         alphaStack[alphaDepth] = alpha;
      }

      alphaDepth++;
      alpha = alpha * Math.clamp(factor, 0.0F, 1.0F);
   }

   public static void popAlpha() {
      if (alphaDepth > 0) {
         alphaDepth--;
         if (alphaDepth < alphaStack.length) {
            alpha = alphaStack[alphaDepth];
         }
      }
   }

   public static float alpha() {
      return alpha;
   }

   public static int faded(int color) {
      return alpha >= 1.0F ? color : Math.round((color >>> 24) * alpha) << 24 | color & 16777215;
   }

   private void add(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int top, int bottom) {
      if (alpha < 1.0F) {
         top = faded(top);
         bottom = faded(bottom);
      }

      if (!(x1 <= x0) && !(y1 <= y0) && (top | bottom) >>> 24 != 0) {
         if ((this.count + 1) * 8 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
            this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
         }

         this.colors[2 * this.count] = top;
         this.colors[2 * this.count + 1] = bottom;
         int i = this.count++ * 8;
         this.data[i] = x0;
         this.data[i + 1] = y0;
         this.data[i + 2] = x1;
         this.data[i + 3] = y1;
         this.data[i + 4] = u0;
         this.data[i + 5] = v0;
         this.data[i + 6] = u1;
         this.data[i + 7] = v1;
         this.minX = Math.min(this.minX, x0);
         this.minY = Math.min(this.minY, y0);
         this.maxX = Math.max(this.maxX, x1);
         this.maxY = Math.max(this.maxY, y1);
      }
   }

   /**
    * Starts a stretch of drawing where Specter shapes and text drawn back to back become ONE GUI element instead of
    * one each. Vanilla places every new GUI element by walking all earlier ones it might overlap, so a HUD made of a
    * few hundred little shapes costs O(n^2) per frame (Where Are You's map alone was a quarter of the frame). Layering
    * does not change: anything else added to the GUI (vanilla text, items, fills) first flushes what is waiting, see
    * {@code GuiRenderStateMixin}. Always pair with {@link #endGroup()} in a finally.
    */
   public static void beginGroup() {
      groupDepth++;
   }

   public static void endGroup() {
      if (groupDepth > 0 && --groupDepth == 0) {
         flushGroup();
      }
   }

   /** Hands the waiting merged batch to the GUI now (called before any other GUI element is added). */
   public static void flushGroup() {
      QuadBatch p = pending;
      GuiGraphicsExtractor c = pendingCtx;
      if (p != null && !flushing) {
         pending = null;
         pendingCtx = null;
         flushing = true;
         try {
            p.submitNow(c);
         } finally {
            flushing = false;
         }
      }
   }

   private boolean sameKind(QuadBatch o) {
      return this.pipeline == o.pipeline && this.textured == o.textured && this.textures.equals(o.textures) && java.util.Objects.equals(this.scissor, o.scissor);
   }

   public void submit(GuiGraphicsExtractor ctx) {
      if (this.count == 0) return;
      Matrix3x2f p = this.pose;
      // merge only plain translate/scale poses (every Specter card), so corners can be moved into screen space exactly
      if (groupDepth > 0 && !flushing && p.m01 == 0.0F && p.m10 == 0.0F && p.m00 > 0.0F && p.m11 > 0.0F) {
         if (pending != null && (pendingCtx != ctx || !pending.sameKind(this))) flushGroup();
         if (pending == null) {
            pending = new QuadBatch(this.pipeline, this.textures, this.textured, new Matrix3x2f(), this.scissor);
            pendingCtx = ctx;
         }
         QuadBatch to = pending;
         for (int q = 0; q < this.count; q++) {
            int i = q * 8;
            to.append(
               p.m00 * this.data[i] + p.m20, p.m11 * this.data[i + 1] + p.m21, p.m00 * this.data[i + 2] + p.m20, p.m11 * this.data[i + 3] + p.m21,
               this.data[i + 4], this.data[i + 5], this.data[i + 6], this.data[i + 7], this.colors[2 * q], this.colors[2 * q + 1]
            );
         }
         return;
      }
      if (pending != null) flushGroup();
      this.submitNow(ctx);
   }

   /** Adds a quad exactly as given (alpha already applied when it was first added). */
   private void append(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int top, int bottom) {
      if ((this.count + 1) * 8 > this.data.length) {
         this.data = Arrays.copyOf(this.data, this.data.length * 2);
         this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
      }
      this.colors[2 * this.count] = top;
      this.colors[2 * this.count + 1] = bottom;
      int i = this.count++ * 8;
      this.data[i] = x0;
      this.data[i + 1] = y0;
      this.data[i + 2] = x1;
      this.data[i + 3] = y1;
      this.data[i + 4] = u0;
      this.data[i + 5] = v0;
      this.data[i + 6] = u1;
      this.data[i + 7] = v1;
      this.minX = Math.min(this.minX, x0);
      this.minY = Math.min(this.minY, y0);
      this.maxX = Math.max(this.maxX, x1);
      this.maxY = Math.max(this.maxY, y1);
   }

   private void submitNow(GuiGraphicsExtractor ctx) {
      if (this.count != 0) {
         int left = (int)Math.floor(this.minX);
         int top = (int)Math.floor(this.minY);
         int width = (int)Math.ceil(this.maxX) - left;
         int height = (int)Math.ceil(this.maxY) - top;
         ScreenRectangle bounds = new ScreenRectangle(left, top, Math.max(1, width), Math.max(1, height)).transformMaxBounds(this.pose);
         if (this.scissor != null) {
            bounds = this.scissor.intersection(bounds);
         }

         if (bounds != null) {
            ((GuiGraphicsExtractorAccessor)ctx).getGuiRenderState()
               .addGuiElement(
                  new QuadBatch.Element(
                     this.pipeline,
                     this.textures,
                     this.textured,
                     this.pose,
                     Arrays.copyOf(this.data, this.count * 8),
                     Arrays.copyOf(this.colors, this.count * 2),
                     this.count,
                     this.scissor,
                     bounds
                  )
               );
         }
      }
   }

   private record Element(
      RenderPipeline pipeline,
      TextureSetup textureSetup,
      boolean textured,
      Matrix3x2f pose,
      float[] data,
      int[] colors,
      int count,
      @Nullable ScreenRectangle scissorArea,
      ScreenRectangle bounds
   ) implements GuiElementRenderState {
      @Override
      public void buildVertices(VertexConsumer vertices) {
         for (int q = 0; q < this.count; q++) {
            int i = q * 8;
            float x0 = this.data[i];
            float y0 = this.data[i + 1];
            float x1 = this.data[i + 2];
            float y1 = this.data[i + 3];
            int top = this.colors[2 * q];
            int bottom = this.colors[2 * q + 1];
            if (this.textured) {
               float u0 = this.data[i + 4];
               float v0 = this.data[i + 5];
               float u1 = this.data[i + 6];
               float v1 = this.data[i + 7];
               vertices.addVertexWith2DPose(this.pose, x0, y0).setUv(u0, v0).setColor(top);
               vertices.addVertexWith2DPose(this.pose, x0, y1).setUv(u0, v1).setColor(bottom);
               vertices.addVertexWith2DPose(this.pose, x1, y1).setUv(u1, v1).setColor(bottom);
               vertices.addVertexWith2DPose(this.pose, x1, y0).setUv(u1, v0).setColor(top);
            } else {
               vertices.addVertexWith2DPose(this.pose, x0, y0).setColor(top);
               vertices.addVertexWith2DPose(this.pose, x0, y1).setColor(bottom);
               vertices.addVertexWith2DPose(this.pose, x1, y1).setColor(bottom);
               vertices.addVertexWith2DPose(this.pose, x1, y0).setColor(top);
            }
         }
      }
   }
}
