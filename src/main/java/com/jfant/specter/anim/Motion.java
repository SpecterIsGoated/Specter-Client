package com.jfant.specter.anim;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class Motion {
   public static final double MAX_STEP = 0.1;
   public static final double REDUCED_MILLIS = 90.0;
   private static LongSupplier nanos = System::nanoTime;
   private static long lastNanos;
   private static double now;
   private static double dt;
   private static long frame;
   private static boolean enabled = true;
   private static boolean reduced;
   private static double speed = 1.0;
   private static double intensity = 1.0;
   private static final Map<Channel, Boolean> channels = new EnumMap<>(Channel.class);

   private Motion() {
   }

   public static void frame() {
      long t = nanos.getAsLong();
      if (lastNanos == 0L) {
         lastNanos = t;
      }

      dt = Math.clamp((t - lastNanos) / 1.0E9, 0.0, 0.1);
      lastNanos = t;
      now = now + dt;
      frame++;
      Animator.sweep();
   }

   public static double now() {
      return now;
   }

   public static double dt() {
      return dt;
   }

   public static long frameCount() {
      return frame;
   }

   public static void configure(boolean enabled, double speed, double intensity, boolean reduced) {
      Motion.enabled = enabled;
      Motion.speed = Math.clamp(speed, 0.1, 10.0);
      Motion.intensity = Math.clamp(intensity, 0.0, 2.0);
      Motion.reduced = reduced;
   }

   public static void channel(Channel channel, boolean on) {
      channels.put(channel, on);
   }

   public static boolean plays(Channel channel) {
      return enabled && channels.getOrDefault(channel, true);
   }

   public static boolean reduced() {
      return reduced;
   }

   public static double speed() {
      return speed;
   }

   public static double seconds(Spec spec) {
      if (!plays(spec.channel())) {
         return 0.0;
      } else {
         double millis = spec.millis() / speed;
         if (reduced) {
            millis = Math.min(millis, 90.0);
         }

         return millis / 1000.0;
      }
   }

   public static double amount(Channel channel) {
      return plays(channel) && !reduced ? intensity : 0.0;
   }

   public static double stagger(int index, double millisEach, Channel channel) {
      return plays(channel) && !reduced ? index * millisEach / 1000.0 / speed : 0.0;
   }

   static void clock(LongSupplier source) {
      nanos = source;
      lastNanos = 0L;
      now = 0.0;
      dt = 0.0;
      frame = 0L;
   }

   static void reset() {
      clock(System::nanoTime);
      configure(true, 1.0, 1.0, false);
      channels.clear();
      Animator.clear();
   }
}
