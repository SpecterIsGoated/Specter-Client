package com.jfant.specter.modules;

import com.jfant.specter.ChunkMemory;
import com.jfant.specter.Scope;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.bypass.BypassToast;
import com.jfant.specter.bypass.OwnChunks;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.render.ChunkAreas;
import com.jfant.specter.render.ChunkOutline;
import com.jfant.specter.render.ChunkRects;
import com.jfant.specter.scan.AntiXray;
import com.jfant.specter.scan.BlockKinds;
import com.jfant.specter.scan.ChunkScanner;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class PrimeChunkFinder extends Module {
   private static final int MAX_FLAGGED = 256;
   private static final byte[] UNSENT = new byte[0];
   private static final int MAX_BASELINES = 4000;
   private static final int MAX_OWN = 60000;
   private static final long GONE_LAMP_MS = 5000L;
   private static final long JOIN_QUIET_MS = 3000L;
   private static final int NOTIFY_REACH = 3;
   private static final int MAX_CHANGED = 1024;
   private static final int LIGHT = 0;
   private static final int SKY = 1;
   private static final int REDSTONE = 2;
   private static final int LAMP = 3;
   private static final String[] KIND_NAMES = new String[]{"light", "sky light", "redstone", "lamp"};
   private static final int STRONG_DELTA = 10;
   private static final int NEW_LAMP_PEAK = 12;
   private static final int MAX_PEAK_CELLS = 4;
   private static final long SWAP_QUIET_MS = 3000L;
   private static final int WEAK_TO_CONFIRM = 2;
   private static final int MAX_LOGGED = 400;
   private static final int START_RADIUS = 24;
   private static final int START_CHUNKS_PER_TICK = 16;
   private static final int MAX_CELL_CHECKS = 4096;
   private static final int ENOUGH_UNEXPLAINED = 64;
   private static final int AMETHYST_LIGHT = 5;
   private static final int ORE_LIGHT = 9;
   private static final int[] NO_CELLS = new int[0];
   private static final long[] NO_ORES = new long[0];
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> watchLight = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("light")
            .description("Count underground light changes: a lamp, torch or glowstone put down.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> watchRedstone = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("redstone")
            .description("Count underground redstone changes: wire, repeaters, pistons, droppers, lamps...")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> watchSky = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sky-light")
            .description("Also count changes of the sky light underground: a hole or a shaft opened to the surface.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> maxY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-y")
            .description(
               "Only light and redstone below this height count (rounded down to a multiple of 16). DonutSMP sends real light for y 0-15 much further out than below 0."
            )
            .defaultValue(16)
            .range(-48, 64)
            .sliderRange(-48, 64)
            .onChanged(v -> {
               if (this.isActive()) {
                  this.reset();
               }
            })
            .build()
      );
   private final Setting<Integer> activeChanges = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("active-changes")
            .description("Every change is drawn and a new place is announced; a chunk is announced again, as active, when its changes reach this.")
            .defaultValue(5)
            .range(1, 50)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Boolean> chat = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat").description("Say in chat when an area is found.").defaultValue(true).build());
   private final Setting<Boolean> popup = this.sgGeneral
      .add(new BoolSetting.Builder().name("popup").description("Show a pop-up for each area found.").defaultValue(true).build());
   private final Setting<Boolean> sound = this.sgGeneral
      .add(new BoolSetting.Builder().name("sound").description("Play a bell with the pop-up.").defaultValue(true).visible(this.popup::get).build());
   private final Setting<SettingColor> chunkColor = this.sgRender
      .add(new ColorSetting.Builder().name("chunk-color").description("The colour of flagged chunks.").defaultValue(new SettingColor(0, 255, 90, 80)).build());
   private final Setting<Boolean> labels = this.sgRender
      .add(new BoolSetting.Builder().name("labels").description("Label each place: what was seen there and how long ago.").defaultValue(true).build());
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw a line from you to each flagged place.").defaultValue(true).build());
   private final Setting<Integer> fixedY = this.sgRender
      .add(new IntSetting.Builder().name("y").description("The height the overlay is drawn at.").defaultValue(63).sliderRange(-64, 320).build());
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Only draw chunks within this many chunks of you.")
            .defaultValue(64)
            .min(1)
            .sliderRange(8, 128)
            .build()
      );
   private final Long2ObjectLinkedOpenHashMap<byte[][]> baselines = new Long2ObjectLinkedOpenHashMap();
   private final Long2ObjectLinkedOpenHashMap<byte[][]> skyBaselines = new Long2ObjectLinkedOpenHashMap();
   private final OwnChunks own = new OwnChunks("prime-chunk-finder");
   private final ChunkMemory found = new ChunkMemory("prime-chunk-finder-finds", ".csv");
   private final LongLinkedOpenHashSet flagged = new LongLinkedOpenHashSet();
   private final Long2ObjectLinkedOpenHashMap<int[]> changes = new Long2ObjectLinkedOpenHashMap();
   private final Long2LongOpenHashMap goneLamps = new Long2LongOpenHashMap();
   private final ArrayDeque<Long> startQueue = new ArrayDeque<>();
   private final LongOpenHashSet checkedAmethyst = new LongOpenHashSet();
   private final LongOpenHashSet hasAmethyst = new LongOpenHashSet();
   private final Long2ObjectOpenHashMap<long[]> oreCache = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<int[]> lampCache = new Long2ObjectOpenHashMap();
   private final MutableBlockPos cursor = new MutableBlockPos();
   private ClientLevel lastWorld;
   private long ownRadiusChunk = -9223372036854775808L;
   private long quietUntil;
   private long[] shown = new long[0];
   private long[] lastShown;
   private int lastChunkX = -2147483648;
   private int lastChunkZ = -2147483648;
   private final LongArrayList visibleCells = new LongArrayList();
   private final IntArrayList renderRects = new IntArrayList();
   private final ChunkOutline outline = new ChunkOutline();
   private final IntArrayList outlineEdges = new IntArrayList();
   private final ChunkAreas areas = new ChunkAreas();
   private final Color fill = new Color();
   private final Color line = new Color();
   private long lightFlags;
   private long redstoneFlags;
   private long packetsRead;
   private long byAmethyst;
   private long byOre;
   private long bySource;
   private long bySwap;
   private long darkLit;
   private long lampsPlaced;
   private long weakWaiting;
   private long logged;
   private final Long2LongOpenHashMap lastSwap = new Long2LongOpenHashMap();
   private final Long2LongOpenHashMap lastChange = new Long2LongOpenHashMap();
   private final LongArrayList labelChunks = new LongArrayList();
   private final Vector3d labelPos = new Vector3d();
   private static final Color LABEL_TITLE = new Color(255, 255, 255);
   private static final Color LABEL_TEXT = new Color(235, 235, 235);
   private int ticks;
   private int checks;

   public PrimeChunkFinder() {
      super(
         SpecterClientAddon.BASES,
         "prime-chunk-finder",
         "Finds areas where light or redstone keeps changing below y 0 - a lamp or a machine somebody put down."
      );
   }

   @Override
   public void onActivate() {
      this.reset();
      this.queueLoadedChunks();
   }

   @Override
   public void onDeactivate() {
      SpecterClientAddon.LOG
         .info(
            "Prime Chunk Finder: {} light updates read, {} light changes, {} redstone changes, {} lamps placed/broken; blocks of light let go: {} by amethyst, {} by redstone ore, {} by a light source standing; light updates skipped: {} anti-xray swap nearby, {} dark-to-lit not a lamp; {} weak changes left undrawn",
            new Object[]{
               this.packetsRead,
               this.lightFlags,
               this.redstoneFlags,
               this.lampsPlaced,
               this.byAmethyst,
               this.byOre,
               this.bySource,
               this.bySwap,
               this.darkLit,
               this.weakWaiting
            }
         );
      this.reset();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.reset();
   }

   private void reset() {
      this.baselines.clear();
      this.own.flush();
      this.own.select(null);
      this.found.flush();
      this.found.select(null);
      this.skyBaselines.clear();
      this.changes.clear();
      this.flagged.clear();
      this.goneLamps.clear();
      this.startQueue.clear();
      this.clearCaches();
      this.shown = new long[0];
      this.lastShown = null;
      this.lastWorld = null;
      this.ownRadiusChunk = -9223372036854775808L;
      this.lightFlags = this.redstoneFlags = this.packetsRead = 0L;
      this.byAmethyst = this.byOre = this.bySource = this.bySwap = this.darkLit = this.lampsPlaced = this.weakWaiting = this.logged = 0L;
      this.lastSwap.clear();
      this.lastChange.clear();
      this.labelChunks.clear();
   }

   private void clearCaches() {
      this.checkedAmethyst.clear();
      this.hasAmethyst.clear();
      this.oreCache.clear();
      this.lampCache.clear();
   }

   private void queueLoadedChunks() {
      if (this.mc.level != null && this.mc.player != null) {
         ChunkPos at = this.mc.player.chunkPosition();

         for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
               this.startQueue.add(ChunkPos.asLong(at.x + dx, at.z + dz));
            }
         }
      }
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      this.take(event.packet);
   }

   private void take(Packet<?> packet) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> inner : bundle.subPackets()) {
            this.take(inner);
         }
      } else if (packet instanceof ClientboundLoginPacket) {
         long until = System.currentTimeMillis() + 3000L;
         this.mc.execute(() -> {
            this.quietUntil = until;
            if (this.isActive()) {
               this.reset();
            }
         });
      } else if (packet instanceof ClientboundLevelChunkWithLightPacket data) {
         int x = data.getX();
         int z = data.getZ();
         ClientboundLightUpdatePacketData light = data.getLightData();
         this.mc.execute(() -> {
            if (this.isActive() && this.mc.level != null) {
               this.onLight(x, z, light, false);
            }
         });
      } else if (packet instanceof ClientboundLightUpdatePacket update) {
         int x = update.getX();
         int z = update.getZ();
         ClientboundLightUpdatePacketData light = update.getLightData();
         this.mc.execute(() -> {
            if (this.isActive() && this.mc.level != null) {
               this.onLight(x, z, light, true);
            }
         });
      }
   }

   private int belowSections() {
      return Math.max(0, Math.min(32, Math.floorDiv(this.maxY.get(), 16) - this.mc.level.getMinSectionY()));
   }

   private int topY() {
      return Math.floorDiv(this.maxY.get(), 16) * 16;
   }

   private boolean swapNear(long key, long now) {
      if (this.lastSwap.isEmpty()) {
         return false;
      } else {
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (now - this.lastSwap.getOrDefault(ChunkPos.asLong(cx + dx, cz + dz), -4611686018427387904L) < 3000L) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private void onLight(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data, boolean update) {
      long key = ChunkPos.asLong(chunkX, chunkZ);
      if (this.belowSections() != 0) {
         long now = System.currentTimeMillis();
         boolean compare = update && now >= this.quietUntil && !this.own.isProtected(key);
         if (update) {
            this.packetsRead++;
         }

         if (compare && this.swapNear(key, now)) {
            compare = false;
            this.bySwap++;
         }

         PrimeChunkFinder.Summary block = this.read(
            chunkX,
            chunkZ,
            update,
            false,
            this.baselines,
            data.getBlockYMask(),
            data.getBlockUpdates(),
            data.getEmptyBlockYMask(),
            compare && this.watchLight.get()
         );
         PrimeChunkFinder.Summary sky = this.watchSky.get()
            ? this.read(chunkX, chunkZ, update, true, this.skyBaselines, data.getSkyYMask(), data.getSkyUpdates(), data.getEmptySkyYMask(), compare)
            : null;
         if (block != null && block.count > 0) {
            this.lightFlags++;
            this.change(
               key,
               0,
               block.x,
               block.y,
               block.z,
               block.strong(),
               (block.lamp ? "dark section lit, peak " + block.now : "light " + block.was + " -> " + block.now) + ", " + block.count + " blocks"
            );
         } else if (sky != null && sky.count > 0) {
            this.lightFlags++;
            this.change(key, 1, sky.x, sky.y, sky.z, false, "sky light " + sky.was + " -> " + sky.now + ", " + sky.count + " blocks");
         }
      }
   }

   private PrimeChunkFinder.Summary read(
      int chunkX,
      int chunkZ,
      boolean update,
      boolean sky,
      Long2ObjectLinkedOpenHashMap<byte[][]> store,
      BitSet inited,
      List<byte[]> nibbles,
      BitSet empty,
      boolean compare
   ) {
      long key = ChunkPos.asLong(chunkX, chunkZ);
      int below = this.belowSections();
      byte[][] sections = update ? (byte[][])store.get(key) : unsent(below);
      if (sections != null && sections.length != below) {
         sections = null;
      }

      if (update && sections == null) {
         sections = this.copyFromWorld(chunkX, chunkZ, below, sky ? LightLayer.SKY : LightLayer.BLOCK);
         if (sections == null) {
            return null;
         }
      }

      PrimeChunkFinder.Summary summary = compare ? new PrimeChunkFinder.Summary(sky) : null;
      if (compare && !sky) {
         this.clearCaches();
      }

      int shift = this.mc.level.getLightEngine().getMinLightSection() - this.mc.level.getMinSectionY();
      int next = 0;

      for (int bit = inited.nextSetBit(0); bit >= 0 && next < nibbles.size(); bit = inited.nextSetBit(bit + 1)) {
         byte[] sent = nibbles.get(next++);
         int slot = bit + shift;
         if (slot >= 0 && slot < below && sent.length == 2048) {
            this.replace(sections, slot, (byte[])sent.clone(), chunkX, chunkZ, summary);
         }
      }

      for (int bitx = empty.nextSetBit(0); bitx >= 0; bitx = empty.nextSetBit(bitx + 1)) {
         int slot = bitx + shift;
         if (slot >= 0 && slot < below) {
            this.replace(sections, slot, null, chunkX, chunkZ, summary);
         }
      }

      store.putAndMoveToLast(key, sections);

      while (store.size() > 4000) {
         store.removeFirst();
      }

      return summary;
   }

   private void replace(byte[][] sections, int slot, byte[] now, int chunkX, int chunkZ, PrimeChunkFinder.Summary summary) {
      byte[] was = sections[slot];
      boolean lit = now != null && !allZero(now);
      if (summary != null && summary.count < 64 && was != UNSENT && was != null && lit) {
         this.compareSection(was, now, chunkX, chunkZ, this.mc.level.getMinSectionY() + slot, summary);
      } else if (summary != null && !summary.sky && was == null && lit) {
         this.darkToLit(now, chunkX, chunkZ, this.mc.level.getMinSectionY() + slot, summary);
      }

      sections[slot] = lit ? now : null;
   }

   private void darkToLit(byte[] now, int chunkX, int chunkZ, int sectionY, PrimeChunkFinder.Summary summary) {
      int peak = 0;
      int peakCells = 0;
      int peakIndex = -1;

      for (int index = 0; index < 4096; index++) {
         int v = now[index >> 1] >> ((index & 1) << 2) & 15;
         if (v > peak) {
            peak = v;
            peakCells = 1;
            peakIndex = index;
         } else if (v == peak) {
            peakCells++;
         }
      }

      if (peak >= 12 && peakCells <= 4) {
         int x = (chunkX << 4) + (peakIndex & 15);
         int y = (sectionY << 4) + (peakIndex >>> 8 & 15);
         int z = (chunkZ << 4) + (peakIndex >>> 4 & 15);
         if (!this.explained(x, y, z, peak)) {
            summary.add(x, y, z, 0, peak);
            summary.lamp = true;
         }
      } else {
         this.darkLit++;
      }
   }

   private static byte[][] unsent(int below) {
      byte[][] sections = new byte[below][];
      Arrays.fill(sections, UNSENT);
      return sections;
   }

   private static boolean allZero(byte[] light) {
      for (byte b : light) {
         if (b != 0) {
            return false;
         }
      }

      return true;
   }

   private void compareSection(byte[] was, byte[] now, int chunkX, int chunkZ, int sectionY, PrimeChunkFinder.Summary summary) {
      this.checks = 0;

      for (int i = 0; i < 2048; i++) {
         int a = was == null ? 0 : was[i] & 255;
         int b = now == null ? 0 : now[i] & 255;
         int differ = a ^ b;
         if (differ != 0) {
            if ((differ & 15) != 0) {
               this.record(chunkX, chunkZ, sectionY, i << 1, a & 15, b & 15, summary);
            }

            if ((differ & 240) != 0) {
               this.record(chunkX, chunkZ, sectionY, (i << 1) + 1, a >>> 4, b >>> 4, summary);
            }

            if (summary.count >= 64 || this.checks >= 4096) {
               return;
            }
         }
      }
   }

   private void record(int chunkX, int chunkZ, int sectionY, int index, int was, int now, PrimeChunkFinder.Summary summary) {
      this.checks++;
      int x = (chunkX << 4) + (index & 15);
      int y = (sectionY << 4) + (index >>> 8 & 15);
      int z = (chunkZ << 4) + (index >>> 4 & 15);
      if (summary.sky || !this.explained(x, y, z, Math.max(was, now))) {
         summary.add(x, y, z, was, now);
      }
   }

   private boolean explained(int x, int y, int z, int value) {
      if (this.fromAmethyst(x, y, z, value)) {
         this.byAmethyst++;
         return true;
      } else if (this.fromRedstoneOre(x, y, z, value)) {
         this.byOre++;
         return true;
      } else if (this.fromSource(x, y, z, value)) {
         this.bySource++;
         return true;
      } else {
         return false;
      }
   }

   private byte[][] copyFromWorld(int chunkX, int chunkZ, int below, LightLayer type) {
      if (this.mc.level.getChunkSource().getChunkNow(chunkX, chunkZ) != null && ChunkScanner.isLightReady(this.mc.level, chunkX, chunkZ)) {
         LayerLightEventListener view = this.mc.level.getLightEngine().getLayerListener(type);
         if (view == null) {
            return null;
         } else {
            byte[][] sections = new byte[below][];
            int bottom = this.mc.level.getMinSectionY();

            for (int s = 0; s < below; s++) {
               DataLayer array = view.getDataLayerData(SectionPos.of(chunkX, bottom + s, chunkZ));
               if (array == null) {
                  sections[s] = UNSENT;
               } else if (!array.isEmpty()) {
                  byte[] bytes = array.copy().getData();
                  if (!allZero(bytes)) {
                     sections[s] = bytes;
                  }
               }
            }

            return sections;
         }
      } else {
         return null;
      }
   }

   private boolean fromAmethyst(int x, int y, int z, int value) {
      if (value > 5) {
         return false;
      } else {
         boolean near = false;

         for (int sx = x - 5 >> 4; sx <= x + 5 >> 4 && !near; sx++) {
            for (int sy = y - 5 >> 4; sy <= y + 5 >> 4 && !near; sy++) {
               for (int sz = z - 5 >> 4; sz <= z + 5 >> 4; sz++) {
                  long section = SectionPos.asLong(sx, sy, sz);
                  if (this.checkedAmethyst.add(section) && this.sectionHas(sx, sy, sz, BlockKinds::isAmethyst)) {
                     this.hasAmethyst.add(section);
                  }

                  if (this.hasAmethyst.contains(section)) {
                     near = true;
                     break;
                  }
               }
            }
         }

         return near && this.amethystNear(x, y, z);
      }
   }

   private boolean amethystNear(int x, int y, int z) {
      for (int dx = -5; dx <= 5; dx++) {
         int restX = 5 - Math.abs(dx);

         for (int dy = -restX; dy <= restX; dy++) {
            int restY = restX - Math.abs(dy);

            for (int dz = -restY; dz <= restY; dz++) {
               if (BlockKinds.isAmethyst(this.mc.level.getBlockState(this.cursor.set(x + dx, y + dy, z + dz)))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean fromRedstoneOre(int x, int y, int z, int value) {
      if (value > 9) {
         return false;
      } else {
         int reach = 9 - value;

         for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
            for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
               for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
                  for (long ore : this.exposedOres(sx, sy, sz)) {
                     int distance = Math.abs(BlockPos.getX(ore) - x) + Math.abs(BlockPos.getY(ore) - y) + Math.abs(BlockPos.getZ(ore) - z);
                     if (distance <= reach) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   private boolean fromSource(int x, int y, int z, int value) {
      if (value > 15) {
         return false;
      } else {
         int reach = 15 - value;

         for (int sx = x - reach >> 4; sx <= x + reach >> 4; sx++) {
            for (int sy = y - reach >> 4; sy <= y + reach >> 4; sy++) {
               for (int sz = z - reach >> 4; sz <= z + reach >> 4; sz++) {
                  for (int torch : this.sources(sx, sy, sz)) {
                     int index = torch >>> 4;
                     int lx = (sx << 4) + (index & 15);
                     int ly = (sy << 4) + (index >>> 8);
                     int lz = (sz << 4) + (index >>> 4 & 15);
                     if (Math.abs(lx - x) + Math.abs(ly - y) + Math.abs(lz - z) <= (torch & 15) - value) {
                        return true;
                     }
                  }
               }
            }
         }

         if (this.goneLamps.isEmpty()) {
            return false;
         } else {
            long now = System.currentTimeMillis();
            ObjectIterator var18 = this.goneLamps.long2LongEntrySet().iterator();

            while (var18.hasNext()) {
               Entry gone = (Entry)var18.next();
               long packed = gone.getLongValue();
               if (packed >>> 4 >= now) {
                  long pos = gone.getLongKey();
                  int distance = Math.abs(BlockPos.getX(pos) - x) + Math.abs(BlockPos.getY(pos) - y) + Math.abs(BlockPos.getZ(pos) - z);
                  if (distance <= (int)(packed & 15L) - value) {
                     return true;
                  }
               }
            }

            return false;
         }
      }
   }

   private boolean sectionHas(int sx, int sy, int sz, Predicate<BlockState> test) {
      LevelChunkSection section = this.sectionAt(sx, sy, sz);
      return section != null && !section.hasOnlyAir() && section.maybeHas(test);
   }

   private LevelChunkSection sectionAt(int sx, int sy, int sz) {
      if (sy >= this.mc.level.getMinSectionY() && sy < this.mc.level.getMaxSectionY()) {
         LevelChunk chunk = this.mc.level.getChunkSource().getChunkNow(sx, sz);
         return chunk == null ? null : chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
      } else {
         return null;
      }
   }

   private long[] exposedOres(int sx, int sy, int sz) {
      long section = SectionPos.asLong(sx, sy, sz);
      long[] cached = (long[])this.oreCache.get(section);
      if (cached != null) {
         return cached;
      } else {
         long[] found = NO_ORES;
         LevelChunkSection s = this.sectionAt(sx, sy, sz);
         if (s != null && !s.hasOnlyAir() && s.maybeHas(BlockKinds::isRedstoneOre)) {
            LongArrayList ores = new LongArrayList();

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     if (BlockKinds.isRedstoneOre(s.getBlockState(x, y, z))) {
                        int wx = (sx << 4) + x;
                        int wy = (sy << 4) + y;
                        int wz = (sz << 4) + z;
                        if (this.exposed(wx, wy, wz)) {
                           ores.add(BlockPos.asLong(wx, wy, wz));
                        }
                     }
                  }
               }
            }

            if (!ores.isEmpty()) {
               found = ores.toLongArray();
            }
         }

         this.oreCache.put(section, found);
         return found;
      }
   }

   private boolean exposed(int x, int y, int z) {
      for (Direction d : Direction.values()) {
         BlockState neighbour = this.mc.level.getBlockState(this.cursor.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()));
         if (neighbour.isAir() || !neighbour.canOcclude()) {
            return true;
         }
      }

      return false;
   }

   private int[] sources(int sx, int sy, int sz) {
      long section = SectionPos.asLong(sx, sy, sz);
      int[] cached = (int[])this.lampCache.get(section);
      if (cached != null) {
         return cached;
      } else {
         int[] found = NO_CELLS;
         LevelChunkSection s = this.sectionAt(sx, sy, sz);
         if (s != null && !s.hasOnlyAir() && s.maybeHas(st -> st.getLightEmission() > 0)) {
            IntArrayList list = new IntArrayList();

            for (int index = 0; index < 4096; index++) {
               int lx = index & 15;
               int ly = index >>> 8;
               int lz = index >>> 4 & 15;
               BlockState state = s.getBlockState(lx, ly, lz);
               int light = state.getLightEmission();
               if (light > 0
                  && (
                     lx <= 0
                        || lx >= 15
                        || ly <= 0
                        || ly >= 15
                        || lz <= 0
                        || lz >= 15
                        || s.getBlockState(lx - 1, ly, lz).getLightEmission() < light
                        || s.getBlockState(lx + 1, ly, lz).getLightEmission() < light
                        || s.getBlockState(lx, ly - 1, lz).getLightEmission() < light
                        || s.getBlockState(lx, ly + 1, lz).getLightEmission() < light
                        || s.getBlockState(lx, ly, lz - 1).getLightEmission() < light
                        || s.getBlockState(lx, ly, lz + 1).getLightEmission() < light
                  )) {
                  list.add(index << 4 | Math.min(15, light));
               }
            }

            if (!list.isEmpty()) {
               found = list.toIntArray();
            }
         }

         this.lampCache.put(section, found);
         return found;
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      BlockState old = event.oldState;
      BlockState state = event.newState;
      if (old != null && state != null && old != state && this.mc.level != null) {
         long millis = System.currentTimeMillis();
         long key = ChunkPos.asLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
         boolean swap = AntiXray.isSwap(old, state);
         if (swap) {
            this.lastSwap.put(key, millis);
         }

         int oldLight = Math.min(15, old.getLightEmission());
         if (oldLight > 0 && state.getLightEmission() < oldLight) {
            this.goneLamps.put(event.pos.asLong(), millis + 5000L << 4 | oldLight);
         }

         if (!swap && event.pos.getY() < this.topY()) {
            if (!this.own.isProtected(key) && millis >= this.quietUntil) {
               int x = event.pos.getX();
               int y = event.pos.getY();
               int z = event.pos.getZ();
               if (this.watchLight.get()) {
                  boolean was = BlockKinds.isPlacedLight(old);
                  boolean is = BlockKinds.isPlacedLight(state);
                  if (was != is) {
                     this.lampsPlaced++;
                     BlockState lamp = is ? state : old;
                     this.change(key, 3, x, y, z, true, (is ? "placed " : "broke ") + BuiltInRegistries.BLOCK.getKey(lamp.getBlock()).getPath());
                     return;
                  }
               }

               if (this.watchRedstone.get()) {
                  if (BlockKinds.isRedstonePart(state) || BlockKinds.isRedstonePart(old)) {
                     this.redstoneFlags++;
                     BlockState part = BlockKinds.isRedstonePart(state) ? state : old;
                     String what = BuiltInRegistries.BLOCK.getKey(part.getBlock()).getPath()
                        + (old.getBlock() == state.getBlock() ? " switched" : (BlockKinds.isRedstonePart(state) ? " placed" : " broken"));
                     this.change(key, 2, x, y, z, true, what);
                  }
               }
            }
         }
      }
   }

   private void change(long key, int kind, int x, int y, int z, boolean strong, String what) {
      boolean fresh = !nearby(key, this.flagged::contains);
      int[] counts = (int[])this.changes.get(key);
      if (counts == null) {
         counts = new int[KIND_NAMES.length];
      }

      counts[kind]++;
      this.changes.putAndMoveToLast(key, counts);

      while (this.changes.size() > 1024) {
         this.changes.removeFirst();
      }

      this.lastChange.put(key, System.currentTimeMillis());
      boolean drawn = strong || this.flagged.contains(key) || this.changesAround(key) >= 2;
      this.log(kind, x, y, z, strong, what, drawn);
      if (!drawn) {
         this.weakWaiting++;
      } else {
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               long other = ChunkPos.asLong(cx + dx, cz + dz);
               if (other != key && !this.flagged.contains(other) && total((int[])this.changes.get(other)) != 0) {
                  this.flagged.addAndMoveToLast(other);
                  this.found.put(other, 0);
               }
            }
         }

         this.flagged.addAndMoveToLast(key);
         this.found.put(key, 0);

         while (this.flagged.size() > 256) {
            this.flagged.removeFirstLong();
         }

         int active = this.activeChanges.get();
         Item icon = kind == 2 ? Items.REDSTONE : (kind == 3 ? Items.LANTERN : Items.TORCH);
         String at = x + ", " + y + ", " + z;
         if (fresh) {
            this.tell(icon, "Underground activity", headline(kind, what) + " " + at, String.format("Underground %s at %s (%s)", KIND_NAMES[kind], at, what));
         } else if (total(counts) == active && !nearby(key, other -> other != key && total((int[])this.changes.get(other)) >= active)) {
            this.tell(icon, "Active underground", "Active " + at, String.format("Underground activity keeps going at %s: %d changes in this chunk", at, active));
         }
      }
   }

   private int changesAround(long key) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);
      int sum = 0;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            sum += total((int[])this.changes.get(ChunkPos.asLong(cx + dx, cz + dz)));
         }
      }

      return sum;
   }

   private static String headline(int kind, String what) {
      return switch (kind) {
         case 1 -> "Sky opened";
         case 2 -> "Redstone";
         case 3 -> what.startsWith("placed") ? "Lamp placed" : "Lamp broken";
         default -> what.startsWith("dark") ? "New light" : "Light changed";
      };
   }

   private String distanceText(int x, int z) {
      if (this.mc.player == null) {
         return "?";
      } else {
         double dx = x + 0.5 - this.mc.player.getX();
         double dz = z + 0.5 - this.mc.player.getZ();
         return (int)Math.sqrt(dx * dx + dz * dz) + " blocks";
      }
   }

   private void log(int kind, int x, int y, int z, boolean strong, String what, boolean drawn) {
      if (this.logged++ < 400L) {
         SpecterClientAddon.LOG
            .info(
               "Prime: {} {} at {}, {}, {} ({}), {} away, {}",
               new Object[]{
                  strong ? "strong" : "weak", KIND_NAMES[kind], x, y, z, what, this.distanceText(x, z), drawn ? "drawn" : "waiting for a second change"
               }
            );
      }
   }

   private static int total(int[] counts) {
      if (counts == null) {
         return 0;
      } else {
         int sum = 0;

         for (int c : counts) {
            sum += c;
         }

         return sum;
      }
   }

   private static boolean nearby(long key, LongPredicate test) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);

      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            if (test.test(ChunkPos.asLong(cx + dx, cz + dz))) {
               return true;
            }
         }
      }

      return false;
   }

   private void tell(Item icon, String title, String popupText, String chatText) {
      if (this.chat.get()) {
         this.info("%s", new Object[]{chatText});
      }

      if (this.popup.get()) {
         BypassToast.addOrUpdate(new ItemStack(icon), title, Component.literal(popupText), this.sound.get());
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         if (this.mc.level != this.lastWorld) {
            this.reset();
            this.lastWorld = this.mc.level;
            this.own.select(Scope.current(this.mc));
            this.found.select(Scope.current(this.mc));
            LongIterator loaded = this.found.all().keySet().iterator();

            while (loaded.hasNext()) {
               long key = (Long)loaded.next();
               this.flagged.add(key);
            }

            this.queueLoadedChunks();
         }

         this.own.tickAutosave();
         this.found.tickAutosave();
         if (this.mc.player.getY() < this.topY()) {
            ChunkPos at = this.mc.player.chunkPosition();
            long here = at.toLong();
            if (here != this.ownRadiusChunk && this.own.size() < 60000) {
               this.ownRadiusChunk = here;

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     this.own.protect(ChunkPos.asLong(at.x + dx, at.z + dz));
                  }
               }
            }
         }

         for (int i = 0; i < 16 && !this.startQueue.isEmpty(); i++) {
            long key = this.startQueue.poll();
            int below = this.belowSections();
            if (below == 0) {
               break;
            }

            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            if (!this.baselines.containsKey(key)) {
               byte[][] copy = this.copyFromWorld(x, z, below, LightLayer.BLOCK);
               if (copy != null) {
                  this.baselines.putAndMoveToLast(key, copy);
               }
            }

            if (this.watchSky.get() && !this.skyBaselines.containsKey(key)) {
               byte[][] copy = this.copyFromWorld(x, z, below, LightLayer.SKY);
               if (copy != null) {
                  this.skyBaselines.putAndMoveToLast(key, copy);
               }
            }
         }

         while (this.baselines.size() > 4000) {
            this.baselines.removeFirst();
         }

         while (this.skyBaselines.size() > 4000) {
            this.skyBaselines.removeFirst();
         }

         if (this.ticks % 20 == 0) {
            long now = System.currentTimeMillis();
            if (!this.goneLamps.isEmpty()) {
               this.goneLamps.values().removeIf(packed -> packed >>> 4 < now);
            }

            if (!this.lastSwap.isEmpty()) {
               this.lastSwap.values().removeIf(t -> now - t >= 3000L);
            }
         }

         this.ticks++;
         LongArrayList loaded = new LongArrayList(this.flagged.size());
         LongListIterator var19 = this.flagged.iterator();

         while (var19.hasNext()) {
            long keyx = (Long)var19.next();
            if (this.mc.level.getChunkSource().getChunkNow(ChunkPos.getX(keyx), ChunkPos.getZ(keyx)) != null) {
               loaded.add(keyx);
            }
         }

         this.shown = loaded.toLongArray();
         if (this.ticks % 10 == 0) {
            this.labelChunks.clear();
            LongArrayList sorted = new LongArrayList(this.shown);
            sorted.sort((a, b) -> Integer.compare(total((int[])this.changes.get(b)), total((int[])this.changes.get(a))));
            LongListIterator var21 = sorted.iterator();

            while (var21.hasNext()) {
               long keyx = (Long)var21.next();
               boolean taken = false;
               int cx = ChunkPos.getX(keyx);
               int cz = ChunkPos.getZ(keyx);

               for (int i = 0; i < this.labelChunks.size() && !taken; i++) {
                  long other = this.labelChunks.getLong(i);
                  taken = Math.abs(ChunkPos.getX(other) - cx) <= 3 && Math.abs(ChunkPos.getZ(other) - cz) <= 3;
               }

               if (!taken) {
                  this.labelChunks.add(keyx);
               }
            }
         }
      } else {
         this.reset();
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.labels.get() && !this.labelChunks.isEmpty() && this.mc.player != null) {
         int chunkX = this.mc.player.getBlockX() >> 4;
         int chunkZ = this.mc.player.getBlockZ() >> 4;
         int distance = this.renderDistance.get();
         long now = System.currentTimeMillis();
         TextRenderer text = TextRenderer.get();

         for (int i = 0; i < this.labelChunks.size(); i++) {
            long key = this.labelChunks.getLong(i);
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (Math.abs(cx - chunkX) <= distance && Math.abs(cz - chunkZ) <= distance) {
               int[] sum = new int[KIND_NAMES.length];
               long newest = 0L;

               for (int dx = -3; dx <= 3; dx++) {
                  for (int dz = -3; dz <= 3; dz++) {
                     long other = ChunkPos.asLong(cx + dx, cz + dz);
                     if (this.flagged.contains(other)) {
                        int[] c = (int[])this.changes.get(other);
                        if (c != null) {
                           for (int k = 0; k < sum.length; k++) {
                              sum[k] += c[k];
                           }
                        }

                        newest = Math.max(newest, this.lastChange.getOrDefault(other, 0L));
                     }
                  }
               }

               double x = (cx << 4) + 8;
               double z = (cz << 4) + 8;
               this.labelPos.set(x, this.fixedY.get() + 3, z);
               if (NametagUtils.to2D(this.labelPos, 2.2)) {
                  String title = sum[3] > 0 ? "Lamps" : (sum[2] > 0 ? "Redstone" : (total(sum) > 0 ? "Light" : "Found before"));
                  String[] lines = new String[]{title, seenText(sum), newest > 0L ? ago(now - newest) : ""};
                  NametagUtils.begin(this.labelPos);
                  text.begin();
                  double h = text.getHeight(true);

                  for (int l = 0; l < lines.length; l++) {
                     if (!lines[l].isEmpty()) {
                        text.render(lines[l], -text.getWidth(lines[l], true) / 2.0, -h * (lines.length - l), l == 0 ? LABEL_TITLE : LABEL_TEXT, true);
                     }
                  }

                  text.end();
                  NametagUtils.end();
               }
            }
         }
      }
   }

   private static String seenText(int[] sum) {
      StringBuilder sb = new StringBuilder();
      String[] names = new String[]{"light", "sky light", "redstone", "lamp"};

      for (int k : new int[]{3, 2, 0, 1}) {
         if (sum[k] != 0) {
            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(sum[k]).append(' ').append(names[k]).append(k == 3 && sum[k] > 1 ? "s" : "");
         }
      }

      return sb.length() == 0 ? "from an earlier visit" : sb.toString();
   }

   private static String ago(long millis) {
      long s = Math.max(0L, millis / 1000L);
      return s < 60L ? s + "s ago" : (s < 3600L ? s / 60L + "m ago" : s / 3600L + "h ago");
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      long[] snapshot = this.shown;
      if (snapshot.length != 0 && this.mc.player != null) {
         int chunkX = this.mc.player.getBlockX() >> 4;
         int chunkZ = this.mc.player.getBlockZ() >> 4;
         if (!Arrays.equals(snapshot, this.lastShown) || chunkX != this.lastChunkX || chunkZ != this.lastChunkZ) {
            this.lastShown = snapshot;
            this.lastChunkX = chunkX;
            this.lastChunkZ = chunkZ;
            ChunkRects.rebuild(snapshot, chunkX, chunkZ, this.renderDistance.get(), 4096, true, this.visibleCells, this.renderRects);
            this.outline.rebuild(this.visibleCells, this.outlineEdges);
            this.areas.find(snapshot);
         }

         SettingColor c = this.chunkColor.get();
         this.fill.set((Color)c);
         this.line.set(c.r, c.g, c.b, 255);
         double y = this.fixedY.get().intValue();
         ChunkOutline.draw(event.renderer, this.renderRects, this.outlineEdges, y, this.fill, this.line, ShapeMode.Both);
         Vec3 from = RenderUtils.center;
         if (this.tracers.get() && from != null) {
            for (int i = 0; i < this.areas.count(); i++) {
               event.renderer.line(from.x, from.y, from.z, this.areas.x(i), y, this.areas.z(i), this.line);
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      return this.flagged.isEmpty() ? null : String.valueOf(this.flagged.size());
   }

   public long[] flaggedChunks() {
      return this.flagged.toLongArray();
   }

   private static final class Summary {
      final boolean sky;
      int count;
      int x;
      int y;
      int z;
      int was;
      int now;
      boolean lamp;

      boolean strong() {
         return this.lamp || !this.sky && Math.abs(this.now - this.was) >= 10;
      }

      Summary(boolean sky) {
         this.sky = sky;
      }

      void add(int x, int y, int z, int was, int now) {
         if (this.count == 0 || Math.abs(now - was) > Math.abs(this.now - this.was)) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.was = was;
            this.now = now;
         }

         this.count++;
      }
   }
}
