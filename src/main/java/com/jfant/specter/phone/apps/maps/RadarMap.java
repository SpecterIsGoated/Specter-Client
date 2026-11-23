package com.jfant.specter.phone.apps.maps;

import com.jfant.specter.phone.gfx.PhoneTextures;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * A top-down map of the loaded world around the player, coloured like vanilla maps (with the same north-south
 * height shading). Built a few rows per tick so it never costs a frame, refreshed while the map is on screen.
 * Under a ceiling (the Nether) it maps the first floor below you instead of the roof.
 */
public final class RadarMap {
   public static final int SIZE = 128;
   private final int[] pixels = new int[SIZE * SIZE];
   private final int[] heights = new int[SIZE];
   private int row = -1;
   private int step = 1;
   private int centerX;
   private int centerZ;
   private int builtX;
   private int builtZ;
   private int builtStep;
   private boolean ready;
   @Nullable
   private net.minecraft.resources.Identifier texture;
   private long lastBuild;

   /** Blocks per pixel (1 = 128 blocks across, 2 = 256). */
   public void setScale(int blocksPerPixel) {
      if (blocksPerPixel != this.step) {
         this.step = blocksPerPixel;
         this.row = -1;
      }
   }

   public int scale() {
      return this.builtStep == 0 ? this.step : this.builtStep;
   }

   public int originX() {
      return this.builtX;
   }

   public int originZ() {
      return this.builtZ;
   }

   @Nullable
   public net.minecraft.resources.Identifier texture() {
      return this.ready ? this.texture : null;
   }

   /** Call every tick while the map is visible. */
   public void tick() {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null) return;
      if (this.row < 0) {
         if (System.currentTimeMillis() - this.lastBuild < 1500 && this.ready && this.builtStep == this.step) return;
         this.centerX = mc.player.getBlockX();
         this.centerZ = mc.player.getBlockZ();
         this.row = 0;
      }
      boolean ceiling = level.dimensionType().hasCeiling();
      int startY = mc.player.getBlockY() + 2;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int half = SIZE / 2;
      for (int rows = 0; rows < (ceiling ? 6 : 16) && this.row < SIZE; rows++, this.row++) {
         int wz = this.centerZ + (this.row - half) * this.step;
         for (int px = 0; px < SIZE; px++) {
            int wx = this.centerX + (px - half) * this.step;
            int color = 0;
            int height = Integer.MIN_VALUE;
            if (level.hasChunk(wx >> 4, wz >> 4)) {
               int y;
               if (ceiling) {
                  y = startY;
                  int floor = Math.max(level.getMinY(), startY - 24);
                  while (y > floor && level.getBlockState(pos.set(wx, y, wz)).isAir()) y--;
               } else {
                  y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
               }
               BlockState state = level.getBlockState(pos.set(wx, y, wz));
               MapColor mapColor = state.getMapColor(level, pos);
               height = y;
               if (mapColor != MapColor.NONE) {
                  int prev = this.row > 0 ? this.heights[px] : y;
                  MapColor.Brightness b = y > prev ? MapColor.Brightness.HIGH : y < prev ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                  color = mapColor.calculateARGBColor(b) | 0xFF000000;
               }
            }
            this.heights[px] = height == Integer.MIN_VALUE ? this.heights[px] : height;
            this.pixels[this.row * SIZE + px] = color;
         }
      }
      if (this.row >= SIZE) {
         NativeImage image = new NativeImage(SIZE, SIZE, false);
         for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) image.setPixel(x, y, this.pixels[y * SIZE + x]);
         this.texture = PhoneTextures.image("maps/radar", image);
         this.builtX = this.centerX;
         this.builtZ = this.centerZ;
         this.builtStep = this.step;
         this.ready = true;
         this.row = -1;
         this.lastBuild = System.currentTimeMillis();
      }
   }

   public void release() {
      PhoneTextures.releaseImage("maps/radar");
      this.texture = null;
      this.ready = false;
      this.row = -1;
   }
}
