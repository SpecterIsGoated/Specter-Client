package com.jfant.specter.phone.render;

import com.jfant.specter.phone.core.PhoneAnimationController;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.gfx.Colors;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the phone in the first-person hand pass (body, glowing screen, the player's arm holding it) and works out
 * where its glass lands on screen so the interface can be drawn exactly over it.
 *
 * <p>The vanilla hands are replaced while the phone is out. The pose stack is reset to plain view space (no view bob,
 * no hurt shake), so the phone holds perfectly still at rest and the 2D interface stays glued to the glass.
 */
public final class PhoneRenderer {
   public static final Identifier WHITE = Identifier.fromNamespaceAndPath("specter", "textures/phone/white");
   private static final int FULL_BRIGHT = 0xF000F0;

   private static RenderType body;
   private static RenderType sheen;
   private static boolean ready;

   /** Where the glass is in GUI coordinates this frame. */
   public static final class ScreenRect {
      public float x;
      public float y;
      /** GUI units per phone point. */
      public float scale;
      public boolean valid;
   }

   // scratch (render thread only)
   private static final Matrix4f M = new Matrix4f();
   private static final Vector3f V = new Vector3f();
   private static final Vector3f N = new Vector3f();
   private static final Vector3f HAND = new Vector3f();
   private static final Vector3f SHOULDER = new Vector3f();
   private static final Quaternionf Q = new Quaternionf();

   private PhoneRenderer() {
   }

   /** Creates the texture and render types. Call from the client tick (never inside a render pass). */
   public static void ensureResources() {
      if (ready) return;
      NativeImage white = new NativeImage(4, 4, false);
      for (int y = 0; y < 4; y++) {
         for (int x = 0; x < 4; x++) white.setPixel(x, y, 0xFFFFFFFF);
      }
      Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "specter-phone-white", white));
      body = RenderTypes.entitySolid(WHITE);
      sheen = RenderTypes.entityTranslucent(WHITE);
      ready = true;
   }

   public static boolean ready() {
      return ready;
   }

   // ============================================================================================== projection

   /**
    * Projects the glass corners of the phone (model matrix in view space) with the hand projection and returns the
    * rectangle in GUI coordinates. Exact while the glass faces the camera (always true at rest).
    */
   public static void project(Matrix4f model, float hudFovDegrees, ScreenRect out) {
      Window window = Minecraft.getInstance().getWindow();
      float w = window.getWidth(), h = window.getHeight();
      int guiScale = window.getGuiScale();
      if (w <= 0 || h <= 0 || guiScale <= 0) {
         out.valid = false;
         return;
      }
      float f = 1.0F / (float)Math.tan(Math.toRadians(hudFovDegrees) / 2.0);
      float aspect = w / h;
      float z = PhoneMesh.FRONT_Z + 0.02F;
      model.transformPosition(V.set(-PhoneAnimationController.SCREEN_W / 2, PhoneAnimationController.SCREEN_H / 2, z));
      if (V.z >= -0.01F) {
         out.valid = false;
         return;
      }
      float x0 = (f / aspect * V.x / -V.z + 1) / 2 * w / guiScale;
      float y0 = (1 - f * V.y / -V.z) / 2 * h / guiScale;
      model.transformPosition(V.set(PhoneAnimationController.SCREEN_W / 2, -PhoneAnimationController.SCREEN_H / 2, z));
      if (V.z >= -0.01F) {
         out.valid = false;
         return;
      }
      float x1 = (f / aspect * V.x / -V.z + 1) / 2 * w / guiScale;
      out.x = x0;
      out.y = y0;
      out.scale = (x1 - x0) / 180.0F;
      out.valid = out.scale > 0.05F;
   }

   // ============================================================================================== first person

   /**
    * Submits the phone (and the arm holding it) instead of the vanilla hands.
    *
    * @param screenTexture the wallpaper on the glass, or null to show {@code screenColor} while it paints
    */
   public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState, Matrix4f model, PhoneTheme theme,
                                        @Nullable Identifier screenTexture, int screenColor, float screenOn, boolean showArm) {
      if (!ready) return;
      // plain view space: undo the vanilla view rotation the pose stack starts with, and drop bobbing / hurt shake
      Matrix4f view = new Matrix4f(RenderSystem.getModelViewStack()).invert();
      poseStack.pushPose();
      poseStack.setIdentity();
      poseStack.mulPose(view);
      AvatarRenderState avatar = playerState.avatarRenderState;
      int light = avatar != null ? avatar.lightCoords : FULL_BRIGHT;
      boolean left = avatar != null && avatar.mainArm == HumanoidArm.LEFT;

      if (showArm && avatar != null && !avatar.isInvisible) submitArm(poseStack, collector, avatar, model, left, light);

      poseStack.pushPose();
      poseStack.mulPose(model);
      int[] palette = palette(theme);
      collector.submitCustomGeometry(poseStack, body, (pose, buf) -> emitBody(pose, buf, palette, light));
      float on = Math.clamp(screenOn, 0.0F, 1.0F);
      int tint = Colors.lerp(0xFF050508, 0xFFFFFFFF, on);
      RenderType screen = RenderTypes.text(screenTexture != null ? screenTexture : WHITE);
      int screenTint = screenTexture != null ? tint : Colors.lerp(0xFF050508, screenColor | 0xFF000000, on);
      collector.submitCustomGeometry(poseStack, screen, (pose, buf) -> emitScreen(pose, buf, screenTint));
      collector.submitCustomGeometry(poseStack, body, (pose, buf) -> emitHole(pose, buf));
      float glare = 0.10F * (1.0F - 0.75F * on);
      collector.submitCustomGeometry(poseStack, sheen, (pose, buf) -> emitSheen(pose, buf, glare));
      poseStack.popPose();
      poseStack.popPose();
   }

   /** Frame, back and button colours for the theme. */
   private static int[] palette(PhoneTheme theme) {
      return switch (theme.id()) {
         case "daylight" -> new int[]{0xFFC9CCD4, 0xFF0A0A0D, 0xFFE8E5EC, 0xFF15151A, 0xFFB9BCC4, 0xFFD9D6DE, 0xFF9C9FA8};
         case "frosting" -> new int[]{0xFFE7A6BF, 0xFF0A0709, 0xFFF3C9D8, 0xFF140C10, 0xFFD995B0, 0xFFEBB8CB, 0xFFB57890};
         default -> new int[]{0xFF46434F, 0xFF07070A, 0xFF1D1A24, 0xFF0B0B0F, 0xFF3A3742, 0xFF2A2732, 0xFF5B5866};
      };
   }

   private static void emitBody(PoseStack.Pose pose, VertexConsumer buf, int[] palette, int light) {
      PhoneMesh mesh = PhoneMesh.get();
      Matrix4f m = pose.pose();
      for (int q = 0; q < mesh.bodyQuads; q++) {
         int base = palette[mesh.bodyMat[q]];
         for (int k = 0; k < 4; k++) {
            int i = q * 4 + k;
            int color = Colors.shade(base, mesh.bodyShade[i]);
            buf.addVertex(m, mesh.bodyPos[i * 3], mesh.bodyPos[i * 3 + 1], mesh.bodyPos[i * 3 + 2])
               .setColor(color)
               .setUv(0.5F, 0.5F)
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(light)
               .setNormal(pose, mesh.bodyNrm[i * 3], mesh.bodyNrm[i * 3 + 1], mesh.bodyNrm[i * 3 + 2]);
         }
      }
   }

   private static void emitScreen(PoseStack.Pose pose, VertexConsumer buf, int tint) {
      PhoneMesh mesh = PhoneMesh.get();
      Matrix4f m = pose.pose();
      for (int i = 0; i < mesh.screenQuads * 4; i++) {
         buf.addVertex(m, mesh.screenPos[i * 3], mesh.screenPos[i * 3 + 1], mesh.screenPos[i * 3 + 2])
            .setColor(tint)
            .setUv(mesh.screenUv[i * 2], mesh.screenUv[i * 2 + 1])
            .setLight(FULL_BRIGHT);
      }
   }

   private static void emitHole(PoseStack.Pose pose, VertexConsumer buf) {
      PhoneMesh mesh = PhoneMesh.get();
      Matrix4f m = pose.pose();
      for (int i = 0; i < mesh.holeQuads * 4; i++) {
         buf.addVertex(m, mesh.holePos[i * 3], mesh.holePos[i * 3 + 1], mesh.holePos[i * 3 + 2])
            .setColor(0xFF020203)
            .setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(pose, 0, 0, 1);
      }
   }

   /** A faint diagonal reflection across the glass (strongest while the screen is dark). */
   private static void emitSheen(PoseStack.Pose pose, VertexConsumer buf, float strength) {
      if (strength <= 0.005F) return;
      Matrix4f m = pose.pose();
      float hw = PhoneAnimationController.SCREEN_W / 2 - 2, hh = PhoneAnimationController.SCREEN_H / 2 - 2, z = PhoneMesh.FRONT_Z + 0.08F;
      int a0 = Math.round(255 * strength), a1 = 0;
      // a band from the upper left
      buf.addVertex(m, -hw, hh, z).setColor(255, 255, 255, a0).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
      buf.addVertex(m, -hw, hh * 0.15F, z).setColor(255, 255, 255, a1).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
      buf.addVertex(m, hw * 0.1F, hh, z).setColor(255, 255, 255, a1).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
      buf.addVertex(m, hw * 0.1F, hh, z).setColor(255, 255, 255, a1).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
   }

   /**
    * The player's own arm (their skin, sleeve layer included) reaching up from below to hold the phone's lower edge.
    * The vanilla arm model runs from the shoulder at (-0.375, 0, 0) to the hand at (-0.375, 0.75, 0); it is turned to
    * point from a shoulder below the screen to a grip point behind the phone, and scaled to sit right at this close
    * range.
    */
   private static void submitArm(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState avatar, Matrix4f model, boolean left, int light) {
      AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(avatar);
      if (renderer == null) return;
      float side = left ? -1 : 1;
      model.transformPosition(HAND.set(side * (PhoneAnimationController.BODY_W / 2 - 16.0F), -PhoneAnimationController.BODY_H / 2 + 14.0F, -PhoneAnimationController.BODY_D / 2 - 8.0F));
      SHOULDER.set(side * 0.30F, -0.98F, -0.22F);
      V.set(HAND).sub(SHOULDER).normalize();
      float armScale = ARM_SCALE;
      poseStack.pushPose();
      poseStack.translate(HAND.x, HAND.y, HAND.z);
      Q.rotationTo(0, 1, 0, V.x, V.y, V.z);
      poseStack.rotate(Q);
      poseStack.rotate(Q.rotationY((float)Math.toRadians(ARM_ROLL * side)));
      poseStack.scale(armScale, armScale, armScale);
      poseStack.translate(side * 0.375F, -0.75F, 0.0F);
      if (left) {
         renderer.renderLeftHand(poseStack, collector, light, avatar.skin.body().texturePath(), avatar.showLeftSleeve);
      } else {
         renderer.renderRightHand(poseStack, collector, light, avatar.skin.body().texturePath(), avatar.showRightSleeve);
      }
      poseStack.popPose();
   }

   /** Tuning for the arm (dev screenshots). */
   static float ARM_SCALE = 0.55F;
   static float ARM_ROLL = 200.0F;
}
