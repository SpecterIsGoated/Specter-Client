package com.jfant.specter.mech;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Per-player state of one mech: the transformation state machine, the smoothed flight attitude and everything the
 * robot animation needs, ticked at 20 Hz (so it keeps running while the player is off screen) and interpolated per
 * frame. One instance per visible player, reused; drawing never allocates.
 *
 * <p>Everything here is derived from data the server already sends to every client (elytra flight flag, position,
 * attached firework rockets, pose), which is why another Specter user sees the same transformation at the same moment.
 */
public final class MechAnim {
   /** Seconds for a full transformation at animation speed 1. */
   public static final float BASE_DURATION = 1.35F;
   /** Ticks the elytra flag has to stay off before the mech starts changing back (rides out server flicker). */
   private static final int RELEASE_TICKS = 3;

   public final int entityId;
   public final MechInputs in = new MechInputs();
   public final MechPose pose = new MechPose();
   public final MechPalette palette = new MechPalette();

   // ---- tick state (value + value last tick, interpolated by the partial tick)
   private float t, tPrev;
   private boolean target;
   private int releaseTicks;
   private float heading, headingPrev, pitch, pitchPrev, roll, rollPrev;
   private float yawRate;
   private float ail, ailPrev, ele, elePrev, rud, rudPrev;
   private float gear = 1, gearPrev = 1;
   private float jetY = 0.47F, jetYPrev = 0.47F;
   private float boost, boostPrev;
   private int boostHold;
   private float air, airPrev, landing, landingPrev, sprint, sprintPrev, crouch, crouchPrev;
   private float turnRate, turnPhase, turnStep, turnStepPrev, lastBodyYaw;
   private boolean wasOnGround = true;
   private double lastVy;
   private double px, py, pz;
   private boolean hasPos;
   private double vx, vy, vz;
   private float flash, flashPrev;
   private float stepPhaseLast;
   int ticksSinceSeen;
   /** Set by the module: the player counts as a mech this tick. */
   public boolean eligible;
   /** Last frame this mech was drawn (System.nanoTime). */
   public long lastFrame;

   // ---- frame state, read by the render passes
   public double renderX, renderY, renderZ;
   public double camX, camY, camZ;
   public int light = 0xF000F0;
   public int overlay;
   public float time;
   public float visorPulse = 1;
   public int nozzleColor = 0x401808;
   public float flameLength, flamePower, afterburner;
   public final Trail trailL = new Trail(), trailR = new Trail();
   private boolean trailsOn;

   public final SubmitNodeCollector.CustomGeometryRenderer solidPass = (p, b) -> MechRender.emitSolid(this, p, b);
   public final SubmitNodeCollector.CustomGeometryRenderer glowPass = (p, b) -> MechRender.emitGlow(this, p, b);
   public final SubmitNodeCollector.CustomGeometryRenderer flamePass = (p, b) -> MechRender.emitFlames(this, p, b);
   public final SubmitNodeCollector.CustomGeometryRenderer trailPass = (p, b) -> MechRender.emitTrails(this, p, b);

   // events for the module (sounds / particles), consumed every tick
   public boolean evTransformStart, evTransformBack, evIgnite, evAfterburner, evLanding, evStep;
   public float evLandingStrength;

   private final Vector3f tmp = new Vector3f();

   public MechAnim(int entityId) {
      this.entityId = entityId;
   }

   /** Settings the animation needs, read from the module every tick. */
   public interface Config {
      boolean transformAnimation();

      boolean reverseAnimation();

      float animationSpeed();

      boolean flightAnimation();

      boolean afterburners();

      boolean landingGear();

      boolean trails();
   }

   public float transform() {
      return this.t;
   }

   public boolean jetTarget() {
      return this.target;
   }

   public float boost() {
      return this.boost;
   }

   public float speed() {
      return (float)Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
   }

   public double velX() {
      return this.vx;
   }

   public double velY() {
      return this.vy;
   }

   public double velZ() {
      return this.vz;
   }

   public boolean hasFlames() {
      return this.flameLength > 0.02F && this.flamePower > 0.02F;
   }

   public boolean hasTrails() {
      return this.trailsOn && (this.trailL.count > 1 || this.afterburner > 0.05F);
   }

   // ================================================================================================ tick

   public void tick(Player p, boolean boosting, Config cfg) {
      this.ticksSinceSeen = 0;
      boolean flying = p.isFallFlying();
      // --- state machine
      if (flying) this.releaseTicks = 0;
      else this.releaseTicks++;
      boolean newTarget = flying || this.target && this.releaseTicks < RELEASE_TICKS;
      if (newTarget != this.target) {
         this.target = newTarget;
         if (newTarget) this.evTransformStart = true;
         else this.evTransformBack = true;
      }
      this.tPrev = this.t;
      float step = 0.05F * cfg.animationSpeed() / BASE_DURATION;
      if (this.target) {
         this.t = cfg.transformAnimation() ? Math.min(1, this.t + step) : 1;
      } else {
         this.t = cfg.reverseAnimation() && cfg.transformAnimation() ? Math.max(0, this.t - step) : 0;
      }
      if (this.target && this.tPrev < 0.9F && this.t >= 0.9F) {
         this.flash = 1;
         this.evIgnite = true;
      }
      this.flashPrev = this.flash;
      this.flash = Math.max(0, this.flash - 0.05F / 0.5F);

      // --- motion
      Vec3 pos = p.position();
      double dx = 0, dy = 0, dz = 0;
      if (this.hasPos) {
         dx = pos.x - this.px;
         dy = pos.y - this.py;
         dz = pos.z - this.pz;
         if (dx * dx + dy * dy + dz * dz > 100) dx = dy = dz = 0; // teleport
      }
      this.px = pos.x;
      this.py = pos.y;
      this.pz = pos.z;
      this.hasPos = true;
      this.vx += (dx - this.vx) * 0.55;
      this.vy += (dy - this.vy) * 0.55;
      this.vz += (dz - this.vz) * 0.55;
      double hor = Math.sqrt(this.vx * this.vx + this.vz * this.vz);
      double speed = Math.sqrt(hor * hor + this.vy * this.vy);

      // --- aircraft attitude: point where it actually flies
      float hdgTarget, pitchTarget;
      if (flying && speed > 0.08) {
         hdgTarget = (float)Math.toDegrees(Math.atan2(-this.vx, this.vz));
         float path = (float)-Math.toDegrees(Math.atan2(this.vy, hor));
         pitchTarget = Mth.lerp(0.7F, p.getXRot(), path);
      } else if (flying) {
         hdgTarget = p.getYRot();
         pitchTarget = p.getXRot();
      } else {
         hdgTarget = p.yBodyRot;
         pitchTarget = 0;
      }
      if (this.tPrev <= 0.0001F) {
         // starting from the robot: begin from where the robot faces
         this.heading = this.headingPrev = p.yBodyRot;
         this.pitch = this.pitchPrev = 0;
         this.roll = this.rollPrev = 0;
      }
      this.headingPrev = this.heading;
      this.heading = this.heading + Mth.wrapDegrees(hdgTarget - this.heading) * 0.45F;
      this.pitchPrev = this.pitch;
      this.pitch = this.pitch + (Mth.clamp(pitchTarget, -85, 85) - this.pitch) * 0.35F;
      float yawStep = Mth.wrapDegrees(this.heading - this.headingPrev) * 20.0F;
      this.yawRate += (yawStep - this.yawRate) * 0.3F;
      float turbulence = flying ? (float)(Math.sin(p.tickCount * 0.21) * 1.4 + Math.sin(p.tickCount * 0.077 + 1.3) * 1.1) : 0;
      float rollTarget = flying ? Mth.clamp(this.yawRate * 0.55F, -75, 75) + turbulence : 0;
      this.rollPrev = this.roll;
      this.roll = this.roll + (rollTarget - this.roll) * 0.22F;
      float pitchRate = (this.pitch - this.pitchPrev) * 20.0F;
      this.ailPrev = this.ail;
      this.elePrev = this.ele;
      this.rudPrev = this.rud;
      this.ail += (Mth.clamp((rollTarget - this.roll) / 18.0F, -1, 1) - this.ail) * 0.4F;
      this.ele += (Mth.clamp(-pitchRate / 50.0F, -1, 1) - this.ele) * 0.4F;
      this.rud += (Mth.clamp(this.yawRate / 160.0F, -1, 1) - this.rud) * 0.4F;
      this.gearPrev = this.gear;
      float gearTarget = cfg.landingGear() && !flying ? 1 : 0;
      this.gear += Mth.clamp(gearTarget - this.gear, -0.05F / 0.6F, 0.05F / 0.6F);
      this.jetYPrev = this.jetY;
      float jetYTarget = flying ? 0.32F : cfg.landingGear() ? 0.47F : 0.30F;
      this.jetY += (jetYTarget - this.jetY) * 0.25F;

      // --- afterburner
      if (boosting && cfg.afterburners()) this.boostHold = 3;
      this.boostPrev = this.boost;
      if (this.boostHold > 0) {
         this.boostHold--;
         if (this.boost < 0.05F && this.t > 0.75F) this.evAfterburner = true;
         this.boost = Math.min(1, this.boost + 0.05F / 0.12F);
      } else {
         this.boost = Math.max(0, this.boost - 0.05F / 0.4F);
      }

      // --- robot motion
      boolean onGround = p.onGround();
      float airTarget = !onGround && !flying && !p.isPassenger() && !p.isInWater() && !p.getAbilities().flying ? 1 : 0;
      if (p.getAbilities().flying && !onGround) airTarget = 0.6F;
      this.airPrev = this.air;
      this.air += Mth.clamp(airTarget - this.air, -0.05F / 0.12F, 0.05F / 0.15F);
      this.landingPrev = this.landing;
      if (onGround && !this.wasOnGround && this.lastVy < -0.28 && !flying) {
         this.landing = (float)Math.min(1.0, (-this.lastVy - 0.2) * 2.2);
         this.evLanding = true;
         this.evLandingStrength = this.landing;
      } else {
         this.landing = Math.max(0, this.landing - 0.05F / 0.42F);
      }
      this.wasOnGround = onGround;
      this.lastVy = dy;
      this.sprintPrev = this.sprint;
      this.sprint += Mth.clamp((p.isSprinting() ? 1 : 0) - this.sprint, -0.25F, 0.25F);
      this.crouchPrev = this.crouch;
      this.crouch += Mth.clamp((p.isCrouching() ? 1 : 0) - this.crouch, -0.34F, 0.34F);
      float bodyYaw = p.yBodyRot;
      float dYaw = Mth.wrapDegrees(bodyYaw - this.lastBodyYaw);
      this.lastBodyYaw = bodyYaw;
      this.turnRate += (dYaw * 20.0F - this.turnRate) * 0.4F;
      boolean walking = p.walkAnimation.speed() > 0.15F;
      float turnTarget = 0;
      if (Math.abs(this.turnRate) > 25.0F && !walking && onGround) {
         this.turnPhase += Math.abs(dYaw) * 0.12F;
         turnTarget = 1;
      }
      this.turnStepPrev = this.turnStep;
      this.turnStep += Mth.clamp(turnTarget - this.turnStep, -0.25F, 0.25F);
      // footsteps on the robot's own stride
      if (this.t < 0.15F && onGround && p.walkAnimation.speed() > 0.25F) {
         float phase = p.walkAnimation.position() * 0.6662F;
         if (Math.floor(phase / Math.PI) != Math.floor(this.stepPhaseLast / Math.PI)) this.evStep = true;
         this.stepPhaseLast = phase;
      }

      // --- vapour trails from the wing tips (world positions, one point per tick)
      this.trailsOn = cfg.trails();
      boolean makeTrail = cfg.trails() && this.t > 0.95F && flying && (speed > 1.0 || Math.abs(this.yawRate) > 90 && speed > 0.6 || this.boost > 0.5F);
      float trailAlpha = makeTrail ? Mth.clamp((float)(speed - 0.6) * 1.2F + this.boost * 0.4F, 0.15F, 1) : 0;
      if (this.lastFrame != 0) {
         this.trailL.push(this.renderX + this.tipL.x, this.renderY + this.tipL.y, this.renderZ + this.tipL.z, trailAlpha);
         this.trailR.push(this.renderX + this.tipR.x, this.renderY + this.tipR.y, this.renderZ + this.tipR.z, trailAlpha);
      }
   }

   private final Vector3f tipL = new Vector3f(), tipR = new Vector3f();
   private final Vector3f tipMirror = new Vector3f(-MechModel.WING_TIP.x, MechModel.WING_TIP.y, MechModel.WING_TIP.z);

   // ================================================================================================ frame

   /** Fills the inputs from the render state and computes the pose for this frame. */
   public void frame(AvatarRenderState state, float partial, float modelScale, boolean flightAnim) {
      MechInputs in = this.in;
      in.transform = Mth.lerp(partial, this.tPrev, this.t);
      in.forward = this.target;
      in.scale = modelScale * state.scale;
      in.time = state.ageInTicks / 20.0F;
      in.bodyYaw = state.bodyRot;
      in.headYaw = state.yRot;
      in.headPitch = state.xRot;
      in.walkPos = state.walkAnimationPos;
      in.walkSpeed = state.walkAnimationSpeed;
      in.sprint = Mth.lerp(partial, this.sprintPrev, this.sprint);
      in.crouch = Mth.lerp(partial, this.crouchPrev, this.crouch);
      in.sit = state.isPassenger ? 1 : 0;
      in.swim = state.isFallFlying ? 0 : state.swimAmount;
      in.air = Mth.lerp(partial, this.airPrev, this.air);
      in.vy = (float)this.vy;
      in.landing = Mth.lerp(partial, this.landingPrev, this.landing);
      in.swing = state.swingAnimation;
      in.rightHanded = state.mainArm == HumanoidArm.RIGHT;
      in.holdingRight = !state.rightHandItemState.isEmpty();
      in.holdingLeft = !state.leftHandItemState.isEmpty();
      in.aiming = aims(state.rightArmPose) || aims(state.leftArmPose);
      in.blocking = state.rightArmPose == HumanoidModel.ArmPose.BLOCK || state.leftArmPose == HumanoidModel.ArmPose.BLOCK;
      in.turnRate = this.turnRate;
      in.turnPhase = this.turnPhase;
      in.turnStep = Mth.lerp(partial, this.turnStepPrev, this.turnStep);
      in.death = state.deathTime > 0 ? Math.min(1, Mth.sqrt(state.deathTime / 20.0F * 1.6F)) : 0;
      in.heading = this.headingPrev + Mth.wrapDegrees(this.heading - this.headingPrev) * partial;
      in.pitch = Mth.lerp(partial, this.pitchPrev, this.pitch);
      in.roll = Mth.lerp(partial, this.rollPrev, this.roll);
      in.jetY = Mth.lerp(partial, this.jetYPrev, this.jetY);
      in.aileron = Mth.lerp(partial, this.ailPrev, this.ail);
      in.elevator = Mth.lerp(partial, this.elePrev, this.ele);
      in.rudder = Mth.lerp(partial, this.rudPrev, this.rud);
      in.gear = Mth.lerp(partial, this.gearPrev, this.gear);
      in.boost = Mth.lerp(partial, this.boostPrev, this.boost);
      in.flightAnim = flightAnim;
      this.pose.compute(in);

      this.renderX = state.x;
      this.renderY = state.y;
      this.renderZ = state.z;
      this.light = state.lightCoords;
      this.overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
      this.time = in.time;
      this.lastFrame = System.nanoTime();
      MechRender.partPoint(this.pose, MechModel.WING_L, MechModel.WING_TIP, this.tipL);
      MechRender.partPoint(this.pose, MechModel.WING_R, this.tipMirror, this.tipR);
      this.updateEngine(partial);
   }

   private static boolean aims(HumanoidModel.ArmPose pose) {
      return pose == HumanoidModel.ArmPose.BOW_AND_ARROW || pose == HumanoidModel.ArmPose.CROSSBOW_HOLD || pose == HumanoidModel.ArmPose.CROSSBOW_CHARGE;
   }

   /**
    * Engine look for this frame: off in robot mode (except the heel thrusters flickering when the transformation starts
    * and when the robot jumps), a blue cruise flame in jet mode, the ignition burst when the aircraft locks together,
    * and the long orange afterburner while a rocket is boosting.
    */
   private void updateEngine(float partial) {
      float tr = this.in.transform;
      float jet = MechPose.smooth((tr - 0.82F) / 0.18F);
      float flashNow = Mth.lerp(partial, this.flashPrev, this.flash);
      float boostNow = this.in.boost;
      // heel thrusters: activation flicker (going up) and robot jump thrust
      float activation = this.target && tr > 0.02F && tr < 0.4F ? (float)Math.sin(Math.PI * (tr - 0.02F) / 0.38F) : 0;
      float jump = (1 - jet) * Math.max(0, Math.min(1, this.in.vy * 5.0F)) * this.in.air;
      float heel = Math.max(activation * (0.6F + 0.4F * (float)Math.sin(this.time * 45.0F)), jump);
      float cruise = jet * 0.55F;
      this.flamePower = Math.min(1, Math.max(heel * 0.8F, cruise + flashNow * 0.7F + boostNow * 0.6F));
      this.flameLength = Math.max(heel * 0.35F, jet * (0.32F + flashNow * 0.9F + boostNow * 1.5F));
      this.afterburner = Math.max(boostNow, flashNow * 0.8F);
      // nozzle interior glow: dull when cold, hot orange when running, white-hot on afterburner
      float heat = Math.max(heel, Math.max(cruise * 1.2F, this.afterburner));
      int r = (int)Mth.lerp(heat, 60, 255), g = (int)Mth.lerp(heat, 24, 150 + 70 * this.afterburner), b = (int)Mth.lerp(heat, 12, 60 + 150 * this.afterburner);
      this.nozzleColor = r << 16 | g << 8 | b;
      // visor: soft pulse, flickers during the transformation, dims as the head folds away
      float pulse = 0.85F + 0.15F * (float)Math.sin(this.time * 2.2F);
      float flicker = tr > 0.05F && tr < 0.35F ? 0.6F + 0.4F * (float)Math.abs(Math.sin(this.time * 31.0F)) : 1;
      this.visorPulse = pulse * flicker;
   }

   /** Clears the transient event flags (call after the module has played the sounds / particles). */
   public void clearEvents() {
      this.evTransformStart = this.evTransformBack = this.evIgnite = this.evAfterburner = this.evLanding = this.evStep = false;
   }

   /** Ring buffer of trail points (world space). */
   public static final class Trail {
      static final int SIZE = 28;
      final double[] x = new double[SIZE], y = new double[SIZE], z = new double[SIZE];
      final float[] a = new float[SIZE];
      int head;
      int count;

      void push(double px, double py, double pz, float alpha) {
         if (alpha <= 0.01F && this.count > 0) {
            // let the existing trail fade out instead of adding invisible points
            this.count--;
            return;
         }
         if (alpha <= 0.01F) return;
         this.head = (this.head + 1) % SIZE;
         this.x[this.head] = px;
         this.y[this.head] = py;
         this.z[this.head] = pz;
         this.a[this.head] = alpha;
         if (this.count < SIZE) this.count++;
      }

      /** k = 0 is the newest point. */
      int index(int k) {
         return ((this.head - k) % SIZE + SIZE) % SIZE;
      }
   }
}
