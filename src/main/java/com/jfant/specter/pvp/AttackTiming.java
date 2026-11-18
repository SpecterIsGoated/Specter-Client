package com.jfant.specter.pvp;

public final class AttackTiming {
   private int target = -1;
   private long reactedAt;
   private long chargedAt = -1L;
   private int chargePause;

   public void see(int id, long tick, int reactionTicks) {
      if (id != this.target) {
         this.target = id;
         this.reactedAt = tick + Math.max(0, reactionTicks);
      }
   }

   public void lose() {
      this.target = -1;
   }

   public int target() {
      return this.target;
   }

   public boolean ready(long tick, float cooldown, int pauseTicks) {
      if (this.target == -1) {
         return false;
      } else if (cooldown < 1.0F) {
         this.chargedAt = -1L;
         return false;
      } else {
         if (this.chargedAt < 0L) {
            this.chargedAt = tick;
            this.chargePause = Math.max(0, pauseTicks);
         }

         return tick >= this.reactedAt && tick >= this.chargedAt + this.chargePause;
      }
   }

   public long chargedFor(long tick) {
      return this.chargedAt < 0L ? -1L : tick - this.chargedAt;
   }

   public void attacked() {
      this.chargedAt = -1L;
   }
}
