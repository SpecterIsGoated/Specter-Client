package com.jfant.specter.core.utils.render;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.Utils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector4f;

public class NametagUtils {
   private static final Vector4f vec4 = new Vector4f();
   private static final Vector4f mmMat4 = new Vector4f();
   private static final Vector4f pmMat4 = new Vector4f();
   private static final Vector3d camera = new Vector3d();
   private static final Vector3d cameraNegated = new Vector3d();
   private static final Matrix4f model = new Matrix4f();
   private static final Matrix4f projection = new Matrix4f();
   private static double windowScale;
   public static double scale;

   private NametagUtils() {
   }

   public static void onRender(Matrix4f modelView) {
      model.set(modelView);
      projection.set(RenderUtils.projection);
      Utils.set(camera, SpecterCore.mc.gameRenderer.getMainCamera().position());
      cameraNegated.set(camera);
      cameraNegated.negate();
      windowScale = SpecterCore.mc.getWindow().calculateScale(1, false);
   }

   public static boolean to2D(Vector3d pos, double scale) {
      return to2D(pos, scale, true);
   }

   public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling) {
      return to2D(pos, scale, distanceScaling, false);
   }

   public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling, boolean allowBehind) {
      NametagUtils.scale = scale;
      if (distanceScaling) {
         NametagUtils.scale = NametagUtils.scale * getScale(pos);
      }

      vec4.set(cameraNegated.x + pos.x, cameraNegated.y + pos.y, cameraNegated.z + pos.z, 1.0);
      vec4.mul(model, mmMat4);
      mmMat4.mul(projection, pmMat4);
      boolean behind = pmMat4.w <= 0.0F;
      if (behind && !allowBehind) {
         return false;
      } else {
         toScreen(pmMat4);
         double x = pmMat4.x * SpecterCore.mc.getWindow().getWidth();
         double y = pmMat4.y * SpecterCore.mc.getWindow().getHeight();
         if (behind) {
            x = SpecterCore.mc.getWindow().getWidth() - x;
            y = SpecterCore.mc.getWindow().getHeight() - y;
         }

         if (!Double.isInfinite(x) && !Double.isInfinite(y)) {
            pos.set(x / windowScale, SpecterCore.mc.getWindow().getHeight() - y / windowScale, allowBehind ? pmMat4.w : pmMat4.z);
            return true;
         } else {
            return false;
         }
      }
   }

   public static void begin(Vector3d pos) {
      Matrix4fStack matrices = RenderSystem.getModelViewStack();
      begin(matrices, pos);
   }

   public static void begin(Vector3d pos, GuiGraphics drawContext) {
      begin(pos);
      Matrix3x2fStack matrices = drawContext.pose();
      matrices.pushMatrix();
      matrices.scale(1.0F / SpecterCore.mc.getWindow().getGuiScale());
      matrices.translate((float)pos.x, (float)pos.y);
      matrices.scale((float)scale, (float)scale);
   }

   private static void begin(Matrix4fStack matrices, Vector3d pos) {
      matrices.pushMatrix();
      matrices.translate((float)pos.x, (float)pos.y, 0.0F);
      matrices.scale((float)scale, (float)scale, 1.0F);
   }

   public static void end() {
      RenderSystem.getModelViewStack().popMatrix();
   }

   public static void end(GuiGraphics drawContext) {
      end();
      drawContext.pose().popMatrix();
   }

   private static double getScale(Vector3d pos) {
      double dist = camera.distance(pos);
      return Mth.clamp(1.0 - dist * 0.01, 0.5, 2.147483647E9);
   }

   private static void toScreen(Vector4f vec) {
      float newW = 1.0F / vec.w * 0.5F;
      vec.x = vec.x * newW + 0.5F;
      vec.y = vec.y * newW + 0.5F;
      vec.z = vec.z * newW + 0.5F;
      vec.w = newW;
   }
}
