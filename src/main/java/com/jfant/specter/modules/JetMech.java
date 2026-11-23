package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.mech.MechAnim;
import com.jfant.specter.mech.MechModel;
import com.jfant.specter.mech.MechPalette;
import com.jfant.specter.mech.MechPose;
import com.jfant.specter.mech.MechRender;
import com.jfant.specter.mech.MechSounds;
import com.jfant.specter.mech.MechStateHolder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Iterator;
import java.util.OptionalInt;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Jet Mech: you (and other players) become an original transforming robot that folds into a twin-engine stealth
 * fighter when elytra flight starts, and unfolds again on landing.
 *
 * <p>Purely visual: flight is still vanilla elytra flight (the aircraft just follows where the player really flies),
 * and firework boosts light the afterburners without touching the boost itself.
 *
 * <p>Multiplayer: everything about the animation (robot / jet, transformation progress, afterburner) is derived from
 * what the server already sends to every client - the elytra flag, positions and the rocket attached to a player - so
 * every Specter user sees the same thing at the same moment with no extra traffic. The only thing a server never
 * forwards is "this player wants to be a mech": with Broadcast on, that rides along in an unused bit of your skin
 * settings, which vanilla clients ignore and other Specter clients read. Vanilla players can never see the mech.
 */
public class JetMech extends Module implements MechAnim.Config {
   /** Unused bit of the skin customisation byte (vanilla uses bits 0-6). */
   public static final int BROADCAST_BIT = 0x80;
   private static JetMech instance;

   public enum Others {
      Broadcasting("Mech users"),
      Friends("Friends + mech users"),
      Everyone("Everyone"),
      Nobody("Nobody");

      private final String title;

      Others(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFlight = this.settings.createGroup("Flight");
   private final SettingGroup sgEffects = this.settings.createGroup("Effects");
   private final SettingGroup sgLook = this.settings.createGroup("Paint");
   private final SettingGroup sgMulti = this.settings.createGroup("Multiplayer");

   private final Setting<Boolean> self = this.sgGeneral
      .add(new BoolSetting.Builder().name("self").description("Show yourself as the mech (third person, freecam and the inventory preview).").defaultValue(true).build());
   private final Setting<Boolean> transformation = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("transformation")
            .description("Animate the robot folding into the jet when elytra flight starts. Off = swaps instantly.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> reverse = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("reverse-transformation")
            .description("Animate the jet unfolding back into the robot when you land. Off = swaps back instantly.")
            .defaultValue(true)
            .visible(this.transformation::get)
            .build()
      );
   private final Setting<Double> animSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("animation-speed")
            .description("How fast the transformation plays. 1 = about 1.3 seconds.")
            .defaultValue(1.0)
            .range(0.25, 3.0)
            .sliderRange(0.25, 3.0)
            .build()
      );
   private final Setting<Double> modelScale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("model-scale")
            .description("Size of the robot and the jet. 1 = a bit taller than a player.")
            .defaultValue(1.0)
            .range(0.5, 2.0)
            .sliderRange(0.5, 2.0)
            .build()
      );
   private final Setting<Boolean> flightAnim = this.sgFlight
      .add(
         new BoolSetting.Builder()
            .name("flight-animations")
            .description("Banking into turns, moving control surfaces, thrust vectoring and a little turbulence. Off = the jet just points where you fly.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> landingGear = this.sgFlight
      .add(new BoolSetting.Builder().name("landing-gear").description("Lower the landing gear when the jet is on the ground.").defaultValue(true).build());
   private final Setting<Boolean> afterburners = this.sgEffects
      .add(
         new BoolSetting.Builder()
            .name("afterburners")
            .description("Firework boosts light both engines' afterburners: long blue and orange flames with shock diamonds.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> particles = this.sgEffects
      .add(
         new BoolSetting.Builder()
            .name("engine-particles")
            .description("Exhaust flames and smoke, the ignition burst, sparks while transforming and dust on hard landings.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> trails = this.sgEffects
      .add(new BoolSetting.Builder().name("vapour-trails").description("White trails from the wing tips at high speed and in hard turns.").defaultValue(true).build());
   private final Setting<Boolean> sounds = this.sgEffects
      .add(
         new BoolSetting.Builder()
            .name("custom-sounds")
            .description("Servo and clank sounds while transforming, engine ignition, afterburner roar, a looping jet engine and heavy footsteps. Only you hear them.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> volume = this.sgEffects
      .add(
         new DoubleSetting.Builder()
            .name("sound-volume")
            .description("Volume of the mech's sounds.")
            .defaultValue(0.8)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.sounds::get)
            .build()
      );
   private final Setting<MechPalette.Paint> paint = this.sgLook
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("paint"))
                  .description("Colour scheme. Custom uses the three colours below."))
               .defaultValue(MechPalette.Paint.Specter))
            .build()
      );
   private final Setting<SettingColor> armorColor = this.sgLook
      .add(
         new ColorSetting.Builder()
            .name("armor-color")
            .description("Main armour colour (Custom paint).")
            .defaultValue(new SettingColor(74, 79, 88, 255))
            .visible(() -> this.paint.get() == MechPalette.Paint.Custom)
            .build()
      );
   private final Setting<SettingColor> accentColor = this.sgLook
      .add(
         new ColorSetting.Builder()
            .name("accent-color")
            .description("Accent panels and stripes (Custom paint).")
            .defaultValue(new SettingColor(138, 79, 224, 255))
            .visible(() -> this.paint.get() == MechPalette.Paint.Custom)
            .build()
      );
   private final Setting<SettingColor> glowColor = this.sgLook
      .add(
         new ColorSetting.Builder()
            .name("visor-color")
            .description("Visor and chest light (Custom paint).")
            .defaultValue(new SettingColor(201, 160, 255, 255))
            .visible(() -> this.paint.get() == MechPalette.Paint.Custom)
            .build()
      );
   private final Setting<Boolean> broadcast = this.sgMulti
      .add(
         new BoolSetting.Builder()
            .name("broadcast")
            .description(
               "Let other Specter users see you as the mech. Sets an unused bit in your skin settings: vanilla players see nothing different, but a server plugin could notice the odd bit, so it is off by default."
            )
            .defaultValue(false)
            .onChanged(v -> sendClientInformation())
            .build()
      );
   private final Setting<Others> others = this.sgMulti
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("show-others"))
                  .description(
                     "Which other players you see as mechs. Mech users = players with Broadcast on. Their transformation and afterburners follow their real flight."
                  ))
               .defaultValue(Others.Broadcasting))
            .build()
      );

   private final Int2ObjectOpenHashMap<MechAnim> anims = new Int2ObjectOpenHashMap<>();
   private final Int2ObjectOpenHashMap<MechSounds.Engine> engines = new Int2ObjectOpenHashMap<>();
   private final IntOpenHashSet boosted = new IntOpenHashSet();
   private final MechSounds.Engine.MechVolume engineVolume = new MechSounds.Engine.MechVolume() {
      @Override
      public float volume() {
         return JetMech.this.sounds.get() ? JetMech.this.volume.get().floatValue() : 0;
      }

      @Override
      public boolean alive(MechAnim anim) {
         return JetMech.this.isActive() && JetMech.this.anims.get(anim.entityId) == anim;
      }
   };
   private final Vector3f v1 = new Vector3f();
   private final Vector3f v2 = new Vector3f();
   private int sparkClock;

   public JetMech() {
      super(
         SpecterClientAddon.VISUALS,
         "jet-mech",
         "Become a transforming robot that folds into a stealth fighter jet when you fly with an elytra. Fireworks light the afterburners. Visual only.",
         "mech",
         "transformer",
         "jet"
      );
      instance = this;
   }

   @Override
   public void onActivate() {
      sendClientInformation();
   }

   @Override
   public void onDeactivate() {
      this.anims.clear();
      this.engines.clear();
      sendClientInformation();
   }

   // ================================================================================================ settings for MechAnim

   @Override
   public boolean transformAnimation() {
      return this.transformation.get();
   }

   @Override
   public boolean reverseAnimation() {
      return this.reverse.get();
   }

   @Override
   public float animationSpeed() {
      return this.animSpeed.get().floatValue();
   }

   @Override
   public boolean flightAnimation() {
      return this.flightAnim.get();
   }

   @Override
   public boolean afterburners() {
      return this.afterburners.get();
   }

   @Override
   public boolean landingGear() {
      return this.landingGear.get();
   }

   @Override
   public boolean trails() {
      return this.trails.get();
   }

   // ================================================================================================ multiplayer flag

   /** True when the outgoing client information should carry the mech bit. */
   public static boolean broadcasting() {
      return instance != null && instance.isActive() && instance.broadcast.get();
   }

   private static void sendClientInformation() {
      var mc = net.minecraft.client.Minecraft.getInstance();
      if (mc.player != null && mc.getConnection() != null) mc.options.broadcastOptions();
   }

   private boolean wantsMech(AbstractClientPlayer p) {
      if (p == this.mc.player) return this.self.get();
      if (p.isSpectator()) return false;
      return switch (this.others.get()) {
         case Nobody -> false;
         case Everyone -> true;
         case Friends -> Friends.get().isFriend(p) || broadcasts(p);
         default -> broadcasts(p);
      };
   }

   private static boolean broadcasts(AbstractClientPlayer p) {
      return (p.getEntityData().get(Avatar.DATA_PLAYER_MODE_CUSTOMISATION) & BROADCAST_BIT) != 0;
   }

   // ================================================================================================ tick

   @EventHandler
   private void onTick(TickEvent.Post event) {
      ClientLevel level = this.mc.level;
      if (level == null || this.mc.player == null) return;
      try {
         MechRender.ensureResources();
      } catch (Throwable t) {
         this.error("Jet Mech could not set up its textures: %s", t.getMessage());
         this.toggle();
         return;
      }
      // rockets attached to players = afterburners (the attach id is synced to every client)
      this.boosted.clear();
      for (Entity e : level.entitiesForRendering()) {
         if (e instanceof FireworkRocketEntity rocket) {
            OptionalInt id = rocket.getEntityData().get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET);
            if (id.isPresent()) this.boosted.add(id.getAsInt());
         }
      }
      MechPalette.Paint scheme = this.paint.get();
      int customArmor = this.armorColor.get().getPacked() & 0xFFFFFF;
      int customAccent = this.accentColor.get().getPacked() & 0xFFFFFF;
      int customGlow = this.glowColor.get().getPacked() & 0xFFFFFF;
      Vec3 me = this.mc.player.position();
      for (AbstractClientPlayer p : level.players()) {
         if (p.position().distanceToSqr(me) > 192 * 192) continue;
         boolean eligible = this.wantsMech(p);
         MechAnim anim = this.anims.get(p.getId());
         if (!eligible) {
            if (anim != null) this.anims.remove(p.getId());
            continue;
         }
         if (anim == null) {
            anim = new MechAnim(p.getId());
            this.anims.put(p.getId(), anim);
         }
         anim.eligible = true;
         anim.palette.set(scheme, customArmor, customAccent, customGlow);
         anim.tick(p, this.boosted.contains(p.getId()), this);
         this.effects(level, p, anim);
         anim.clearEvents();
      }
      // forget players that left, died (new id on respawn) or went out of range
      Iterator<Int2ObjectOpenHashMap.Entry<MechAnim>> it = this.anims.int2ObjectEntrySet().fastIterator();
      while (it.hasNext()) {
         var e = it.next();
         Entity ent = level.getEntity(e.getIntKey());
         if (!(ent instanceof AbstractClientPlayer) || ent.isRemoved()) {
            it.remove();
         }
      }
      this.engines.int2ObjectEntrySet().removeIf(e -> e.getValue().isStopped() || !this.anims.containsKey(e.getIntKey()));
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.anims.clear();
      this.engines.clear();
   }

   // ================================================================================================ sounds + particles

   private void effects(ClientLevel level, AbstractClientPlayer p, MechAnim anim) {
      boolean snd = this.sounds.get();
      float vol = this.volume.get().floatValue();
      double x = p.getX(), y = p.getY(), z = p.getZ();
      if (snd) {
         if (anim.evTransformStart && this.transformation.get()) MechSounds.play(MechSounds.TRANSFORM, x, y + 1, z, 0.9F * vol, 1.0F);
         if (anim.evTransformBack && this.transformation.get() && this.reverse.get()) MechSounds.play(MechSounds.TRANSFORM_BACK, x, y + 1, z, 0.9F * vol, 1.0F);
         if (anim.evIgnite) MechSounds.play(MechSounds.IGNITE, x, y, z, 1.0F * vol, 1.0F);
         if (anim.evAfterburner) MechSounds.play(MechSounds.AFTERBURNER, x, y, z, 1.0F * vol, 0.95F + p.getRandom().nextFloat() * 0.1F);
         if (anim.evLanding) MechSounds.play(MechSounds.LAND, x, y, z, (0.4F + 0.6F * anim.evLandingStrength) * vol, 1.0F);
         if (anim.evStep) MechSounds.play(MechSounds.STEP, x, y, z, 0.32F * vol, 0.9F + p.getRandom().nextFloat() * 0.2F);
         // the engine loop starts with the jet and stops itself when the jet is gone
         if (anim.transform() > 0.8F && !this.engines.containsKey(anim.entityId)) {
            MechSounds.Engine engine = new MechSounds.Engine(anim, this.engineVolume);
            this.engines.put(anim.entityId, engine);
            this.mc.getSoundManager().play(engine);
         }
      }
      if (!this.particles.get()) return;
      // positions come from the last drawn frame; skip players we have not drawn for a while
      if (System.nanoTime() - anim.lastFrame > 1_000_000_000L) return;
      MechPose pose = anim.pose;
      double ox = anim.renderX, oy = anim.renderY, oz = anim.renderZ;
      float tr = anim.transform();
      // sparks at the joints while the parts move
      if (tr > 0.12F && tr < 0.9F && ++this.sparkClock % 2 == 0) {
         int part = p.getRandom().nextInt(MechModel.COUNT);
         Vector3f at = pose.pos[part];
         level.addParticle(ParticleTypes.ELECTRIC_SPARK, ox + at.x, oy + at.y, oz + at.z, 0, 0.02, 0);
      }
      if (anim.evIgnite) {
         for (int side = 0; side < 2; side++) {
            int nz = side == 0 ? MechModel.NOZZLE_L : MechModel.NOZZLE_R;
            MechRender.partPoint(pose, nz, MechModel.NOZZLE_EXIT, this.v1);
            MechRender.partAxis(pose, nz, 0, -1, 0, this.v2);
            for (int i = 0; i < 6; i++) {
               double sp = 0.15 + i * 0.03;
               level.addParticle(ParticleTypes.CLOUD, ox + this.v1.x, oy + this.v1.y, oz + this.v1.z,
                  this.v2.x * sp + (p.getRandom().nextFloat() - 0.5) * 0.08, this.v2.y * sp + (p.getRandom().nextFloat() - 0.5) * 0.08,
                  this.v2.z * sp + (p.getRandom().nextFloat() - 0.5) * 0.08);
            }
            for (int i = 0; i < 4; i++) {
               double sp = 0.1 + i * 0.05;
               level.addParticle(ParticleTypes.FLAME, ox + this.v1.x, oy + this.v1.y, oz + this.v1.z, this.v2.x * sp, this.v2.y * sp, this.v2.z * sp);
            }
         }
      }
      if (anim.evLanding && anim.evLandingStrength > 0.3F) {
         for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            level.addParticle(ParticleTypes.POOF, x + Math.cos(a) * 0.4, y + 0.05, z + Math.sin(a) * 0.4, Math.cos(a) * 0.08, 0.01, Math.sin(a) * 0.08);
         }
      }
      // exhaust
      float boost = anim.boost();
      boolean heelThrust = tr < 0.4F && (anim.jetTarget() && tr > 0.02F || anim.velY() > 0.15 && !p.onGround() && !p.isFallFlying());
      if (boost > 0.1F || heelThrust || tr > 0.95F && p.tickCount % 4 == 0) {
         for (int side = 0; side < 2; side++) {
            int nz = side == 0 ? MechModel.NOZZLE_L : MechModel.NOZZLE_R;
            MechRender.partPoint(pose, nz, MechModel.NOZZLE_EXIT, this.v1);
            MechRender.partAxis(pose, nz, 0, -1, 0, this.v2);
            double px = ox + this.v1.x, py = oy + this.v1.y, pz = oz + this.v1.z;
            double vx = anim.velX() * 0.6, vy = anim.velY() * 0.6, vz = anim.velZ() * 0.6;
            if (boost > 0.1F) {
               double sp = 0.25;
               level.addParticle(ParticleTypes.FLAME, px, py, pz, vx + this.v2.x * sp, vy + this.v2.y * sp, vz + this.v2.z * sp);
               if (p.tickCount % 3 == side) level.addParticle(ParticleTypes.SMOKE, px + this.v2.x * 0.9, py + this.v2.y * 0.9, pz + this.v2.z * 0.9, vx * 0.5 + this.v2.x * 0.05, vy * 0.5 + this.v2.y * 0.05, vz * 0.5 + this.v2.z * 0.05);
            } else if (heelThrust) {
               level.addParticle(ParticleTypes.SMALL_FLAME, px, py, pz, this.v2.x * 0.08, this.v2.y * 0.08, this.v2.z * 0.08);
            } else if (anim.speed() > 0.3F) {
               level.addParticle(ParticleTypes.WHITE_SMOKE, px + this.v2.x * 0.3, py + this.v2.y * 0.3, pz + this.v2.z * 0.3, vx * 0.4, vy * 0.4, vz * 0.4);
            }
         }
      }
   }

   // ================================================================================================ render hooks

   /** Called after the vanilla player render state is filled: decides whether this player is drawn as a mech. */
   public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
      MechStateHolder holder = (MechStateHolder)state;
      holder.specter$setMech(null);
      JetMech m = instance;
      if (m == null || !m.isActive() || !MechRender.ready()) return;
      if (!(entity instanceof AbstractClientPlayer)) return;
      MechAnim anim = m.anims.get(entity.getId());
      if (anim == null || !anim.eligible) return;
      // let vanilla handle the odd cases: spectators, invisibility, sleeping
      if (state.isSpectator || state.isInvisible || state.hasPose(Pose.SLEEPING)) return;
      anim.frame(state, partialTicks, m.modelScale.get().floatValue(), m.flightAnim.get());
      holder.specter$setMech(anim);
      float s = m.modelScale.get().floatValue() * state.scale;
      float jet = anim.in.transform;
      state.shadowRadius = (0.5F + 0.7F * jet) * s;
      if (state.nameTagAttachment != null) {
         // the robot is a little taller than a player; the jet is lower
         double lift = (0.25 * s) * (1 - jet) + (-0.9 + 0.6 * s) * jet;
         state.nameTagAttachment = state.nameTagAttachment.add(0, lift, 0);
      }
   }

   public static MechAnim mechOf(AvatarRenderState state) {
      return ((MechStateHolder)state).specter$mech();
   }

   /** Bigger culling box for mechs, so the wings do not vanish when the player's own small box is off screen. */
   public static AABB cullBox(Entity entity, AABB box) {
      JetMech m = instance;
      if (m == null || !m.isActive()) return box;
      MechAnim anim = m.anims.get(entity.getId());
      if (anim == null) return box;
      double g = 1.9 * m.modelScale.get();
      return box.inflate(g, g * 0.6, g);
   }
}
