package com.jfant.specter.phone.core;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where the phone is, in view space (camera at the origin looking down -Z, Y up), for every frame of taking it out
 * and putting it away. Sampled once per frame ({@link #frame}) before the GUI and the hand are drawn, so the 3D
 * phone and the interface drawn over its screen agree exactly.
 *
 * <p>Model units are millimetres of a real 72 x 150 mm phone; {@link #UNIT} converts them to view space. At rest the
 * screen faces the camera square on, which is what lets the 2D interface sit pixel-exactly on the 3D glass.
 */
public final class PhoneAnimationController {
   public enum State {
      CLOSED,
      OPENING,
      OPEN,
      CLOSING
   }

   // ---- the device (millimetres)
   public static final float BODY_W = 72.0F;
   public static final float BODY_H = 150.0F;
   public static final float BODY_D = 7.8F;
   public static final float BODY_R = 10.5F;
   public static final float SCREEN_W = 65.4F;
   public static final float SCREEN_H = 142.4F;
   public static final float SCREEN_R = 7.2F;
   /** View-space distance of the phone at rest and its height there (before the scale setting). */
   private static final float REST_DISTANCE = 0.42F;
   private static final float REST_HEIGHT = 0.47F;
   public static final float UNIT = REST_HEIGHT / BODY_H;

   private static final float OPEN_SECONDS = 0.46F;
   private static final float CLOSE_SECONDS = 0.34F;
   private static final float HOLD_SECONDS = 0.09F;
   private static final float UI_FADE_SECONDS = 0.13F;

   private State state = State.CLOSED;
   /** 0 = in the pocket, 1 = held up. */
   private float t;
   private double stateSince;
   private double lastFrame = -1;
   private float uiAlpha;
   private float speed = 1.0F;

   // settings (from the module)
   private float scale = 1.0F;
   private float offsetX;
   private float offsetY;
   private boolean leftHanded;

   /** Phone model (mm) to view space, this frame. */
   public final Matrix4f model = new Matrix4f();
   private final Quaternionf restRot = new Quaternionf();
   private final Quaternionf pocketRot = new Quaternionf();
   private final Quaternionf rot = new Quaternionf();
   private final Vector3f pos = new Vector3f();

   public State state() {
      return this.state;
   }

   public boolean visible() {
      return this.state != State.CLOSED;
   }

   /** 0..1 how far out the phone is. */
   public float progress() {
      return this.t;
   }

   /** How bright the 3D screen is (it switches on while coming up and off while going down). */
   public float screenOn() {
      if (this.state == State.CLOSING) return smooth(0.45F, 0.92F, this.t);
      return smooth(0.18F, 0.62F, this.t);
   }

   /** Opacity of the interface over the glass: only once the phone has come to rest (then a quick fade). */
   public float uiAlpha() {
      return this.uiAlpha;
   }

   /** The interface may take input (fully out, not leaving). */
   public boolean interactive() {
      return this.state == State.OPEN;
   }

   public void configure(float scale, float offsetX, float offsetY, float speed, boolean leftHanded) {
      this.scale = scale;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.speed = Math.max(0.1F, speed);
      this.leftHanded = leftHanded;
   }

   public void open(double now) {
      if (this.state == State.OPEN || this.state == State.OPENING) return;
      this.state = State.OPENING;
      this.stateSince = now - this.t * OPEN_SECONDS / this.speed;
   }

   public void close(double now) {
      if (this.state == State.CLOSED || this.state == State.CLOSING) return;
      boolean fromRest = this.state == State.OPEN;
      this.state = State.CLOSING;
      // From rest: hold still while the interface fades, then go. Mid-way: reverse from where it is.
      this.stateSince = fromRest ? now : now - HOLD_SECONDS / this.speed - (1 - this.t) * CLOSE_SECONDS / this.speed;
   }

   public void snapClosed() {
      this.state = State.CLOSED;
      this.t = 0;
      this.uiAlpha = 0;
   }

   /** Advances the animation to {@code now} (seconds) and rebuilds the model matrix. Idempotent within a frame. */
   public void frame(double now) {
      if (now == this.lastFrame) return;
      this.lastFrame = now;
      double since = now - this.stateSince;
      switch (this.state) {
         case OPENING -> {
            this.t = (float)Math.min(1.0, since * this.speed / OPEN_SECONDS);
            this.uiAlpha = 0;
            if (this.t >= 1) {
               this.state = State.OPEN;
               this.stateSince = now;
            }
         }
         case OPEN -> {
            this.t = 1;
            this.uiAlpha = (float)Math.min(1.0, since * this.speed / UI_FADE_SECONDS);
         }
         case CLOSING -> {
            double hold = HOLD_SECONDS / this.speed;
            this.uiAlpha = (float)Math.max(0.0, 1.0 - since / hold);
            this.t = since < hold ? 1 : (float)Math.max(0.0, 1.0 - (since - hold) * this.speed / CLOSE_SECONDS);
            if (this.t <= 0) {
               this.state = State.CLOSED;
               this.t = 0;
            }
         }
         default -> {
            this.t = 0;
            this.uiAlpha = 0;
         }
      }
      this.buildModel();
   }

   private void buildModel() {
      float side = this.leftHanded ? -1 : 1;
      boolean opening = this.state != State.CLOSING;
      // position follows an eased arc, rotation settles with a touch of overshoot when coming up
      float tp = opening ? outCubic(this.t) : inOutCubic(this.t);
      float tr = opening ? softBack(this.t) : inOutCubic(this.t);
      float restX = this.offsetX * 0.16F, restY = -0.02F + this.offsetY * 0.12F;
      float restZ = -REST_DISTANCE;
      float pocketX = restX + 0.20F * side, pocketY = -0.66F, pocketZ = -0.34F;
      this.pos.set(lerp(pocketX, restX, tp), lerp(pocketY, restY, tp) + 0.05F * (float)Math.sin(Math.PI * tp), lerp(pocketZ, restZ, tp));
      this.restRot.identity();
      this.pocketRot.identity()
         .rotateY((float)Math.toRadians(-28.0 * side))
         .rotateX((float)Math.toRadians(-72.0))
         .rotateZ((float)Math.toRadians(20.0 * side));
      this.pocketRot.slerp(this.restRot, Math.clamp(tr, 0.0F, 1.08F), this.rot);
      float s = UNIT * this.scale;
      this.model.identity().translate(this.pos).rotate(this.rot).scale(s);
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float outCubic(float t) {
      float u = 1 - t;
      return 1 - u * u * u;
   }

   private static float inOutCubic(float t) {
      return t < 0.5F ? 4 * t * t * t : 1 - (float)Math.pow(-2 * t + 2, 3) / 2;
   }

   /** Ease out with a slight overshoot that settles back to 1. */
   private static float softBack(float t) {
      float c1 = 1.25F, c3 = c1 + 1;
      float u = t - 1;
      return 1 + c3 * u * u * u + c1 * u * u;
   }

   private static float smooth(float edge0, float edge1, float x) {
      float k = Math.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
      return k * k * (3 - 2 * k);
   }
}
