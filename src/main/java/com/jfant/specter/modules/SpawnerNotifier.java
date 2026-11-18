package com.jfant.specter.modules;

import com.jfant.specter.Scope;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.reveal.RevealStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;

public class SpawnerNotifier extends Module {
   private static final int MAX_ANNOUNCEMENTS_PER_TICK = 5;
   private static final int SETTLE_TICKS = 10;
   private static final int MAX_HOLD_TICKS = 60;
   private static final int TAG_TICKS = 5;
   private static final long INFO_MS = 3000L;
   private static final int MAX_TAGS = 24;
   private static final int RING_SEGMENTS = 64;
   private final RevealStore store = new RevealStore();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final SettingGroup sgNametags = this.settings.createGroup("Nametags");
   private final SettingGroup sgStorage = this.settings.createGroup("Storage");
   private final Setting<Boolean> chat = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat").description("Print a line in chat the first time each spawner is seen.").defaultValue(true).build());
   private final Setting<Boolean> sound = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sound")
            .description("Play a ding when a new spawner is found - once per batch of finds, not once per spawner. Only you hear it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> groupRange = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("group-range")
            .description("Spawners within this many blocks of each other count as one spot, reported in one line with how many there are.")
            .defaultValue(16)
            .min(1)
            .sliderRange(1, 64)
            .build()
      );
   private final Setting<SettingColor> color = this.sgRender
      .add(new ColorSetting.Builder().name("color").description("The beam and box color.").defaultValue(new SettingColor(0, 255, 255, 70)).build());
   private final Setting<Double> beamHeight = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("beam-height")
            .description("How far the beam rises above the spawner.")
            .defaultValue(160.0)
            .min(0.0)
            .sliderRange(0.0, 384.0)
            .build()
      );
   private final Setting<Double> beamWidth = this.sgRender
      .add(new DoubleSetting.Builder().name("beam-width").description("How thick the beam is.").defaultValue(0.5).min(0.05).sliderRange(0.1, 2.0).build());
   private final Setting<Boolean> drawBox = this.sgRender
      .add(new BoolSetting.Builder().name("box").description("Also draw a box around the spawner block itself.").defaultValue(true).build());
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Only draw spawners within this many blocks of you.")
            .defaultValue(512)
            .min(16)
            .sliderRange(64, 2048)
            .build()
      );
   private final Setting<Integer> maxRender = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("max-render")
            .description("At most this many beams are drawn at once.")
            .defaultValue(64)
            .min(1)
            .sliderRange(8, 512)
            .build()
      );
   private final Setting<Boolean> nametags = this.sgNametags
      .add(
         new BoolSetting.Builder()
            .name("nametags")
            .description("Show each spawner's mob and how close you have to be for it to run, over the block.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> activationRing = this.sgNametags
      .add(
         new BoolSetting.Builder()
            .name("activation-ring")
            .description("Draw a circle on the floor where the spawner stops running, brighter while you are inside it.")
            .defaultValue(true)
            .visible(this.nametags::get)
            .build()
      );
   private final Setting<Integer> nametagDistance = this.sgNametags
      .add(
         new IntSetting.Builder()
            .name("nametag-distance")
            .description("Only label spawners within this many blocks of you.")
            .defaultValue(64)
            .min(8)
            .sliderRange(16, 256)
            .visible(this.nametags::get)
            .build()
      );
   private final Setting<Double> nametagScale = this.sgNametags
      .add(
         new DoubleSetting.Builder()
            .name("nametag-scale")
            .description("Size of the label.")
            .defaultValue(1.4)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .visible(this.nametags::get)
            .build()
      );
   private final Setting<Boolean> remember = this.sgStorage
      .add(
         new BoolSetting.Builder()
            .name("remember")
            .description(
               "Save found spawners per server and dimension, and reload them when you come back. Reloaded spawners keep their beam and are not announced again."
            )
            .defaultValue(true)
            .build()
      );
   private ExecutorService scanner;
   private final ConcurrentLinkedQueue<Long> announcements = new ConcurrentLinkedQueue<>();
   private final LongArrayList pending = new LongArrayList();
   private int heldTicks;
   private int quietTicks;
   private final ArrayDeque<String> lines = new ArrayDeque<>();
   private final LongArrayList visible = new LongArrayList();
   private final Color lineColor = new Color();
   private static final Color LABEL_TITLE = new Color(255, 255, 255);
   private static final Color LABEL_TEXT = new Color(200, 200, 200);
   private String scope;
   private final List<SpawnerNotifier.Tag> tags = new ArrayList<>();
   private final Long2ObjectOpenHashMap<SpawnerNotifier.Info> infos = new Long2ObjectOpenHashMap();
   private final Vector3d labelPos = new Vector3d();
   private int tagTicks;

   public SpawnerNotifier() {
      super(SpecterClientAddon.ESP, "spawner-notifier", "Announces every spawner the server sends and marks it with a cyan beam.");
   }

   @Override
   public void onActivate() {
      this.scope = null;
      this.store.clear();
      this.resetAnnouncements();
      this.tags.clear();
      this.infos.clear();
      this.scanner = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "spawner-notifier-scanner");
         thread.setDaemon(true);
         return thread;
      });
   }

   @Override
   public void onDeactivate() {
      this.saveIfRemembering();
      if (this.scanner != null) {
         this.scanner.shutdown();
         this.scanner = null;
      }

      this.resetAnnouncements();
      this.tags.clear();
      this.infos.clear();
      this.scope = null;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.scope = null;
      this.submitTask(this.store::clear);
      this.resetAnnouncements();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.saveIfRemembering();
      this.submitTask(this.store::clear);
      this.scope = null;
      this.resetAnnouncements();
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      this.submitScan(event.chunk());
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      long pos = event.pos.asLong();
      if (this.isSpawner(event.newState)) {
         if (this.store.put(pos, event.newState.getBlock())) {
            this.announcements.add(pos);
         }
      } else if (this.isSpawner(event.oldState)) {
         this.store.remove(pos);
      }
   }

   private boolean isSpawner(BlockState state) {
      return state == null ? false : state.is(Blocks.SPAWNER);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         String current = Scope.current(this.mc);
         if (!current.equals(this.scope)) {
            this.saveIfRemembering();
            this.submitTask(this.store::clear);
            this.resetAnnouncements();
            this.scope = current;
            this.loadIfRemembering();
            this.scanLoadedChunks();
         }

         this.announce();
         if (this.nametags.get() && ++this.tagTicks % 5 == 0) {
            this.refreshTags();
         } else if (!this.nametags.get() && !this.tags.isEmpty()) {
            this.tags.clear();
         }
      }
   }

   private void announce() {
      boolean arrived;
      Long pos;
      for (arrived = false; (pos = this.announcements.poll()) != null; arrived = true) {
         this.pending.add(pos);
      }

      if (!this.pending.isEmpty()) {
         this.heldTicks++;
         this.quietTicks = arrived ? 0 : this.quietTicks + 1;
         if (this.quietTicks >= 10 || this.heldTicks >= 60) {
            int spots = !this.chat.get() && !this.sound.get() ? 0 : this.describeSpots(this.chat.get());
            if (spots > 0 && this.sound.get()) {
               this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.0F, 1.0F));
            }

            this.pending.clear();
            this.heldTicks = this.quietTicks = 0;
         }
      }

      for (int budget = 5; budget > 0 && !this.lines.isEmpty(); budget--) {
         this.info(this.lines.poll(), new Object[0]);
      }
   }

   private int describeSpots(boolean say) {
      int spots = 0;
      RevealStore.Snapshot known = this.store.blockSnapshot();
      long[] positions = known.positions();
      Long2IntOpenHashMap indexOf = new Long2IntOpenHashMap(positions.length);
      indexOf.defaultReturnValue(-1);

      for (int i = 0; i < positions.length; i++) {
         indexOf.put(positions[i], i);
      }

      LongOpenHashSet isNew = new LongOpenHashSet(this.pending);
      boolean[] seen = new boolean[positions.length];
      long rangeSq = (long)this.groupRange.get().intValue() * this.groupRange.get().intValue();
      IntArrayList spot = new IntArrayList();

      for (int p = 0; p < this.pending.size(); p++) {
         int start = indexOf.get(this.pending.getLong(p));
         if (start >= 0 && !seen[start]) {
            spot.clear();
            spot.add(start);
            seen[start] = true;

            for (int s = 0; s < spot.size(); s++) {
               long from = positions[spot.getInt(s)];
               int fx = BlockPos.getX(from);
               int fy = BlockPos.getY(from);
               int fz = BlockPos.getZ(from);

               for (int i = 0; i < positions.length; i++) {
                  if (!seen[i]) {
                     long dx = BlockPos.getX(positions[i]) - fx;
                     long dy = BlockPos.getY(positions[i]) - fy;
                     long dz = BlockPos.getZ(positions[i]) - fz;
                     if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                        seen[i] = true;
                        spot.add(i);
                     }
                  }
               }
            }

            spots++;
            if (say) {
               this.lines.add(this.describe(spot, positions, known.blocks(), isNew));
            }
         }
      }

      return spots;
   }

   private String describe(IntArrayList spot, long[] positions, Block[] blocks, LongOpenHashSet isNew) {
      int found = 0;
      double cx = 0.0;
      double cy = 0.0;
      double cz = 0.0;

      for (int s = 0; s < spot.size(); s++) {
         int i = spot.getInt(s);
         if (isNew.contains(positions[i])) {
            found++;
            cx += BlockPos.getX(positions[i]);
            cy += BlockPos.getY(positions[i]);
            cz += BlockPos.getZ(positions[i]);
         }
      }

      cx /= found;
      cy /= found;
      cz /= found;
      long at = 0L;
      double best = 1.7976931348623157E308;

      for (int sx = 0; sx < spot.size(); sx++) {
         long pos = positions[spot.getInt(sx)];
         if (isNew.contains(pos)) {
            double dx = BlockPos.getX(pos) - cx;
            double dy = BlockPos.getY(pos) - cy;
            double dz = BlockPos.getZ(pos) - cz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
               best = d;
               at = pos;
            }
         }
      }

      int x = BlockPos.getX(at);
      int y = BlockPos.getY(at);
      int z = BlockPos.getZ(at);
      int distance = (int)Math.sqrt(this.mc.player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5));
      String line = String.format("%s found at %d, %d, %d (%d blocks away)", what(found), x, y, z, distance);
      if (spot.size() > found) {
         line = line + String.format(" - %d spawners there now", spot.size());
      }

      return line;
   }

   private static String what(int count) {
      return count == 1 ? "Spawner" : count + " spawners";
   }

   private void resetAnnouncements() {
      this.announcements.clear();
      this.pending.clear();
      this.heldTicks = this.quietTicks = 0;
      this.lines.clear();
   }

   @Override
   public String getInfoString() {
      return String.valueOf(this.store.size());
   }

   public void scanLoadedChunks() {
      if (this.mc.level != null) {
         for (ChunkAccess chunk : Utils.chunks()) {
            if (chunk instanceof LevelChunk worldChunk) {
               this.submitScan(worldChunk);
            }
         }
      }
   }

   private void submitScan(LevelChunk chunk) {
      this.submitTask(() -> this.scan(chunk));
   }

   private void scan(LevelChunk chunk) {
      LevelChunkSection[] sections = chunk.getSections();
      int startX = chunk.getPos().getMinBlockX();
      int startZ = chunk.getPos().getMinBlockZ();

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (!section.hasOnlyAir() && section.maybeHas(statex -> statex.is(Blocks.SPAWNER))) {
            int baseY = chunk.getSectionYFromSectionIndex(i) * 16;

            for (int y = 0; y < 16; y++) {
               for (int x = 0; x < 16; x++) {
                  for (int z = 0; z < 16; z++) {
                     BlockState state = section.getBlockState(x, y, z);
                     if (state.is(Blocks.SPAWNER)) {
                        long pos = BlockPos.asLong(startX + x, baseY + y, startZ + z);
                        if (this.store.put(pos, state.getBlock())) {
                           this.announcements.add(pos);
                        }
                     }
                  }
               }
            }
         }
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
      if (this.mc.player != null) {
         long[] spawners = this.store.snapshot();
         if (spawners.length != 0) {
            double maxDistance = this.renderDistance.get().intValue();
            double maxDistanceSq = maxDistance * maxDistance;
            int budget = this.maxRender.get();
            this.visible.clear();

            for (long pos : spawners) {
               double dx = BlockPos.getX(pos) + 0.5 - this.mc.player.getX();
               double dz = BlockPos.getZ(pos) + 0.5 - this.mc.player.getZ();
               if (!(dx * dx + dz * dz > maxDistanceSq)) {
                  this.visible.add(pos);
                  if (this.visible.size() >= budget) {
                     break;
                  }
               }
            }

            if (!this.visible.isEmpty()) {
               SettingColor c = this.color.get();
               this.lineColor.set(c.r, c.g, c.b, 255);
               double half = this.beamWidth.get() / 2.0;
               double height = this.beamHeight.get();
               boolean box = this.drawBox.get();

               for (int i = 0; i < this.visible.size(); i++) {
                  long posx = this.visible.getLong(i);
                  double x = BlockPos.getX(posx);
                  double y = BlockPos.getY(posx);
                  double z = BlockPos.getZ(posx);
                  if (box) {
                     event.renderer.box(x, y, z, x + 1.0, y + 1.0, z + 1.0, c, this.lineColor, ShapeMode.Both, 0);
                  }

                  if (height > 0.0) {
                     event.renderer.box(x + 0.5 - half, y, z + 0.5 - half, x + 0.5 + half, y + height, z + 0.5 + half, c, this.lineColor, ShapeMode.Sides, 0);
                  }
               }
            }
         }
      }
   }

   private void refreshTags() {
      this.tags.clear();
      if (this.mc.level != null && this.mc.player != null) {
         double max = this.nametagDistance.get().intValue();
         double maxSq = max * max;
         long now = System.currentTimeMillis();

         for (long pos : this.store.snapshot()) {
            double dx = BlockPos.getX(pos) + 0.5 - this.mc.player.getX();
            double dy = BlockPos.getY(pos) + 0.5 - this.mc.player.getY();
            double dz = BlockPos.getZ(pos) + 0.5 - this.mc.player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (!(distSq > maxSq)) {
               this.tags.add(new SpawnerNotifier.Tag(pos, null, Math.sqrt(distSq)));
            }
         }

         this.tags.sort(Comparator.comparingDouble(SpawnerNotifier.Tag::distance));
         if (this.tags.size() > 24) {
            this.tags.subList(24, this.tags.size()).clear();
         }

         for (int i = 0; i < this.tags.size(); i++) {
            SpawnerNotifier.Tag tag = this.tags.get(i);
            SpawnerNotifier.Info info = (SpawnerNotifier.Info)this.infos.get(tag.pos());
            if (info == null || now - info.readAt() > 3000L) {
               info = this.read(tag.pos(), now);
               if (info == null) {
                  this.tags.remove(i--);
                  continue;
               }

               this.infos.put(tag.pos(), info);
            }

            this.tags.set(i, new SpawnerNotifier.Tag(tag.pos(), info, tag.distance()));
         }

         if (this.infos.size() > 512) {
            this.infos.clear();
         }
      }
   }

   private SpawnerNotifier.Info read(long pos, long now) {
      BlockEntity entity = this.mc.level.getBlockEntity(BlockPos.of(pos));
      if (!(entity instanceof SpawnerBlockEntity)) {
         return null;
      } else {
         CompoundTag nbt = entity.saveWithoutMetadata(this.mc.level.registryAccess());
         int range = nbt.getIntOr("RequiredPlayerRange", 16);
         String id = nbt.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getStringOr("id", "");
         String mob = id.isEmpty() ? null : EntityType.byString(id).map(type -> type.getDescription().getString()).orElse(null);
         return new SpawnerNotifier.Info(mob, range, now);
      }
   }

   @EventHandler
   private void onRenderRings(Render3DEvent event) {
      if (this.nametags.get() && this.activationRing.get() && !this.tags.isEmpty() && this.mc.player != null) {
         SettingColor c = this.color.get();

         for (SpawnerNotifier.Tag tag : this.tags) {
            int range = tag.info().range();
            if (range > 0) {
               boolean inside = tag.distance() < range;
               this.lineColor.set(c.r, c.g, c.b, inside ? 204 : 102);
               double cx = BlockPos.getX(tag.pos()) + 0.5;
               double cy = BlockPos.getY(tag.pos()) + 0.5;
               double cz = BlockPos.getZ(tag.pos()) + 0.5;
               double px = cx + range;
               double pz = cz;

               for (int i = 1; i <= 64; i++) {
                  double angle = 6.283185307179586 * i / 64.0;
                  double x = cx + Math.cos(angle) * range;
                  double z = cz + Math.sin(angle) * range;
                  event.renderer.line(px, cy, pz, x, cy, z, this.lineColor);
                  px = x;
                  pz = z;
               }
            }
         }
      }
   }

   @EventHandler
   private void onRenderLabels(Render2DEvent event) {
      if (this.nametags.get() && !this.tags.isEmpty() && this.mc.player != null) {
         TextRenderer text = TextRenderer.get();
         double fade = this.nametagDistance.get().intValue() * 0.125;

         for (SpawnerNotifier.Tag tag : this.tags) {
            SpawnerNotifier.Info info = tag.info();
            this.labelPos.set(BlockPos.getX(tag.pos()) + 0.5, BlockPos.getY(tag.pos()) + 1.2, BlockPos.getZ(tag.pos()) + 0.5);
            if (NametagUtils.to2D(this.labelPos, this.nametagScale.get())) {
               String title = info.mob() == null ? "Spawner" : info.mob() + " Spawner";
               String detail = Math.round(tag.distance()) + "m" + (info.range() > 0 ? "  -  " + info.range() + "m range" : "");
               int alpha = (int)(255.0 * Math.max(0.15, Math.min(1.0, (this.nametagDistance.get().intValue() - tag.distance()) / fade)));
               boolean inside = info.range() > 0 && tag.distance() < info.range();
               LABEL_TITLE.a = alpha;
               LABEL_TEXT.set(inside ? 0 : 200, inside ? 255 : 200, inside ? 255 : 200, alpha);
               NametagUtils.begin(this.labelPos);
               text.begin();
               double h = text.getHeight(true);
               text.render(title, -text.getWidth(title, true) / 2.0, -h * 2.0, LABEL_TITLE, true);
               text.render(detail, -text.getWidth(detail, true) / 2.0, -h, LABEL_TEXT, true);
               text.end();
               NametagUtils.end();
            }
         }
      }
   }

   public File file() {
      return this.scope == null ? null : new File(new File(new File(SpecterCore.FOLDER, "specter"), "spawners"), this.scope + ".csv");
   }

   private void saveIfRemembering() {
      if (this.remember.get()) {
         File file = this.file();
         if (file != null) {
            this.submitTask(() -> {
               try {
                  this.store.save(file);
               } catch (IOException var3) {
                  this.mc.execute(() -> this.error("Could not save spawners: %s", new Object[]{var3.getMessage()}));
               }
            });
         }
      }
   }

   private void loadIfRemembering() {
      if (this.remember.get()) {
         File file = this.file();
         if (file != null) {
            this.submitTask(() -> {
               try {
                  this.store.load(file);
                  this.announcements.clear();
               } catch (IOException var3) {
                  this.mc.execute(() -> this.error("Could not load spawners: %s", new Object[]{var3.getMessage()}));
               }
            });
         }
      }
   }

   private record Info(String mob, int range, long readAt) {
   }

   private record Tag(long pos, SpawnerNotifier.Info info, double distance) {
   }
}
