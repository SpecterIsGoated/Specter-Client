package com.jfant.specter.mech;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns {@link MechInputs} into a transform for every part: position, rotation and scale in entity space (blocks,
 * origin at the entity's feet, world axes - yaw is already applied).
 *
 * <p>Both rest poses are animated first (the robot walks, breathes and looks around; the aircraft moves its control
 * surfaces and gear), then each part is blended between the two inside its own time window. Children that share a
 * parent in both poses blend in the parent's frame, so limbs never come apart; the rest blend in entity space along an
 * arc. Pure math (JOML only): one instance per player, reused, no allocation per frame.
 */
public final class MechPose {
   private static final int N = MechModel.COUNT;
   private final MechModel model = MechModel.get();
   private final int[] order = this.model.order();

   /** Results, entity space. */
   public final Vector3f[] pos = vectors();
   public final Quaternionf[] rot = quats();
   public final float[] scale = new float[N];
   /** Draw-only size of each part's own geometry (tucking away in jet mode) and of the F-22 skin it carries. */
   public final float[] coreScale = new float[N];
   public final float[] shellScale = new float[N];

   // per-part animated local transforms
   private final Vector3f[] robotLocalPos = vectors();
   private final Quaternionf[] robotLocalRot = quats();
   private final float[] robotLocalScale = new float[N];
   private final Vector3f[] jetLocalPos = vectors();
   private final Quaternionf[] jetLocalRot = quats();
   private final float[] jetLocalScale = new float[N];
   // absolute (entity space) for each pose
   private final Vector3f[] robotPos = vectors();
   private final Quaternionf[] robotRot = quats();
   private final float[] robotScale = new float[N];
   private final Vector3f[] jetPos = vectors();
   private final Quaternionf[] jetRot = quats();
   private final float[] jetScale = new float[N];

   private final Quaternionf robotRootRot = new Quaternionf();
   private final Vector3f robotRootPos = new Vector3f();
   private final Quaternionf jetRootRot = new Quaternionf();
   private final Vector3f jetRootPos = new Vector3f();
   private final Quaternionf tq = new Quaternionf();
   private final Quaternionf tq2 = new Quaternionf();
   private final Vector3f tv = new Vector3f();
   private final Vector3f tv2 = new Vector3f();
   /** Blend weight of each part this frame (0 robot .. 1 aircraft). */
   public final float[] weight = new float[N];
   /** How far the robot has leant into the flight attitude (first stage). */
   public float lean;

   private static Vector3f[] vectors() {
      Vector3f[] a = new Vector3f[N];
      for (int i = 0; i < N; i++) a[i] = new Vector3f();
      return a;
   }

   private static Quaternionf[] quats() {
      Quaternionf[] a = new Quaternionf[N];
      for (int i = 0; i < N; i++) a[i] = new Quaternionf();
      return a;
   }

   // ================================================================================================ easing

   static float clamp01(float x) {
      return x < 0 ? 0 : x > 1 ? 1 : x;
   }

   static float smooth(float x) {
      x = clamp01(x);
      return x * x * (3 - 2 * x);
   }

   static float easeInOutCubic(float x) {
      x = clamp01(x);
      return x < 0.5F ? 4 * x * x * x : 1 - (float)Math.pow(-2 * x + 2, 3) / 2;
   }

   /** Ease with a small overshoot before settling (a mechanical "clunk" into place). */
   static float lockEase(float x) {
      x = clamp01(x);
      float base = easeInOutCubic(x);
      return base + 0.10F * (float)Math.sin(Math.PI * x) * x * x * x;
   }

   static float rad(float deg) {
      return deg * 0.017453292F;
   }

   /** Weight of a part at transformation progress t. */
   static float partWeight(MechModel.Part p, float t) {
      float x = (t - p.start) / (p.end - p.start);
      return p.lock ? lockEase(x) : easeInOutCubic(x);
   }

   // ================================================================================================ compute

   public void compute(MechInputs in) {
      float t = clamp01(in.transform);
      this.lean = smooth(t / 0.25F);
      // 1) roots
      this.robotRoot(in);
      this.jetRoot(in);
      // 2) animated local poses
      for (int i = 0; i < N; i++) {
         MechModel.Part p = this.model.parts[i];
         this.robotLocalPos[i].set(p.robotPos);
         this.robotLocalRot[i].set(p.robotRot);
         this.robotLocalScale[i] = p.robotScale;
         this.jetLocalPos[i].set(p.jetPos);
         this.jetLocalRot[i].set(p.jetRot);
         this.jetLocalScale[i] = p.jetScale;
      }
      if (t < 0.999F) this.animateRobot(in, 1.0F - this.lean);
      if (t > 0.001F) this.animateJet(in);
      // 3) chain each pose to entity space
      for (int i : this.order) {
         MechModel.Part p = this.model.parts[i];
         chain(p.robotParent, this.robotRootPos, this.robotRootRot, 1.0F, this.robotPos, this.robotRot, this.robotScale,
            this.robotLocalPos[i], this.robotLocalRot[i], this.robotLocalScale[i], i);
         chain(p.jetParent, this.jetRootPos, this.jetRootRot, 1.0F, this.jetPos, this.jetRot, this.jetScale,
            this.jetLocalPos[i], this.jetLocalRot[i], this.jetLocalScale[i], i);
      }
      // 4) blend part by part
      for (int i : this.order) {
         MechModel.Part p = this.model.parts[i];
         float w = t <= 0 ? 0 : t >= 1 ? 1 : partWeight(p, t);
         this.weight[i] = w;
         // the robot's own armour folds into the airframe while the F-22 skin unfolds out of it
         this.coreScale[i] = p.coreHide ? 1.0F - 0.72F * smooth((w - 0.40F) / 0.60F) : 1.0F;
         this.shellScale[i] = p.shell == null ? 0.0F : smooth((w - 0.05F) / 0.80F);
         float arcAmt = (float)Math.sin(Math.PI * clamp01(w));
         if (p.sharedParent()) {
            // blend the local transform, then hang it off the (already blended) parent
            Vector3f lp = this.tv.set(this.robotLocalPos[i]).lerp(this.jetLocalPos[i], w);
            if (arcAmt > 0) lp.fma(arcAmt, p.arc);
            Quaternionf lr = this.tq.set(this.robotLocalRot[i]).slerp(this.jetLocalRot[i], w);
            float ls = lerp(this.robotLocalScale[i], this.jetLocalScale[i], w);
            int par = p.robotParent;
            this.rot[par].transform(lp.mul(this.scale[par]), this.pos[i]).add(this.pos[par]);
            this.rot[i].set(this.rot[par]).mul(lr);
            this.scale[i] = this.scale[par] * ls;
         } else {
            this.pos[i].set(this.robotPos[i]).lerp(this.jetPos[i], w);
            if (arcAmt > 0) {
               this.jetRootRot.transform(this.tv.set(p.arc), this.tv).mul(arcAmt);
               this.pos[i].add(this.tv);
            }
            this.rot[i].set(this.robotRot[i]).slerp(this.jetRot[i], w);
            this.scale[i] = lerp(this.robotScale[i], this.jetScale[i], w);
         }
      }
      // 5) overall size about the feet
      float s = in.scale;
      for (int i = 0; i < N; i++) {
         this.pos[i].mul(s);
         this.scale[i] *= s;
      }
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private void chain(int parent, Vector3f rootPos, Quaternionf rootRot, float rootScale, Vector3f[] outPos, Quaternionf[] outRot,
                      float[] outScale, Vector3f localPos, Quaternionf localRot, float localScale, int i) {
      if (parent < 0) {
         rootRot.transform(this.tv2.set(localPos).mul(rootScale), outPos[i]).add(rootPos);
         outRot[i].set(rootRot).mul(localRot);
         outScale[i] = rootScale * localScale;
      } else {
         outRot[parent].transform(this.tv2.set(localPos).mul(outScale[parent]), outPos[i]).add(outPos[parent]);
         outRot[i].set(outRot[parent]).mul(localRot);
         outScale[i] = outScale[parent] * localScale;
      }
   }

   // ================================================================================================ roots

   /**
    * Robot root: upright and turned to the body yaw, then (first stage of the transformation) leaning forward about
    * the hips into the "flying" attitude, ending face down along the aircraft's axis.
    */
   private void robotRoot(MechInputs in) {
      // upright: feet at the origin
      Quaternionf upright = this.tq.identity().rotateY(rad(-in.bodyYaw));
      // swimming / death roll applied about the hips
      if (in.swim > 0.001F) upright.rotateX(rad(in.swim * (90.0F + in.headPitch)));
      if (in.death > 0.001F) upright.rotateZ(rad(in.death * 90.0F));
      Vector3f hipUp = upright.transform(this.tv.set(0, MechModel.LIE_PIVOT_Y, 0));
      if (in.swim > 0.001F) hipUp.y -= in.swim * 0.75F;
      if (in.death > 0.001F) hipUp.y -= in.death * 0.7F;
      // lying: the jet root rotation, then the lie-down rotation
      Quaternionf lying = this.tq2.set(this.jetRootRotFor(in)).rotateX(rad(90.0F));
      Vector3f hipLie = this.tv2.set(this.jetRootPosFor(in));
      float l = this.lean;
      this.robotRootRot.set(upright).slerp(lying, l);
      Vector3f hip = hipUp.lerp(hipLie, l);
      // root position = hip position minus the rotated hip offset
      this.robotRootRot.transform(this.tv2.set(0, MechModel.LIE_PIVOT_Y, 0));
      this.robotRootPos.set(hip).sub(this.tv2);
   }

   private final Quaternionf jetRotScratch = new Quaternionf();
   private final Vector3f jetPosScratch = new Vector3f();

   private Quaternionf jetRootRotFor(MechInputs in) {
      return this.jetRotScratch.identity().rotateY(rad(-in.heading)).rotateX(rad(in.pitch)).rotateZ(rad(in.roll));
   }

   private Vector3f jetRootPosFor(MechInputs in) {
      return this.jetPosScratch.set(0, in.jetY, 0);
   }

   private void jetRoot(MechInputs in) {
      this.jetRootRot.set(this.jetRootRotFor(in));
      this.jetRootPos.set(this.jetRootPosFor(in));
   }

   // ================================================================================================ robot motion

   /** Applies a rotation (degrees, local X then Y then Z) on top of a part's robot rest rotation. */
   private void rr(int part, float x, float y, float z) {
      if (x == 0 && y == 0 && z == 0) return;
      this.robotLocalRot[part].rotateX(rad(x)).rotateY(rad(y)).rotateZ(rad(z));
   }

   private void rp(int part, float x, float y, float z) {
      this.robotLocalPos[part].add(x, y, z);
   }

   private void animateRobot(MechInputs in, float k) {
      float time = in.time;
      float walk = Math.min(1.0F, in.walkSpeed) * k * (1 - in.sit) * (1 - in.swim);
      float run = in.sprint * walk;
      float phase = in.walkPos * 0.6662F;
      float c = (float)Math.cos(phase), s = (float)Math.sin(phase);

      // --- idle: breathing, a slow scan of the visor, servo twitches now and then
      float breath = (float)Math.sin(time * 1.6F);
      float twitch = twitch(time);
      int twitchKind = (int)(Math.floor(time / 5.0F) * 7.0F) & 3;

      // --- legs
      // A heavy machine: the swinging leg folds at the knee and reaches forward, the planted leg stays straight and
      // pushes back (the knee used to bend on the planted leg, which read as walking backwards). Each heel strike
      // drops the hips and jolts the upper body; the weight rolls over onto the planted foot.
      float runShare = walk > 0 ? run / walk : 0;
      float swingAmp = 30.0F + 14.0F * runShare;
      float thighL = c * swingAmp * walk;
      float thighR = -c * swingAmp * walk;
      float bend = 42.0F + 34.0F * runShare;
      // the left thigh swings forward while sin(phase) > 0, the right one while it is < 0
      float liftL = Math.max(0, s), liftR = Math.max(0, -s);
      float kneeL = (bend * liftL * liftL * (3 - 2 * liftL) + 5.0F) * walk;
      float kneeR = (bend * liftR * liftR * (3 - 2 * liftR) + 5.0F) * walk;
      // a slight kick-out just before the heel comes down (straightening, then the strike)
      thighL -= 4.0F * walk * Math.max(0, s) * Math.max(0, -c);
      thighR -= 4.0F * walk * Math.max(0, -s) * Math.max(0, c);
      // turning in place: small lifting steps
      if (in.turnStep > 0.001F) {
         float ts = (float)Math.sin(in.turnPhase);
         kneeL += 26.0F * in.turnStep * Math.max(0, ts);
         kneeR += 26.0F * in.turnStep * Math.max(0, -ts);
         thighL -= 12.0F * in.turnStep * Math.max(0, ts);
         thighR -= 12.0F * in.turnStep * Math.max(0, -ts);
      }
      // airborne: tuck; landing: squat; crouch; sit
      float air = in.air * k, land = in.landing * k, crouch = in.crouch * k, sit = in.sit * k;
      float rising = Math.max(0, Math.min(1, in.vy * 4.0F)) * air;
      thighL += -22.0F * air + 6.0F * rising - 34.0F * land - 30.0F * crouch - 82.0F * sit;
      thighR += -18.0F * air + 2.0F * rising - 34.0F * land - 30.0F * crouch - 82.0F * sit;
      kneeL += 38.0F * air + 62.0F * land + 56.0F * crouch + 80.0F * sit;
      kneeR += 30.0F * air + 62.0F * land + 56.0F * crouch + 80.0F * sit;
      // heel strikes at phase 0 (right) and pi (left); the load arrives a moment later and is soaked up by the hips
      float impact = (float)Math.pow(Math.max(0, Math.cos(2 * phase - 0.45F)), 3) * walk;
      float settle = (float)Math.pow(Math.max(0, Math.cos(2 * phase - 1.3F)), 2) * walk;
      float stanceL = Math.max(0, -s), stanceR = Math.max(0, s);
      float sway = (stanceL - stanceR) * walk;   // + : weight over the left foot
      float pelvisDrop = -(0.035F + 0.03F * runShare) * impact + 0.012F * (1 - settle) * walk - 0.11F * land - 0.13F * crouch - 0.07F * run;
      this.rp(MechModel.PELVIS, 0.032F * sway * (1 - 0.5F * runShare), pelvisDrop + 0.004F * breath * k, -0.05F * crouch);
      float pelvisTwist = 7.0F * s * walk;
      // the hip of the swinging leg drops (the planted side carries the weight)
      float hipRoll = 5.0F * sway;
      this.rr(MechModel.PELVIS, 0, pelvisTwist, hipRoll);
      this.rr(MechModel.THIGH_L, thighL, -pelvisTwist * 0.5F, -2.0F * air - 3.0F * crouch - hipRoll);
      this.rr(MechModel.THIGH_R, thighR, -pelvisTwist * 0.5F, 2.0F * air + 3.0F * crouch - hipRoll);
      this.rr(MechModel.SHIN_L, kneeL, 0, 0);
      this.rr(MechModel.SHIN_R, kneeR, 0, 0);
      // feet: flat while planted, toes down as they leave the ground, toes up as they reach for the next step
      float toeL = -(thighL + kneeL) * 0.9F + walk * liftL * (22.0F * Math.max(0, c) - 12.0F * Math.max(0, -c));
      float toeR = -(thighR + kneeR) * 0.9F + walk * liftR * (22.0F * Math.max(0, -c) - 12.0F * Math.max(0, c));
      this.rr(MechModel.TOE_L, toeL + 20.0F * air, 0, 0);
      this.rr(MechModel.TOE_R, toeR + 20.0F * air, 0, 0);
      this.rr(MechModel.NOZZLE_L, toeL * 0.6F, 0, 0);
      this.rr(MechModel.NOZZLE_R, toeR * 0.6F, 0, 0);

      // --- torso: leans into the walk, counter-twists against the hips, rolls back over the planted foot and takes
      // a small jolt at every heel strike
      float lean = 5.0F * walk + 13.0F * run + 14.0F * land + 22.0F * crouch - 4.0F * air + 3.0F * impact;
      float turnLead = Math.max(-12.0F, Math.min(12.0F, in.turnRate * 0.05F)) * k;
      this.rr(MechModel.WAIST, lean * 0.4F + 0.6F * breath * k, -pelvisTwist * 0.9F + turnLead * 0.4F, -hipRoll * 0.6F);
      this.rr(MechModel.CHEST, lean * 0.6F + 0.8F * breath * k, -pelvisTwist * 0.8F + turnLead * 0.6F, -hipRoll * 0.5F);
      this.rp(MechModel.CHEST, 0, -0.012F * impact, 0);

      // --- head: follows the look direction, with a slow idle scan and the odd tilt
      float scan = (float)Math.sin(time * 0.37F) * 5.0F * k * (1 - walk);
      float headYaw = clamp(in.headYaw, -75, 75) - turnLead + scan;
      float headPitch = clamp(in.headPitch, -60, 60) - lean * 0.9F;
      float tilt = twitchKind == 1 ? 7.0F * twitch : 0;
      this.robotLocalRot[MechModel.HEAD].rotateY(rad(headYaw * k)).rotateX(rad(headPitch * k)).rotateZ(rad(tilt * k));

      // --- arms
      boolean right = in.rightHanded;
      float sw = in.swing;
      float swingArm = sw > 0 ? (float)(-Math.sin(Math.sqrt(sw) * Math.PI) * 72.0F - Math.sin(sw * Math.PI) * 18.0F) : 0;
      float armPhase = phase - 0.35F;
      float armSwing = (float)Math.cos(armPhase) * (24.0F + 26.0F * runShare) * walk;
      float armL = -armSwing - 14.0F * air;
      float armR = armSwing - 14.0F * air;
      float elbowRest = -10.0F - 14.0F * walk - 56.0F * run;
      // the arm swinging forward bends more; a heel strike makes both dip for a moment
      float elbowL = elbowRest - 12.0F * Math.max(0, armSwing / 30.0F) * walk - 6.0F * impact - 30.0F * land - 20.0F * crouch;
      float elbowR = elbowRest - 12.0F * Math.max(0, -armSwing / 30.0F) * walk - 6.0F * impact - 30.0F * land - 20.0F * crouch;
      if (in.holdingLeft) elbowL -= 22.0F;
      if (in.holdingRight) elbowR -= 22.0F;
      float outL = 4.0F + 2.0F * breath + 22.0F * air + 10.0F * land;
      float outR = 4.0F + 2.0F * breath + 22.0F * air + 10.0F * land;
      if (in.aiming) {
         armL = -85.0F + in.headPitch;
         armR = -85.0F + in.headPitch;
         elbowL = -8.0F;
         elbowR = -8.0F;
         outL = -18.0F;
         outR = -18.0F;
      }
      if (in.blocking) {
         if (right) {
            armL = -40.0F;
            elbowL = -70.0F;
            outL = -25.0F;
         } else {
            armR = -40.0F;
            elbowR = -70.0F;
            outR = -25.0F;
         }
      }
      if (right) armR += swingArm;
      else armL += swingArm;
      // the chest twists into a swing
      if (sw > 0) {
         float twist = (float)Math.sin(Math.sqrt(sw) * Math.PI * 2.0) * 10.0F * (right ? -1 : 1);
         this.rr(MechModel.CHEST, 0, twist, 0);
      }
      this.rr(MechModel.UPPER_ARM_L, armL * k, 0, outL * k);
      this.rr(MechModel.UPPER_ARM_R, armR * k, 0, -outR * k);
      this.rr(MechModel.FOREARM_L, elbowL * k, 0, 0);
      this.rr(MechModel.FOREARM_R, elbowR * k, 0, 0);
      // shoulder pads ride up with the arm a little (pistons), hands relax
      this.rr(MechModel.HAND_L, 6.0F * breath * k, 0, -6.0F * k);
      this.rr(MechModel.HAND_R, 6.0F * breath * k, 0, 6.0F * k);

      // --- wings: slow flutter, flare when airborne or landing, spread open as the transformation starts
      float spread = (1 - k);
      float flutter = (float)Math.sin(time * 0.8F) * 2.5F + (twitchKind == 0 ? 8.0F * twitch : 0);
      float flare = 14.0F * air + 10.0F * land - 6.0F * run + flutter + 30.0F * spread - 4.0F * impact;
      float sweep = -10.0F * spread;
      // wing local frame: rotate about the chord (Z) to open, about the normal (Y) to sweep
      this.robotLocalRot[MechModel.WING_L].rotateZ(rad(flare)).rotateY(rad(sweep));
      this.robotLocalRot[MechModel.WING_R].rotateZ(rad(-flare)).rotateY(rad(-sweep));
      // flaperons/ailerons twitch with the wing flutter (servo checks)
      float check = twitchKind == 2 ? 18.0F * twitch : 0;
      this.robotLocalRot[MechModel.AILERON_L].rotateX(rad(check));
      this.robotLocalRot[MechModel.AILERON_R].rotateX(rad(-check));
      // hip blades lift a touch while running
      this.robotLocalRot[MechModel.FIN_L].rotateZ(rad(4.0F * run));
      this.robotLocalRot[MechModel.FIN_R].rotateZ(rad(-4.0F * run));
   }

   /** 0 most of the time, a quick 0 -> 1 -> 0 bump at pseudo-random moments (every few seconds). */
   static float twitch(float time) {
      float slot = (float)Math.floor(time / 5.0F);
      float local = time - slot * 5.0F;
      int h = (int)(slot * 7919.0F) * 0x9E3779B1;
      h ^= h >>> 15;
      float when = 0.5F + (h & 0xFF) / 255.0F * 3.5F;
      float d = local - when;
      if (d < 0 || d > 0.35F) return 0;
      return (float)Math.sin(Math.PI * d / 0.35F);
   }

   static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : v > hi ? hi : v;
   }

   // ================================================================================================ aircraft motion

   private void animateJet(MechInputs in) {
      // landing gear: rotates out of the fuselage and grows from stowed
      float g = in.gear;
      float stow = 1 - g;
      this.jetLocalRot[MechModel.NOSE_GEAR].rotateX(rad(-95.0F * stow));
      this.jetLocalRot[MechModel.GEAR_L].rotateX(rad(95.0F * stow)).rotateZ(rad(-20.0F * stow));
      this.jetLocalRot[MechModel.GEAR_R].rotateX(rad(95.0F * stow)).rotateZ(rad(20.0F * stow));
      float gs = 0.3F + 0.7F * smooth(g * 1.4F);
      this.jetLocalScale[MechModel.NOSE_GEAR] *= gs;
      this.jetLocalScale[MechModel.GEAR_L] *= gs;
      this.jetLocalScale[MechModel.GEAR_R] *= gs;
      if (!in.flightAnim) return;
      float time = in.time;
      float ail = clamp(in.aileron, -1, 1), ele = clamp(in.elevator, -1, 1), rud = clamp(in.rudder, -1, 1);
      // gentle surface hunting in straight flight so it never looks frozen
      float hunt = (float)Math.sin(time * 2.3F) * 1.2F;
      // ailerons differential, flaperons follow half and droop when slow / gear down
      this.jetLocalRot[MechModel.AILERON_L].rotateX(rad(ail * 20.0F + hunt));
      this.jetLocalRot[MechModel.AILERON_R].rotateX(rad(-ail * 20.0F - hunt));
      float droop = 18.0F * g;
      this.jetLocalRot[MechModel.FLAPERON_L].rotateX(rad(ail * 9.0F + droop));
      this.jetLocalRot[MechModel.FLAPERON_R].rotateX(rad(-ail * 9.0F + droop));
      // all-moving stabilisers: pitch together, roll against each other
      this.jetLocalRot[MechModel.STAB_L].rotateX(rad(ele * 14.0F + ail * 6.0F));
      this.jetLocalRot[MechModel.STAB_R].rotateX(rad(ele * 14.0F - ail * 6.0F));
      // rudders
      this.jetLocalRot[MechModel.RUDDER_L].rotateX(rad(rud * 18.0F + hunt * 0.5F));
      this.jetLocalRot[MechModel.RUDDER_R].rotateX(rad(rud * 18.0F + hunt * 0.5F));
      // F-22 style two-dimensional thrust vectoring: the nozzles pitch up to 20 degrees with the stabilisers (exhaust
      // up = nose up), split a little in a roll, and never sit perfectly still while the engines run
      float vec = clamp(ele * 20.0F + (float)Math.sin(time * 1.7F) * 1.5F, -20.0F, 20.0F);
      this.jetLocalRot[MechModel.NOZZLE_L].rotateX(rad(clamp(vec + ail * 7.0F, -20.0F, 20.0F)));
      this.jetLocalRot[MechModel.NOZZLE_R].rotateX(rad(clamp(vec - ail * 7.0F, -20.0F, 20.0F)));
   }
}
