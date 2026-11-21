package com.jfant.specter.fx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where rain reaches the ground, for the wet-ground pass: a small map of the columns around the camera, uploaded as an
 * RGBA8 texture. Per column it holds the height rain stops at (the motion-blocking heightmap: leaves, roofs and water
 * all stop it, so the floor under a tree or a roof stays dry) and what kind of surface that is.
 *
 * <p>Texel encoding (see {@link #encode}): r + g * 256 = top height + 64, b = kind (0 dry/no rain here, 128 ground,
 * 255 water), a = 255. Rebuilt about once a second while it is wet, and whenever the camera has moved far from its
 * centre. Render thread only.
 */
public final class WetMap {
   public static final int SIZE = 128;
   public static final int KIND_DRY = 0;
   public static final int KIND_GROUND = 128;
   public static final int KIND_WATER = 255;
   private static final long REBUILD_MS = 1000L;

   private static NativeImage image;
   private static GpuTexture texture;
   private static GpuTextureView view;
   private static int originX;
   private static int originZ;
   private static boolean valid;
   private static long builtAt;
   private static ClientLevel builtFor;
   private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

   private WetMap() {
   }

   /** Rebuilds the map if it is stale or the camera has wandered. False if there is nothing usable. */
   static boolean update(ClientLevel level, double camX, double camZ) {
      int cx = (int)Math.floor(camX);
      int cz = (int)Math.floor(camZ);
      long now = System.currentTimeMillis();
      boolean moved = Math.abs(cx - (originX + SIZE / 2)) > 16 || Math.abs(cz - (originZ + SIZE / 2)) > 16;
      if (!valid || moved || level != builtFor || now - builtAt >= REBUILD_MS) {
         build(level, cx - SIZE / 2, cz - SIZE / 2);
         builtAt = now;
         builtFor = level;
      }

      return valid;
   }

   private static void build(ClientLevel level, int x0, int z0) {
      if (image == null) {
         image = new NativeImage(SIZE, SIZE, false);
      }

      int seaLevel = level.getSeaLevel();
      for (int dz = 0; dz < SIZE; dz++) {
         for (int dx = 0; dx < SIZE; dx++) {
            int x = x0 + dx;
            int z = z0 + dz;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int kind = KIND_DRY;
            if (top > level.getMinY()) {
               POS.set(x, top, z);
               // Biome lookups are the slow part; the noise-free 4x4 biome grid makes every 4th column enough.
               boolean rainHere = ((dx | dz) & 3) != 0 && dx > 0 && dz > 0
                  ? kindAt(dx - 1, dz) != KIND_DRY || kindAt(dx, dz - 1) != KIND_DRY
                  : level.getBiome(POS).value().getPrecipitationAt(POS, seaLevel) == Biome.Precipitation.RAIN;
               if (rainHere) {
                  POS.set(x, top - 1, z);
                  kind = level.getFluidState(POS).isEmpty() ? KIND_GROUND : KIND_WATER;
               }
            }

            image.setPixel(dx, dz, encode(top, kind));
         }
      }

      ensureTexture();
      RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
      originX = x0;
      originZ = z0;
      valid = true;
   }

   private static int kindAt(int dx, int dz) {
      return image.getPixel(dx, dz) & 0xFF;
   }

   /** NativeImage#setPixel takes ARGB on 26.x (r = bits 16-23): r = height low byte, g = high byte, b = kind. */
   static int encode(int topY, int kind) {
      int h = Math.max(0, Math.min(65535, topY + 64));
      return 0xFF000000 | (h & 0xFF) << 16 | (h >> 8 & 0xFF) << 8 | kind & 0xFF;
   }

   private static void ensureTexture() {
      if (texture == null) {
         texture = RenderSystem.getDevice()
            .createTexture(() -> "Specter wet map", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
         view = RenderSystem.getDevice().createTextureView(texture);
      }
   }

   static GpuTextureView view() {
      return view;
   }

   /** The map's corner relative to the camera, in blocks (small numbers, so no float precision trouble far out). */
   static float cornerX(double camX) {
      return (float)(originX - camX);
   }

   static float cornerZ(double camZ) {
      return (float)(originZ - camZ);
   }

   static void release() {
      if (view != null) {
         view.close();
      }

      if (texture != null) {
         texture.close();
      }

      if (image != null) {
         image.close();
      }

      view = null;
      texture = null;
      image = null;
      valid = false;
      builtFor = null;
   }
}
