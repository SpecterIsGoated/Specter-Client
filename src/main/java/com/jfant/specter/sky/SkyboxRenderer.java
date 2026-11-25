package com.jfant.specter.sky;

import com.jfant.specter.modules.Skyboxes;
import com.jfant.specter.render.GpuKit;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the {@link Skyboxes} 360 degree panorama (an equirectangular JPEG in textures/sky/skybox) behind the world:
 * one full-screen triangle on the far plane after the solid terrain, so only pixels where nothing was drawn (the sky)
 * get it. The picture is decoded off the render thread the first time a sky is picked and kept until another is.
 */
public final class SkyboxRenderer {
   private static final Logger LOG = LoggerFactory.getLogger("Specter Skybox");
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/specter_skybox"))
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_sky"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/specter_skybox"))
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .withCull(false)
      .withVertexBinding(0, DefaultVertexFormat.POSITION)
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .build();

   private static GpuBuffer triangle;
   private static GpuTexture texture;
   private static GpuTextureView view;
   private static String loaded;
   private static String loading;
   private static CompletableFuture<Decoded> pending;
   private static boolean failed;
   /** Average colour just above the picture's horizon (0-1): the terrain fog fades into it. */
   private static final Vector3f horizon = new Vector3f(0.6F, 0.7F, 0.85F);

   private record Decoded(String file, NativeImage image, float r, float g, float b) {
   }

   private SkyboxRenderer() {
   }

   /** Whether the chosen sky's picture is on the GPU and this world has an overworld-style sky. */
   public static boolean ready() {
      Skyboxes.Sky sky = Skyboxes.current();
      return !failed && sky != null && texture != null && sky.file.equals(loaded) && overworldSky();
   }

   public static Vector3f horizon() {
      return horizon;
   }

   private static boolean overworldSky() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null && mc.level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
   }

   /**
    * Once a frame from the start of the sky render (render thread, no pass open): starts loading the picked sky,
    * uploads a finished one, frees one no longer used. Kept apart from {@link #ready()} so the answer cannot change
    * between the two checks of one frame.
    */
   public static void frame() {
      if (failed) return;
      Skyboxes.Sky sky = Skyboxes.current();
      if (sky == null) {
         if (texture != null) release();
         return;
      }
      try {
         if (!sky.file.equals(loaded) && !sky.file.equals(loading) && pending == null) {
            loading = sky.file;
            String file = sky.file;
            pending = CompletableFuture.supplyAsync(() -> decode(file));
         }
         if (pending != null && pending.isDone()) {
            Decoded d = pending.join();
            pending = null;
            loading = null;
            if (d != null) {
               try {
                  closeTexture();
                  texture = GpuKit.upload("Specter skybox " + d.file(), d.image());
                  view = RenderSystem.getDevice().createTextureView(texture);
                  loaded = d.file();
                  horizon.set(d.r(), d.g(), d.b());
               } finally {
                  d.image().close();
               }
            }
         }
      } catch (RuntimeException | LinkageError e) {
         fail(e);
      }
   }

   private static Decoded decode(String file) {
      Identifier id = Identifier.fromNamespaceAndPath("specter-client", "textures/sky/skybox/" + file + ".jpg");
      Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
      if (resource.isEmpty()) {
         LOG.warn("Missing skybox {}", id);
         return null;
      }
      try (InputStream in = resource.get().open()) {
         BufferedImage img = ImageIO.read(in);
         if (img == null) throw new IllegalStateException("not a readable JPEG");
         int w = img.getWidth(), h = img.getHeight();
         int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
         NativeImage out = new NativeImage(w, h, false);
         double r = 0, g = 0, b = 0;
         long n = 0;
         int y0 = (int)(h * 0.44), y1 = (int)(h * 0.5);
         for (int y = 0; y < h; y++) {
            int row = y * w;
            boolean band = y >= y0 && y < y1;
            for (int x = 0; x < w; x++) {
               int c = rgb[row + x];
               out.setPixel(x, y, 0xFF000000 | c);
               if (band && (x & 7) == 0) {
                  r += c >> 16 & 255;
                  g += c >> 8 & 255;
                  b += c & 255;
                  n++;
               }
            }
         }
         float k = n == 0 ? 0 : 1.0F / (255.0F * n);
         return new Decoded(file, out, (float)(r * k), (float)(g * k), (float)(b * k));
      } catch (Exception e) {
         LOG.error("Could not read skybox {}", id, e);
         return null;
      }
   }

   /** After the solid world: the panorama into every pixel still at the far plane. */
   public static void draw() {
      if (!ready()) return;
      try {
         Minecraft mc = Minecraft.getInstance();
         RenderTarget main = mc.gameRenderer.mainRenderTarget();
         if (triangle == null) triangle = GpuKit.fullscreenTriangle("Specter skybox triangle");
         float b = Skyboxes.brightness();
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         try (RenderPass pass = encoder.createRenderPass(() -> "Specter skybox", main.getColorTextureView(), Optional.empty(),
               main.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
               .writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(b, b, b, 1.0F), new Vector3f(Skyboxes.yawRadians(), 0, 0), new Matrix4f());
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform("Sampler0", view, RenderSystem.getSamplerCache()
               .getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false));
            pass.setVertexBuffer(0, triangle.slice());
            pass.draw(3, 1, 0, 0);
         }
      } catch (RuntimeException | LinkageError e) {
         fail(e);
      }
   }

   private static void fail(Throwable e) {
      failed = true;
      LOG.error("Skybox stopped drawing after an error; the normal sky is back until restart.", e);
   }

   private static void closeTexture() {
      if (view != null) view.close();
      if (texture != null) texture.close();
      view = null;
      texture = null;
      loaded = null;
   }

   public static void release() {
      closeTexture();
      if (triangle != null) {
         triangle.close();
         triangle = null;
      }
   }
}
