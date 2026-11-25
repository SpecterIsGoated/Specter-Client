package com.jfant.specter.sky;

import com.jfant.specter.core.MixinPlugin;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.mixin.ClientWorldInvoker;
import com.jfant.specter.modules.Atmosphere;
import com.jfant.specter.modules.NebulaNight;
import com.jfant.specter.render.GpuKit;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.imageio.ImageIO;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkyRenderer {
   private static final Logger LOG = LoggerFactory.getLogger("Specter Sky");
   private static final String NS = "specter-client";
   private static final Identifier NOISE = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/noise.png");
   private static final Identifier MILKY_WAY = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/milkyway.jpg");
   private static final Identifier NEBULA = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/nebula.jpg");
   public static final double WRAP = 10240.0;
   // 26.x: uniforms are declared through bind-group layouts, depth state through DepthStencilState (reversed Z,
   // so "nearer or equal" is GREATER_THAN_OR_EQUAL), and no depth state at all means no depth test or write.
   private static final BindGroupLayout SKY_UBO = GpuKit.ubo("SpecterSky");
   private static final RenderPipeline SKY = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/specter_sky"))
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(BindGroupLayouts.FOG)
      .withBindGroupLayout(SKY_UBO)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
      .withCull(false)
      .withVertexBinding(0, DefaultVertexFormat.POSITION)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .build();
   private static final RenderPipeline CLOUDS = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/specter_clouds"))
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(SKY_UBO)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_clouds"))
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .withCull(false)
      .withVertexBinding(0, DefaultVertexFormat.POSITION)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .build();
   private static final RenderPipeline SHADOWS = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/specter_cloud_shadow"))
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(SKY_UBO)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_cloud_shadow"))
      .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO)))
      .withCull(false)
      .withVertexBinding(0, DefaultVertexFormat.POSITION)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
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

   /** True when the clouds / cloud shadows still have to be drawn once the solid world is done this frame. */
   public static boolean wantsAfterSolid() {
      return afterSolidPending;
   }

   // 26.x: called from inside vanilla's open sky render pass (right after the sky disc), so the sky is drawn into that
   // pass instead of a new one; the sun, moon and stars that vanilla draws next still land on top of it.
   // 26.x: nothing but drawing may happen while a render pass is open, so the resources and this frame's uniforms
   // are prepared from the start of vanilla's sky render (before its pass opens) and drawSky only binds and draws.
   private static boolean skyPrepared;

   public static void prepareSky() {
      skyPrepared = false;
      afterSolidPending = false;
      boolean atmosphere = Atmosphere.drawsSky() || Atmosphere.drawsClouds();
      boolean nebulaOn = NebulaNight.on();
      if (!failed && (atmosphere || nebulaOn)) {
         Minecraft mc = Minecraft.getInstance();
         ClientLevel world = mc.level;
         Camera camera = mc.gameRenderer.mainCamera();
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
               skyPrepared = skyWanted;
               afterSolidPending = afterWanted;
            } catch (LinkageError | RuntimeException var9) {
               fail(var9);
            }
         }
      }
   }

   public static void drawSky(RenderPass pass) {
      if (skyPrepared && !failed) {
         skyPrepared = false;

         try {
            pass.pushDebugGroup(() -> "Specter sky");
            bind(pass, SKY, true, false);
            pass.popDebugGroup();
         } catch (LinkageError | RuntimeException e) {
            fail(e);
         }
      }
   }

   public static void afterSolidTerrain() {
      if (afterSolidPending) {
         afterSolidPending = false;
         if (!failed) {
            Minecraft mc = Minecraft.getInstance();

            try {
               RenderTarget main = mc.gameRenderer.mainRenderTarget();
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
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

      try (RenderPass pass = depth != null
            ? encoder.createRenderPass(() -> label, color, Optional.empty(), depth, OptionalDouble.empty())
            : encoder.createRenderPass(() -> label, color, Optional.empty())) {
         RenderSystem.bindDefaultUniforms(pass);
         bind(pass, pipeline, skyTextures, depthSampler);
      }
   }

   private static void bind(RenderPass pass, RenderPipeline pipeline, boolean skyTextures, boolean depthSampler) {
      GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
         .writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
      pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
      pass.setUniform("DynamicTransforms", transforms);
      pass.setUniform("SpecterSky", ubo.currentBuffer());
      pass.setUniform("Sampler0", noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true));
      if (skyTextures) {
         GpuSampler wrapAround = RenderSystem.getSamplerCache()
            .getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
         pass.setUniform("Sampler1", milkyWayView, wrapAround);
         pass.setUniform("Sampler2", nebulaView, wrapAround);
      }

      if (depthSampler) {
         pass.setUniform("Sampler1", depthCopy.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      }

      pass.setVertexBuffer(0, triangle.slice());
      pass.draw(3, 1, 0, 0);
   }

   private static void copyDepth(RenderTarget main) {
      depthCopy = GpuKit.copyDepth(depthCopy, "specter-sky-depth", main);
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
      params.renderDistance = mc.options.getEffectiveRenderDistance() * 16.0F;
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

      try (MappedView view = ubo.currentBuffer().map(false, true)) {
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
         ubo = GpuKit.uniformRing("Specter sky UBO", UBO_SIZE);
      }

      if (triangle == null) {
         triangle = GpuKit.fullscreenTriangle("Specter sky triangle");
      }

      if (noise == null) {
         noise = GpuKit.uploadMipmapped("Specter sky noise", read(mc, NOISE));
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
               var4 = id.getPath().endsWith(".jpg") ? readJpeg(in) : NativeImage.read(in);
            }

            return var4;
         } catch (Exception var8) {
            throw new IllegalStateException("Could not read " + id, var8);
         }
      }
   }

   /** NativeImage only reads PNG, so the JPEG sky photos (a fraction of the PNG size) are decoded with the JDK and copied pixel by pixel. */
   private static NativeImage readJpeg(InputStream in) throws IOException {
      BufferedImage decoded = ImageIO.read(in);
      if (decoded == null) {
         throw new IOException("Not a readable JPEG");
      }

      NativeImage image = new NativeImage(decoded.getWidth(), decoded.getHeight(), false);
      for (int y = 0; y < decoded.getHeight(); y++) {
         for (int x = 0; x < decoded.getWidth(); x++) {
            image.setPixel(x, y, 0xFF000000 | decoded.getRGB(x, y) & 0xFFFFFF);
         }
      }

      return image;
   }

   private static GpuTexture load(Minecraft mc, Identifier id, String label) {
      try (NativeImage image = read(mc, id)) {
         return GpuKit.upload(label, image);
      }
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
