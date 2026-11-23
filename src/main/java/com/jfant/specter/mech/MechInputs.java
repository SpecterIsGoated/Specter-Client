package com.jfant.specter.mech;

/**
 * Everything the pose needs for one frame, already interpolated to the frame's partial tick. Plain fields so the
 * per-player object is reused every frame (no allocation while rendering).
 */
public final class MechInputs {
   /** Transformation progress: 0 = robot, 1 = aircraft. */
   public float transform;
   /** Whether the transformation is heading towards the aircraft (true) or back to the robot. */
   public boolean forward = true;
   /** Overall size (model scale setting x the entity's own scale). */
   public float scale = 1.0F;
   /** Seconds, for idle motion. */
   public float time;

   // ---- robot
   public float bodyYaw;
   public float headYaw;
   public float headPitch;
   public float walkPos;
   public float walkSpeed;
   public float sprint;
   public float crouch;
   public float sit;
   public float swim;
   /** 0 = on the ground, 1 = fully airborne (jumping or falling, not gliding). */
   public float air;
   /** Vertical speed, blocks per tick (positive = rising). */
   public float vy;
   /** Landing squash, 1 right at touchdown, decaying to 0. */
   public float landing;
   /** Main arm attack swing, 0..1. */
   public float swing;
   public boolean rightHanded = true;
   public boolean holdingRight;
   public boolean holdingLeft;
   public boolean aiming;
   public boolean blocking;
   /** Body yaw speed in degrees per second (turning in place). */
   public float turnRate;
   /** Little shuffle steps while turning in place: phase (radians) and strength. */
   public float turnPhase;
   public float turnStep;
   /** 0..1 death tip-over. */
   public float death;

   // ---- aircraft
   public float heading;
   public float pitch;
   public float roll;
   /** Height of the aircraft's centre above the entity's feet. */
   public float jetY = 0.32F;
   /** Control surface commands, -1..1. */
   public float aileron;
   public float elevator;
   public float rudder;
   /** Landing gear: 0 = up, 1 = down. */
   public float gear;
   /** Afterburner, 0..1. */
   public float boost;
   /** Flight animation enabled (off = rigid aircraft that just points where it flies). */
   public boolean flightAnim = true;
}
