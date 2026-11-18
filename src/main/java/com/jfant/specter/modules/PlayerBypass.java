package com.jfant.specter.modules;

import com.jfant.specter.ChunkMemory;
import com.jfant.specter.Scope;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.bypass.AutoRtp;
import com.jfant.specter.bypass.BypassToast;
import com.jfant.specter.bypass.SimulationSignals;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.webhook.Webhook;
import com.jfant.specter.webhook.WebhookSettings;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

public class PlayerBypass extends Module {
   private static final int MARK_COLOR = -11205;
   private static final int MARK_FILL_ALPHA = 34;
   private static final float FALLOFF = 0.3F;
   private static final float MIN_SOURCE = 0.5F;
   private static final int MAX_FOUND = 256;
   private static final int ALERT_SPACING = 4;
   private static final int PIN_SPACING = 1;
   private static final float FROZEN_NEIGHBOUR = 0.5F;
   private static final int REPORT_SOURCES = 4;
   private static final int FUNNEL_TICKS = 1200;
   private static final long FOUND_MEMORY_MS = 600000L;
   private static final int PAST_LINE_ALPHA = 120;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTuning = this.settings.createGroup("Tuning");
   private final SettingGroup sgAutoRtp = this.settings.createGroup("Auto RTP");
   private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");
   private final Setting<Integer> sensitivity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sensitivity")
            .description(
               "How little it takes to report a player. A change only a ticking chunk makes (growth, decay, flowing water) counts 10 points, a hit or mob event 5, a mob moving 2.5, a plain block change 1.6. 1 needs about 24 points in one area, 5 about 8, 10 about 2."
            )
            .defaultValue(5)
            .range(1, 10)
            .sliderRange(1, 10)
            .build()
      );
   private final Setting<Integer> memory = this.sgTuning
      .add(
         new IntSetting.Builder()
            .name("memory")
            .description("Seconds a sign counts for, fading as it goes. Longer lets a slow trickle add up; shorter forgets sooner.")
            .defaultValue(60)
            .min(10)
            .sliderRange(10, 180)
            .build()
      );
   private final Setting<Integer> simulationDistance = this.sgTuning
      .add(
         new IntSetting.Builder()
            .name("simulation-distance")
            .description(
               "How many chunks around a player the server ticks. 0 uses what the server announces, unless that is more than 12 - DonutSMP announces 24 but ticks 4 - and then 4."
            )
            .defaultValue(0)
            .range(0, 12)
            .sliderRange(0, 12)
            .build()
      );
   private final Setting<Integer> ringMargin = this.sgTuning
      .add(
         new IntSetting.Builder()
            .name("ring-margin")
            .description(
               "Chunks to push the start of the ring out. The ring starts 2 chunks past the simulation distance, just beyond where your own simulation moves mobs; more only throws away signs close to you."
            )
            .defaultValue(0)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Integer> heightFloor = this.sgTuning
      .add(
         new IntSetting.Builder()
            .name("ignore-below-y")
            .description("Ignore signs below this height, and everything while you are below it. 0 hides every base in the deepslate; -64 ignores nothing.")
            .defaultValue(-64)
            .range(-64, 320)
            .sliderRange(-64, 64)
            .build()
      );
   private final Setting<Boolean> autoRtpOn = this.sgAutoRtp
      .add(
         new BoolSetting.Builder()
            .name("auto-rtp")
            .description("Keep running the RTP command and scanning each spot until a player is reported. Always off when the game starts.")
            .defaultValue(false)
            .onChanged(v -> {
               if (this.isActive()) {
                  if (v) {
                     this.autoRtp().start();
                  } else {
                     this.autoRtp().stopByUser();
                  }
               }
            })
            .build()
      );
   private final Setting<String> rtpCommand = this.sgAutoRtp
      .add(
         new StringSetting.Builder()
            .name("rtp-command")
            .description("The command to run, without the slash, like rtp.")
            .defaultValue("rtp")
            .visible(this.autoRtpOn::get)
            .build()
      );
   private final Setting<Double> rtpScanTime = this.sgAutoRtp
      .add(
         new DoubleSetting.Builder()
            .name("scan-time")
            .description(
               "Seconds to scan each spot after landing. Signs only count once their chunk has been loaded a few seconds and mobs have to be seen moving, so short scans miss players."
            )
            .defaultValue(15.0)
            .range(2.0, 60.0)
            .sliderRange(2.0, 60.0)
            .decimalPlaces(1)
            .visible(this.autoRtpOn::get)
            .build()
      );
   private final Setting<Double> rtpDelay = this.sgAutoRtp
      .add(
         new DoubleSetting.Builder()
            .name("rtp-delay")
            .description("Seconds at least between one RTP and the next. Any cooldown the server names is waited out as well.")
            .defaultValue(6.0)
            .range(3.0, 120.0)
            .sliderRange(3.0, 60.0)
            .decimalPlaces(1)
            .visible(this.autoRtpOn::get)
            .build()
      );
   private final Setting<Integer> rtpTimeout = this.sgAutoRtp
      .add(
         new IntSetting.Builder()
            .name("rtp-timeout")
            .description("Seconds to wait for a teleport before trying again. A warm-up the server names is added on.")
            .defaultValue(10)
            .range(3, 60)
            .sliderRange(3, 60)
            .visible(this.autoRtpOn::get)
            .build()
      );
   private final Setting<Integer> rtpMax = this.sgAutoRtp
      .add(
         new IntSetting.Builder()
            .name("max-rtps")
            .description("Stop after this many RTPs. 0 never stops.")
            .defaultValue(100)
            .range(0, 500)
            .sliderRange(0, 500)
            .visible(this.autoRtpOn::get)
            .build()
      );
   private final Setting<String> rtpMenuItem = this.sgAutoRtp
      .add(
         new StringSetting.Builder()
            .name("menu-item")
            .description("If the command opens a menu, the item to click in it, like overworld. Several can be given, separated by commas.")
            .defaultValue("")
            .visible(this.autoRtpOn::get)
            .build()
      );
   private final WebhookSettings webhook = new WebhookSettings(this.sgWebhook, "Player Bypass");
   private AutoRtp autoRtp;
   private static boolean sessionStarted;
   private final LongOpenHashSet peaks = new LongOpenHashSet();
   private final Map<ResourceKey<Level>, Long2LongOpenHashMap> foundByDimension = new HashMap<>();
   private Long2LongOpenHashMap found = new Long2LongOpenHashMap();
   private int session = -1;
   private final LongOpenHashSet shown = new LongOpenHashSet();
   private final ChunkMemory past = new ChunkMemory("player-bypass-2-finds", ".csv");
   private final LongOpenHashSet shownPast = new LongOpenHashSet();
   private String scope;
   private final Long2FloatOpenHashMap field = new Long2FloatOpenHashMap();
   private final Long2FloatOpenHashMap raw = new Long2FloatOpenHashMap();
   private int ticks;
   private int playersFound;
   private float minuteBest;
   private final Color fill = new Color();
   private final Color line = new Color();

   public PlayerBypass() {
      super(SpecterClientAddon.BASES, "player-bypass", "Finds hidden players from chunks that keep ticking outside your simulation distance.");
   }

   @Override
   public void onActivate() {
      this.foundByDimension.clear();
      this.found = new Long2LongOpenHashMap();
      this.session = SimulationSignals.session();
      SimulationSignals.clear();
      this.configure();
      this.playersFound = 0;
      this.minuteBest = 0.0F;
      if (!sessionStarted) {
         sessionStarted = true;
         if (this.autoRtpOn.get()) {
            this.autoRtpOn.set(false);
         }
      }

      if (this.autoRtpOn.get()) {
         this.autoRtp().start();
      }
   }

   @Override
   public void onDeactivate() {
      SimulationSignals.configure(false, 0, 0, true, 30, 0);
      this.leavePast();
      this.peaks.clear();
      this.foundByDimension.clear();
      this.found = new Long2LongOpenHashMap();
      this.shown.clear();
      this.field.clear();
      this.raw.clear();
      this.autoRtp().stopByUser();
      this.autoRtp().forgetCooldown();
      if (this.autoRtpOn.get()) {
         this.autoRtpOn.set(false);
      }
   }

   private AutoRtp autoRtp() {
      if (this.autoRtp == null) {
         this.autoRtp = new AutoRtp(new AutoRtp.Host() {
            @Override
            public String command() {
               return PlayerBypass.this.rtpCommand.get();
            }

            @Override
            public long scanMs() {
               return Math.round(PlayerBypass.this.rtpScanTime.get() * 1000.0);
            }

            @Override
            public long delayMs() {
               return Math.round(PlayerBypass.this.rtpDelay.get() * 1000.0);
            }

            @Override
            public long timeoutMs() {
               return PlayerBypass.this.rtpTimeout.get().intValue() * 1000L;
            }

            @Override
            public int maxRtps() {
               return PlayerBypass.this.rtpMax.get();
            }

            @Override
            public String menuItem() {
               return PlayerBypass.this.rtpMenuItem.get();
            }

            @Override
            public boolean sound() {
               return true;
            }

            @Override
            public boolean showCoordinates() {
               return true;
            }

            @Override
            public void say(String message) {
               PlayerBypass.this.info("%s", new Object[]{message});
            }

            @Override
            public void warn(String message) {
               PlayerBypass.this.warning("%s", new Object[]{message});
            }

            @Override
            public void switchedOff() {
               PlayerBypass.this.autoRtpOn.set(false);
            }
         });
      }

      return this.autoRtp;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.autoRtp != null && this.autoRtp.running()) {
         Packet<?> packet = event.packet;
         this.mc.execute(() -> {
            if (packet instanceof ClientboundLevelChunkWithLightPacket) {
               this.autoRtp.chunkArrived();
            }

            this.autoRtp.onPacket(packet);
         });
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (this.autoRtp != null && this.autoRtp.running()) {
         this.autoRtp.onPacketSent(event.packet);
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.leavePast();
   }

   private void leavePast() {
      this.past.flush();
      this.past.select(null);
      this.scope = null;
      this.shownPast.clear();
   }

   private void configure() {
      SimulationSignals.configure(true, this.simulationDistance.get(), this.ringMargin.get(), true, this.memory.get(), this.heightFloor.get());
   }

   private static boolean netherOrEnd(Level level) {
      ResourceKey<Level> dimension = level.dimension();
      return dimension.equals(Level.NETHER)
         || dimension.equals(Level.END)
         || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER)
         || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)
         || !level.dimensionType().hasSkyLight();
   }

   private float threshold() {
      return 8.0F * (float)Math.pow(2.0, (5.0 - this.sensitivity.get().intValue()) / 2.5);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.autoRtp != null) {
         this.autoRtp.tick();
      }

      if (this.mc.level != null && this.mc.player != null) {
         if (this.session != SimulationSignals.session()) {
            this.session = SimulationSignals.session();
            this.foundByDimension.clear();
            this.peaks.clear();
            this.shown.clear();
         }

         this.found = this.foundByDimension.computeIfAbsent(this.mc.level.dimension(), k -> new Long2LongOpenHashMap());
         String place = Scope.current(this.mc);
         if (!place.equals(this.scope)) {
            this.past.flush();
            this.scope = place;
            this.past.select(place);
         }

         this.past.tickAutosave();
         this.configure();
         SimulationSignals.tick();
         if (netherOrEnd(this.mc.level)) {
            SimulationSignals.dropPins();
            this.shown.clear();
         } else {
            this.drainPins();
            if (++this.ticks % 5 == 0) {
               this.collect(this.mc.player);
               this.expire();
               this.alert();
               this.refreshShown(this.mc.player);
            }

            if (this.ticks % 1200 == 0) {
               this.logFunnel(this.mc.player);
            }
         }
      }
   }

   private void logFunnel(LocalPlayer self) {
      SpecterClientAddon.LOG
         .info(
            "[Player Bypass] last minute - sim {} (server says {}{}), ring {}-{} (server view {}), you at y {}, held {}: {}; best area {} of {} needed",
            new Object[]{
               SimulationSignals.simulation(),
               SimulationSignals.serverSimulation(),
               SimulationSignals.announcedSimulationFake() ? ", not believed" : "",
               SimulationSignals.ringStart(),
               SimulationSignals.view(),
               SimulationSignals.serverView(),
               self.getBlockY(),
               SimulationSignals.holding(),
               SimulationSignals.funnel(),
               String.format(Locale.ROOT, "%.1f", this.minuteBest),
               String.format(Locale.ROOT, "%.1f", this.threshold())
            }
         );
      this.minuteBest = 0.0F;
   }

   private void collect(LocalPlayer self) {
      this.peaks.clear();
      this.field.clear();
      this.raw.clear();
      long now = SimulationSignals.now();
      int sim = SimulationSignals.simulation();
      int pcx = self.blockPosition().getX() >> 4;
      int pcz = self.blockPosition().getZ() >> 4;
      int start = SimulationSignals.ringStart();
      int view = SimulationSignals.view();
      int side = 2 * sim + 1;
      float[] weights = new float[side * side];
      float span = sim * 1.4143F + 1.0F;

      for (int dx = -sim; dx <= sim; dx++) {
         for (int dz = -sim; dz <= sim; dz++) {
            weights[(dx + sim) * side + dz + sim] = 1.0F - 0.3F * (float)Math.sqrt(dx * dx + dz * dz) / span;
         }
      }

      ObjectIterator var24 = SimulationSignals.chunks().long2ObjectEntrySet().iterator();

      while (var24.hasNext()) {
         Entry<SimulationSignals.ChunkSignal> entry = (Entry<SimulationSignals.ChunkSignal>)var24.next();
         long key = entry.getLongKey();
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);
         int d = SimulationSignals.distance(cx, cz, pcx, pcz);
         if (d >= start && d <= view) {
            float score = ((SimulationSignals.ChunkSignal)entry.getValue()).score(now);
            if (!(score < 0.5F)) {
               for (int dx = -sim; dx <= sim; dx++) {
                  for (int dz = -sim; dz <= sim; dz++) {
                     long area = ChunkPos.asLong(cx + dx, cz + dz);
                     this.field.addTo(area, score * weights[(dx + sim) * side + dz + sim]);
                     this.raw.addTo(area, score);
                  }
               }
            }
         }
      }

      LongIterator it = SimulationSignals.frozen().iterator();

      while (it.hasNext()) {
         long frozen = it.nextLong();
         this.field.remove(frozen);
         this.raw.remove(frozen);
         int fx = ChunkPos.getX(frozen);
         int fz = ChunkPos.getZ(frozen);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               long near = ChunkPos.asLong(fx + dx, fz + dz);
               if (this.field.containsKey(near)) {
                  this.field.put(near, this.field.get(near) * 0.5F);
                  this.raw.put(near, this.raw.get(near) * 0.5F);
               }
            }
         }
      }

      float threshold = this.threshold();
      ObjectIterator var29 = this.field.long2FloatEntrySet().iterator();

      while (var29.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry)var29.next();
         long key = entry.getLongKey();
         float total = this.raw.get(key);
         if (total > this.minuteBest) {
            this.minuteBest = total;
         }

         if (total >= threshold && this.isPeak(key, entry.getFloatValue(), sim)) {
            this.peaks.add(key);
         }
      }
   }

   private boolean isPeak(long key, float value, int reach) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);

      for (int dx = -reach; dx <= reach; dx++) {
         for (int dz = -reach; dz <= reach; dz++) {
            if (dx != 0 || dz != 0) {
               long other = ChunkPos.asLong(cx + dx, cz + dz);
               float near = this.field.get(other);
               if (near > value || near == value && other < key) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private boolean known(long key, int spacing) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);
      long now = System.currentTimeMillis();
      boolean any = false;
      ObjectIterator var9 = this.found.long2LongEntrySet().iterator();

      while (var9.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry)var9.next();
         long old = entry.getLongKey();
         if (SimulationSignals.distance(ChunkPos.getX(old), ChunkPos.getZ(old), cx, cz) <= spacing) {
            entry.setValue(now);
            any = true;
         }
      }

      return any;
   }

   private void remember(long key) {
      this.found.put(key, System.currentTimeMillis());
      this.past.put(key, 0);

      while (this.found.size() > 256) {
         long oldest = 0L;
         long oldestAt = 9223372036854775807L;
         ObjectIterator var7 = this.found.long2LongEntrySet().iterator();

         while (var7.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry)var7.next();
            if (entry.getLongValue() < oldestAt) {
               oldestAt = entry.getLongValue();
               oldest = entry.getLongKey();
            }
         }

         this.found.remove(oldest);
      }
   }

   private void expire() {
      long now = System.currentTimeMillis();
      this.found.values().removeIf(at -> now - at > 600000L);
   }

   private void drainPins() {
      SimulationSignals.Pin pin;
      while ((pin = SimulationSignals.pollPin()) != null) {
         BlockPos pos = pin.pos();
         long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
         SpecterClientAddon.LOG.info("[Player Bypass] {}", pin.detail());
         if (!this.known(key, 1)) {
            this.remember(key);
            this.info("%s", new Object[]{pin.detail()});
            if (!this.autoRtp().playerSpotted(pos.getX(), pos.getZ(), false)) {
               BypassToast.addOrUpdate(
                  glintHead(), "Player Activity Detected", Component.literal("at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true
               );
            }

            if (this.webhook.enabled()) {
               this.webhook
                  .send(
                     new Webhook.Embed("Hidden player acting", pin.detail(), 15158332)
                        .field("Location", Webhook.coordinates(pos.getX(), pos.getY(), pos.getZ()), false)
                        .context()
                  );
            }
         }
      }
   }

   private void alert() {
      long now = SimulationSignals.now();
      int spacing = Math.max(4, 2 * SimulationSignals.simulation());
      LongIterator it = this.peaks.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (!this.known(key, spacing)) {
            this.remember(key);
            this.playersFound++;
            int x = (ChunkPos.getX(key) << 4) + 8;
            int z = (ChunkPos.getZ(key) << 4) + 8;
            List<String> lines = this.report(key, x, z, now);
            if (!this.autoRtp().playerSpotted(x, z, false)) {
               BypassToast.addOrUpdate(glintHead(), "Player Activity Detected", Component.literal("at " + x + ", " + z), true);
            }

            if (this.webhook.enabled()) {
               Webhook.Embed embed = new Webhook.Embed("Player activity detected", lines.get(0), 15158332)
                  .field("Location", "`X: " + x + "  Z: " + z + "`", false);
               if (lines.size() > 1) {
                  embed.field("Strongest signs", String.join("\n", lines.subList(1, lines.size())), false);
               }

               this.webhook.send(embed.context());
            }
         }
      }
   }

   private void refreshShown(LocalPlayer self) {
      this.shown.clear();
      int pcx = self.blockPosition().getX() >> 4;
      int pcz = self.blockPosition().getZ() >> 4;
      int reach = SimulationSignals.view() + SimulationSignals.simulation() + 2;
      LongIterator it = this.found.keySet().iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (SimulationSignals.distance(ChunkPos.getX(key), ChunkPos.getZ(key), pcx, pcz) <= reach) {
            this.shown.add(key);
         }
      }

      this.shownPast.clear();
      it = this.past.all().keySet().iterator();

      while (it.hasNext()) {
         long key = (Long)it.next();
         if (!this.shown.contains(key) && SimulationSignals.distance(ChunkPos.getX(key), ChunkPos.getZ(key), pcx, pcz) <= reach) {
            this.shownPast.add(key);
         }
      }
   }

   private List<String> report(long foundKey, int x, int z, long now) {
      int sim = SimulationSignals.simulation();
      int fx = ChunkPos.getX(foundKey);
      int fz = ChunkPos.getZ(foundKey);
      List<Entry<SimulationSignals.ChunkSignal>> sources = new ArrayList<>();
      ObjectIterator lines = SimulationSignals.chunks().long2ObjectEntrySet().iterator();

      while (lines.hasNext()) {
         Entry<SimulationSignals.ChunkSignal> entry = (Entry<SimulationSignals.ChunkSignal>)lines.next();
         long key = entry.getLongKey();
         if (SimulationSignals.distance(ChunkPos.getX(key), ChunkPos.getZ(key), fx, fz) <= sim
            && ((SimulationSignals.ChunkSignal)entry.getValue()).score(now) >= 0.5F) {
            sources.add(entry);
         }
      }

      sources.sort((a, b) -> Float.compare(((SimulationSignals.ChunkSignal)b.getValue()).score(now), ((SimulationSignals.ChunkSignal)a.getValue()).score(now)));
      List<String> linesx = new ArrayList<>();
      linesx.add(
         "Found "
            + x
            + ", "
            + z
            + " from "
            + sources.size()
            + " chunks (sim "
            + sim
            + ", ring "
            + SimulationSignals.ringStart()
            + "-"
            + SimulationSignals.view()
            + ")"
      );

      for (int i = 0; i < Math.min(4, sources.size()); i++) {
         SimulationSignals.ChunkSignal signal = (SimulationSignals.ChunkSignal)sources.get(i).getValue();
         long key = sources.get(i).getLongKey();
         StringBuilder kinds = new StringBuilder();

         for (SimulationSignals.Kind kind : SimulationSignals.Kind.values()) {
            if (signal.count(kind) > 0) {
               kinds.append(kinds.isEmpty() ? "" : ", ").append(kind.title).append(" x").append(signal.count(kind));
            }
         }

         linesx.add(
            " chunk "
               + ((ChunkPos.getX(key) << 4) + 8)
               + ", "
               + ((ChunkPos.getZ(key) << 4) + 8)
               + String.format(Locale.ROOT, " score %.1f: ", signal.score(now))
               + kinds
         );

         for (String recent : signal.recent()) {
            linesx.add("   " + recent);
         }
      }

      for (String text : linesx) {
         SpecterClientAddon.LOG.info("[Player Bypass] {}", text);
         this.info("%s", new Object[]{text});
      }

      return linesx;
   }

   private static ItemStack glintHead() {
      ItemStack head = new ItemStack(Items.PLAYER_HEAD);
      head.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      return head;
   }

   public int playersFound() {
      return this.playersFound;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.level != null && this.mc.player != null && !netherOrEnd(this.mc.level) && (!this.shown.isEmpty() || !this.shownPast.isEmpty())) {
         double bottom = this.mc.level.getMinY();
         double top = this.mc.level.getMaxY() + 1;
         this.fill.set(ARGB.red(-11205), ARGB.green(-11205), ARGB.blue(-11205), 34);
         this.line.set(ARGB.red(-11205), ARGB.green(-11205), ARGB.blue(-11205), 255);
         LongIterator it = this.shown.iterator();

         while (it.hasNext()) {
            long key = it.nextLong();
            double x0 = ChunkPos.getX(key) << 4;
            double z0 = ChunkPos.getZ(key) << 4;
            event.renderer.box(x0, bottom, z0, x0 + 16.0, top, z0 + 16.0, this.fill, this.line, ShapeMode.Both, 0);
         }

         this.fill.a = 17;
         this.line.a = 120;
         it = this.shownPast.iterator();

         while (it.hasNext()) {
            long key = it.nextLong();
            double x0 = ChunkPos.getX(key) << 4;
            double z0 = ChunkPos.getZ(key) << 4;
            event.renderer.box(x0, bottom, z0, x0 + 16.0, top, z0 + 16.0, this.fill, this.line, ShapeMode.Both, 0);
         }
      }
   }
}
