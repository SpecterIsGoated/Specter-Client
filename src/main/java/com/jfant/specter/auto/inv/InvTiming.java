package com.jfant.specter.auto.inv;

import java.util.concurrent.ThreadLocalRandom;

public final class InvTiming {
   private boolean randomize = true;
   private double variation = 0.25;
   private double lastMs = -1.0;

   public InvTiming configure(boolean randomize, double variationPercent) {
      this.randomize = randomize;
      this.variation = Math.max(0.0, Math.min(1.0, variationPercent / 100.0));
      return this;
   }

   public boolean randomizing() {
      return this.randomize;
   }

   public double nextMs(double baseMs, double budgetMs) {
      double base = Math.max(0.0, baseMs);
      double cap = budgetMs < 0.0 ? 1.7976931348623157E308 : Math.max(0.0, budgetMs);
      if (this.randomize && !(this.variation <= 0.0) && !(base <= 0.0)) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         double spread = base * this.variation;
         double low = Math.max(0.0, base - spread);
         double high = Math.min(cap, base + spread);
         if (high < low) {
            low = high;
         }

         double gap = Math.max(1.0, base * 0.03);
         double value = 0.0;

         for (int i = 0; i < 4; i++) {
            double hump = (random.nextDouble() + random.nextDouble()) / 2.0;
            value = low + (high - low) * hump;
            if (this.lastMs < 0.0 || Math.abs(value - this.lastMs) >= gap) {
               break;
            }
         }

         if (this.lastMs >= 0.0 && Math.abs(value - this.lastMs) < gap) {
            double up = this.lastMs + gap;
            value = up <= high ? up : Math.max(0.0, this.lastMs - gap);
         }

         value = Math.max(0.0, Math.min(cap, value));
         this.lastMs = value;
         return value;
      } else {
         double fixed = Math.min(base, cap);
         this.lastMs = fixed;
         return fixed;
      }
   }

   public long nextNanos(double baseMs, double budgetMs) {
      return Math.round(this.nextMs(baseMs, budgetMs) * 1000000.0);
   }

   public double rangeMs(double lowMs, double highMs, double tempo, double budgetMs) {
      double middle = (lowMs + highMs) / 2.0 * tempo;
      double half = (highMs - lowMs) / 2.0 * tempo;
      double saved = this.variation;
      this.variation = middle <= 0.0 ? 0.0 : Math.min(1.0, half / middle * (saved / 0.25));

      double var15;
      try {
         var15 = this.nextMs(middle, budgetMs);
      } finally {
         this.variation = saved;
      }

      return var15;
   }

   public void reset() {
      this.lastMs = -1.0;
   }
}
