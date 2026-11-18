package com.jfant.specter.fx;

import com.jfant.specter.anim.Motion;
import com.jfant.specter.core.MixinPlugin;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.RenderAfterWorldEvent;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.modules.Bioluminescence;
import com.jfant.specter.modules.ColorGrading;
import com.jfant.specter.modules.MarineLayer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldFx {
   private static final Logger LOG = LoggerFactory.getLogger("Specter Fx");
   private static final String NS = "specter-client";
   private static final Identifier NOISE = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/noise.png");
   public static final double WRAP = 4096.0;
   private static final RenderPipeline BIO = base("specter_bio")
      .withSampler("Sampler0")
      .withSampler("Sampler1")
      .withSampler("Sampler2")
      .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE))
      .build();
   private static final RenderPipeline MARINE = base("specter_marine")
      .withSampler("Sampler0")
      .withSampler("Sampler1")
      .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ZERO, DestFactor.ONE))
      .build();
   private static final RenderPipeline GRADE = base("specter_grade").withSampler("Sampler0").withoutBlend().build();
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

   private static Builder base(String name) {
      return RenderPipeline.builder()
         .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/" + name))
         .withUniform("SpecterFx", UniformType.UNIFORM_BUFFER)
         .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_fx"))
         .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/" + name))
         .withDepthWrite(false)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withCull(false)
         .withVertexFormat(DefaultVertexFormat.POSITION, Mode.TRIANGLES);
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
      p.renderDistance = mc.gameRenderer.getRenderDistance();
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

      try (MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(ubo.currentBuffer(), false, true)) {
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

      try (RenderPass pass = encoder.createRenderPass(() -> label, target, OptionalInt.empty(), null, OptionalDouble.empty())) {
         pass.setPipeline(pipeline);
         pass.setUniform("SpecterFx", ubo.currentBuffer());
         boolean frame = pipeline == GRADE;
         pass.bindTexture(
            "Sampler0",
            sampler0,
            frame ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST) : RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true)
         );
         if (sampler1 != null) {
            pass.bindTexture("Sampler1", sampler1, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         }

         if (sampler2 != null) {
            pass.bindTexture("Sampler2", sampler2, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         }

         pass.setVertexBuffer(0, triangle);
         pass.draw(0, 3);
      }
   }

   private static TextureTarget copyDepth(TextureTarget copy, String name, RenderTarget main) {
      if (copy == null) {
         copy = new TextureTarget(name, main.width, main.height, true);
      } else if (copy.width != main.width || copy.height != main.height) {
         copy.resize(main.width, main.height);
      }

      copy.copyDepthFrom(main);
      return copy;
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

         frameCopy = device.createTexture(() -> "Specter fx frame copy", 5, TextureFormat.RGBA8, main.width, main.height, 1, 1);
         frameCopyView = device.createTextureView(frameCopy);
      }

      device.createCommandEncoder().copyTextureToTexture(main.getColorTexture(), frameCopy, 0, 0, 0, 0, 0, main.width, main.height);
   }

   private static void ensureResources(Minecraft mc) {
      GpuDevice device = RenderSystem.getDevice();
      if (ubo == null) {
         ubo = new MappableRingBuffer(() -> "Specter fx UBO", 130, UBO_SIZE);
      }

      if (triangle == null) {
         ByteBuffer data = MemoryUtil.memAlloc(36);

         try {
            data.putFloat(-1.0F)
               .putFloat(-1.0F)
               .putFloat(0.0F)
               .putFloat(3.0F)
               .putFloat(-1.0F)
               .putFloat(0.0F)
               .putFloat(-1.0F)
               .putFloat(3.0F)
               .putFloat(0.0F)
               .flip();
            triangle = device.createBuffer(() -> "Specter fx triangle", 32, data);
         } finally {
            MemoryUtil.memFree(data);
         }
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
            level = NativeImage.read(Format.RGBA, in);
         } catch (Exception var15) {
            throw new IllegalStateException("Could not read " + NOISE, var15);
         }
      }

      int levels = 1 + Mth.log2(Math.max(1, Math.min(level.getWidth(), level.getHeight())));
      GpuTexture texture = RenderSystem.getDevice()
         .createTexture(() -> "Specter fx noise", 5, TextureFormat.RGBA8, level.getWidth(), level.getHeight(), 1, levels);
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

      try {
         for (int i = 0; i < levels; i++) {
            encoder.writeToTexture(texture, level, i, 0, 0, 0, level.getWidth(), level.getHeight(), 0, 0);
            if (i + 1 < levels) {
               NativeImage next = halve(level);
               level.close();
               level = next;
            }
         }
      } finally {
         level.close();
      }

      return texture;
   }

   private static NativeImage halve(NativeImage src) {
      int w = Math.max(1, src.getWidth() / 2);
      int h = Math.max(1, src.getHeight() / 2);
      NativeImage out = new NativeImage(w, h, false);

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;

            for (int dy = 0; dy < 2; dy++) {
               for (int dx = 0; dx < 2; dx++) {
                  int c = src.getPixel(Math.min(src.getWidth() - 1, x * 2 + dx), Math.min(src.getHeight() - 1, y * 2 + dy));
                  a += c >>> 24;
                  r += c >> 16 & 0xFF;
                  g += c >> 8 & 0xFF;
                  b += c & 0xFF;
               }
            }

            out.setPixel(x, y, (a + 2) / 4 << 24 | (r + 2) / 4 << 16 | (g + 2) / 4 << 8 | (b + 2) / 4);
         }
      }

      return out;
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
