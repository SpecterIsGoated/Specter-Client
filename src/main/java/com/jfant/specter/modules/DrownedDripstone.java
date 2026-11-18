package com.jfant.specter.modules;

import com.jfant.specter.Scope;
import com.jfant.specter.ScoreStore;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.scan.DripstoneScanner;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class DrownedDripstone extends Module {
   private static final long RESCAN_DEBOUNCE_MS = 2000L;
   private static final int MAX_ANNOUNCEMENTS_PER_TICK = 5;
   private static final int WEST = 1;
   private static final int EAST = 2;
   private static final int NORTH = 4;
   private static final int SOUTH = 8;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> stalactiteMinLength = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("stalactite-min-length")
            .description("Mark a stalactite once it is at least this long - hidden air, real dripstone and hanging stone in one needle all count.")
            .defaultValue(4)
            .min(1)
            .sliderRange(1, 16)
            .onChanged(v -> {
               if (this.isActive()) {
                  this.rescanLoadedChunks();
               }
            })
            .build()
      );
   private final Setting<Integer> stalagmiteMinLength = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("stalagmite-min-length")
            .description("Mark a stalagmite once it is at least this long - hidden air and real dripstone in one needle both count.")
            .defaultValue(8)
            .min(1)
            .sliderRange(1, 16)
            .onChanged(v -> {
               if (this.isActive()) {
                  this.rescanLoadedChunks();
               }
            })
            .build()
      );
   private final Setting<Integer> maxY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-y")
            .description("Ignore needles whose top is above this Y. Keeps surface water (docks, ice, builds) out.")
            .defaultValue(50)
            .range(-64, 320)
            .sliderRange(-64, 320)
            .onChanged(v -> {
               if (this.isActive()) {
                  this.rescanLoadedChunks();
               }
            })
            .build()
      );
   private final Setting<Integer> minY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-y")
            .description("Ignore needles whose bottom is below this Y.")
            .defaultValue(-64)
            .range(-64, 320)
            .sliderRange(-64, 320)
            .onChanged(v -> {
               if (this.isActive()) {
                  this.rescanLoadedChunks();
               }
            })
            .build()
      );
   private final Setting<Boolean> hangingStone = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hanging-stone")
            .description(
               "Also flag stone hanging down from a dripstone block, plus every stone hanging below it - stalactites the server swaps for stone. Real or hidden dripstone below the stone joins the same find."
            )
            .defaultValue(true)
            .onChanged(v -> {
               if (this.isActive()) {
                  this.rescanLoadedChunks();
               }
            })
            .build()
      );
   private final Setting<Integer> stoneMinLength = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("stone-min-length")
            .description("Flag hanging stone once this many stones hang in a chain under the dripstone block.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 16)
            .visible(this.hangingStone::get)
            .onChanged(v -> {
               if (this.isActive()) {
                  this.rescanLoadedChunks();
               }
            })
            .build()
      );
   private final Setting<Boolean> chat = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat").description("Print a line in chat the first time each hidden dripstone is found.").defaultValue(true).build());
   private final Setting<SettingColor> stalactiteColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("stalactite-color")
            .description("Box color for stalactites and full columns.")
            .defaultValue(new SettingColor(100, 255, 200, 100))
            .build()
      );
   private final Setting<SettingColor> stalagmiteColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("stalagmite-color")
            .description("Box color for stalagmites.")
            .defaultValue(new SettingColor(255, 160, 60, 100))
            .build()
      );
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw a line to each find.").defaultValue(false).build());
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Only draw finds within this many blocks of you.")
            .defaultValue(256)
            .min(16)
            .sliderRange(32, 1024)
            .build()
      );
   private final Setting<Integer> maxRender = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("max-render")
            .description("At most this many finds are drawn at once, nearest first.")
            .defaultValue(128)
            .min(1)
            .sliderRange(8, 512)
            .build()
      );
   private ExecutorService scanner;
   private final ConcurrentHashMap<Long, DripstoneScanner.Find[]> finds = new ConcurrentHashMap<>();
   private final Set<Long> announced = ConcurrentHashMap.newKeySet();
   private final ConcurrentLinkedQueue<DripstoneScanner.Find> announcements = new ConcurrentLinkedQueue<>();
   private final Long2LongOpenHashMap rescanDue = new Long2LongOpenHashMap();
   private final LongArrayList dueKeys = new LongArrayList();
   private final Long2ByteOpenHashMap missingEdges = new Long2ByteOpenHashMap();
   private final List<DripstoneScanner.Find> visible = new ArrayList<>();
   private final Color lineColor = new Color();
   private String scope;

   public DrownedDripstone() {
      super(
         SpecterClientAddon.ESP,
         "dripstone-bypass",
         "Finds hidden stalactites and stalagmites under water, where the anti-xray leaves air needles in the water. Any real dripstone or hanging stone in the same needle is measured with it as one."
      );
   }

   @Override
   public void onActivate() {
      this.scope = null;
      this.clearAll();
      this.scanner = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "drowned-dripstone-scanner");
         thread.setDaemon(true);
         return thread;
      });
   }

   @Override
   public void onDeactivate() {
      if (this.scanner != null) {
         this.scanner.shutdownNow();
         this.scanner = null;
      }

      this.clearAll();
      this.scope = null;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.scope = null;
      this.clearAll();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.scope = null;
      this.clearAll();
   }

   private void clearAll() {
      this.finds.clear();
      this.announced.clear();
      this.announcements.clear();
      this.rescanDue.clear();
      this.missingEdges.clear();
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      LevelChunk chunk = event.chunk();
      this.submitScan(chunk);
      int chunkX = chunk.getPos().x;
      int chunkZ = chunk.getPos().z;
      long due = System.currentTimeMillis() + 2000L;
      this.rescanIfMissing(chunkX + 1, chunkZ, 1, due);
      this.rescanIfMissing(chunkX - 1, chunkZ, 2, due);
      this.rescanIfMissing(chunkX, chunkZ + 1, 4, due);
      this.rescanIfMissing(chunkX, chunkZ - 1, 8, due);
   }

   private void rescanIfMissing(int chunkX, int chunkZ, int side, long due) {
      long key = ScoreStore.key(chunkX, chunkZ);
      if ((this.missingEdges.get(key) & side) != 0) {
         this.rescanDue.putIfAbsent(key, due);
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (isRelevant(event.oldState) || isRelevant(event.newState)) {
         int x = event.pos.getX();
         int z = event.pos.getZ();
         int chunkX = x >> 4;
         int chunkZ = z >> 4;
         long due = System.currentTimeMillis() + 2000L;
         this.rescanDue.putIfAbsent(ScoreStore.key(chunkX, chunkZ), due);
         int localX = x & 15;
         int localZ = z & 15;
         if (localX == 0) {
            this.rescanDue.putIfAbsent(ScoreStore.key(chunkX - 1, chunkZ), due);
         } else if (localX == 15) {
            this.rescanDue.putIfAbsent(ScoreStore.key(chunkX + 1, chunkZ), due);
         }

         if (localZ == 0) {
            this.rescanDue.putIfAbsent(ScoreStore.key(chunkX, chunkZ - 1), due);
         } else if (localZ == 15) {
            this.rescanDue.putIfAbsent(ScoreStore.key(chunkX, chunkZ + 1), due);
         }
      }
   }

   public DripstoneScanner.Rule rule() {
      return new DripstoneScanner.Rule(
         this.stalactiteMinLength.get(),
         this.stalagmiteMinLength.get(),
         this.hangingStone.get() ? this.stoneMinLength.get() : 0,
         this.minY.get(),
         this.maxY.get()
      );
   }

   private static boolean isRelevant(BlockState state) {
      return state != null
         && (
            state.isAir() || DripstoneScanner.isWater(state) || state.is(Blocks.STONE) || state.is(Blocks.DRIPSTONE_BLOCK) || DripstoneScanner.isPointed(state)
         );
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         String current = Scope.current(this.mc);
         if (!current.equals(this.scope)) {
            this.clearAll();
            this.scope = current;
            this.rescanLoadedChunks();
         }

         if (!this.rescanDue.isEmpty()) {
            long now = System.currentTimeMillis();
            this.dueKeys.clear();
            ObjectIterator i = Long2LongMaps.fastIterable(this.rescanDue).iterator();

            while (i.hasNext()) {
               Entry entry = (Entry)i.next();
               if (entry.getLongValue() <= now) {
                  this.dueKeys.add(entry.getLongKey());
               }
            }

            for (int ix = 0; ix < this.dueKeys.size(); ix++) {
               long key = this.dueKeys.getLong(ix);
               this.rescanDue.remove(key);
               LevelChunk chunk = this.loadedChunk(ScoreStore.chunkX(key), ScoreStore.chunkZ(key));
               if (chunk != null) {
                  this.submitScan(chunk);
               } else {
                  this.finds.remove(key);
               }
            }
         }

         this.announce();
      }
   }

   private void announce() {
      int budget = 5;

      DripstoneScanner.Find find;
      while (budget > 0 && (find = this.announcements.poll()) != null) {
         budget--;
         if (this.chat.get()) {
            int y = find.kind() == DripstoneScanner.Kind.STALAGMITE ? find.bottomY() : find.topY();
            int distance = (int)Math.sqrt(this.mc.player.distanceToSqr(find.x() + 0.5, y + 0.5, find.z() + 0.5));
            this.info("%s (length %d) at %d, %d, %d (%d blocks away)", new Object[]{find.kind().title, find.length(), find.x(), y, find.z(), distance});
         }
      }
   }

   public void rescanLoadedChunks() {
      if (this.mc.level != null) {
         for (ChunkAccess chunk : Utils.chunks()) {
            if (chunk instanceof LevelChunk worldChunk) {
               this.submitScan(worldChunk);
            }
         }
      }
   }

   private LevelChunk loadedChunk(int chunkX, int chunkZ) {
      return this.mc.level.getChunkSource().getChunkNow(chunkX, chunkZ);
   }

   private void submitScan(LevelChunk chunk) {
      if (this.mc.level != null) {
         int chunkX = chunk.getPos().x;
         int chunkZ = chunk.getPos().z;
         long key = ScoreStore.key(chunkX, chunkZ);
         LevelChunk west = this.loadedChunk(chunkX - 1, chunkZ);
         LevelChunk east = this.loadedChunk(chunkX + 1, chunkZ);
         LevelChunk north = this.loadedChunk(chunkX, chunkZ - 1);
         LevelChunk south = this.loadedChunk(chunkX, chunkZ + 1);
         int missing = (west == null ? 1 : 0) | (east == null ? 2 : 0) | (north == null ? 4 : 0) | (south == null ? 8 : 0);
         if (missing == 0) {
            this.missingEdges.remove(key);
         } else {
            this.missingEdges.put(key, (byte)missing);
         }

         DripstoneScanner.Rule rule = this.rule();
         this.submitTask(() -> {
            DripstoneScanner.Find[] found = DripstoneScanner.scan(chunk, west, east, north, south, rule);
            if (found.length == 0) {
               this.finds.remove(key);
            } else {
               this.finds.put(key, found);
            }

            for (DripstoneScanner.Find find : found) {
               if (this.announced.add(find.id())) {
                  this.announcements.add(find);
               }
            }
         });
      }
   }

   private boolean submitTask(Runnable task) {
      ExecutorService executor = this.scanner;
      if (executor == null) {
         return false;
      } else {
         try {
            executor.submit(task);
            return true;
         } catch (RejectedExecutionException var4) {
            return false;
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null && !this.finds.isEmpty()) {
         double maxDistance = this.renderDistance.get().intValue();
         double maxDistanceSq = maxDistance * maxDistance;
         double px = this.mc.player.getX();
         double pz = this.mc.player.getZ();
         this.visible.clear();

         for (DripstoneScanner.Find[] chunkFinds : this.finds.values()) {
            for (DripstoneScanner.Find find : chunkFinds) {
               double dx = find.x() + 0.5 - px;
               double dz = find.z() + 0.5 - pz;
               if (dx * dx + dz * dz <= maxDistanceSq) {
                  this.visible.add(find);
               }
            }
         }

         if (!this.visible.isEmpty()) {
            int budget = this.maxRender.get();
            if (this.visible.size() > budget) {
               this.visible.sort((a, b) -> Double.compare(horizontalDistanceSq(a, px, pz), horizontalDistanceSq(b, px, pz)));
            }

            boolean drawTracers = this.tracers.get();
            Vec3 from = RenderUtils.center;
            int count = Math.min(budget, this.visible.size());

            for (int i = 0; i < count; i++) {
               DripstoneScanner.Find findx = this.visible.get(i);
               SettingColor c = findx.kind() == DripstoneScanner.Kind.STALAGMITE ? this.stalagmiteColor.get() : this.stalactiteColor.get();
               this.lineColor.set(c.r, c.g, c.b, 255);
               double x = findx.x();
               double z = findx.z();
               event.renderer.box(x, findx.bottomY(), z, x + 1.0, findx.topY() + 1, z + 1.0, c, this.lineColor, ShapeMode.Both, 0);
               if (drawTracers && from != null) {
                  event.renderer.line(from.x, from.y, from.z, x + 0.5, (findx.bottomY() + findx.topY() + 1) / 2.0, z + 0.5, this.lineColor);
               }
            }
         }
      }
   }

   private static double horizontalDistanceSq(DripstoneScanner.Find find, double px, double pz) {
      double dx = find.x() + 0.5 - px;
      double dz = find.z() + 0.5 - pz;
      return dx * dx + dz * dz;
   }
}
