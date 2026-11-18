package com.jfant.specter.sky;

import com.jfant.specter.core.MixinPlugin;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.mixin.ClientWorldInvoker;
import com.jfant.specter.modules.Atmosphere;
import com.jfant.specter.modules.NebulaNight;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkyRenderer {
   private static final Logger LOG = LoggerFactory.getLogger("Specter Sky");
   private static final String NS = "specter-client";
   private static final Identifier NOISE = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/noise.png");
   private static final Identifier MILKY_WAY = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/milkyway.png");
   private static final Identifier NEBULA = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/nebula.png");
   public static final double WRAP = 10240.0;
   private static final RenderPipeline SKY = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/specter_sky"))
      .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
      .withUniform("Projection", UniformType.UNIFORM_BUFFER)
      .withUniform("Fog", UniformType.UNIFORM_BUFFER)
      .withUniform("SpecterSky", UniformType.UNIFORM_BUFFER)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withSampler("Sampler0")
      .withSampler("Sampler1")
      .withSampler("Sampler2")
      .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.TRIANGLES)
      .build();
   private static final RenderPipeline CLOUDS = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/specter_clouds"))
      .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
      .withUniform("Projection", UniformType.UNIFORM_BUFFER)
      .withUniform("SpecterSky", UniformType.UNIFORM_BUFFER)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_clouds"))
      .withSampler("Sampler0")
      .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withCull(false)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.TRIANGLES)
      .build();
   private static final RenderPipeline SHADOWS = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/specter_cloud_shadow"))
      .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
      .withUniform("Projection", UniformType.UNIFORM_BUFFER)
      .withUniform("SpecterSky", UniformType.UNIFORM_BUFFER)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_cloud_shadow"))
      .withSampler("Sampler0")
      .withSampler("Sampler1")
      .withBlend(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO))
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.TRIANGLES)
      .build();
   private static final int UBO_SIZE = uboSize();
   private static final SkyParams params = new SkyParams();
   private static final Matrix4f scratch = new Matrix4f();
   private static final Vector3f sun = new Vector3f();
   private static final Vector3f moon = new Vector3f();
   private static MappableRingBuffer ubo;
   private static GpuBuffer triangle;
   private static GpuTexture noise;
   private static GpuTexture milkyWay;
   private static GpuTexture nebula;
   private static GpuTextureView noiseView;
   private static GpuTextureView milkyWayView;
   private static GpuTextureView nebulaView;
   private static TextureTarget depthCopy;
   private static boolean afterSolidPending;
   private static long gatheredAt;
   private static boolean failed;

   private SkyRenderer() {
   }

   private static boolean shaderPack() {
      return MixinPlugin.isIrisPresent && RenderUtils.isShaderPackInUse();
   }

   public static boolean replacesSunsetGlow() {
      return !failed && Atmosphere.drawsSky() && !safeShaderPack();
   }

   public static boolean replacesStars() {
      return !failed && NebulaNight.drawsStars() && !safeShaderPack();
   }

   private static boolean safeShaderPack() {
      try {
         return shaderPack();
      } catch (LinkageError | RuntimeException var1) {
         fail(var1);
         return true;
      }
   }

   public static void drawSky() {
      afterSolidPending = false;
      boolean atmosphere = Atmosphere.drawsSky() || Atmosphere.drawsClouds();
      boolean nebulaOn = NebulaNight.on();
      if (!failed && (atmosphere || nebulaOn)) {
         Minecraft mc = Minecraft.getInstance();
         ClientLevel world = mc.level;
         Camera camera = mc.gameRenderer.getMainCamera();
         if (world != null && camera != null) {
            try {
               if (shaderPack()) {
                  return;
               }

               float tickProgress = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
               gather(mc, world, camera, tickProgress);
               Atmosphere.fill(params);
               NebulaNight.fill(params);
               boolean skyWanted = params.atmosphereSky || params.nebula && params.night > 0.001F;
               boolean afterWanted = params.lowClouds || params.cirrus > 0.0F;
               if (!skyWanted && !afterWanted) {
                  return;
               }

               ensureResources(mc);
               upload();
               if (skyWanted) {
                  RenderTarget main = mc.getMainRenderTarget();
                  draw("Specter sky", SKY, main.getColorTextureView(), main.getDepthTextureView(), true, false);
               }

               afterSolidPending = afterWanted;
            } catch (LinkageError | RuntimeException var9) {
               fail(var9);
            }
         }
      }
   }

   public static void afterSolidTerrain() {
      if (afterSolidPending) {
         afterSolidPending = false;
         if (!failed) {
            Minecraft mc = Minecraft.getInstance();

            try {
               RenderTarget main = mc.getMainRenderTarget();
               if (params.shadow > 0.0F && params.lowClouds && params.daylight > 0.01F) {
                  copyDepth(main);
                  draw("Specter cloud shadows", SHADOWS, main.getColorTextureView(), null, false, true);
               }

               draw("Specter clouds", CLOUDS, main.getColorTextureView(), main.getDepthTextureView(), false, false);
            } catch (LinkageError | RuntimeException var2) {
               fail(var2);
            }
         }
      }
   }

   private static void draw(String label, RenderPipeline pipeline, GpuTextureView color, GpuTextureView depth, boolean skyTextures, boolean depthSampler) {
      GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
         .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

      try (RenderPass pass = encoder.createRenderPass(() -> label, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
         pass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("DynamicTransforms", transforms);
         pass.setUniform("SpecterSky", ubo.currentBuffer());
         pass.bindTexture("Sampler0", noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true));
         if (skyTextures) {
            GpuSampler wrapAround = RenderSystem.getSamplerCache()
               .getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
            pass.bindTexture("Sampler1", milkyWayView, wrapAround);
            pass.bindTexture("Sampler2", nebulaView, wrapAround);
         }

         if (depthSampler) {
            pass.bindTexture("Sampler1", depthCopy.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         }

         pass.setVertexBuffer(0, triangle);
         pass.draw(0, 3);
      }
   }

   private static void copyDepth(RenderTarget main) {
      if (depthCopy == null) {
         depthCopy = new TextureTarget("specter-sky-depth", main.width, main.height, true);
      } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
         depthCopy.resize(main.width, main.height);
      }

      depthCopy.copyDepthFrom(main);
   }

   private static void fail(Throwable e) {
      failed = true;
      afterSolidPending = false;
      LOG.error("Specter sky stopped drawing after an error; vanilla's sky is back until restart.", e);
   }

   private static void gather(Minecraft mc, ClientLevel world, Camera camera, float tickProgress) {
      gatheredAt = System.nanoTime();
      params.reset();
      EnvironmentAttributeProbe env = camera.attributeProbe();
      float sunAngle = env.getValue(EnvironmentAttributes.SUN_ANGLE, tickProgress) * 0.017453292F;
      float moonAngle = env.getValue(EnvironmentAttributes.MOON_ANGLE, tickProgress) * 0.017453292F;
      float starAngle = env.getValue(EnvironmentAttributes.STAR_ANGLE, tickProgress) * 0.017453292F;
      celestial(sunAngle, sun);
      celestial(moonAngle, moon);
      params.sunX = sun.x;
      params.sunY = sun.y;
      params.sunZ = sun.z;
      params.moonX = moon.x;
      params.moonY = moon.y;
      params.moonZ = moon.z;
      MoonPhase phase = env.getValue(EnvironmentAttributes.MOON_PHASE, tickProgress);
      params.moonBright = (1.0F + Mth.cos(phase.index() * 3.1415927F / 4.0F)) / 2.0F * 0.85F + 0.15F;
      params.seconds = (float)(System.nanoTime() / 1.0E9 % 3600.0);
      params.daylight = Mth.clamp(sun.y * 2.0F + 0.5F, 0.0F, 1.0F);
      params.night = smoothstep(0.02F, -0.25F, sun.y);
      params.rain = world.getRainLevel(tickProgress);
      params.thunder = world.getThunderLevel(tickProgress);
      params.flash = Math.min(1.0F, ((ClientWorldInvoker)world).specter$lightningTicksLeft() / 2.0F);
      double x = camera.position().x;
      double y = camera.position().y;
      double z = camera.position().z;
      params.camX = (float)(x - Math.floor(x / 10240.0) * 10240.0);
      params.camY = (float)y;
      params.camZ = (float)(z - Math.floor(z / 10240.0) * 10240.0);
      params.renderDistance = mc.gameRenderer.getRenderDistance();
      params.starRot.identity().rotateX(-starAngle).rotateY(1.5707964F);
      params.starRot.mulLocal(NebulaNight.galaxyFrame());
   }

   private static void celestial(float angle, Vector3f out) {
      scratch.identity().rotateY(-1.5707964F).rotateX(angle);
      scratch.transformDirection(0.0F, 1.0F, 0.0F, out);
   }

   public static float smoothstep(float edge0, float edge1, float x) {
      float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   public static boolean fresh() {
      return gatheredAt != 0L && System.nanoTime() - gatheredAt < 500000000L;
   }

   public static SkyParams params() {
      return params;
   }

   private static int uboSize() {
      Std140SizeCalculator c = new Std140SizeCalculator();

      for (int i = 0; i < 14; i++) {
         c.putVec4();
      }

      c.putMat4f();

      for (int i = 0; i < 5; i++) {
         c.putVec4();
      }

      for (int i = 0; i < 88; i++) {
         c.putVec4();
      }

      c.putVec4();
      return c.get();
   }

   private static void upload() {
      ubo.rotate();

      try (MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(ubo.currentBuffer(), false, true)) {
         Std140Builder b = Std140Builder.intoBuffer(view.data());
         SkyParams p = params;
         b.putVec4(p.sunX, p.sunY, p.sunZ, p.seconds);
         b.putVec4(p.moonX, p.moonY, p.moonZ, p.moonBright);
         b.putVec4(p.atmosphereSky ? 1.0F : 0.0F, p.nebula ? 1.0F : 0.0F, p.daylight, p.night);
         b.putVec4(p.rain, p.thunder, p.flash, p.cloudQuality);
         b.putVec4(p.camX, p.camY, p.camZ, p.renderDistance);
         b.putVec4(p.horizonBlend, 0.0F, p.exposure, p.fog);
         b.putVec4(p.saturation, p.brightness, p.sunGlow, p.sunsetStrength);
         b.putVec4(p.haze, p.customMix, 0.0F, p.morning ? 1.0F : 0.0F);
         b.putVec4(p.zenith[0], p.zenith[1], p.zenith[2], 0.0F);
         b.putVec4(p.horizon[0], p.horizon[1], p.horizon[2], 0.0F);
         b.putVec4(p.sunset[0], p.sunset[1], p.sunset[2], 0.0F);
         b.putVec4(p.coverage, p.opacity, p.cloudHeight, p.thickness);
         b.putVec4(p.cirrus, p.shadow, p.lowClouds ? 1.0F : 0.0F, p.steps);
         b.putVec4(p.windX, p.windZ, p.cirrusX, p.cirrusZ);
         b.putMat4f(p.starRot);
         b.putVec4(p.density, p.starBrightness, p.twinkle, p.skyBrightness);
         b.putVec4(p.milkyWay, p.nebulaIntensity, p.horizonGlow, p.aurora);
         b.putVec4(p.colorA[0], p.colorA[1], p.colorA[2], p.dust);
         b.putVec4(p.colorB[0], p.colorB[1], p.colorB[2], p.stars ? 1.0F : 0.0F);
         b.putVec4(p.constellations, p.meteorCount, p.lineCount, p.nightQuality);
         float[] m = p.meteors;

         for (int i = 0; i < 24; i++) {
            b.putVec4(m[i * 4], m[i * 4 + 1], m[i * 4 + 2], m[i * 4 + 3]);
         }

         float[] l = p.lines;

         for (int i = 0; i < 64; i++) {
            b.putVec4(l[i * 4], l[i * 4 + 1], l[i * 4 + 2], l[i * 4 + 3]);
         }

         b.putVec4(p.cloudSoftness, p.cloudDetail, p.cloudScale, p.cloudTower);
      }
   }

   private static void ensureResources(Minecraft mc) {
      GpuDevice device = RenderSystem.getDevice();
      if (ubo == null) {
         ubo = new MappableRingBuffer(() -> "Specter sky UBO", 130, UBO_SIZE);
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
            triangle = device.createBuffer(() -> "Specter sky triangle", 32, data);
         } finally {
            MemoryUtil.memFree(data);
         }
      }

      if (noise == null) {
         noise = loadMipmapped(mc, NOISE, "Specter sky noise");
         noiseView = device.createTextureView(noise);
      }

      if (milkyWay == null) {
         milkyWay = load(mc, MILKY_WAY, "Specter Milky Way");
         milkyWayView = device.createTextureView(milkyWay);
      }

      if (nebula == null) {
         nebula = load(mc, NEBULA, "Specter nebulae");
         nebulaView = device.createTextureView(nebula);
      }
   }

   private static NativeImage read(Minecraft mc, Identifier id) {
      Optional<Resource> resource = mc.getResourceManager().getResource(id);
      if (resource.isEmpty()) {
         LOG.warn("Missing sky texture {}, drawing without it", id);
         NativeImage blank = new NativeImage(1, 1, true);
         blank.setPixel(0, 0, -16777216);
         return blank;
      } else {
         try {
            NativeImage var4;
            try (InputStream in = resource.get().open()) {
               var4 = NativeImage.read(Format.RGBA, in);
            }

            return var4;
         } catch (Exception var8) {
            throw new IllegalStateException("Could not read " + id, var8);
         }
      }
   }

   private static GpuTexture load(Minecraft mc, Identifier id, String label) {
      GpuTexture var5;
      try (NativeImage image = read(mc, id)) {
         GpuTexture texture = RenderSystem.getDevice().createTexture(() -> label, 5, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);
         RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
         var5 = texture;
      }

      return var5;
   }

   private static GpuTexture loadMipmapped(Minecraft mc, Identifier id, String label) {
      NativeImage level = read(mc, id);
      int levels = 1 + Mth.log2(Math.max(1, Math.min(level.getWidth(), level.getHeight())));
      GpuTexture texture = RenderSystem.getDevice().createTexture(() -> label, 5, TextureFormat.RGBA8, level.getWidth(), level.getHeight(), 1, levels);
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
      if (!Atmosphere.on() && !NebulaNight.on()) {
         RenderSystem.assertOnRenderThread();
         afterSolidPending = false;
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

         if (milkyWayView != null) {
            milkyWayView.close();
         }

         if (milkyWay != null) {
            milkyWay.close();
         }

         if (nebulaView != null) {
            nebulaView.close();
         }

         if (nebula != null) {
            nebula.close();
         }

         nebula = null;
         milkyWay = null;
         noise = null;
         nebulaView = null;
         milkyWayView = null;
         noiseView = null;
         if (depthCopy != null) {
            depthCopy.destroyBuffers();
            depthCopy = null;
         }
      }
   }
}
