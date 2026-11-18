package com.jfant.specter.anim;

public record Spec(double millis, Easing easing, Channel channel) {
   public static final Spec HOVER = new Spec(110.0, Easing.OUT_CUBIC, Channel.GUI);
   public static final Spec PRESS = new Spec(90.0, Easing.OUT_QUAD, Channel.GUI);
   public static final Spec TINT = new Spec(140.0, Easing.SMOOTH, Channel.GUI);
   public static final Spec TOGGLE = new Spec(190.0, Easing.STANDARD, Channel.GUI);
   public static final Spec EXPAND = new Spec(240.0, Easing.STANDARD, Channel.GUI);
   public static final Spec COLLAPSE = new Spec(180.0, Easing.STANDARD, Channel.GUI);
   public static final Spec VALUE = new Spec(160.0, Easing.OUT_CUBIC, Channel.GUI);
   public static final Spec REFLOW = new Spec(220.0, Easing.STANDARD, Channel.GUI);
   public static final Spec POP_IN = new Spec(170.0, Easing.ENTER, Channel.GUI);
   public static final Spec POP_OUT = new Spec(110.0, Easing.EXIT, Channel.GUI);
   public static final Spec SCREEN_IN = new Spec(300.0, Easing.ENTER, Channel.GUI);
   public static final Spec SCREEN_OUT = new Spec(170.0, Easing.EXIT, Channel.GUI);
   public static final Spec MODULE = new Spec(420.0, Easing.OUT_CUBIC, Channel.MODULES);
   public static final Spec LIST = new Spec(260.0, Easing.STANDARD, Channel.MODULES);
   public static final Spec TOAST_IN = new Spec(320.0, Easing.ENTER, Channel.HUD);
   public static final Spec TOAST_OUT = new Spec(220.0, Easing.EXIT, Channel.HUD);
   public static final Spec TOAST_MOVE = new Spec(260.0, Easing.STANDARD, Channel.HUD);
   public static final Spec WORLD = new Spec(1400.0, Easing.IN_OUT_SINE, Channel.WORLD);
   public static final Spec WORLD_FADE = new Spec(900.0, Easing.IN_OUT_SINE, Channel.WORLD);
   public static final Spec ZOOM = new Spec(220.0, Easing.OUT_CUBIC, Channel.CAMERA);

   public Spec(double millis, Easing easing, Channel channel) {
      if (millis < 0.0) {
         throw new IllegalArgumentException("millis < 0");
      } else {
         this.millis = millis;
         this.easing = easing;
         this.channel = channel;
      }
   }

   public Spec scaled(double factor) {
      return new Spec(this.millis * factor, this.easing, this.channel);
   }

   public Spec lasting(double millis) {
      return new Spec(millis, this.easing, this.channel);
   }

   public Spec on(Channel channel) {
      return new Spec(this.millis, this.easing, channel);
   }

   public Spec eased(Easing easing) {
      return new Spec(this.millis, easing, this.channel);
   }
}
