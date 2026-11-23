package com.jfant.specter.mech;

import static com.jfant.specter.mech.MechPalette.ACCENT;
import static com.jfant.specter.mech.MechPalette.ARMOR;
import static com.jfant.specter.mech.MechPalette.CANOPY;
import static com.jfant.specter.mech.MechPalette.DARK;
import static com.jfant.specter.mech.MechPalette.GLOW;
import static com.jfant.specter.mech.MechPalette.NOZZLE;
import static com.jfant.specter.mech.MechPalette.PANEL;
import static com.jfant.specter.mech.MechPalette.SILVER;
import static com.jfant.specter.mech.MechPalette.TRIM;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Jet Mech: one set of parts that is both an original robot and a twin-engine stealth fighter.
 *
 * <p>Every part has two rest poses. The ROBOT pose is given in robot space (blocks, feet at the origin, +Y up, +Z the
 * way the robot faces, +X its left). The JET pose is given in jet space (origin at the aircraft's centre, +Z the nose,
 * +Y up, +X the left wing). Parts never swap or fade: the transformation moves each one from the first pose to the
 * second inside its own time window, so the robot visibly folds into the aircraft.
 *
 * <p>How the robot maps onto the aircraft: the robot lies face down along the flight path (head forward). The back
 * pack carries the canopy and the folded nose, the torso is the forward fuselage, the arms tuck into the sides as the
 * intakes, the legs are the twin engines with the heels as the exhaust nozzles, the toes fold under the engines, the
 * hip blades become the canted vertical tails, the shin blades the horizontal stabilisers, and the back wings swing
 * down into the main wings. The head sinks into the chest.
 */
public final class MechModel {
   /** Robot point that sits at the centre of the aircraft when the robot lies down (just above the hips). */
   public static final float LIE_PIVOT_Y = 1.05F;

   public static final int PELVIS = 0;
   public static final int WAIST = 1;
   public static final int CHEST = 2;
   public static final int HEAD = 3;
   public static final int BACKPACK = 4;
   public static final int NOSE_BASE = 5;
   public static final int NOSE_TIP = 6;
   public static final int UPPER_ARM_L = 7;
   public static final int UPPER_ARM_R = 8;
   public static final int FOREARM_L = 9;
   public static final int FOREARM_R = 10;
   public static final int HAND_L = 11;
   public static final int HAND_R = 12;
   public static final int THIGH_L = 13;
   public static final int THIGH_R = 14;
   public static final int SHIN_L = 15;
   public static final int SHIN_R = 16;
   public static final int NOZZLE_L = 17;
   public static final int NOZZLE_R = 18;
   public static final int TOE_L = 19;
   public static final int TOE_R = 20;
   public static final int WING_L = 21;
   public static final int WING_R = 22;
   public static final int FLAPERON_L = 23;
   public static final int FLAPERON_R = 24;
   public static final int AILERON_L = 25;
   public static final int AILERON_R = 26;
   public static final int FIN_L = 27;
   public static final int FIN_R = 28;
   public static final int RUDDER_L = 29;
   public static final int RUDDER_R = 30;
   public static final int STAB_L = 31;
   public static final int STAB_R = 32;
   public static final int KEEL = 33;
   public static final int NOSE_GEAR = 34;
   public static final int GEAR_L = 35;
   public static final int GEAR_R = 36;
   public static final int COUNT = 37;

   /** Nozzle exit in the nozzle part's frame; the exhaust leaves along -Y. */
   public static final Vector3f NOZZLE_EXIT = new Vector3f(0.0F, -0.125F, -0.005F);
   /** Wing tip (trailing corner) in the wing part's frame, for vapour trails. */
   public static final Vector3f WING_TIP = new Vector3f(0.72F, 0.0F, -0.70F);

   private static MechModel instance;

   public final Part[] parts = new Part[COUNT];

   public static MechModel get() {
      if (instance == null) instance = new MechModel();
      return instance;
   }

   public static final class Part {
      public final int index;
      public final String name;
      public final MechMesh mesh;
      public final int glowQuads;
      /** Robot hierarchy: parent and the rest transform relative to it. */
      public int robotParent = -1;
      public final Vector3f robotPos = new Vector3f();
      public final Quaternionf robotRot = new Quaternionf();
      public float robotScale = 1.0F;
      /** Jet hierarchy: parent and the rest transform relative to it (or to the jet origin). */
      public int jetParent = -1;
      public final Vector3f jetPos = new Vector3f();
      public final Quaternionf jetRot = new Quaternionf();
      public float jetScale = 1.0F;
      /** Transformation window, as fractions of the whole transformation. */
      public float start;
      public float end;
      /** Settles with a small mechanical overshoot at the end of its window. */
      public boolean lock;
      /** Extra sideways/upward swing in the middle of the move (in the parent / jet frame). */
      public final Vector3f arc = new Vector3f();

      Part(int index, String name, MechMesh mesh) {
         this.index = index;
         this.name = name;
         this.mesh = mesh;
         this.glowQuads = mesh.glowQuads();
      }

      public boolean sharedParent() {
         return this.robotParent >= 0 && this.robotParent == this.jetParent;
      }
   }

   // ======================================================================================================== build

   private final Vector3f[] robotAbsPos = new Vector3f[COUNT];
   private final Quaternionf[] robotAbsRot = new Quaternionf[COUNT];
   private final Vector3f[] jetAbsPos = new Vector3f[COUNT];
   private final Quaternionf[] jetAbsRot = new Quaternionf[COUNT];

   private MechModel() {
      this.buildTorso();
      this.buildArms();
      this.buildLegs();
      this.buildFlyingSurfaces();
      this.buildGear();
      for (int i = 0; i < COUNT; i++) {
         if (this.parts[i] == null) throw new IllegalStateException("mech part " + i + " missing");
      }
   }

   /** Quaternion from rotations in degrees, applied X then Y then Z in the part's own frame. */
   static Quaternionf euler(float x, float y, float z) {
      return new Quaternionf().rotateX(rad(x)).rotateY(rad(y)).rotateZ(rad(z));
   }

   /** The rotation that lays a robot-space part down in jet space (robot +Y becomes the nose, +Z becomes down). */
   static Quaternionf lying() {
      return new Quaternionf().rotateX(rad(90.0F));
   }

   static Quaternionf lying(float x, float y, float z) {
      return lying().mul(euler(x, y, z));
   }

   /** Rotation whose local X axis becomes {@code xAxis} and local Z axis becomes (roughly) {@code zAxis}. */
   static Quaternionf basis(Vector3f xAxis, Vector3f zAxis) {
      Vector3f x = new Vector3f(xAxis).normalize();
      Vector3f y = new Vector3f(zAxis).cross(x).normalize();
      Vector3f z = new Vector3f(x).cross(y).normalize();
      Matrix3f m = new Matrix3f(x, y, z);
      return new Quaternionf().setFromNormalized(m);
   }

   static float rad(float deg) {
      return (float)Math.toRadians(deg);
   }

   private static Quaternionf mirror(Quaternionf q) {
      return new Quaternionf(q.x, -q.y, -q.z, q.w);
   }

   private static Vector3f mirror(Vector3f v) {
      return new Vector3f(-v.x, v.y, v.z);
   }

   private Part add(int index, String name, Consumer<MechMesh.Builder> geometry, boolean mirrored) {
      MechMesh.Builder b = MechMesh.builder().mirror(mirrored);
      geometry.accept(b);
      Part p = new Part(index, name, b.build());
      this.parts[index] = p;
      return p;
   }

   /** Robot rest pose given absolutely in robot space; stored relative to the robot parent. */
   private void robotAbs(int i, int parent, Vector3f pos, Quaternionf rot) {
      this.robotAbsPos[i] = new Vector3f(pos);
      this.robotAbsRot[i] = new Quaternionf(rot);
      Part p = this.parts[i];
      p.robotParent = parent;
      relative(parent < 0 ? null : this.robotAbsPos[parent], parent < 0 ? null : this.robotAbsRot[parent], pos, rot, p.robotPos, p.robotRot);
   }

   /** Robot rest pose given relative to the parent's frame. */
   private void robotLocal(int i, int parent, Vector3f pos, Quaternionf rot) {
      Part p = this.parts[i];
      p.robotParent = parent;
      p.robotPos.set(pos);
      p.robotRot.set(rot);
      this.robotAbsRot[i] = new Quaternionf(this.robotAbsRot[parent]).mul(rot);
      this.robotAbsPos[i] = this.robotAbsRot[parent].transform(new Vector3f(pos)).add(this.robotAbsPos[parent]);
   }

   private void jetAbs(int i, int parent, Vector3f pos, Quaternionf rot) {
      this.jetAbsPos[i] = new Vector3f(pos);
      this.jetAbsRot[i] = new Quaternionf(rot);
      Part p = this.parts[i];
      p.jetParent = parent;
      relative(parent < 0 ? null : this.jetAbsPos[parent], parent < 0 ? null : this.jetAbsRot[parent], pos, rot, p.jetPos, p.jetRot);
   }

   private void jetLocal(int i, int parent, Vector3f pos, Quaternionf rot) {
      Part p = this.parts[i];
      p.jetParent = parent;
      p.jetPos.set(pos);
      p.jetRot.set(rot);
      this.jetAbsRot[i] = new Quaternionf(this.jetAbsRot[parent]).mul(rot);
      this.jetAbsPos[i] = this.jetAbsRot[parent].transform(new Vector3f(pos)).add(this.jetAbsPos[parent]);
   }

   private static void relative(Vector3f parentPos, Quaternionf parentRot, Vector3f pos, Quaternionf rot, Vector3f outPos, Quaternionf outRot) {
      if (parentPos == null) {
         outPos.set(pos);
         outRot.set(rot);
         return;
      }
      Quaternionf inv = new Quaternionf(parentRot).conjugate();
      inv.transform(outPos.set(pos).sub(parentPos));
      outRot.set(inv).mul(rot);
   }

   private static void window(Part p, float start, float end) {
      p.start = start;
      p.end = end;
   }

   /** Robot space position of a point after lying down (before the parts move). */
   static Vector3f lie(float x, float y, float z) {
      return new Vector3f(x, -z, y - LIE_PIVOT_Y);
   }

   // ======================================================================================================== torso

   private void buildTorso() {
      Part pelvis = this.add(PELVIS, "pelvis", b -> {
         b.taperY(ARMOR, 0, 0, -0.08F, 0.07F, 0.15F, 0.10F, 0.19F, 0.12F, 0, 0);
         b.box(PANEL, -0.195F, 0.045F, -0.125F, 0.195F, 0.085F, 0.125F);
         b.taperY(ACCENT, 0, 0.13F, -0.15F, 0.05F, 0.045F, 0.02F, 0.075F, 0.025F, 0, 0.01F);
         b.box(TRIM, -0.02F, -0.02F, 0.15F, 0.02F, 0.04F, 0.168F);
         b.box(PANEL, -0.13F, -0.07F, -0.15F, 0.13F, 0.06F, -0.11F);
         b.prism(SILVER, 0, 10, -0.03F, 0, 0.10F, 0.21F, 0.055F, 0.055F);
         b.prism(SILVER, 0, 10, -0.03F, 0, -0.21F, -0.10F, 0.055F, 0.055F);
         b.box(DARK, 0.188F, -0.05F, -0.06F, 0.20F, 0.03F, 0.06F);
         b.box(DARK, -0.20F, -0.05F, -0.06F, -0.188F, 0.03F, 0.06F);
      }, false);
      this.robotAbs(PELVIS, -1, new Vector3f(0, 1.02F, 0), new Quaternionf());
      this.jetAbs(PELVIS, -1, new Vector3f(0, 0.0F, -0.05F), lying());
      window(pelvis, 0.28F, 0.62F);

      Part waist = this.add(WAIST, "waist", b -> {
         b.prism(DARK, 1, 8, 0, 0, -0.03F, 0.15F, 0.085F, 0.085F);
         b.taperY(ARMOR, 0, 0.075F, 0.0F, 0.055F, 0.10F, 0.035F, 0.11F, 0.035F, 0, 0.005F);
         b.taperY(ARMOR, 0, 0.08F, 0.065F, 0.13F, 0.11F, 0.035F, 0.125F, 0.038F, 0, 0.005F);
         b.box(PANEL, 0.09F, 0.0F, -0.07F, 0.14F, 0.13F, 0.06F);
         b.box(PANEL, -0.14F, 0.0F, -0.07F, -0.09F, 0.13F, 0.06F);
         b.prism(SILVER, 1, 6, 0.148F, 0.03F, -0.02F, 0.15F, 0.015F, 0.015F);
         b.prism(SILVER, 1, 6, -0.148F, 0.03F, -0.02F, 0.15F, 0.015F, 0.015F);
         b.taperY(PANEL, 0, -0.09F, 0.0F, 0.14F, 0.11F, 0.03F, 0.12F, 0.035F, 0, -0.005F);
      }, false);
      this.robotAbs(WAIST, PELVIS, new Vector3f(0, 1.12F, 0), new Quaternionf());
      this.jetAbs(WAIST, PELVIS, new Vector3f(0, 0.0F, 0.06F), lying());
      window(waist, 0.28F, 0.62F);

      Part chest = this.add(CHEST, "chest", b -> {
         b.taperY(ARMOR, 0, 0.005F, 0.0F, 0.40F, 0.19F, 0.105F, 0.25F, 0.135F, 0, 0.005F);
         for (boolean m : new boolean[]{false, true}) {
            b.mirror(m);
            // swept pectoral plates, accent coloured
            b.hexa(ACCENT, new float[]{
               0.015F, 0.14F, 0.12F, 0.245F, 0.21F, 0.12F, 0.245F, 0.37F, 0.12F, 0.015F, 0.385F, 0.12F,
               0.015F, 0.14F, 0.172F, 0.245F, 0.21F, 0.147F, 0.245F, 0.37F, 0.147F, 0.015F, 0.385F, 0.172F
            });
            // trapezius armour up to the collar
            b.hexa(ARMOR, new float[]{
               0.09F, 0.37F, -0.09F, 0.25F, 0.37F, -0.10F, 0.25F, 0.40F, -0.10F, 0.09F, 0.445F, -0.09F,
               0.09F, 0.37F, 0.09F, 0.25F, 0.37F, 0.10F, 0.25F, 0.40F, 0.10F, 0.09F, 0.445F, 0.09F
            });
            b.prism(SILVER, 0, 10, 0.34F, 0.0F, 0.22F, 0.30F, 0.06F, 0.06F);
            // side vents
            b.box(DARK, 0.205F, 0.10F, -0.05F, 0.222F, 0.24F, 0.05F);
         }
         b.mirror(false);
         b.box(DARK, -0.11F, 0.03F, 0.10F, 0.11F, 0.14F, 0.123F);
         for (int i = 0; i < 3; i++) {
            b.box(SILVER, -0.10F, 0.045F + i * 0.032F, 0.118F, 0.10F, 0.057F + i * 0.032F, 0.128F);
         }
         b.taperY(DARK, 0, 0, 0.38F, 0.445F, 0.13F, 0.085F, 0.10F, 0.07F, 0, 0);
         // chest core light
         b.glow(GLOW, new float[]{0, 0.18F, 0.1745F, 0.026F, 0.21F, 0.1745F, 0, 0.24F, 0.1745F, -0.026F, 0.21F, 0.1745F}, 0, 0, 1);
         b.box(DARK, -0.035F, 0.17F, 0.155F, 0.035F, 0.25F, 0.172F);
      }, false);
      this.robotAbs(CHEST, WAIST, new Vector3f(0, 1.25F, 0), new Quaternionf());
      this.jetAbs(CHEST, WAIST, new Vector3f(0, -0.01F, 0.20F), lying());
      window(chest, 0.28F, 0.62F);

      Part head = this.add(HEAD, "head", b -> {
         b.prism(DARK, 1, 8, 0, 0, -0.04F, 0.07F, 0.048F, 0.042F);
         b.prism(SILVER, 1, 6, 0.04F, -0.03F, -0.04F, 0.06F, 0.012F, 0.012F);
         b.prism(SILVER, 1, 6, -0.04F, -0.03F, -0.04F, 0.06F, 0.012F, 0.012F);
         // skull, sloping back over the top
         b.hexa(ARMOR, new float[]{
            -0.105F, 0.05F, -0.095F, 0.105F, 0.05F, -0.095F, 0.08F, 0.27F, -0.085F, -0.08F, 0.27F, -0.085F,
            -0.105F, 0.05F, 0.095F, 0.105F, 0.05F, 0.095F, 0.08F, 0.27F, 0.05F, -0.08F, 0.27F, 0.05F
         });
         // faceplate
         b.hexa(SILVER, new float[]{
            -0.045F, 0.035F, 0.07F, 0.045F, 0.035F, 0.07F, 0.072F, 0.125F, 0.07F, -0.072F, 0.125F, 0.07F,
            -0.045F, 0.035F, 0.115F, 0.045F, 0.035F, 0.115F, 0.072F, 0.125F, 0.128F, -0.072F, 0.125F, 0.128F
         });
         b.box(DARK, -0.03F, 0.058F, 0.11F, 0.03F, 0.068F, 0.123F);
         b.box(DARK, -0.024F, 0.078F, 0.115F, 0.024F, 0.088F, 0.126F);
         // visor recess and the glowing V visor
         b.box(DARK, -0.095F, 0.125F, 0.06F, 0.095F, 0.185F, 0.112F);
         for (boolean m : new boolean[]{false, true}) {
            b.mirror(m);
            b.glow(GLOW, new float[]{0.0F, 0.135F, 0.1135F, 0.088F, 0.155F, 0.1135F, 0.088F, 0.175F, 0.1135F, 0.0F, 0.157F, 0.1135F}, 0, 0, 1);
            // swept antenna fins
            b.hexa(ACCENT, new float[]{
               0.097F, 0.13F, 0.03F, 0.097F, 0.22F, 0.01F, 0.097F, 0.34F, -0.16F, 0.097F, 0.30F, -0.17F,
               0.112F, 0.13F, 0.03F, 0.112F, 0.22F, 0.01F, 0.112F, 0.34F, -0.16F, 0.112F, 0.30F, -0.17F
            });
            // ear pods
            b.box(SILVER, 0.095F, 0.07F, -0.05F, 0.125F, 0.15F, 0.045F);
            b.box(TRIM, 0.123F, 0.095F, -0.02F, 0.131F, 0.125F, 0.015F);
         }
         b.mirror(false);
         // brow
         b.hexa(ARMOR, new float[]{
            -0.10F, 0.18F, 0.04F, 0.10F, 0.18F, 0.04F, 0.09F, 0.215F, 0.04F, -0.09F, 0.215F, 0.04F,
            -0.10F, 0.18F, 0.118F, 0.10F, 0.18F, 0.118F, 0.09F, 0.215F, 0.10F, -0.09F, 0.215F, 0.10F
         });
         // centre crest blade
         b.hexa(ACCENT, new float[]{
            -0.013F, 0.20F, 0.10F, -0.013F, 0.30F, 0.03F, -0.013F, 0.315F, -0.07F, -0.013F, 0.25F, -0.10F,
            0.013F, 0.20F, 0.10F, 0.013F, 0.30F, 0.03F, 0.013F, 0.315F, -0.07F, 0.013F, 0.25F, -0.10F
         });
      }, false);
      this.robotAbs(HEAD, CHEST, new Vector3f(0, 1.69F, 0.01F), new Quaternionf());
      // sinks into the chest cavity and shrinks so nothing pokes out
      this.jetAbs(HEAD, CHEST, new Vector3f(0, -0.03F, 0.44F), lying());
      head.jetScale = 0.4F;
      window(head, 0.10F, 0.34F);

      Part pack = this.add(BACKPACK, "backpack", b -> {
         b.taperY(ARMOR, 0, -0.03F, -0.22F, 0.24F, 0.17F, 0.03F, 0.15F, 0.03F, 0, 0.0F);
         b.taperY(DARK, 0, -0.068F, -0.15F, 0.21F, 0.10F, 0.008F, 0.085F, 0.008F, 0, 0);
         // canopy: main bubble, then the tapered ends
         b.taperY(CANOPY, 0, -0.085F, -0.10F, 0.13F, 0.085F, 0.02F, 0.08F, 0.03F, 0, -0.01F);
         b.taperY(CANOPY, 0, -0.095F, 0.13F, 0.22F, 0.08F, 0.03F, 0.02F, 0.01F, 0, 0.025F);
         b.taperY(CANOPY, 0, -0.072F, -0.16F, -0.10F, 0.02F, 0.006F, 0.085F, 0.02F, 0, -0.013F);
         b.box(DARK, -0.088F, 0.04F, -0.128F, 0.088F, 0.055F, -0.062F);
         for (boolean m : new boolean[]{false, true}) {
            b.mirror(m);
            b.box(DARK, 0.10F, -0.19F, -0.066F, 0.155F, -0.06F, -0.055F);
            b.prism(SILVER, 2, 8, 0.10F, 0.22F, -0.065F, -0.03F, 0.035F, 0.035F);
         }
         b.mirror(false);
         b.box(PANEL, -0.05F, -0.21F, -0.07F, 0.05F, -0.17F, -0.04F);
      }, false);
      this.robotAbs(BACKPACK, CHEST, new Vector3f(0, 1.48F, -0.13F), new Quaternionf());
      this.jetAbs(BACKPACK, CHEST, new Vector3f(0, 0.11F, 0.60F), lying());
      window(pack, 0.40F, 0.68F);
      pack.arc.set(0, 0.06F, 0);

      // nose: chined radome folded upright behind the head; the tip telescopes out of it in jet mode.
      // Local +Y is the nose axis, local -Z its top (it becomes the aircraft's upper side when lying down).
      Part noseBase = this.add(NOSE_BASE, "nose-base", b -> {
         b.hexa(ARMOR, new float[]{
            -0.16F, 0, 0, 0.16F, 0, 0, 0.055F, 0, -0.11F, -0.055F, 0, -0.11F,
            -0.085F, 0.46F, 0, 0.085F, 0.46F, 0, 0.03F, 0.46F, -0.058F, -0.03F, 0.46F, -0.058F
         });
         b.hexa(PANEL, new float[]{
            -0.16F, 0, 0, 0.16F, 0, 0, 0.06F, 0, 0.14F, -0.06F, 0, 0.14F,
            -0.085F, 0.46F, 0, 0.085F, 0.46F, 0, 0.032F, 0.46F, 0.066F, -0.032F, 0.46F, 0.066F
         });
         b.hexa(ACCENT, new float[]{
            -0.018F, 0.02F, -0.1125F, 0.018F, 0.02F, -0.1125F, 0.011F, 0.40F, -0.0665F, -0.011F, 0.40F, -0.0665F,
            -0.018F, 0.02F, -0.118F, 0.018F, 0.02F, -0.118F, 0.011F, 0.40F, -0.072F, -0.011F, 0.40F, -0.072F
         });
      }, false);
      // robot: a short radome collar leaning back behind the head (slightly telescoped in)
      this.robotAbs(NOSE_BASE, BACKPACK, new Vector3f(0, 1.62F, -0.24F), euler(-25.0F, 0, 0));
      noseBase.robotScale = 0.85F;
      this.jetAbs(NOSE_BASE, BACKPACK, new Vector3f(0, 0.04F, 0.66F), lying());
      window(noseBase, 0.44F, 0.74F);
      noseBase.arc.set(0, 0.10F, 0);

      Part noseTip = this.add(NOSE_TIP, "nose-tip", b -> {
         b.hexa(ARMOR, new float[]{
            -0.083F, 0, 0, 0.083F, 0, 0, 0.029F, 0, -0.056F, -0.029F, 0, -0.056F,
            -0.004F, 0.44F, 0.012F, 0.004F, 0.44F, 0.012F, 0.002F, 0.44F, 0.008F, -0.002F, 0.44F, 0.008F
         });
         b.hexa(PANEL, new float[]{
            -0.083F, 0, 0, 0.083F, 0, 0, 0.031F, 0, 0.064F, -0.031F, 0, 0.064F,
            -0.004F, 0.44F, 0.012F, 0.004F, 0.44F, 0.012F, 0.002F, 0.44F, 0.016F, -0.002F, 0.44F, 0.016F
         });
         b.prism(SILVER, 1, 6, 0, 0.012F, 0.43F, 0.52F, 0.006F, 0.003F);
      }, false);
      this.robotLocal(NOSE_TIP, NOSE_BASE, new Vector3f(0, 0.02F, 0), new Quaternionf());
      this.jetLocal(NOSE_TIP, NOSE_BASE, new Vector3f(0, 0.455F, 0), new Quaternionf());
      window(noseTip, 0.66F, 0.90F);
      noseTip.lock = true;

      Part keel = this.add(KEEL, "keel", b -> {
         b.taperY(ARMOR, 0, -0.02F, -0.22F, 0.0F, 0.035F, 0.02F, 0.07F, 0.03F, 0, 0.0F);
         b.taperY(TRIM, 0, -0.045F, -0.20F, -0.04F, 0.01F, 0.006F, 0.012F, 0.006F, 0, 0.0F);
      }, false);
      this.robotAbs(KEEL, PELVIS, new Vector3f(0, 0.97F, -0.13F), new Quaternionf());
      this.jetAbs(KEEL, PELVIS, new Vector3f(0, 0.08F, -0.60F), lying());
      window(keel, 0.48F, 0.80F);
      keel.arc.set(0, 0.08F, 0);
   }

   // ======================================================================================================== arms

   private void buildArms() {
      for (int side = 0; side < 2; side++) {
         boolean m = side == 1;
         float sx = m ? -1 : 1;
         int upper = m ? UPPER_ARM_R : UPPER_ARM_L, fore = m ? FOREARM_R : FOREARM_L, hand = m ? HAND_R : HAND_L;

         Part up = this.add(upper, m ? "upper-arm-r" : "upper-arm-l", b -> {
            // big angular shoulder pad - its top vent becomes the intake mouth in jet mode
            b.hexa(ARMOR, new float[]{
               -0.02F, -0.11F, -0.12F, 0.165F, -0.13F, -0.13F, 0.15F, 0.09F, -0.115F, -0.02F, 0.11F, -0.10F,
               -0.02F, -0.11F, 0.12F, 0.165F, -0.13F, 0.13F, 0.15F, 0.09F, 0.115F, -0.02F, 0.11F, 0.10F
            });
            b.hexa(ACCENT, new float[]{
               0.163F, -0.10F, -0.10F, 0.173F, -0.10F, -0.10F, 0.159F, 0.05F, -0.09F, 0.149F, 0.05F, -0.09F,
               0.163F, -0.10F, 0.10F, 0.173F, -0.10F, 0.10F, 0.159F, 0.05F, 0.09F, 0.149F, 0.05F, 0.09F
            });
            b.hexa(DARK, new float[]{
               0.0F, 0.106F, -0.08F, 0.13F, 0.094F, -0.08F, 0.13F, 0.104F, -0.08F, 0.0F, 0.116F, -0.08F,
               0.0F, 0.106F, 0.08F, 0.13F, 0.094F, 0.08F, 0.13F, 0.104F, 0.08F, 0.0F, 0.116F, 0.08F
            });
            b.taperY(DARK, 0.065F, 0, -0.34F, -0.08F, 0.045F, 0.045F, 0.05F, 0.05F, 0, 0);
            b.taperY(PANEL, 0.065F, 0.0F, -0.31F, -0.12F, 0.06F, 0.065F, 0.066F, 0.07F, 0, 0);
            b.prism(SILVER, 0, 10, -0.35F, 0.0F, 0.0F, 0.13F, 0.05F, 0.05F);
         }, m);
         this.robotAbs(upper, CHEST, new Vector3f(0.33F * sx, 1.60F, 0), m ? euler(0, 0, -6) : euler(0, 0, 6));
         this.jetAbs(upper, CHEST, new Vector3f(0.235F * sx, -0.035F, 0.50F), lying());
         window(up, 0.18F, 0.50F);
         up.arc.set(0.08F * sx, 0, 0);

         Part fa = this.add(fore, m ? "forearm-r" : "forearm-l", b -> {
            b.taperY(ARMOR, 0, 0.005F, -0.32F, -0.02F, 0.07F, 0.075F, 0.055F, 0.06F, 0, 0);
            b.hexa(ACCENT, new float[]{
               0.068F, -0.29F, -0.035F, 0.077F, -0.29F, -0.035F, 0.066F, -0.07F, -0.03F, 0.057F, -0.07F, -0.03F,
               0.068F, -0.29F, 0.035F, 0.077F, -0.29F, 0.035F, 0.066F, -0.07F, 0.03F, 0.057F, -0.07F, 0.03F
            });
            b.box(SILVER, -0.06F, -0.335F, -0.06F, 0.06F, -0.31F, 0.065F);
            b.taperY(PANEL, 0, -0.065F, -0.08F, 0.02F, 0.05F, 0.02F, 0.04F, 0.02F, 0, 0.0F);
            b.box(DARK, -0.04F, -0.25F, 0.072F, 0.04F, -0.10F, 0.083F);
         }, m);
         this.robotLocal(fore, upper, new Vector3f(0.065F * sx, -0.35F, 0), new Quaternionf());
         this.jetLocal(fore, upper, new Vector3f(0.065F * sx, -0.34F, 0), new Quaternionf());
         window(fa, 0.22F, 0.54F);

         Part hd = this.add(hand, m ? "hand-r" : "hand-l", b -> {
            b.box(DARK, -0.045F, -0.09F, -0.035F, 0.045F, 0.0F, 0.04F);
            b.box(ARMOR, 0.03F, -0.085F, -0.04F, 0.052F, -0.005F, 0.045F);
            for (int f = 0; f < 3; f++) {
               float z0 = -0.032F + f * 0.028F;
               b.box(SILVER, -0.03F, -0.16F, z0, 0.02F, -0.085F, z0 + 0.022F);
            }
            b.box(SILVER, -0.05F, -0.075F, 0.028F, -0.026F, -0.012F, 0.05F);
         }, m);
         this.robotLocal(hand, fore, new Vector3f(0, -0.335F, 0.005F), new Quaternionf());
         this.jetLocal(hand, fore, new Vector3f(0, -0.17F, 0), new Quaternionf());
         hd.jetScale = 0.55F;
         window(hd, 0.14F, 0.34F);
      }
   }

   // ======================================================================================================== legs

   private void buildLegs() {
      for (int side = 0; side < 2; side++) {
         boolean m = side == 1;
         float sx = m ? -1 : 1;
         int thigh = m ? THIGH_R : THIGH_L, shin = m ? SHIN_R : SHIN_L, nozzle = m ? NOZZLE_R : NOZZLE_L, toe = m ? TOE_R : TOE_L;

         Part th = this.add(thigh, m ? "thigh-r" : "thigh-l", b -> {
            b.taperY(DARK, 0, 0, -0.40F, -0.02F, 0.06F, 0.06F, 0.07F, 0.07F, 0, 0);
            b.taperY(ARMOR, 0, 0.0F, -0.38F, -0.04F, 0.095F, 0.095F, 0.115F, 0.105F, 0, 0);
            b.taperY(PANEL, 0, 0.10F, -0.33F, -0.08F, 0.07F, 0.012F, 0.08F, 0.012F, 0, 0.005F);
            b.taperY(PANEL, 0, -0.10F, -0.34F, -0.06F, 0.075F, 0.012F, 0.085F, 0.012F, 0, -0.005F);
            b.prism(SILVER, 0, 10, -0.41F, 0.0F, -0.075F, 0.075F, 0.055F, 0.055F);
         }, m);
         this.robotAbs(thigh, PELVIS, new Vector3f(0.135F * sx, 0.97F, 0), new Quaternionf());
         this.jetAbs(thigh, PELVIS, new Vector3f(0.15F * sx, -0.01F, -0.10F), lying());
         window(th, 0.22F, 0.56F);

         Part sh = this.add(shin, m ? "shin-r" : "shin-l", b -> {
            b.taperY(ARMOR, 0, 0.0F, -0.41F, -0.03F, 0.115F, 0.115F, 0.10F, 0.105F, 0, 0);
            b.hexa(ACCENT, new float[]{
               -0.075F, -0.13F, 0.10F, 0.075F, -0.13F, 0.10F, 0.065F, 0.03F, 0.085F, -0.065F, 0.03F, 0.085F,
               -0.075F, -0.13F, 0.135F, 0.075F, -0.13F, 0.135F, 0.065F, 0.03F, 0.12F, -0.065F, 0.03F, 0.12F
            });
            b.taperY(PANEL, 0, 0.115F, -0.38F, -0.15F, 0.085F, 0.012F, 0.075F, 0.01F, 0, -0.005F);
            b.box(DARK, -0.06F, -0.33F, -0.126F, 0.06F, -0.12F, -0.11F);
            for (int i = 0; i < 3; i++) {
               b.box(SILVER, -0.055F, -0.30F + i * 0.06F, -0.13F, 0.055F, -0.285F + i * 0.06F, -0.122F);
            }
            b.taperY(TRIM, 0, 0, -0.40F, -0.37F, 0.118F, 0.118F, 0.117F, 0.117F, 0, 0);
         }, m);
         this.robotLocal(shin, thigh, new Vector3f(0, -0.41F, 0), new Quaternionf());
         this.jetLocal(shin, thigh, new Vector3f(0, -0.46F, 0), new Quaternionf());
         window(sh, 0.26F, 0.60F);

         // heel = the engine's rectangular thrust-vectoring nozzle; its exit faces the ground in robot mode
         Part nz = this.add(nozzle, m ? "nozzle-r" : "nozzle-l", b -> {
            b.taperY(ARMOR, 0, 0.0F, -0.10F, 0.0F, 0.10F, 0.095F, 0.11F, 0.105F, 0, 0);
            b.hexa(SILVER, new float[]{
               -0.10F, -0.155F, -0.088F, 0.10F, -0.155F, -0.088F, 0.10F, -0.10F, -0.10F, -0.10F, -0.10F, -0.10F,
               -0.10F, -0.155F, -0.072F, 0.10F, -0.155F, -0.072F, 0.10F, -0.10F, -0.08F, -0.10F, -0.10F, -0.08F
            });
            b.hexa(SILVER, new float[]{
               -0.10F, -0.155F, 0.062F, 0.10F, -0.155F, 0.062F, 0.10F, -0.10F, 0.07F, -0.10F, -0.10F, 0.07F,
               -0.10F, -0.155F, 0.078F, 0.10F, -0.155F, 0.078F, 0.10F, -0.10F, 0.09F, -0.10F, -0.10F, 0.09F
            });
            b.box(SILVER, 0.084F, -0.15F, -0.088F, 0.10F, -0.10F, 0.078F);
            b.box(SILVER, -0.10F, -0.15F, -0.088F, -0.084F, -0.10F, 0.078F);
            b.glow(NOZZLE, new float[]{-0.084F, -0.105F, -0.072F, 0.084F, -0.105F, -0.072F, 0.084F, -0.105F, 0.062F, -0.084F, -0.105F, 0.062F}, 0, -1, 0);
         }, m);
         this.robotLocal(nozzle, shin, new Vector3f(0, -0.41F, -0.025F), new Quaternionf());
         this.jetLocal(nozzle, shin, new Vector3f(0, -0.45F, 0.0F), new Quaternionf());
         window(nz, 0.32F, 0.62F);

         Part tp = this.add(toe, m ? "toe-r" : "toe-l", b -> {
            b.hexa(ARMOR, new float[]{
               -0.09F, -0.13F, 0.0F, 0.09F, -0.13F, 0.0F, 0.085F, -0.02F, 0.0F, -0.085F, -0.02F, 0.0F,
               -0.085F, -0.13F, 0.22F, 0.085F, -0.13F, 0.22F, 0.07F, -0.07F, 0.21F, -0.07F, -0.07F, 0.21F
            });
            b.hexa(ACCENT, new float[]{
               -0.075F, -0.13F, 0.17F, 0.075F, -0.13F, 0.17F, 0.066F, -0.068F, 0.17F, -0.066F, -0.068F, 0.17F,
               -0.072F, -0.13F, 0.235F, 0.072F, -0.13F, 0.235F, 0.06F, -0.08F, 0.225F, -0.06F, -0.08F, 0.225F
            });
            b.box(DARK, -0.088F, -0.14F, 0.005F, 0.088F, -0.125F, 0.215F);
            b.prism(SILVER, 0, 8, -0.06F, 0.0F, -0.08F, 0.08F, 0.04F, 0.04F);
         }, m);
         this.robotLocal(toe, shin, new Vector3f(0, -0.43F, 0.075F), new Quaternionf());
         // folds up flat under the engine (the shin's front is the aircraft's belly)
         this.jetLocal(toe, shin, new Vector3f(0, -0.38F, 0.0F), euler(-90.0F, 0, 0));
         window(tp, 0.28F, 0.52F);
      }
   }

   // ======================================================================================================== wings, tails

   private void buildFlyingSurfaces() {
      for (int side = 0; side < 2; side++) {
         boolean m = side == 1;
         float sx = m ? -1 : 1;
         int wing = m ? WING_R : WING_L, flap = m ? FLAPERON_R : FLAPERON_L, ail = m ? AILERON_R : AILERON_L;
         int fin = m ? FIN_R : FIN_L, rud = m ? RUDDER_R : RUDDER_L, stab = m ? STAB_R : STAB_L;

         // ---- main wing (local: X span outwards, Z chord towards the leading edge, origin = leading edge root)
         Part wg = this.add(wing, m ? "wing-r" : "wing-l", b -> {
            b.airfoil(PANEL, ARMOR, p(0, 0), p(0.72F, -0.648F), p(0.72F, -0.74F), p(0, -0.79F), 0.055F, 0.02F, 0.35F);
            b.airfoil(ACCENT, ACCENT, p(0.60F, -0.54F), p(0.72F, -0.648F), p(0.72F, -0.74F), p(0.60F, -0.77F), 0.032F, 0.026F, 0.35F);
            // a raised accent stripe along the leading edge, and the root hinge rod
            b.airfoil(ACCENT, ACCENT, p(0.03F, -0.03F), p(0.58F, -0.525F), p(0.58F, -0.565F), p(0.03F, -0.085F), 0.06F, 0.034F, 0.3F);
            b.prism(SILVER, 2, 8, 0.0F, 0.0F, -0.76F, -0.02F, 0.022F, 0.022F);
         }, m);
         // robot: stands out from the upper back, swept back and out, leading edge on top
         Vector3f spanDir = new Vector3f(0.788F, 0, -0.616F);
         Vector3f up = new Vector3f(0, 1, 0);
         float beta = rad(58.0F);
         Vector3f xr = new Vector3f(up).mul((float)Math.sin(beta)).add(new Vector3f(spanDir).mul((float)Math.cos(beta)));
         Vector3f zr = new Vector3f(up).mul((float)Math.cos(beta)).sub(new Vector3f(spanDir).mul((float)Math.sin(beta)));
         Quaternionf wingRobot = basis(xr, zr);
         this.robotAbs(wing, BACKPACK, new Vector3f(0.10F * sx, 1.71F, -0.19F), m ? mirror(wingRobot) : wingRobot);
         this.jetAbs(wing, -1, new Vector3f(0.30F * sx, -0.005F, 0.25F), new Quaternionf());
         window(wg, 0.34F, 0.76F);
         wg.lock = true;
         wg.arc.set(0.18F * sx, 0.30F, -0.05F);

         Part fl = this.add(flap, m ? "flaperon-r" : "flaperon-l", b -> {
            b.airfoil(PANEL, ARMOR, p(0, 0), p(0.36F, 0.025F), p(0.36F, -0.04F), p(0, -0.13F), 0.03F, 0.02F, 0.15F);
         }, m);
         this.robotLocal(flap, wing, new Vector3f(0, 0, -0.79F), new Quaternionf());
         this.jetLocal(flap, wing, new Vector3f(0, 0, -0.79F), new Quaternionf());
         window(fl, 0.34F, 0.76F);

         Part al = this.add(ail, m ? "aileron-r" : "aileron-l", b -> {
            b.airfoil(PANEL, ARMOR, p(0, 0), p(0.30F, 0.0208F), p(0.30F, 0.0136F), p(0, -0.0614F), 0.022F, 0.012F, 0.2F);
         }, m);
         this.robotLocal(ail, wing, new Vector3f(0.38F * sx, 0, -0.7636F), new Quaternionf());
         this.jetLocal(ail, wing, new Vector3f(0.38F * sx, 0, -0.7636F), new Quaternionf());
         window(al, 0.34F, 0.76F);

         // ---- vertical tail (local X = span), folded on the outer thigh as a hip blade in robot mode
         Part fn = this.add(fin, m ? "fin-r" : "fin-l", b -> {
            b.airfoil(ARMOR, ARMOR, p(0, 0), p(0.34F, -0.14F), p(0.34F, -0.31F), p(0, -0.31F), 0.035F, 0.015F, 0.4F);
            b.airfoil(ACCENT, ACCENT, p(0.27F, -0.111F), p(0.34F, -0.14F), p(0.34F, -0.31F), p(0.27F, -0.31F), 0.024F, 0.02F, 0.4F);
         }, m);
         Quaternionf finRobot = basis(new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
         Quaternionf finJet = basis(new Vector3f(0.454F, 0.891F, 0), new Vector3f(0, 0, 1));
         this.robotAbs(fin, m ? THIGH_R : THIGH_L, new Vector3f(0.258F * sx, 0.95F, 0.09F), m ? mirror(finRobot) : finRobot);
         this.jetAbs(fin, -1, new Vector3f(0.25F * sx, 0.10F, -0.60F), m ? mirror(finJet) : finJet);
         window(fn, 0.52F, 0.84F);
         fn.lock = true;
         fn.arc.set(0.10F * sx, 0.14F, 0);

         Part rd = this.add(rud, m ? "rudder-r" : "rudder-l", b -> {
            b.airfoil(PANEL, PANEL, p(0, 0), p(0.28F, 0), p(0.28F, -0.016F), p(0, -0.09F), 0.02F, 0.01F, 0.2F);
         }, m);
         this.robotLocal(rud, fin, new Vector3f(0, 0, -0.31F), new Quaternionf());
         this.jetLocal(rud, fin, new Vector3f(0, 0, -0.31F), new Quaternionf());
         window(rd, 0.52F, 0.84F);

         // ---- horizontal stabiliser, folded flat on the outer shin (span pointing forward) in robot mode
         Part st = this.add(stab, m ? "stab-r" : "stab-l", b -> {
            b.airfoil(PANEL, ARMOR, p(0, 0), p(0.28F, -0.25F), p(0.28F, -0.37F), p(0, -0.44F), 0.03F, 0.012F, 0.35F);
            b.airfoil(ACCENT, ACCENT, p(0.22F, -0.196F), p(0.28F, -0.25F), p(0.28F, -0.37F), p(0.22F, -0.385F), 0.02F, 0.016F, 0.35F);
         }, m);
         Quaternionf stabRobot = basis(new Vector3f(0, 0, 1), new Vector3f(0, 1, 0));
         this.robotAbs(stab, m ? SHIN_R : SHIN_L, new Vector3f(0.268F * sx, 0.53F, -0.11F), m ? mirror(stabRobot) : stabRobot);
         this.jetAbs(stab, -1, new Vector3f(0.27F * sx, -0.02F, -0.86F), new Quaternionf());
         window(st, 0.58F, 0.86F);
         st.lock = true;
         st.arc.set(0.10F * sx, 0, 0);
      }
   }

   private static float[] p(float x, float z) {
      return new float[]{x, z};
   }

   // ======================================================================================================== gear

   private void buildGear() {
      Part nose = this.add(NOSE_GEAR, "nose-gear", b -> {
         b.prism(SILVER, 1, 6, 0, 0, -0.30F, 0.0F, 0.013F, 0.013F);
         b.prism(DARK, 0, 10, -0.30F, 0.0F, -0.022F, 0.022F, 0.04F, 0.04F);
         b.prism(SILVER, 0, 6, -0.30F, 0.0F, -0.026F, 0.026F, 0.014F, 0.014F);
         b.box(PANEL, -0.03F, -0.05F, -0.01F, 0.03F, 0.0F, 0.01F);
      }, false);
      this.robotLocal(NOSE_GEAR, CHEST, new Vector3f(0, 0.22F, 0.0F), euler(90, 0, 0));
      nose.robotScale = 0.3F;
      this.jetAbs(NOSE_GEAR, NOSE_BASE, new Vector3f(0, -0.06F, 0.86F), new Quaternionf());
      window(nose, 0.40F, 0.70F);

      for (int side = 0; side < 2; side++) {
         boolean m = side == 1;
         int gear = m ? GEAR_R : GEAR_L;
         Part g = this.add(gear, m ? "gear-r" : "gear-l", b -> {
            b.prism(SILVER, 1, 6, 0, 0, -0.26F, 0.0F, 0.017F, 0.017F);
            b.prism(DARK, 0, 12, -0.29F, 0.0F, -0.025F, 0.025F, 0.055F, 0.055F);
            b.prism(SILVER, 0, 6, -0.29F, 0.0F, -0.03F, 0.03F, 0.02F, 0.02F);
            b.box(PANEL, -0.012F, -0.20F, 0.02F, 0.012F, 0.0F, 0.05F);
         }, m);
         this.robotLocal(gear, m ? THIGH_R : THIGH_L, new Vector3f(0, -0.20F, 0.0F), euler(90, 0, 0));
         g.robotScale = 0.3F;
         this.jetLocal(gear, m ? THIGH_R : THIGH_L, new Vector3f((m ? -1 : 1) * 0.05F, -0.20F, 0.12F), euler(-90, 0, 0));
         window(g, 0.40F, 0.70F);
      }
   }

   /** All parts in an order where every parent comes before its children (in both hierarchies). */
   public int[] order() {
      List<Integer> out = new ArrayList<>();
      boolean[] done = new boolean[COUNT];
      while (out.size() < COUNT) {
         boolean progressed = false;
         for (int i = 0; i < COUNT; i++) {
            if (done[i]) continue;
            Part p = this.parts[i];
            boolean ready = (p.robotParent < 0 || done[p.robotParent]) && (p.jetParent < 0 || done[p.jetParent]);
            if (ready) {
               done[i] = true;
               out.add(i);
               progressed = true;
            }
         }
         if (!progressed) throw new IllegalStateException("mech part hierarchy has a loop");
      }
      return out.stream().mapToInt(Integer::intValue).toArray();
   }
}
