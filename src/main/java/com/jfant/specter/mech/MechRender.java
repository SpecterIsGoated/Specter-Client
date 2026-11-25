package com.jfant.specter.mech;

import com.jfant.specter.mixin.RenderLayerInvoker;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a mech: solid armour (lit, textured metal), glowing visor and nozzles, engine flames and vapour trails.
 *
 * <p>Everything is submitted as custom geometry into the vanilla entity pass, so it sorts, fogs and lights like any
 * other entity. Geometry comes from {@link MechPose}; all scratch objects are reused, nothing is allocated per frame.
 */
public final class MechRender {
   public static final Identifier METAL = Identifier.fromNamespaceAndPath("specter", "textures/mech/metal");
   public static final Identifier WHITE = Identifier.fromNamespaceAndPath("specter", "textures/mech/white");

   /** Additive, no depth write, both sides: exhaust flames and glow halos. */
   private static final RenderPipeline FLAME_PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter", "pipeline/mech_flame"))
      .withBindGroupLayout(BindGroupLayouts.GLOBALS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.FOG)
      .withVertexShader("core/rendertype_lightning")
      .withFragmentShader("core/rendertype_lightning")
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
      .withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .withCull(false)
      .build();
   /** Ordinary alpha blending, no depth write, both sides: vapour trails and heat haze. */
   private static final RenderPipeline TRAIL_PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter", "pipeline/mech_trail"))
      .withBindGroupLayout(BindGroupLayouts.GLOBALS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.FOG)
      .withVertexShader("core/rendertype_lightning")
      .withFragmentShader("core/rendertype_lightning")
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
      .withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .withCull(false)
      .build();

   private static RenderType solid;
   private static RenderType glow;
   private static RenderType flame;
   private static RenderType trail;
   private static boolean texturesReady;

   private static final int FLAME_SIDES = 8;
   private static final float[] COS = new float[FLAME_SIDES + 1];
   private static final float[] SIN = new float[FLAME_SIDES + 1];

   static {
      for (int i = 0; i <= FLAME_SIDES; i++) {
         double a = Math.PI * 2.0 * i / FLAME_SIDES + Math.PI / FLAME_SIDES;
         COS[i] = (float)Math.cos(a);
         SIN[i] = (float)Math.sin(a);
      }
   }

   // scratch (render thread only)
   private static final Matrix4f M = new Matrix4f();
   private static final Vector3f N = new Vector3f();
   private static final Vector3f V = new Vector3f();
   private static final Vector3f A = new Vector3f();
   private static final Vector3f B = new Vector3f();
   private static final Quaternionf Q = new Quaternionf();

   private MechRender() {
   }

   public static boolean ready() {
      return texturesReady;
   }

   /** Builds the textures and render types. Call from the client tick (never inside a render pass). */
   public static void ensureResources() {
      if (texturesReady) return;
      Minecraft mc = Minecraft.getInstance();
      mc.getTextureManager().register(METAL, new DynamicTexture(() -> "specter-mech-metal", metalImage()));
      NativeImage white = new NativeImage(4, 4, false);
      for (int y = 0; y < 4; y++) {
         for (int x = 0; x < 4; x++) white.setPixel(x, y, 0xFFFFFFFF);
      }
      mc.getTextureManager().register(WHITE, new DynamicTexture(() -> "specter-mech-white", white));
      solid = RenderLayerInvoker.specter$of(
         "specter_mech_solid",
         RenderSetup.builder(RenderPipelines.ENTITY_SOLID)
            .withTexture("Sampler0", METAL, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
            .useLightmap()
            .useOverlay()
            .affectsCrumbling()
            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
            .createRenderSetup()
      );
      glow = RenderTypes.eyes(WHITE);
      flame = RenderLayerInvoker.specter$of("specter_mech_flame", RenderSetup.builder(FLAME_PIPELINE).sortOnUpload().createRenderSetup());
      trail = RenderLayerInvoker.specter$of("specter_mech_trail", RenderSetup.builder(TRAIL_PIPELINE).sortOnUpload().createRenderSetup());
      texturesReady = true;
   }

   /**
    * Brushed, panelled metal: light grey so the vertex colours tint it. Seams every quarter block with rivets, a fine
    * brushed grain and slow mottling so large plates are not flat.
    */
   private static NativeImage metalImage() {
      int size = 128;
      NativeImage img = new NativeImage(size, size, false);
      java.util.Random r = new java.util.Random(0x5EC7E9L);
      float[] grain = new float[size * size];
      for (int y = 0; y < size; y++) {
         float row = r.nextFloat();
         for (int x = 0; x < size; x++) {
            // brushed along x: row noise + soft per-pixel noise
            grain[y * size + x] = row * 0.5F + r.nextFloat() * 0.5F;
         }
      }
      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float g = grain[y * size + x] * 0.05F + grain[y * size + (x + 1) % size] * 0.05F;
            float mottle = (float)(Math.sin(x * 0.098 + Math.sin(y * 0.061) * 2.0) * Math.cos(y * 0.083) * 0.035);
            float v = 0.88F + g + mottle;
            int sx = x % 64, sy = y % 64;
            boolean seam = sx == 0 || sy == 0 || (sy == 32 && sx > 6 && sx < 58);
            boolean bevel = sx == 1 || sy == 1;
            if (seam) v *= 0.55F;
            else if (bevel) v *= 1.06F;
            // rivets along the seams
            if ((sy == 4 || sy == 60) && sx % 8 == 4 || (sx == 4 || sx == 60) && sy % 8 == 4) v *= 0.78F;
            int c = Math.max(0, Math.min(255, (int)(v * 255)));
            img.setPixel(x, y, 0xFF000000 | c << 16 | c << 8 | c);
         }
      }
      return img;
   }

   // ============================================================================================== submit

   /** Replaces the player model with the mech. Called from the living entity renderer at the start of submit. */
   public static void submit(AvatarRenderState state, MechAnim anim, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      if (!texturesReady) return;
      anim.camX = camera.pos.x;
      anim.camY = camera.pos.y;
      anim.camZ = camera.pos.z;
      collector.submitCustomGeometry(poseStack, solid, anim.solidPass);
      collector.submitCustomGeometry(poseStack, glow, anim.glowPass);
      if (anim.hasFlames()) collector.submitCustomGeometry(poseStack, flame, anim.flamePass);
      if (anim.hasTrails()) collector.submitCustomGeometry(poseStack, trail, anim.trailPass);
      submitHeldItems(state, anim, poseStack, collector);
   }

   /** Held items in the robot's hands (hidden while the hands are folded away). */
   private static void submitHeldItems(AvatarRenderState state, MechAnim anim, PoseStack poseStack, SubmitNodeCollector collector) {
      MechPose pose = anim.pose;
      for (int side = 0; side < 2; side++) {
         boolean leftArm = side == 0;
         ItemStackRenderState item = leftArm ? state.leftHandItemState : state.rightHandItemState;
         if (item.isEmpty()) continue;
         int hand = leftArm ? MechModel.HAND_L : MechModel.HAND_R;
         if (pose.weight[hand] > 0.35F || pose.scale[hand] < 0.05F) continue;
         poseStack.pushPose();
         Vector3f p = pose.pos[hand];
         poseStack.translate(p.x, p.y, p.z);
         poseStack.rotate(pose.rot[hand]);
         float s = pose.scale[hand];
         poseStack.scale(s, s, s);
         // into the vanilla arm convention: robot space has +Y up and +Z in front, vanilla model space has +Y down and
         // -Z in front (same +X), which is a half turn about X - not a mirror, which turned held items round
         poseStack.rotate(Q.identity().rotateX((float)Math.PI));
         // then the same hold transform vanilla's ItemInHandLayer uses
         poseStack.rotate(Q.identity().rotateX((float)Math.toRadians(-90.0)));
         poseStack.rotate(Q.identity().rotateY((float)Math.PI));
         poseStack.translate((leftArm ? -1 : 1) * 0.02F, 0.03F, -0.11F);
         item.submit(poseStack, collector, anim.light, OverlayTexture.NO_OVERLAY, state.outlineColor);
         poseStack.popPose();
      }
   }

   // ============================================================================================== passes

   private static int argb(int rgb, float shade, int alpha) {
      int r = Math.min(255, (int)((rgb >> 16 & 255) * shade));
      int g = Math.min(255, (int)((rgb >> 8 & 255) * shade));
      int b = Math.min(255, (int)((rgb & 255) * shade));
      return alpha << 24 | r << 16 | g << 8 | b;
   }

   /** Lit armour. */
   static void emitSolid(MechAnim anim, PoseStack.Pose pose, VertexConsumer buf) {
      MechModel model = MechModel.get();
      MechPose mp = anim.pose;
      for (int i = 0; i < MechModel.COUNT; i++) {
         float s = mp.scale[i];
         if (s < 0.02F) continue;
         MechModel.Part part = model.parts[i];
         solidMesh(anim, pose, buf, part.mesh, i, s * mp.coreScale[i]);
         if (part.shell != null) solidMesh(anim, pose, buf, part.shell, i, s * mp.shellScale[i]);
      }
   }

   private static void solidMesh(MechAnim anim, PoseStack.Pose pose, VertexConsumer buf, MechMesh mesh, int part, float s) {
      if (s < 0.02F) return;
      MechPose mp = anim.pose;
      int[] pal = anim.palette.rgb;
      int light = anim.light, overlay = anim.overlay;
      Quaternionf rot = mp.rot[part];
      Vector3f p = mp.pos[part];
      M.set(pose.pose()).translate(p.x, p.y, p.z).rotate(rot).scale(s);
      float[] pos = mesh.pos, uv = mesh.uv, shade = mesh.shade, nrm = mesh.vnormal;
      for (int q = 0; q < mesh.quads; q++) {
         int mat = mesh.material[q];
         if (MechPalette.glows(mat)) continue;
         int rgb = pal[mat];
         for (int k = 0; k < 4; k++) {
            int vi = q * 4 + k;
            rot.transform(N.set(nrm[vi * 3], nrm[vi * 3 + 1], nrm[vi * 3 + 2]));
            buf.addVertex(M, pos[vi * 3], pos[vi * 3 + 1], pos[vi * 3 + 2])
               .setColor(argb(rgb, shade[vi], 255))
               .setUv(uv[vi * 2], uv[vi * 2 + 1])
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(pose, N.x, N.y, N.z);
         }
      }
   }

   /** Visor, chest core and nozzle interiors: full bright. */
   static void emitGlow(MechAnim anim, PoseStack.Pose pose, VertexConsumer buf) {
      MechModel model = MechModel.get();
      MechPose mp = anim.pose;
      int glowRgb = anim.palette.rgb[MechPalette.GLOW];
      int nozzleRgb = anim.nozzleColor;
      float visor = anim.visorPulse;
      for (int i = 0; i < MechModel.COUNT; i++) {
         MechModel.Part part = model.parts[i];
         if (part.glowQuads == 0) continue;
         for (int layer = 0; layer < 2; layer++) {
         MechMesh mesh = layer == 0 ? part.mesh : part.shell;
         if (mesh == null) continue;
         float s = mp.scale[i] * (layer == 0 ? mp.coreScale[i] : mp.shellScale[i]);
         if (s < 0.02F) continue;
         Quaternionf rot = mp.rot[i];
         Vector3f p = mp.pos[i];
         M.set(pose.pose()).translate(p.x, p.y, p.z).rotate(rot).scale(s);
         for (int q = 0; q < mesh.quads; q++) {
            int mat = mesh.material[q];
            if (!MechPalette.glows(mat)) continue;
            int color = mat == MechPalette.NOZZLE ? nozzleRgb | 0xFF000000 : argb(glowRgb, visor, 255);
            rot.transform(N.set(mesh.normal[q * 3], mesh.normal[q * 3 + 1], mesh.normal[q * 3 + 2]));
            for (int k = 0; k < 4; k++) {
               int vi = q * 4 + k;
               buf.addVertex(M, mesh.pos[vi * 3], mesh.pos[vi * 3 + 1], mesh.pos[vi * 3 + 2])
                  .setColor(color)
                  .setUv(0.5F, 0.5F)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(0xF000F0)
                  .setNormal(pose, N.x, N.y, N.z);
            }
         }
         }
      }
   }

   /**
    * Exhaust plumes out of both nozzles. Layered additive cones: a white-blue core, a body that turns from blue
    * (cruise) to orange (afterburner), a soft outer glow, and bright shock diamonds when the afterburner is lit.
    */
   static void emitFlames(MechAnim anim, PoseStack.Pose pose, VertexConsumer buf) {
      MechPose mp = anim.pose;
      float time = anim.time;
      for (int side = 0; side < 2; side++) {
         int nz = side == 0 ? MechModel.NOZZLE_L : MechModel.NOZZLE_R;
         float s = mp.scale[nz];
         if (s < 0.05F) continue;
         float len = anim.flameLength, power = anim.flamePower, ab = anim.afterburner;
         if (len < 0.02F || power < 0.02F) continue;
         Vector3f p = mp.pos[nz];
         M.set(pose.pose()).translate(p.x, p.y, p.z).rotate(mp.rot[nz]).scale(s).translate(MechModel.NOZZLE_EXIT.x, 0, MechModel.NOZZLE_EXIT.z);
         // flicker: two incommensurate sines + a per-side phase
         float fl = 1.0F + 0.08F * (float)Math.sin(time * 37.0F + side * 1.7F) + 0.06F * (float)Math.sin(time * 61.0F + side * 0.4F);
         float l = len * fl;
         float ex = MechModel.NOZZLE_HALF.x * 0.92F, ez = MechModel.NOZZLE_HALF.z * 0.92F, y0 = MechModel.NOZZLE_EXIT.y;
         // body
         int bodyR = (int)lerp(110, 255, ab), bodyG = (int)lerp(130, 135, ab), bodyB = (int)lerp(255, 60, ab);
         cone(buf, y0, l, ex * 0.95F, ez * 0.95F, 0.35F, bodyR, bodyG, bodyB, power * 0.75F, 0.0F, 6);
         // outer glow
         cone(buf, y0, l * 0.75F, ex * 1.35F, ez * 1.35F, 0.9F, (int)lerp(80, 255, ab), (int)lerp(90, 90, ab), (int)lerp(255, 30, ab), power * 0.28F, 0.0F, 4);
         // core
         cone(buf, y0, l * (0.45F + 0.15F * ab), ex * 0.55F, ez * 0.55F, 0.1F, 225, 235, 255, power, 0.0F, 4);
         // shock diamonds
         if (ab > 0.05F) {
            for (int d = 0; d < 4; d++) {
               float at = y0 - l * (0.16F + d * 0.17F);
               float sz = (0.55F - d * 0.09F);
               diamond(buf, at, 0.07F * l / 1.6F + 0.03F, ex * sz, ez * sz, 255, (int)lerp(230, 200, d / 4.0F), 200, ab * power * (0.9F - d * 0.18F));
            }
         }
      }
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   /**
    * A tapered cone along -Y from y0: half sizes ex/ez at the start, shrinking to {@code tip} of that at the end,
    * alpha fading to 0 along the length.
    */
   private static void cone(VertexConsumer buf, float y0, float len, float ex, float ez, float tip, int r, int g, int b, float alpha, float swell, int segs) {
      for (int sgm = 0; sgm < segs; sgm++) {
         float t0 = sgm / (float)segs, t1 = (sgm + 1) / (float)segs;
         float k0 = shape(t0, tip, swell), k1 = shape(t1, tip, swell);
         float ya = y0 - len * t0, yb = y0 - len * t1;
         int a0 = (int)(255 * alpha * fade(t0)), a1 = (int)(255 * alpha * fade(t1));
         if (a0 <= 0 && a1 <= 0) continue;
         for (int i = 0; i < FLAME_SIDES; i++) {
            float c0 = COS[i], s0 = SIN[i], c1 = COS[i + 1], s1 = SIN[i + 1];
            buf.addVertex(M, c0 * ex * k0, ya, s0 * ez * k0).setColor(r, g, b, a0);
            buf.addVertex(M, c1 * ex * k0, ya, s1 * ez * k0).setColor(r, g, b, a0);
            buf.addVertex(M, c1 * ex * k1, yb, s1 * ez * k1).setColor(r, g, b, a1);
            buf.addVertex(M, c0 * ex * k1, yb, s0 * ez * k1).setColor(r, g, b, a1);
         }
      }
   }

   private static float shape(float t, float tip, float swell) {
      return (1.0F - t) + t * tip + swell * (float)Math.sin(Math.PI * t);
   }

   private static float fade(float t) {
      return (1.0F - t) * (1.0F - t * 0.35F);
   }

   private static void diamond(VertexConsumer buf, float at, float half, float ex, float ez, int r, int g, int b, float alpha) {
      int a = (int)(255 * Math.max(0, Math.min(1, alpha)));
      if (a <= 0) return;
      for (int i = 0; i < FLAME_SIDES; i++) {
         float c0 = COS[i], s0 = SIN[i], c1 = COS[i + 1], s1 = SIN[i + 1];
         // front half (point -> ring) and back half (ring -> point)
         buf.addVertex(M, 0, at + half, 0).setColor(r, g, b, 0);
         buf.addVertex(M, 0, at + half, 0).setColor(r, g, b, 0);
         buf.addVertex(M, c1 * ex, at, s1 * ez).setColor(r, g, b, a);
         buf.addVertex(M, c0 * ex, at, s0 * ez).setColor(r, g, b, a);
         buf.addVertex(M, c0 * ex, at, s0 * ez).setColor(r, g, b, a);
         buf.addVertex(M, c1 * ex, at, s1 * ez).setColor(r, g, b, a);
         buf.addVertex(M, 0, at - half, 0).setColor(r, g, b, 0);
         buf.addVertex(M, 0, at - half, 0).setColor(r, g, b, 0);
      }
   }

   /**
    * Vapour trails from the wing tips (camera-facing ribbons that fade with age) and a faint shimmering heat haze
    * behind the nozzles while the afterburner is lit. A real refraction pass is not possible inside the entity pass,
    * so the haze is a soft, wobbling translucent veil.
    */
   static void emitTrails(MechAnim anim, PoseStack.Pose pose, VertexConsumer buf) {
      M.set(pose.pose());
      double ox = anim.renderX, oy = anim.renderY, oz = anim.renderZ;
      for (int side = 0; side < 2; side++) {
         MechAnim.Trail tr = side == 0 ? anim.trailL : anim.trailR;
         int n = tr.count;
         if (n < 2) continue;
         for (int k = 0; k < n - 1; k++) {
            int ia = tr.index(k), ib = tr.index(k + 1);
            float ax = (float)(tr.x[ia] - ox), ay = (float)(tr.y[ia] - oy), az = (float)(tr.z[ia] - oz);
            float bx = (float)(tr.x[ib] - ox), by = (float)(tr.y[ib] - oy), bz = (float)(tr.z[ib] - oz);
            float aa = tr.a[ia] * (1.0F - k / (float)n), ab = tr.a[ib] * (1.0F - (k + 1) / (float)n);
            if (aa <= 0.01F && ab <= 0.01F) continue;
            // side vector: perpendicular to the segment and the view direction
            A.set(bx - ax, by - ay, bz - az);
            B.set((float)(anim.camX - ox) - ax, (float)(anim.camY - oy) - ay, (float)(anim.camZ - oz) - az);
            A.cross(B);
            float len = A.length();
            if (len < 1.0E-5F) continue;
            float wa = 0.035F + 0.05F * (k / (float)n), wb = 0.035F + 0.05F * ((k + 1) / (float)n);
            A.div(len);
            int ca = (int)(aa * 150), cb = (int)(ab * 150);
            buf.addVertex(M, ax - A.x * wa, ay - A.y * wa, az - A.z * wa).setColor(240, 244, 255, ca);
            buf.addVertex(M, ax + A.x * wa, ay + A.y * wa, az + A.z * wa).setColor(240, 244, 255, ca);
            buf.addVertex(M, bx + A.x * wb, by + A.y * wb, bz + A.z * wb).setColor(240, 244, 255, cb);
            buf.addVertex(M, bx - A.x * wb, by - A.y * wb, bz - A.z * wb).setColor(240, 244, 255, cb);
         }
      }
      // heat haze behind the nozzles
      float ab = anim.afterburner * anim.flamePower;
      if (ab > 0.05F) {
         MechPose mp = anim.pose;
         for (int side = 0; side < 2; side++) {
            int nz = side == 0 ? MechModel.NOZZLE_L : MechModel.NOZZLE_R;
            float s = mp.scale[nz];
            Vector3f p = mp.pos[nz];
            M.set(pose.pose()).translate(p.x, p.y, p.z).rotate(mp.rot[nz]).scale(s).translate(MechModel.NOZZLE_EXIT.x, 0, MechModel.NOZZLE_EXIT.z);
            float l = anim.flameLength * 1.25F;
            for (int sgm = 0; sgm < 5; sgm++) {
               float t0 = sgm / 5.0F, t1 = (sgm + 1) / 5.0F;
               float w0 = 0.12F + 0.22F * t0, w1 = 0.12F + 0.22F * t1;
               float wob0 = 0.03F * (float)Math.sin(anim.time * 23.0F + sgm * 1.9F + side);
               float wob1 = 0.03F * (float)Math.sin(anim.time * 23.0F + (sgm + 1) * 1.9F + side);
               int a0 = (int)(ab * 34 * (1 - t0)), a1 = (int)(ab * 34 * (1 - t1));
               float y0 = MechModel.NOZZLE_EXIT.y - l * t0, y1 = MechModel.NOZZLE_EXIT.y - l * t1;
               buf.addVertex(M, -w0 + wob0, y0, wob0).setColor(255, 250, 240, a0);
               buf.addVertex(M, w0 + wob0, y0, wob0).setColor(255, 250, 240, a0);
               buf.addVertex(M, w1 + wob1, y1, wob1).setColor(255, 250, 240, a1);
               buf.addVertex(M, -w1 + wob1, y1, wob1).setColor(255, 250, 240, a1);
               buf.addVertex(M, wob0, y0, -w0 + wob0).setColor(255, 250, 240, a0);
               buf.addVertex(M, wob0, y0, w0 + wob0).setColor(255, 250, 240, a0);
               buf.addVertex(M, wob1, y1, w1 + wob1).setColor(255, 250, 240, a1);
               buf.addVertex(M, wob1, y1, -w1 + wob1).setColor(255, 250, 240, a1);
            }
         }
      }
   }

   /** World position of a point given in a part's frame (for particles and trails). */
   public static Vector3f partPoint(MechPose pose, int part, Vector3f local, Vector3f out) {
      out.set(local).mul(pose.scale[part]);
      pose.rot[part].transform(out);
      return out.add(pose.pos[part]);
   }

   /** Direction of a part's local axis in entity space. */
   public static Vector3f partAxis(MechPose pose, int part, float x, float y, float z, Vector3f out) {
      return pose.rot[part].transform(out.set(x, y, z));
   }
}
