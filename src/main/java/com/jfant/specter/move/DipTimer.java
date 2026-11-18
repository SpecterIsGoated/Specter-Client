package com.jfant.specter.move;

final class DipTimer {
   private int since;
   private int stage;

   void reset() {
      this.since = 0;
      this.stage = 0;
   }

   double next(boolean enabled, int interval, double amount) {
      if (!enabled) {
         this.stage = 0;
         return 0.0;
      } else if (this.stage == 1) {
         this.stage = 2;
         return -amount;
      } else if (this.stage == 2) {
         this.stage = 0;
         return amount;
      } else {
         if (++this.since >= interval) {
            this.since = 0;
            this.stage = 1;
         }

         return 0.0;
      }
   }
}
