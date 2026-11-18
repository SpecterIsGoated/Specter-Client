package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.mixin.ParticleAccessor;
import com.jfant.specter.render.TintVertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.Int2LongMap.Entry;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HitEffects extends Module {
   private static HitEffects instance;
   private static int nextFakeId = -2000000;
   private static final long KILL_WINDOW_MS = 3000L;
   private static final long COMBAT_WINDOW_MS = 5000L;
   private static final double KILL_RANGE = 12.0;
   private static final int LIGHTNING_TICKS = 30;
   private static final long VANILLA_ECHO_MS = 1500L;
   private static final int MAX_SPARKS = 600;
   private static final double RAINBOW_MS = 1600.0;
   private final SettingGroup sgHit = this.settings.getDefaultGroup();
   private final SettingGroup sgKill = this.settings.createGroup("Kill");
   private final Setting<Boolean> playersOnly = this.sgHit
      .add(new BoolSetting.Builder().name("players-only").description("Only for players, not mobs.").defaultValue(false).build());
   private final Setting<Boolean> hideVanilla = this.sgHit
      .add(
         new BoolSetting.Builder()
            .name("hide-vanilla-hits")
            .description(
               "Hide the crit and sharpness sparks, sweep and damage hearts your own hits make, so only these effects show. Other players' hits keep theirs."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> particles = this.sgHit
      .add(
         new BoolSetting.Builder()
            .name("hit-particles")
            .description("A burst of colour-changing dust and glints out of what you hit.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> amount = this.sgHit
      .add(
         new IntSetting.Builder()
            .name("amount")
            .description("How many particles a hit makes.")
            .defaultValue(14)
            .range(2, 60)
            .sliderRange(2, 40)
            .visible(this.particles::get)
            .build()
      );
   private final Setting<HitEffects.DustColors> dustColors = this.sgHit
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("dust-colors"))
                     .description(
                        "Fade: from dust-color to fade-color as it floats away. Rainbow: each speck runs through the rainbow. Solid: dust-color only."
                     ))
                  .defaultValue(HitEffects.DustColors.Fade))
               .visible(this.particles::get))
            .build()
      );
   private final Setting<SettingColor> dustColor = this.sgHit
      .add(
         new ColorSetting.Builder()
            .name("dust-color")
            .description("The dust's colour (where it starts, for Fade).")
            .defaultValue(new SettingColor(255, 110, 199))
            .visible(() -> this.particles.get() && this.dustColors.get() != HitEffects.DustColors.Rainbow)
            .build()
      );
   private final Setting<SettingColor> fadeColor = this.sgHit
      .add(
         new ColorSetting.Builder()
            .name("fade-color")
            .description("The colour the dust fades to.")
            .defaultValue(new SettingColor(110, 215, 255))
            .visible(() -> this.particles.get() && this.dustColors.get() == HitEffects.DustColors.Fade)
            .build()
      );
   private final Setting<Boolean> flash = this.sgHit
      .add(new BoolSetting.Builder().name("hit-flash").description("A short coloured flash over what you hit.").defaultValue(true).build());
   private final Setting<SettingColor> flashColor = this.sgHit
      .add(
         new ColorSetting.Builder()
            .name("flash-color")
            .description("The flash's colour; its alpha is how strong it starts.")
            .defaultValue(new SettingColor(255, 90, 190, 140))
            .visible(this.flash::get)
            .build()
      );
   private final Setting<Integer> flashTime = this.sgHit
      .add(
         new IntSetting.Builder()
            .name("flash-time")
            .description("How long the flash fades for, in milliseconds.")
            .defaultValue(350)
            .range(50, 1000)
            .sliderRange(50, 600)
            .visible(this.flash::get)
            .build()
      );
   private final Setting<Boolean> hitSound = this.sgHit
      .add(new BoolSetting.Builder().name("hit-sound").description("A soft amethyst chime on each hit, only you hear it.").defaultValue(false).build());
   private final Setting<HitEffects.KillEffect> killEffect = this.sgKill
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("kill-effect"))
                  .description("What happens where something you killed dies."))
               .defaultValue(HitEffects.KillEffect.Both))
            .build()
      );
   private final Setting<HitEffects.BoltColors> boltColors = this.sgKill
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("lightning-colors"))
                     .description("Rainbow: the bolt runs through colours, top to bottom and over time. Custom: one colour. Vanilla: untouched."))
                  .defaultValue(HitEffects.BoltColors.Rainbow))
               .visible(this::lightningOn))
            .build()
      );
   private final Setting<SettingColor> boltColor = this.sgKill
      .add(
         new ColorSetting.Builder()
            .name("lightning-color")
            .description("The bolt's colour for Custom.")
            .defaultValue(new SettingColor(255, 110, 199))
            .visible(() -> this.lightningOn() && this.boltColors.get() == HitEffects.BoltColors.Custom)
            .build()
      );
   private final Setting<Boolean> thunder = this.sgKill
      .add(
         new BoolSetting.Builder()
            .name("thunder")
            .description("Let the lightning make its thunder. Off is a silent bolt.")
            .defaultValue(true)
            .visible(this::lightningOn)
            .build()
      );
   private final Int2LongOpenHashMap recentHits = new Int2LongOpenHashMap();
   private final ConcurrentHashMap<Integer, Long> echoHits = new ConcurrentHashMap<>();
   private volatile double hitX;
   private volatile double hitY;
   private volatile double hitZ;
   private volatile double meX;
   private volatile double meY;
   private volatile double meZ;
   private volatile long hitAt;
   private final Int2LongOpenHashMap bolts = new Int2LongOpenHashMap();
   private final LongOpenHashSet boltSeeds = new LongOpenHashSet();
   private final IntArrayList scratch = new IntArrayList();
   private final Int2LongOpenHashMap celebrated = new Int2LongOpenHashMap();
   private final List<HitEffects.Spark> sparks = new ArrayList<>();
   private long lastCombat;
   private int logged;
   private int boltLogs;
   private int dustLogs;
   private HitEffects.Spark watched;
   private boolean cirrusChecked;
   private long watchedAt;
   private Entity flashed;
   private long flashedAt;
   private long ticks;
   private final Color flashNow = new Color();

   public HitEffects() {
      super(
         SpecterClientAddon.VISUALS,
         "hit-effects",
         "Colour-changing dust, glints and a flash when you hit; colour-changing lightning and souls when you kill. Only you see them."
      );
      instance = this;
   }

   private boolean lightningOn() {
      return this.killEffect.get() == HitEffects.KillEffect.Lightning || this.killEffect.get() == HitEffects.KillEffect.Both;
   }

   @Override
   public void onDeactivate() {
      this.clearBolts();
      this.reset();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.bolts.clear();
      this.reset();
   }

   private void reset() {
      this.recentHits.clear();
      this.echoHits.clear();
      this.celebrated.clear();
      this.boltSeeds.clear();
      this.sparks.clear();
      this.flashed = null;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      this.lastCombat = System.currentTimeMillis();
      if (event.entity instanceof LivingEntity target && this.mc.level != null && this.mc.player != null && this.counts(target)) {
         long now = System.currentTimeMillis();
         this.recentHits.put(target.getId(), now);
         this.echoHits.put(target.getId(), now);
         this.hitX = target.getX();
         this.hitY = target.getY(0.5);
         this.hitZ = target.getZ();
         this.meX = this.mc.player.getX();
         this.meY = this.mc.player.getY(0.5);
         this.meZ = this.mc.player.getZ();
         this.hitAt = now;
         if (this.particles.get()) {
            this.burst(target);
         }

         if (this.flash.get()) {
            this.flashed = target;
            this.flashedAt = now;
         }

         if (this.hitSound.get()) {
            this.mc
               .level
               .playLocalSound(
                  target.getX(),
                  target.getY(),
                  target.getZ(),
                  SoundEvents.AMETHYST_BLOCK_CHIME,
                  SoundSource.PLAYERS,
                  0.8F,
                  1.4F + this.mc.level.getRandom().nextFloat() * 0.3F,
                  false
               );
         }
      }
   }

   private boolean counts(Entity entity) {
      return entity != this.mc.player && (!this.playersOnly.get() || entity instanceof Player);
   }

   private void burst(LivingEntity target) {
      AABB box = target.getBoundingBox();
      RandomSource random = this.mc.level.getRandom();
      DustParticleOptions dust = new DustParticleOptions(rgb(this.dustColor.get()), 1.7F);
      double cx = (box.minX + box.maxX) / 2.0;
      double cy = box.minY + box.getYsize() * 0.6;
      double cz = (box.minZ + box.maxZ) / 2.0;
      float baseHue = (float)(System.currentTimeMillis() % 1600L / 1600.0);
      int n = this.amount.get();

      for (int i = 0; i < n; i++) {
         double x = box.minX + random.nextDouble() * box.getXsize();
         double y = box.minY + random.nextDouble() * box.getYsize();
         double z = box.minZ + random.nextDouble() * box.getZsize();
         if (i % 4 == 3) {
            double dx = x - cx;
            double dy = y - cy;
            double dz = z - cz;
            double len = Math.max(0.05, Math.sqrt(dx * dx + dy * dy + dz * dz));
            this.mc.level.addParticle(ParticleTypes.END_ROD, cx, cy, cz, dx / len * 0.18, dy / len * 0.18, dz / len * 0.18);
         } else {
            double vx = (random.nextDouble() - 0.5) * 5.0;
            double vy = random.nextDouble() * 3.5;
            double vz = (random.nextDouble() - 0.5) * 5.0;
            if (this.mc.particleEngine.createParticle(dust, x, y, z, vx, vy, vz) instanceof SingleQuadParticle billboard && this.sparks.size() < 600) {
               HitEffects.Spark spark = new HitEffects.Spark(billboard, baseHue + (float)i / n);
               this.sparks.add(spark);
               this.recolor(spark, 0.0F);
               if (this.watched == null && this.dustLogs < 2) {
                  this.watched = spark;
                  this.watchedAt = this.ticks;
                  SpecterClientAddon.LOG
                     .info("Hit Effects: dust is {} ({} mode), start {}", new Object[]{billboard.getClass().getSimpleName(), this.dustColors.get(), billboard});
               }
            }
         }
      }

      if (this.logged < 3) {
         this.logged++;
         SpecterClientAddon.LOG.info("Hit Effects: hit {}, {} particles", target.getName().getString(), n);
      }
   }

   private void recolor(HitEffects.Spark spark, float tickDelta) {
      ParticleAccessor life = (ParticleAccessor)spark.particle();
      float t = Mth.clamp((life.specter$getAge() + tickDelta) / Math.max(1, life.specter$getMaxAge()), 0.0F, 1.0F);

      int c = switch ((HitEffects.DustColors)this.dustColors.get()) {
         case Fade -> lerpRgb(rgb(this.dustColor.get()), rgb(this.fadeColor.get()), t);
         case Rainbow -> hsb(spark.hue() + t * 0.8F + (float)(System.currentTimeMillis() % 1600L / 1600.0));
         case Solid -> rgb(this.dustColor.get());
      };
      spark.particle().setColor((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F);
   }

   public static boolean hidesOwnVanilla() {
      return instance != null && instance.isActive() && instance.hideVanilla.get();
   }

   private boolean echoOfYourHit(int entityId) {
      Long at = this.echoHits.get(entityId);
      return at != null && System.currentTimeMillis() - at <= 1500L;
   }

   private boolean nearYourHit(double x, double y, double z) {
      if (System.currentTimeMillis() - this.hitAt > 1500L) {
         return false;
      } else {
         double dt = sq(x - this.hitX) + sq(y - this.hitY) + sq(z - this.hitZ);
         double dm = sq(x - this.meX) + sq(y - this.meY) + sq(z - this.meZ);
         return dt <= 16.0 || dm <= 9.0;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null) {
         this.ticks++;
         long now = System.currentTimeMillis();
         this.scratch.clear();
         ObjectIterator i = this.recentHits.int2LongEntrySet().iterator();

         while (i.hasNext()) {
            Entry entry = (Entry)i.next();
            int id = entry.getIntKey();
            Entity entity = this.mc.level.getEntity(id);
            if (now - entry.getLongValue() > 3000L) {
               this.scratch.add(id);
            } else if (entity instanceof LivingEntity living && (living.isDeadOrDying() || living.getHealth() <= 0.0F)) {
               this.scratch.add(id);
               this.died(living, true);
            }
         }

         for (int ix = 0; ix < this.scratch.size(); ix++) {
            this.recentHits.remove(this.scratch.getInt(ix));
         }

         this.celebrated.values().removeIf(at -> now - at > 10000L);
         if (this.watched != null && this.ticks - this.watchedAt >= 8L) {
            this.dustLogs++;
            SpecterClientAddon.LOG.info("Hit Effects: dust after 8 ticks: alive {}, {}", this.watched.particle().isAlive(), this.watched.particle());
            this.watched = null;
         }

         this.echoHits.values().removeIf(at -> now - at > 1500L);
         this.boltSeeds.clear();
         this.scratch.clear();
         i = this.bolts.int2LongEntrySet().iterator();

         while (i.hasNext()) {
            Entry entry = (Entry)i.next();
            if (this.ticks >= entry.getLongValue()) {
               this.scratch.add(entry.getIntKey());
            } else if (this.mc.level.getEntity(entry.getIntKey()) instanceof LightningBolt bolt) {
               this.boltSeeds.add(bolt.seed);
            }
         }

         for (int ix = 0; ix < this.scratch.size(); ix++) {
            int id = this.scratch.getInt(ix);
            this.bolts.remove(id);
            this.mc.level.removeEntity(id, RemovalReason.DISCARDED);
         }
      }
   }

   @EventHandler
   private void onSend(PacketEvent.Send event) {
      if (event.packet instanceof ServerboundUseItemOnPacket || event.packet instanceof ServerboundUseItemPacket) {
         this.lastCombat = System.currentTimeMillis();
      }
   }

   @EventHandler
   private void onReceive(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundAnimatePacket animation) {
         int kind = animation.getAction();
         if ((kind == 4 || kind == 5) && this.hideVanilla.get() && this.echoOfYourHit(animation.getId())) {
            event.cancel();
         }
      } else if (!(event.packet instanceof ClientboundLevelParticlesPacket particle)) {
         if (event.packet instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 3) {
            this.mc.execute(() -> {
               if (this.mc.level != null && this.isActive()) {
                  if (packet.getEntity(this.mc.level) instanceof LivingEntity living) {
                     this.died(living, false);
                  }
               }
            });
         }
      } else {
         ParticleType<?> type = particle.particle().getType();
         if ((type == ParticleTypes.SWEEP_ATTACK || type == ParticleTypes.DAMAGE_INDICATOR || type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT)
            && this.hideVanilla.get()
            && this.nearYourHit(particle.x(), particle.y(), particle.z())) {
            event.cancel();
         }
      }
   }

   private void died(LivingEntity target, boolean hitByYou) {
      if (this.mc.player != null && this.counts(target) && !this.celebrated.containsKey(target.getId())) {
         long now = System.currentTimeMillis();
         Long hit = this.recentHits.containsKey(target.getId()) ? this.recentHits.get(target.getId()) : null;
         boolean yours = hitByYou || hit != null && now - hit <= 3000L || now - this.lastCombat <= 5000L && target.distanceToSqr(this.mc.player) <= 144.0;
         if (yours) {
            this.celebrated.put(target.getId(), now);
            this.kill(target);
         }
      }
   }

   private void kill(LivingEntity target) {
      SpecterClientAddon.LOG.info("Hit Effects: kill {}", target.getName().getString());
      if (!this.cirrusChecked) {
         this.cirrusChecked = true;
         SpecterClientAddon.LOG.info("Hit Effects: Cirrus lightning hook {}", cirrusHook());
      }

      HitEffects.KillEffect effect = this.killEffect.get();
      Vec3 at = target.position();
      if (effect == HitEffects.KillEffect.Lightning || effect == HitEffects.KillEffect.Both) {
         this.lightning(at);
      }

      if (effect == HitEffects.KillEffect.Souls || effect == HitEffects.KillEffect.Both) {
         this.souls(target);
      }
   }

   private void lightning(Vec3 at) {
      LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(this.mc.level, EntitySpawnReason.TRIGGERED);
      if (bolt != null) {
         bolt.setId(nextFakeId--);
         bolt.setVisualOnly(true);
         bolt.snapTo(at.x, at.y, at.z);
         if (!this.thunder.get()) {
            bolt.setSilent(true);
         }

         this.mc.level.addEntity(bolt);
         this.bolts.put(bolt.getId(), this.ticks + 30L);
         this.boltSeeds.add(bolt.seed);
      }
   }

   private static String cirrusHook() {
      try {
         Class<?> renderer = Class.forName("com.jvn.cirrus.client.CirrusLightningRenderer");

         for (Method method : renderer.getDeclaredMethods()) {
            if (method.getName().contains("tintBolt")) {
               return "attached";
            }
         }

         return "NOT attached";
      } catch (ClassNotFoundException var5) {
         return "not needed (no Cirrus)";
      }
   }

   private void souls(LivingEntity target) {
      AABB box = target.getBoundingBox();
      RandomSource random = this.mc.level.getRandom();

      for (int i = 0; i < 24; i++) {
         double x = box.minX + random.nextDouble() * box.getXsize();
         double y = box.minY + random.nextDouble() * box.getYsize() * 0.6;
         double z = box.minZ + random.nextDouble() * box.getZsize();
         this.mc
            .level
            .addParticle(
               i % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME,
               x,
               y,
               z,
               (random.nextDouble() - 0.5) * 0.08,
               0.05 + random.nextDouble() * 0.12,
               (random.nextDouble() - 0.5) * 0.08
            );
      }

      this.mc
         .level
         .playLocalSound(
            target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.9F + random.nextFloat() * 0.2F, false
         );
   }

   private void clearBolts() {
      if (this.mc.level != null) {
         IntIterator var1 = this.bolts.keySet().iterator();

         while (var1.hasNext()) {
            int id = (Integer)var1.next();
            this.mc.level.removeEntity(id, RemovalReason.DISCARDED);
         }
      }

      this.bolts.clear();
      this.boltSeeds.clear();
   }

   public static CustomGeometryRenderer tintBolt(long seed, CustomGeometryRenderer draw, String renderer) {
      HitEffects self = instance;
      if (self == null || !self.isActive() || self.boltColors.get() == HitEffects.BoltColors.Vanilla) {
         return draw;
      } else if (!self.boltSeeds.contains(seed)) {
         if (!self.boltSeeds.isEmpty() && self.boltLogs < 6) {
            self.boltLogs++;
            SpecterClientAddon.LOG.info("Hit Effects: {} drew a bolt with seed {}, ours are {}", new Object[]{renderer, seed, self.boltSeeds});
         }

         return draw;
      } else {
         if (self.boltLogs < 6) {
            self.boltLogs++;
            SpecterClientAddon.LOG.info("Hit Effects: colouring our bolt (drawn by {})", renderer);
         }

         return (matrices, consumer) -> draw.render(matrices, new TintVertexConsumer(consumer, self::boltRgb));
      }
   }

   private int boltRgb(float y) {
      if (this.boltColors.get() == HitEffects.BoltColors.Custom) {
         return rgb(this.boltColor.get());
      } else {
         float cycle = (float)(System.currentTimeMillis() % 1600L / 1600.0);
         return hsb(cycle - y / 96.0F);
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (!this.sparks.isEmpty()) {
         this.sparks.removeIf(sparkx -> !sparkx.particle().isAlive());

         for (HitEffects.Spark spark : this.sparks) {
            this.recolor(spark, event.tickDelta);
         }
      }

      if (this.flashed != null) {
         double age = (double)(System.currentTimeMillis() - this.flashedAt) / this.flashTime.get().intValue();
         if (!(age >= 1.0) && !this.flashed.isRemoved()) {
            SettingColor c = this.flashColor.get();
            this.flashNow.set(c.r, c.g, c.b, (int)(c.a * (1.0 - age)));
            Vec3 pos = this.flashed.getPosition(event.tickDelta);
            AABB box = this.flashed.getBoundingBox().move(pos.subtract(this.flashed.position())).inflate(0.05);
            event.renderer.box(box, this.flashNow, this.flashNow, ShapeMode.Sides, 0);
         } else {
            this.flashed = null;
         }
      }
   }

   private static int rgb(SettingColor c) {
      return c.r << 16 | c.g << 8 | c.b;
   }

   private static int hsb(float hue) {
      return java.awt.Color.HSBtoRGB(hue - (float)Math.floor(hue), 0.7F, 1.0F) & 16777215;
   }

   private static int lerpRgb(int a, int b, float t) {
      int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return r << 16 | g << 8 | bl;
   }

   private static double sq(double v) {
      return v * v;
   }

   public static enum BoltColors {
      Rainbow,
      Custom,
      Vanilla;
   }

   public static enum DustColors {
      Fade,
      Rainbow,
      Solid;
   }

   public static enum KillEffect {
      Lightning,
      Souls,
      Both,
      None;
   }

   private record Spark(SingleQuadParticle particle, float hue) {
   }
}
