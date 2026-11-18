package com.jfant.specter.macro;

import java.util.concurrent.ThreadLocalRandom;

public final class Pace {
   public static final double GAP_MEDIAN_MS = 170.0;
   public static final double GAP_SIGMA = 0.35;
   public static final double GAP_FLOOR_MS = 50.0;
   public static final double GAP_CEILING_MS = 2400.0;
   private static final double DRIFT_STEP = 0.012;
   private static final double DRIFT_MIN = 0.8;
   private static final double DRIFT_MAX = 1.6;
   private static final double FATIGUE_PER_MINUTE = 0.004;
   private static final double FATIGUE_MAX = 1.35;
   private static final double START_DELAY_MEDIAN_MS = 3200.0;
   private static final double START_DELAY_SIGMA = 0.35;
   private double drift = 1.0;
   private long startedAt = System.currentTimeMillis();

   public void begin() {
      this.startedAt = System.currentTimeMillis();
      this.drift = clamp(logNormal(1.0, 0.08), 0.8, 1.6);
   }

   public int startDelay() {
      return (int)clamp(logNormal(3200.0, 0.35), 1200.0, 20000.0);
   }

   public int nextGap() {
      return this.nextGap(170.0);
   }

   public int nextGap(double medianMs) {
      this.drift = clamp(this.drift * Math.exp(0.012 * ThreadLocalRandom.current().nextGaussian()), 0.8, 1.6);
      double minutes = (System.currentTimeMillis() - this.startedAt) / 60000.0;
      double fatigue = Math.min(1.0 + minutes * 0.004, 1.35);
      return (int)clamp(logNormal(medianMs * this.drift * fatigue, 0.35), 50.0, Math.max(2400.0, medianMs * 4.0));
   }

   public static double logNormal(double median, double sigma) {
      return median * Math.exp(sigma * ThreadLocalRandom.current().nextGaussian());
   }

   public static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}
