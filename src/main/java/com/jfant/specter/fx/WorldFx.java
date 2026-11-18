package com.jfant.specter.fx;

import com.jfant.specter.anim.Motion;
import com.jfant.specter.core.MixinPlugin;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.RenderAfterWorldEvent;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.modules.Bioluminescence;
import com.jfant.specter.modules.ColorGrading;
import com.jfant.specter.modules.MarineLayer;
import com.jfant.specter.render.GpuKit;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldFx {
   private static final Logger LOG = LoggerFactory.getLogger("Specter Fx");
   private static final String NS = "specter-client";
   private static final Identifier NOISE = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/noise.png");
   public static final double WRAP = 4096.0;
   // 26.x: uniforms are declared through bind-group layouts; no depth state means no depth test or write.
   private static final BindGroupLayout FX_UBO = GpuKit.ubo("SpecterFx");
   private static final RenderPipeline BIO = base("specter_bio", BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2, new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)));
   private static final RenderPipeline MARINE = base(
      "specter_marine",
      BindGroupLayouts.SAMPLER0_SAMPLER1,
      new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ZERO, BlendFactor.ONE))
   );
   private static final RenderPipeline GRADE = base("specter_grade", BindGroupLayouts.SAMPLER0, ColorTargetState.DEFAULT);
   private static final int UBO_SIZE = uboSize();
   private static final FxParams params = new FxParams();
   private static final Matrix4f scratch = new Matrix4f();
   private static final Vector3f sun = new Vector3f();
   private static final Vector3f moon = new Vector3f();
   private static MappableRingBuffer ubo;
   private static GpuBuffer triangle;
   private static GpuTexture noise;
   private static GpuTextureView noiseView;
   private static TextureTarget solidDepth;
   private static TextureTarget worldDepth;
   private static GpuTexture frameCopy;
   private static GpuTextureView frameCopyView;
   private static long preparedFrame = -1L;
   private static long solidDepthFrame = -1L;
   private static boolean failed;
   private static boolean subscribed;

   private static RenderPipeline base(String name, BindGroupLayout samplers, ColorTargetState color) {
      return RenderPipeline.builder()
         .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/" + name))
         .withBindGroupLayout(FX_UBO)
         .withBindGroupLayout(samplers)
         .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_fx"))
         .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/" + name))
         .withColorTargetState(color)
         .withCull(false)
         .withVertexBinding(0, DefaultVertexFormat.POSITION)
         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
         .build();
   }

   private WorldFx() {
   }

   public static void init() {
      if (!subscribed) {
         subscribed = true;
         SpecterCore.EVENT_BUS.subscribe(WorldFx.class);
      }
   }

   @EventHandler
   private static void onAfterWorld(RenderAfterWorldEvent event) {
      afterHand();
   }

   private static boolean anyOn() {
      return Bioluminescence.on() || MarineLayer.on() || ColorGrading.on();
   }

   private static boolean shaderPack() {
      return MixinPlugin.isIrisPresent && RenderUtils.isShaderPackInUse();
   }

   public static void captureSolidDepth() {
      if (!failed && Bioluminescence.on()) {
         try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
               return;
            }

            solidDepth = copyDepth(solidDepth, "specter-fx-solid-depth", mc.gameRenderer.mainRenderTarget());
            solidDepthFrame = Motion.frameCount();
         } catch (LinkageError | RuntimeException var1) {
            fail(var1);
         }
      }
   }

   public static void afterWorld(Matrix4f projection, Matrix4f view, float tickProgress) {
      if (!failed && (Bioluminescence.on() || MarineLayer.on())) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            try {
               if (shaderPack()) {
                  return;
               }

               if (!prepare(mc, projection, view, tickProgress)) {
                  return;
               }

               boolean bio = params.bio && params.bioStrength > 0.001F && solidDepthFrame == Motion.frameCount();
               boolean fog = params.fog && params.fogStrength > 0.001F;
               if (!bio && !fog) {
                  return;
               }

               RenderTarget main = mc.gameRenderer.mainRenderTarget();
               worldDepth = copyDepth(worldDepth, "specter-fx-world-depth", main);
               GpuTextureView depth = worldDepth.getDepthTextureView();
               if (bio) {
                  draw("Specter bioluminescence", BIO, main.getColorTextureView(), noiseView, solidDepth.getDepthTextureView(), depth);
               }

               if (fog) {
                  draw("Specter marine layer", MARINE, main.getColorTextureView(), noiseView, depth, null);
               }
            } catch (LinkageError | RuntimeException var8) {
               fail(var8);
            }
         }
      }
   }

   public static void afterHand() {
      if (!failed && ColorGrading.on()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            try {
               if (shaderPack()) {
                  return;
               }

               if (preparedFrame != Motion.frameCount() && !prepare(mc, null, null, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))) {
                  return;
               }

               if (!params.grade || params.gradeMix <= 0.001F) {
                  return;
               }

               RenderTarget main = mc.gameRenderer.mainRenderTarget();
               copyFrame(main);
               draw("Specter color grading", GRADE, main.getColorTextureView(), frameCopyView, null, null);
            } catch (LinkageError | RuntimeException var2) {
               fail(var2);
            }
         }
      }
   }

   private static void fail(Throwable e) {
      failed = true;
      LOG.error("Specter's world effects stopped after an error; they are off until restart.", e);
   }

   private static boolean prepare(Minecraft mc, Matrix4f projection, Matrix4f view, float tickProgress) {
      preparedFrame = Motion.frameCount();
      FxParams p = params;
      p.reset();
      ClientLevel world = mc.level;
      Camera camera = mc.gameRenderer.mainCamera();
      if (projection != null && view != null) {
         scratch.set(projection).mul(view).invert(p.invViewProj);
      }

      double x = camera.position().x;
      double y = camera.position().y;
      double z = camera.position().z;
      p.worldX = x;
      p.worldY = y;
      p.worldZ = z;
      p.camX = (float)(x - Math.floor(x / 4096.0) * 4096.0);
      p.camY = (float)y;
      p.camZ = (float)(z - Math.floor(z / 4096.0) * 4096.0);
      p.seconds = (float)(System.nanoTime() / 1.0E9 % 3600.0);
      EnvironmentAttributeProbe env = camera.attributeProbe();
      celestial(env.getValue(EnvironmentAttributes.SUN_ANGLE, tickProgress) * 0.017453292F, sun);
      celestial(env.getValue(EnvironmentAttributes.MOON_ANGLE, tickProgress) * 0.017453292F, moon);
      p.sunX = sun.x;
      p.sunY = sun.y;
      p.sunZ = sun.z;
      p.moonX = moon.x;
      p.moonY = moon.y;
      p.moonZ = moon.z;
      MoonPhase phase = env.getValue(EnvironmentAttributes.MOON_PHASE, tickProgress);
      p.moonBright = (1.0F + Mth.cos(phase.index() * 3.1415927F / 4.0F)) / 2.0F * 0.85F + 0.15F;
      float t = Mth.clamp((sun.y - 0.05F) / -0.27F, 0.0F, 1.0F);
      p.night = t * t * (3.0F - 2.0F * t);
      p.underwater = camera.getFluidInCamera() == FogType.WATER;
      p.rain = world.getRainLevel(tickProgress);
      p.renderDistance = mc.options.getEffectiveRenderDistance() * 16.0F;
      RenderTarget main = mc.gameRenderer.mainRenderTarget();
      p.aspect = main.height == 0 ? 1.0F : (float)main.width / main.height;
      p.overworld = world.dimensionType().skybox() == Skybox.OVERWORLD;
      Bioluminescence.fill(p);
      MarineLayer.fill(p);
      ColorGrading.fill(p);
      if (!anyOn()) {
         release();
         return false;
      } else {
         ensureResources(mc);
         upload();
         return true;
      }
   }

   private static void celestial(float angle, Vector3f out) {
      scratch.identity().rotateY(-1.5707964F).rotateX(angle);
      scratch.transformDirection(0.0F, 1.0F, 0.0F, out);
   }

   private static int uboSize() {
      Std140SizeCalculator c = new Std140SizeCalculator();
      c.putMat4f();

      for (int i = 0; i < 31; i++) {
         c.putVec4();
      }

      return c.get();
   }

   private static void upload() {
      ubo.rotate();

      try (MappedView view = ubo.currentBuffer().map(false, true)) {
         Std140Builder b = Std140Builder.intoBuffer(view.data());
         FxParams p = params;
         b.putMat4f(p.invViewProj);
         b.putVec4(p.camX, p.camY, p.camZ, p.seconds);
         b.putVec4(p.sunX, p.sunY, p.sunZ, p.night);
         b.putVec4(p.moonX, p.moonY, p.moonZ, p.moonBright);
         b.putVec4(p.underwater ? 1.0F : 0.0F, p.rain, p.renderDistance, 0.0F);
         b.putVec4(p.bioStrength, p.bioShore, p.bioSparkle, p.bioPatches);
         b.putVec4(p.bioDeep[0], p.bioDeep[1], p.bioDeep[2], p.bioWake);
         b.putVec4(p.bioBright[0], p.bioBright[1], p.bioBright[2], p.bioMotes);
         b.putVec4(p.bioReach, p.bioWaveSpeed, 0.0F, p.bioFloor);
         float[] w = p.wakes;

         for (int i = 0; i < 12; i++) {
            b.putVec4(w[i * 4], w[i * 4 + 1], w[i * 4 + 2], w[i * 4 + 3]);
         }

         b.putVec4(p.fogDensity, p.fogBase, p.fogFalloff, p.fogMaxCover);
         b.putVec4(p.fogColor[0], p.fogColor[1], p.fogColor[2], p.fogSunGlow);
         b.putVec4(p.fogStart, p.fogPatchiness, p.fogPatchSize, p.fogStrength);
         b.putVec4(p.fogDriftX, p.fogDriftZ, p.fogChurn, p.fogSkyReach);
         b.putVec4(p.fogCamU, p.fogCamV, 0.0F, 0.0F);
         b.putVec4(p.exposure, p.brightness, p.contrast, p.saturation);
         b.putVec4(p.temperature, p.tint, p.gamma, p.vibrance);
         b.putVec4(p.highlights, p.shadows, p.vignette, p.grain);
         b.putVec4(p.fade, p.gradeMix, p.seconds, p.aspect);
         b.putVec4(p.shadowTint[0], p.shadowTint[1], p.shadowTint[2], p.shadowTintAmount);
         b.putVec4(p.highlightTint[0], p.highlightTint[1], p.highlightTint[2], p.highlightTintAmount);
      }
   }

   private static void draw(
      String label, RenderPipeline pipeline, GpuTextureView target, GpuTextureView sampler0, GpuTextureView sampler1, GpuTextureView sampler2
   ) {
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

      try (RenderPass pass = encoder.createRenderPass(() -> label, target, Optional.empty())) {
         pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
         pass.setUniform("SpecterFx", ubo.currentBuffer());
         boolean frame = pipeline == GRADE;
         pass.setUniform(
            "Sampler0",
            sampler0,
            frame ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST) : RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true)
         );
         if (sampler1 != null) {
            pass.setUniform("Sampler1", sampler1, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         }

         if (sampler2 != null) {
            pass.setUniform("Sampler2", sampler2, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         }

         pass.setVertexBuffer(0, triangle.slice());
         pass.draw(3, 1, 0, 0);
      }
   }

   private static TextureTarget copyDepth(TextureTarget copy, String name, RenderTarget main) {
      return GpuKit.copyDepth(copy, name, main);
   }

   private static void copyFrame(RenderTarget main) {
      GpuDevice device = RenderSystem.getDevice();
      if (frameCopy == null || frameCopy.getWidth(0) != main.width || frameCopy.getHeight(0) != main.height) {
         if (frameCopyView != null) {
            frameCopyView.close();
         }

         if (frameCopy != null) {
            frameCopy.close();
         }

         frameCopy = device.createTexture(() -> "Specter fx frame copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, main.width, main.height, 1, 1);
         frameCopyView = device.createTextureView(frameCopy);
      }

      device.createCommandEncoder().copyTextureToTexture(main.getColorTexture(), frameCopy, 0, 0, 0, 0, 0, main.width, main.height);
   }

   private static void ensureResources(Minecraft mc) {
      GpuDevice device = RenderSystem.getDevice();
      if (ubo == null) {
         ubo = GpuKit.uniformRing("Specter fx UBO", UBO_SIZE);
      }

      if (triangle == null) {
         triangle = GpuKit.fullscreenTriangle("Specter fx triangle");
      }

      if (noise == null) {
         noise = loadNoise(mc);
         noiseView = device.createTextureView(noise);
      }
   }

   private static GpuTexture loadNoise(Minecraft mc) {
      Optional<Resource> resource = mc.getResourceManager().getResource(NOISE);
      NativeImage level;
      if (resource.isEmpty()) {
         LOG.warn("Missing noise texture {}, the world effects will look flat", NOISE);
         level = new NativeImage(1, 1, true);
         level.setPixel(0, 0, -8355712);
      } else {
         try (InputStream in = resource.get().open()) {
            level = NativeImage.read(in);
         } catch (Exception var15) {
            throw new IllegalStateException("Could not read " + NOISE, var15);
         }
      }

      return GpuKit.uploadMipmapped("Specter fx noise", level);
   }



   public static void release() {
      if (!anyOn() && RenderSystem.isOnRenderThread()) {
         if (ubo != null) {
            ubo.close();
            ubo = null;
         }

         if (triangle != null) {
            triangle.close();
            triangle = null;
         }

         if (noiseView != null) {
            noiseView.close();
         }

         if (noise != null) {
            noise.close();
         }

         noise = null;
         noiseView = null;
         if (frameCopyView != null) {
            frameCopyView.close();
         }

         if (frameCopy != null) {
            frameCopy.close();
         }

         frameCopy = null;
         frameCopyView = null;
         if (solidDepth != null) {
            solidDepth.destroyBuffers();
            solidDepth = null;
         }

         if (worldDepth != null) {
            worldDepth.destroyBuffers();
            worldDepth = null;
         }

         solidDepthFrame = -1L;
         preparedFrame = -1L;
      }
   }
}
