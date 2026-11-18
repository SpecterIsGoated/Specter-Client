package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.scan.ChunkScanner;
import com.jfant.specter.scan.LightBoxes;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;

public class LightDebug extends Module {
   private static final int CHUNKS_PER_TICK = 24;
   private static final long TICK_BUDGET_NANOS = 1500000L;
   private static final int REST_TICKS = 20;
   private static final int MAX_LIT_CELLS = 250000;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> scanRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("render-radius")
            .description("How many chunks around you to look at.")
            .defaultValue(8)
            .range(1, 16)
            .sliderRange(1, 16)
            .onChanged(v -> this.restart = true)
            .build()
      );
   private final Setting<Integer> minLight = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-light")
            .description("The dimmest light to show, 1-15. A torch is 14, glowing amethyst 5 and under, lava 15.")
            .defaultValue(5)
            .range(1, 15)
            .sliderRange(1, 15)
            .onChanged(v -> this.restart = true)
            .build()
      );
   private final Setting<Integer> maxY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-y")
            .description("Only look at or below this height. Light under y 0 is a base or a lit cave; higher up there is daylight and surface builds.")
            .defaultValue(-1)
            .range(-64, 319)
            .sliderRange(-64, 64)
            .onChanged(v -> this.restart = true)
            .build()
      );
   private final Setting<Boolean> denoise = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("denoise")
            .description("Hide the faint outer glow of far-off lights, so only real lights show.")
            .defaultValue(true)
            .onChanged(v -> this.restart = true)
            .build()
      );
   private final Setting<SettingColor> color = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("The light colour. Brighter light is drawn closer to white.")
            .defaultValue(new SettingColor(255, 204, 0, 128))
            .onChanged(v -> this.colorsDirty = true)
            .build()
      );
   private final Setting<Integer> maxBoxes = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("max-boxes")
            .description("Draw at most this many boxes, the ones nearest you first. Lower it if the frame rate drops near big lit areas.")
            .defaultValue(5000)
            .range(100, 20000)
            .sliderRange(500, 12000)
            .onChanged(v -> this.restart = true)
            .build()
      );
   private ExecutorService worker;
   private volatile int generation;
   private volatile LightBoxes.Result result = LightBoxes.Result.EMPTY;
   private volatile boolean busy;
   private final ArrayDeque<Long> queue = new ArrayDeque<>();
   private List<LightDebug.LightChunk> gathered = new ArrayList<>();
   private LightDebug.Pass pass;
   private int rest;
   private boolean restart;
   private ClientLevel lastWorld;
   private boolean colorsDirty = true;
   private final Color[] sides = new Color[16];
   private final Color[] lines = new Color[16];

   public LightDebug() {
      super(
         SpecterClientAddon.BASES,
         "light-debug",
         "Highlights artificial light underground: torches, lamps, glowstone - anything lit where no daylight reaches."
      );

      for (int i = 0; i < 16; i++) {
         this.sides[i] = new Color();
         this.lines[i] = new Color();
      }
   }

   @Override
   public void onActivate() {
      this.reset();
      this.worker = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "specter-light-debug");
         thread.setDaemon(true);
         return thread;
      });
   }

   @Override
   public void onDeactivate() {
      if (this.worker != null) {
         this.worker.shutdownNow();
         this.worker = null;
      }

      this.reset();
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.reset();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.reset();
   }

   private void reset() {
      this.generation++;
      this.queue.clear();
      this.gathered = new ArrayList<>();
      this.pass = null;
      this.busy = false;
      this.result = LightBoxes.Result.EMPTY;
      this.rest = 0;
      this.restart = false;
      this.lastWorld = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null && this.worker != null) {
         if (this.mc.level != this.lastWorld || this.restart) {
            this.reset();
            this.lastWorld = this.mc.level;
         }

         if (!this.busy) {
            if (this.pass == null) {
               if (this.rest > 0) {
                  this.rest--;
                  return;
               }

               this.startPass();
            }

            this.gather();
            if (this.queue.isEmpty()) {
               List<LightDebug.LightChunk> chunks = this.gathered;
               LightDebug.Pass current = this.pass;
               this.gathered = new ArrayList<>();
               this.pass = null;
               this.rest = 20;
               this.busy = true;

               try {
                  this.worker.submit(() -> {
                     try {
                        LightBoxes.Result built = build(chunks, current);
                        if (current.generation() == this.generation) {
                           this.result = built;
                        }
                     } finally {
                        this.busy = false;
                     }
                  });
               } catch (RejectedExecutionException var5) {
                  this.busy = false;
               }
            }
         }
      }
   }

   private void startPass() {
      ChunkPos center = this.mc.player.chunkPosition();
      int radius = this.scanRadius.get();
      this.pass = new LightDebug.Pass(center.x(), center.z(), this.minLight.get(), this.maxY.get(), this.denoise.get(), this.maxBoxes.get(), this.generation);
      List<long[]> order = new ArrayList<>();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            order.add(new long[]{dx * dx + dz * dz, ChunkPos.pack(center.x() + dx, center.z() + dz)});
         }
      }

      order.sort((a, b) -> Long.compare(a[0], b[0]));
      this.queue.clear();

      for (long[] entry : order) {
         this.queue.add(entry[1]);
      }
   }

   private void gather() {
      long deadline = System.nanoTime() + 1500000L;
      int copied = 0;
      LayerLightEventListener block = this.mc.level.getLightEngine().getLayerListener(LightLayer.BLOCK);
      LayerLightEventListener sky = this.mc.level.getLightEngine().getLayerListener(LightLayer.SKY);

      while (!this.queue.isEmpty() && copied < 24 && System.nanoTime() < deadline) {
         long key = this.queue.poll();
         int chunkX = ChunkPos.getX(key);
         int chunkZ = ChunkPos.getZ(key);
         if (this.mc.level.getChunkSource().getChunkNow(chunkX, chunkZ) != null && ChunkScanner.isLightReady(this.mc.level, chunkX, chunkZ)) {
            int bottom = this.mc.level.getMinSectionY();
            int top = Math.min(this.mc.level.getMaxSectionY() - 1, this.pass.maxY() >> 4);
            int count = top - bottom + 1;
            if (count > 0) {
               DataLayer[] blockLight = new DataLayer[count];
               DataLayer[] skyLight = new DataLayer[count];
               boolean any = false;

               for (int s = 0; s < count; s++) {
                  SectionPos at = SectionPos.of(chunkX, bottom + s, chunkZ);
                  DataLayer b = block.getDataLayerData(at);
                  if (b != null && !b.isEmpty()) {
                     blockLight[s] = b.copy();
                     DataLayer k = sky.getDataLayerData(at);
                     skyLight[s] = k == null ? null : k.copy();
                     any = true;
                  }
               }

               if (any) {
                  this.gathered.add(new LightDebug.LightChunk(chunkX, chunkZ, bottom, blockLight, skyLight));
               }

               copied++;
            }
         }
      }
   }

   private static LightBoxes.Result build(List<LightDebug.LightChunk> chunks, LightDebug.Pass pass) {
      Long2IntOpenHashMap hits = new Long2IntOpenHashMap();

      for (LightDebug.LightChunk chunk : chunks) {
         collect(chunk, pass, hits);
         if (hits.size() >= 250000) {
            break;
         }
      }

      return LightBoxes.build(hits, pass.denoise(), pass.minLight(), pass.maxBoxes(), pass.centerX() * 16 + 8, pass.centerZ() * 16 + 8);
   }

   private static void collect(LightDebug.LightChunk chunk, LightDebug.Pass pass, Long2IntOpenHashMap hits) {
      int startX = chunk.chunkX() << 4;
      int startZ = chunk.chunkZ() << 4;

      for (int s = 0; s < chunk.block().length; s++) {
         DataLayer block = chunk.block()[s];
         if (block != null) {
            DataLayer sky = chunk.sky()[s];
            int baseY = chunk.bottomSection() + s << 4;

            for (int y = 0; y < 16 && baseY + y <= pass.maxY(); y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     int light = block.get(x, y, z);
                     if (light >= pass.minLight() && (sky == null || light > sky.get(x, y, z))) {
                        hits.put(BlockPos.asLong(startX + x, baseY + y, startZ + z), light);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      LightBoxes.Result shown = this.result;
      if (shown.levels().length != 0) {
         if (this.colorsDirty) {
            this.colorsDirty = false;
            SettingColor base = this.color.get();

            for (int level = 0; level < 16; level++) {
               float t = level / 15.0F;
               int r = Math.round(base.r + (255 - base.r) * t);
               int g = Math.round(base.g + (255 - base.g) * t);
               int b = Math.round(base.b + (255 - base.b) * t);
               this.sides[level].set(r, g, b, Math.round(base.a * (0.5F + 0.5F * t)));
               this.lines[level].set(Math.round(r * 0.45F), Math.round(g * 0.45F), Math.round(b * 0.45F), 255);
            }
         }

         for (int i = 0; i < shown.levels().length; i++) {
            int level = Math.max(0, Math.min(15, shown.levels()[i]));
            Color side = this.sides[level];
            Color line = this.lines[level];

            for (long pos : shown.cells()[i]) {
               int x = BlockPos.getX(pos);
               int y = BlockPos.getY(pos);
               int z = BlockPos.getZ(pos);
               event.renderer.box(x, y, z, x + 1, y + 1, z + 1, side, line, ShapeMode.Both, 0);
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      int total = this.result.total();
      return total == 0 ? null : String.valueOf(total);
   }

   private record LightChunk(int chunkX, int chunkZ, int bottomSection, DataLayer[] block, DataLayer[] sky) {
   }

   private record Pass(int centerX, int centerZ, int minLight, int maxY, boolean denoise, int maxBoxes, int generation) {
   }
}
