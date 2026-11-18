package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public class HoleEsp extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> minDepth = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-depth")
            .description("How tall an open column must be to count as a shaft.")
            .defaultValue(4)
            .range(1, 20)
            .sliderRange(1, 20)
            .onChanged(v -> this.rescanAll())
            .build()
      );
   private final Setting<Boolean> oneByOne = this.sgGeneral
      .add(new BoolSetting.Builder().name("1x1-holes").description("Show 1x1 shafts.").defaultValue(true).onChanged(v -> this.rescanAll()).build());
   private final Setting<Boolean> threeByOne = this.sgGeneral
      .add(new BoolSetting.Builder().name("3x1-holes").description("Show 3x1 shafts.").defaultValue(true).onChanged(v -> this.rescanAll()).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode")).description("How shafts are drawn."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").description("Fill of the shafts.").defaultValue(new SettingColor(255, 255, 255, 45)).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(new ColorSetting.Builder().name("line-color").description("Outline of the shafts.").defaultValue(new SettingColor(255, 255, 255, 220)).build());
   private final Map<Long, List<HoleEsp.Shaft>> holes = new ConcurrentHashMap<>();
   private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
   private ExecutorService worker;
   private int ticks;

   public HoleEsp() {
      super(SpecterClientAddon.ESP, "hole-esp", "Shows long vertical 1x1 and 3x1 shafts, to see if someone already found a base.");
   }

   @Override
   public void onActivate() {
      this.holes.clear();
      this.dirty.clear();
      this.worker = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "specter-hole-esp");
         thread.setDaemon(true);
         return thread;
      });
      this.rescanAll();
   }

   @Override
   public void onDeactivate() {
      if (this.worker != null) {
         this.worker.shutdownNow();
         this.worker = null;
      }

      this.holes.clear();
      this.dirty.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.holes.clear();
      this.dirty.clear();
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      LevelChunk chunk = event.chunk();
      this.submit(chunk.getPos().x(), chunk.getPos().z());
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      this.dirty.add(ChunkPos.pack(event.pos.getX() >> 4, event.pos.getZ() >> 4));
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         this.ticks++;
         if (this.ticks % 20 == 0 && !this.dirty.isEmpty()) {
            for (Long key : List.copyOf(this.dirty)) {
               this.dirty.remove(key);
               this.submit(ChunkPos.getX(key), ChunkPos.getZ(key));
            }
         }

         if (this.ticks % 100 == 0) {
            ChunkPos here = this.mc.player.chunkPosition();
            int keep = this.mc.options.getEffectiveRenderDistance() + 2;
            this.holes.keySet().removeIf(key -> {
               int cx = ChunkPos.getX(key);
               int cz = ChunkPos.getZ(key);
               return (Math.abs(cx - here.x()) > keep || Math.abs(cz - here.z()) > keep) && !this.mc.level.getChunkSource().hasChunk(cx, cz);
            });
         }
      }
   }

   private void rescanAll() {
      if (this.isActive() && this.mc.level != null && this.mc.player != null) {
         this.holes.clear();
         ChunkPos here = this.mc.player.chunkPosition();
         int radius = this.mc.options.getEffectiveRenderDistance() + 1;

         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (this.mc.level.getChunkSource().hasChunk(here.x() + dx, here.z() + dz)) {
                  this.submit(here.x() + dx, here.z() + dz);
               }
            }
         }
      }
   }

   private void submit(int chunkX, int chunkZ) {
      ExecutorService w = this.worker;
      if (w != null) {
         int depth = this.minDepth.get();
         boolean one = this.oneByOne.get();
         boolean three = this.threeByOne.get();

         try {
            w.submit(() -> this.scan(chunkX, chunkZ, depth, one, three));
         } catch (RejectedExecutionException var8) {
         }
      }
   }

   private void scan(int chunkX, int chunkZ, int depth, boolean one, boolean three) {
      ClientLevel world = this.mc.level;
      if (world != null && world.getChunkSource().hasChunk(chunkX, chunkZ)) {
         List<HoleEsp.Shaft> found = new ArrayList<>();
         if (one || three) {
            int bottom = world.getMinY();
            int top = world.getMaxY();
            MutableBlockPos m = new MutableBlockPos();

            for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
               for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                  if (one) {
                     column(world, found, m, x, z, bottom, top, depth, HoleEsp.Shape.ONE);
                  }

                  if (three) {
                     column(world, found, m, x, z, bottom, top, depth, HoleEsp.Shape.THREE_X);
                     column(world, found, m, x, z, bottom, top, depth, HoleEsp.Shape.THREE_Z);
                  }
               }
            }
         }

         long key = ChunkPos.pack(chunkX, chunkZ);
         if (found.isEmpty()) {
            this.holes.remove(key);
         } else {
            this.holes.put(key, found);
         }
      }
   }

   private static void column(ClientLevel world, List<HoleEsp.Shaft> out, MutableBlockPos m, int x, int z, int bottom, int top, int depth, HoleEsp.Shape shape) {
      int run = 0;

      for (int y = bottom; y <= top + 1; y++) {
         if (y <= top && matches(world, m, x, y, z, shape)) {
            run++;
         } else {
            if (run >= depth) {
               out.add(new HoleEsp.Shaft(x, y - run, z, y, shape));
            }

            run = 0;
         }
      }
   }

   private static boolean matches(ClientLevel w, MutableBlockPos m, int x, int y, int z, HoleEsp.Shape shape) {
      return switch (shape) {
         case ONE -> open(w, m, x, y, z) && !open(w, m, x - 1, y, z) && !open(w, m, x + 1, y, z) && !open(w, m, x, y, z - 1) && !open(w, m, x, y, z + 1);
         case THREE_X -> open(w, m, x, y, z)
            && open(w, m, x + 1, y, z)
            && open(w, m, x + 2, y, z)
            && !open(w, m, x - 1, y, z)
            && !open(w, m, x + 3, y, z)
            && !open(w, m, x, y, z - 1)
            && !open(w, m, x, y, z + 1)
            && !open(w, m, x + 1, y, z - 1)
            && !open(w, m, x + 1, y, z + 1)
            && !open(w, m, x + 2, y, z - 1)
            && !open(w, m, x + 2, y, z + 1);
         case THREE_Z -> open(w, m, x, y, z)
            && open(w, m, x, y, z + 1)
            && open(w, m, x, y, z + 2)
            && !open(w, m, x, y, z - 1)
            && !open(w, m, x, y, z + 3)
            && !open(w, m, x - 1, y, z)
            && !open(w, m, x + 1, y, z)
            && !open(w, m, x - 1, y, z + 1)
            && !open(w, m, x + 1, y, z + 1)
            && !open(w, m, x - 1, y, z + 2)
            && !open(w, m, x + 1, y, z + 2);
      };
   }

   private static boolean open(ClientLevel world, MutableBlockPos m, int x, int y, int z) {
      m.set(x, y, z);
      return world.getBlockState(m).getCollisionShape(world, m).isEmpty();
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null && !this.holes.isEmpty()) {
         SettingColor sides = this.sideColor.get();
         SettingColor lines = this.lineColor.get();
         ShapeMode mode = this.shapeMode.get();
         ChunkPos here = this.mc.player.chunkPosition();
         int view = this.mc.options.getEffectiveRenderDistance();

         for (Entry<Long, List<HoleEsp.Shaft>> entry : this.holes.entrySet()) {
            int cx = ChunkPos.getX(entry.getKey());
            int cz = ChunkPos.getZ(entry.getKey());
            if (Math.abs(cx - here.x()) <= view && Math.abs(cz - here.z()) <= view) {
               for (HoleEsp.Shaft s : entry.getValue()) {
                  event.renderer.box(s.x(), s.minY(), s.z(), s.x() + s.shape().sizeX, s.maxY(), s.z() + s.shape().sizeZ, sides, lines, mode, 0);
               }
            }
         }
      }
   }

   private record Shaft(int x, int minY, int z, int maxY, HoleEsp.Shape shape) {
   }

   private static enum Shape {
      ONE(1, 1),
      THREE_X(3, 1),
      THREE_Z(1, 3);

      final int sizeX;
      final int sizeZ;

      private Shape(int sizeX, int sizeZ) {
         this.sizeX = sizeX;
         this.sizeZ = sizeZ;
      }
   }
}
