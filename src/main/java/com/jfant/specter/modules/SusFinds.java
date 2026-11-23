package com.jfant.specter.modules;

import com.jfant.specter.Memory;
import com.jfant.specter.Scope;
import com.jfant.specter.ScoreStore;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.sus.SusFind;
import com.jfant.specter.sus.SusFindStore;
import com.jfant.specter.sus.SusMapScreen;
import com.jfant.specter.render.ChunkOutline;
import com.jfant.specter.render.ChunkRects;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;

/**
 * Sus Finds: one organised, remembered list of suspicious areas.
 *
 * <p>Sus Chunks (grown-too-much growth, Anubis-style heat peaks) and Sus Chunks V2 (holes in overdue growth, Anubis
 * "Better Sus Chunk" style) each only draw what they flag right now and forget it when you fly away. This module takes
 * what they flag every two seconds, groups it into areas, merges an area into the existing entry when it is the same spot
 * (no duplicates when a detector re-flags, the other detector agrees, or you come back later), rates each area Confirmed /
 * Likely / Uncertain ({@link SusFind.Confidence}) and saves the list per server and dimension. The detectors themselves
 * are untouched; turn on whichever you want feeding the list.
 */
public class SusFinds extends Module {
   private static final int POLL_TICKS = 40;
   private static final int SAVE_TICKS = 20 * 30;

   public enum Show {
      All,
      LikelyAndUp,
      ConfirmedOnly
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> mergeRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("merge-radius")
            .description("Flags this many chunks or closer to an existing find join it instead of making a new one.")
            .defaultValue(8)
            .range(1, 32)
            .sliderRange(1, 24)
            .build()
      );
   private final Setting<Integer> keepDays = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("keep-days")
            .description("Forget finds not flagged again for this many days (Confirmed ones last twice as long). 0 keeps them forever.")
            .defaultValue(14)
            .range(0, 365)
            .sliderRange(0, 60)
            .build()
      );
   private final Setting<Boolean> chat = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat").description("Say new finds and finds that become Confirmed in chat.").defaultValue(true).build());
   private final Setting<Boolean> chatUncertain = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("chat-uncertain")
            .description("Also say brand-new Uncertain finds (off: only Likely and Confirmed ones are said).")
            .defaultValue(false)
            .visible(this.chat::get)
            .build()
      );
   private final Setting<Keybind> mapKey = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("map-key")
            .description("Opens the Sus Finds map. Also: .sus-finds map")
            .defaultValue(Keybind.none())
            .action(SusFinds::openMap)
            .build()
      );
   private final Setting<Show> show = this.sgRender
      .add(new EnumSetting.Builder<Show>().name("show").description("Which finds are drawn in the world.").defaultValue(Show.All).build());
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Finds further than this many blocks are only on the map, not drawn in the world.")
            .defaultValue(3000)
            .range(64, 30000)
            .sliderRange(256, 10000)
            .build()
      );
   private final Setting<Boolean> beams = this.sgRender
      .add(new BoolSetting.Builder().name("beams").description("A beam up through the middle of each find, seen through walls.").defaultValue(true).build());
   private final Setting<Boolean> outlines = this.sgRender
      .add(new BoolSetting.Builder().name("outlines").description("Outline the chunks a find covers.").defaultValue(true).build());
   private final Setting<Integer> outlineY = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("outline-y")
            .description("Height the outlines are drawn at.")
            .defaultValue(63)
            .range(-64, 320)
            .sliderRange(-64, 200)
            .visible(this.outlines::get)
            .build()
      );
   private final Setting<Boolean> labels = this.sgRender
      .add(new BoolSetting.Builder().name("labels").description("A label over each find: number, confidence, size and distance.").defaultValue(true).build());

   private final SusFindStore store = new SusFindStore();
   private final Vector3d labelPos = new Vector3d();
   private final Color fill = new Color();
   private final Color line = new Color();
   private final Color dim = new Color(200, 200, 215, 255);
   private String scope;
   private int ticks;
   /** The finds as last copied out of the store; refreshed only when the store changes (not every frame). */
   private List<SusFind> view = List.of();
   private int viewVersion = -1;

   private List<SusFind> view() {
      int v = this.store.version();
      if (v != this.viewVersion) {
         this.view = this.store.snapshot();
         this.viewVersion = v;
         this.shapes.clear();
      }
      return this.view;
   }

   /** Merged rectangles [0] and outer edges [1] of a find, built once per list change (ChunkRects/ChunkOutline). */
   private final Int2ObjectOpenHashMap<IntArrayList[]> shapes = new Int2ObjectOpenHashMap<>();
   private final ChunkOutline outlineBuilder = new ChunkOutline();
   private final LongArrayList cells = new LongArrayList();

   private IntArrayList[] shape(SusFind f) {
      IntArrayList[] shape = this.shapes.get(f.id);
      if (shape != null) return shape;
      long[] keys = new long[f.size()];
      int i = 0;
      for (long key : f.chunks) keys[i++] = ScoreStore.key(ChunkPos.getX(key), ChunkPos.getZ(key));
      IntArrayList rects = new IntArrayList(), edges = new IntArrayList();
      ChunkRects.rebuild(keys, f.blockX() >> 4, f.blockZ() >> 4, Integer.MAX_VALUE / 4, SusFind.MAX_CHUNKS, true, this.cells, rects);
      this.outlineBuilder.rebuild(this.cells, edges);
      shape = new IntArrayList[]{rects, edges};
      this.shapes.put(f.id, shape);
      return shape;
   }

   public SusFinds() {
      super(
         SpecterClientAddon.BASES,
         "sus-finds",
         "Keeps one list of the suspicious areas Sus Chunks and Sus Chunks V2 flag: grouped, never duplicated, rated Confirmed / Likely / Uncertain, remembered, with a map."
      );
   }

   public static SusFinds get() {
      return Modules.get().get(SusFinds.class);
   }

   public static void openMap() {
      SusFinds module = get();
      if (module != null && module.mc.level != null) Utils.screenToOpen = new SusMapScreen(module);
   }

   public SusFindStore store() {
      return this.store;
   }

   @Override
   public void onActivate() {
      this.scope = null;
      this.ticks = 0;
   }

   @Override
   public void onDeactivate() {
      this.save();
      this.scope = null;
   }

   private Path file(String scope) {
      return SpecterCore.FOLDER.toPath().resolve("sus-finds").resolve(scope + ".json");
   }

   private void ensureScope() {
      String now = Scope.current(this.mc);
      if (now.equals(this.scope)) return;
      this.save();
      this.scope = now;
      try {
         this.store.load(this.file(now));
      } catch (IOException | RuntimeException e) {
         SpecterClientAddon.LOG.warn("Sus Finds: could not read {}", this.file(now), e);
         this.store.clear();
      }
   }

   public void save() {
      if (this.scope == null || !this.store.dirty()) return;
      try {
         this.store.save(this.file(this.scope));
      } catch (IOException | RuntimeException e) {
         SpecterClientAddon.LOG.warn("Sus Finds: could not save {}", this.scope, e);
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.save();
      this.scope = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level == null || this.mc.player == null) return;
      this.ensureScope();
      this.ticks++;
      if (this.ticks % POLL_TICKS == 0) this.poll();
      if (this.ticks % SAVE_TICKS == 0) {
         this.store.prune(System.currentTimeMillis(), this.keepDays.get() * 86_400_000L);
         this.save();
      }
   }

   /** Takes what the detectors flag right now. Only chunks that are loaded count, so remembered data is never re-counted. */
   private void poll() {
      long now = System.currentTimeMillis();
      int join = Memory.join();
      int radius = this.mergeRadius.get();
      SusChunks sus = Modules.get().get(SusChunks.class);
      if (sus != null && sus.isActive()) {
         long[] flagged = sus.flaggedSnapshot();
         if (flagged != null && flagged.length > 0) {
            LongArrayList live = new LongArrayList();
            for (long key : flagged) {
               int x = ScoreStore.chunkX(key);
               int z = ScoreStore.chunkZ(key);
               if (this.mc.level.getChunkSource().hasChunk(x, z)) live.add(ChunkPos.pack(x, z));
            }
            this.announce(this.store.ingest(SusFind.SUS_CHUNKS, live.toLongArray(), 1, radius, now, join));
         }
      }

      SusChunkFinderV2 v2 = Modules.get().get(SusChunkFinderV2.class);
      if (v2 != null && v2.isActive()) {
         long[][] tiers = v2.tierChunks();
         for (int tier = 0; tier < tiers.length; tier++) {
            if (tiers[tier].length > 0) this.announce(this.store.ingest(SusFind.SUS_CHUNKS_V2, tiers[tier], tier, radius, now, join));
         }
      }
   }

   private void announce(SusFindStore.Result result) {
      if (!this.chat.get() || !result.any()) return;
      for (SusFind f : result.created()) {
         SusFind.Confidence c = f.confidence();
         if (c == SusFind.Confidence.UNCERTAIN && !this.chatUncertain.get()) continue;
         this.info("New find #%d (%s) at %d, %d - %s.", f.id, c.title, f.blockX(), f.blockZ(), f.sourceText());
      }
      for (SusFind f : result.confirmed()) {
         this.info("Find #%d is now Confirmed (%s) at %d, %d.", f.id, f.joins >= 2 ? "flagged again on a later join" : "both detectors agree", f.blockX(), f.blockZ());
      }
   }

   public boolean shown(SusFind f) {
      return switch (this.show.get()) {
         case All -> true;
         case LikelyAndUp -> f.confidence() != SusFind.Confidence.UNCERTAIN;
         case ConfirmedOnly -> f.confidence() == SusFind.Confidence.CONFIRMED;
      };
   }

   private boolean inRange(SusFind f) {
      double dx = f.blockX() - this.mc.player.getX();
      double dz = f.blockZ() - this.mc.player.getZ();
      double max = this.renderDistance.get();
      return dx * dx + dz * dz <= max * max;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player == null || this.store.size() == 0) return;
      double y = this.outlineY.get();
      for (SusFind f : this.view()) {
         if (!this.shown(f) || !this.inRange(f)) continue;
         int rgb = f.confidence().rgb;
         this.fill.set(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 38);
         this.line.set(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 220);
         if (this.outlines.get()) {
            IntArrayList[] shape = this.shape(f);
            ChunkOutline.draw(event.depthRenderer, shape[0], shape[1], y, this.fill, this.line, ShapeMode.Both);
         }

         if (this.beams.get()) {
            double bx = f.blockX() + 0.5;
            double bz = f.blockZ() + 0.5;
            event.renderer.line(bx, -64.0, bz, bx, 320.0, bz, this.line);
            event.renderer.line(bx - 0.6, y, bz, bx + 0.6, y, bz, this.line);
            event.renderer.line(bx, y, bz - 0.6, bx, y, bz + 0.6, this.line);
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player == null || !this.labels.get() || this.store.size() == 0) return;
      TextRenderer text = TextRenderer.get();
      for (SusFind f : this.view()) {
         if (!this.shown(f) || !this.inRange(f)) continue;
         double dx = f.blockX() - this.mc.player.getX();
         double dz = f.blockZ() - this.mc.player.getZ();
         int distance = (int)Math.sqrt(dx * dx + dz * dz);
         this.labelPos.set(f.blockX() + 0.5, Math.max(this.mc.player.getY() + 8.0, this.outlineY.get() + 4.0), f.blockZ() + 0.5);
         if (!NametagUtils.to2D(this.labelPos, 1.2, false)) continue;
         SusFind.Confidence c = f.confidence();
         String title = "#" + f.id + "  " + c.title;
         String detail = f.size() + (f.size() == 1 ? " chunk  " : " chunks  ") + metres(distance) + "  " + f.sourceText();
         NametagUtils.begin(this.labelPos, event.graphics);
         text.begin(event.graphics);
         double h = text.getHeight(true);
         this.line.set(c.rgb >> 16 & 255, c.rgb >> 8 & 255, c.rgb & 255, 255);
         text.render(title, -text.getWidth(title, true) / 2.0, -h * 2.0, this.line, true);
         text.render(detail, -text.getWidth(detail, true) / 2.0, -h, this.dim, true);
         text.end();
         NametagUtils.end(event.graphics);
      }
   }

   public static String metres(int blocks) {
      return blocks >= 10_000 ? String.format(Locale.ROOT, "%.1fk m", blocks / 1000.0) : blocks + " m";
   }

   /** The finds sorted for lists: Confirmed first, then nearest. */
   public List<SusFind> sorted() {
      List<SusFind> all = this.store.snapshot();
      double px = this.mc.player == null ? 0.0 : this.mc.player.getX();
      double pz = this.mc.player == null ? 0.0 : this.mc.player.getZ();
      all.sort((a, b) -> {
         int byConfidence = Integer.compare(a.confidence().ordinal(), b.confidence().ordinal());
         if (byConfidence != 0) return byConfidence;
         double da = (a.blockX() - px) * (a.blockX() - px) + (a.blockZ() - pz) * (a.blockZ() - pz);
         double db = (b.blockX() - px) * (b.blockX() - px) + (b.blockZ() - pz) * (b.blockZ() - pz);
         return Double.compare(da, db);
      });
      return all;
   }
}
