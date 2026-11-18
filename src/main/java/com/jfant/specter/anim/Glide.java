package com.jfant.specter.anim;

public final class Glide {
   private final Tween offset;
   private final double threshold;
   private double last;
   private boolean started;

   public Glide(Spec spec, double threshold) {
      this.offset = new Tween(spec, 0.0);
      this.threshold = threshold;
   }

   public double follow(double target) {
      if (!this.started) {
         this.started = true;
         this.last = target;
      }

      double jump = target - this.last;
      this.last = target;
      if (Math.abs(jump) > this.threshold) {
         double shown = this.offset.get() - jump;
         this.offset.set(shown).to(0.0);
      }

      return target + this.offset.get();
   }

   public void snap() {
      this.offset.set(0.0);
      this.started = false;
   }

   public boolean moving() {
      return this.offset.moving();
   }
}
