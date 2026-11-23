package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.camera.GhostView;
import com.jfant.specter.core.events.meteor.MouseScrollEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.cosmetic.Fx;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Auto Cliper: a cinematic camera that slowly circles you, for recording clips.
 *
 * <p>The camera orbits your head at a set speed while spiralling gently in and out and up and down, always looking at
 * you. Scroll to move it closer or further (between the min and max distance). It eases in from your own view when
 * turned on. Only the camera moves: you still walk, look, mine and fight exactly as normal (WASD stays relative to your
 * own facing), and the camera never pushes into walls unless you allow it.
 */
public class AutoCliper extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> speed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("rotation-speed").description("Degrees per second around you. Negative goes the other way.").defaultValue(12.0).range(-90.0, 90.0).sliderRange(-45.0, 45.0).build());
   private final Setting<Double> spiral = this.sgGeneral
      .add(new DoubleSetting.Builder().name("spiral").description("How much it drifts in and out while it circles (0 = a perfect circle).").defaultValue(0.35).range(0.0, 1.0).sliderRange(0.0, 1.0).build());
   private final Setting<Double> height = this.sgGeneral
      .add(new DoubleSetting.Builder().name("height").description("Camera height above your eyes, in blocks (up to 256 for a sky view).").defaultValue(1.0).range(-16.0, 256.0).sliderRange(-8.0, 128.0).build());
   private final Setting<Double> bob = this.sgGeneral
      .add(new DoubleSetting.Builder().name("height-wave").description("How much the height rises and falls along the way.").defaultValue(0.6).range(0.0, 32.0).sliderRange(0.0, 16.0).build());
   private final Setting<Double> distance = this.sgGeneral
      .add(new DoubleSetting.Builder().name("distance").description("Starting distance from you in blocks (scroll changes it).").defaultValue(5.0).range(1.0, 256.0).sliderRange(1.0, 128.0).build());
   private final Setting<Double> minDistance = this.sgGeneral
      .add(new DoubleSetting.Builder().name("min-distance").description("Closest scrolling can bring it.").defaultValue(2.0).range(0.5, 64.0).sliderRange(0.5, 32.0).build());
   private final Setting<Double> maxDistance = this.sgGeneral
      .add(new DoubleSetting.Builder().name("max-distance").description("Furthest scrolling can take it.").defaultValue(128.0).range(2.0, 256.0).sliderRange(2.0, 256.0).build());
   private final Setting<Boolean> scroll = this.sgGeneral
      .add(new BoolSetting.Builder().name("scroll-zoom").description("Scroll moves the camera closer/further (instead of changing the hotbar slot).").defaultValue(true).build());
   private final Setting<Boolean> throughWalls = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("through-walls")
            .description("The camera goes straight through blocks and still shows caves and you. Off: it is pulled in front of walls instead.")
            .defaultValue(true)
            .build()
      );

   public double x, y, z;
   public float yaw, pitch;
   private double angle;
   private double wanted;
   private double shown;
   private float blend;
   private long lastFrame;
   private double time;

   public AutoCliper() {
      super(SpecterClientAddon.VISUALS, "auto-cliper", "A cinematic camera that slowly spirals around you for recording clips. Scroll to zoom. Your controls are unchanged.");
   }

   /** The running instance, or null. Freecam wins when both are on. */
   public static AutoCliper on() {
      AutoCliper m = Modules.get().get(AutoCliper.class);
      return m != null && m.isActive() && m.mc.player != null && GhostFreecam.on() == null ? m : null;
   }

   /** Whether the camera may be inside blocks right now (cave culling must be off then; see GhostView). */
   public boolean seesThroughBlocks() {
      return this.throughWalls.get();
   }

   /** The camera view you had before, given back when this turns off (third person hides the floating hand). */
   private net.minecraft.client.CameraType viewBefore;

   @Override
   public void onDeactivate() {
      GhostView.exit();
      if (this.viewBefore != null) this.mc.options.setCameraType(this.viewBefore);
      this.viewBefore = null;
   }

   @Override
   public void onActivate() {
      GhostView.enter();
      this.viewBefore = this.mc.options.getCameraType();
      this.mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
      this.wanted = Mth.clamp(this.distance.get(), this.minDistance.get(), this.maxDistance.get());
      this.shown = this.wanted;
      this.blend = 0.0F;
      this.lastFrame = 0L;
      this.time = 0.0;
      // start behind the player so the move out of first person reads naturally
      this.angle = this.mc.player != null ? this.mc.player.getYRot() + 90.0 : 0.0;
   }

   @EventHandler
   private void onScroll(MouseScrollEvent event) {
      if (!this.scroll.get() || this.mc.gui.screen() != null || on() == null) return;
      this.wanted = Mth.clamp(this.wanted * (event.value > 0 ? 0.88 : 1.14), this.minDistance.get(), this.maxDistance.get());
      event.cancel();
   }

   /** Works out this frame's camera, called by the camera before it is placed. */
   public void stepFrame(float partialTicks) {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      this.time += dt;
      this.angle += this.speed.get() * dt;
      this.blend = Math.min(1.0F, this.blend + dt / 0.9F);
      this.shown = Fx.approach((float)this.shown, (float)this.wanted, 5.0F, dt);

      Vec3 eye = this.mc.player.getEyePosition(partialTicks);
      double sp = this.spiral.get();
      double r = this.shown * (1.0 + sp * 0.35 * Math.sin(this.time * 0.37));
      double h = this.height.get() + this.bob.get() * Math.sin(this.time * 0.23);
      double a = Math.toRadians(this.angle);
      double tx = eye.x + Math.cos(a) * r, ty = eye.y + h, tz = eye.z + Math.sin(a) * r;
      if (!this.throughWalls.get()) {
         BlockHitResult hit = this.mc.level.clip(new ClipContext(eye, new Vec3(tx, ty, tz), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.mc.player));
         if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 at = hit.getLocation();
            Vec3 back = eye.subtract(at).normalize().scale(0.3);
            tx = at.x + back.x;
            ty = at.y + back.y;
            tz = at.z + back.z;
         }
      }
      float e = Fx.smooth(this.blend);
      this.x = Mth.lerp(e, eye.x, tx);
      this.y = Mth.lerp(e, eye.y, ty);
      this.z = Mth.lerp(e, eye.z, tz);
      double dx = eye.x - this.x, dy = eye.y - this.y, dz = eye.z - this.z;
      double flat = Math.sqrt(dx * dx + dz * dz);
      float lookYaw = flat < 1.0E-4 ? this.mc.player.getViewYRot(partialTicks) : (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float lookPitch = flat < 1.0E-4 && Math.abs(dy) < 1.0E-4 ? this.mc.player.getViewXRot(partialTicks) : (float)-Math.toDegrees(Math.atan2(dy, flat));
      float ownYaw = this.mc.player.getViewYRot(partialTicks), ownPitch = this.mc.player.getViewXRot(partialTicks);
      this.yaw = ownYaw + Mth.wrapDegrees(lookYaw - ownYaw) * e;
      this.pitch = Mth.lerp(e, ownPitch, lookPitch);
   }
}
