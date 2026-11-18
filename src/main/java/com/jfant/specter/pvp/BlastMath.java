package com.jfant.specter.pvp;

public final class BlastMath {
   public static final float CRYSTAL_POWER = 6.0F;
   public static final float ANCHOR_POWER = 5.0F;
   public static final float SELF_SCALE = 1.15F;
   public static final float SELF_MARGIN = 2.0F;

   private BlastMath() {
   }

   public static float raw(float power, double distance, double exposure) {
      double reach = power * 2.0;
      double share = distance / reach;
      if (!(share > 1.0) && !(exposure <= 0.0)) {
         double impact = (1.0 - share) * exposure;
         return (float)((impact * impact + impact) / 2.0 * 7.0 * reach + 1.0);
      } else {
         return 0.0F;
      }
   }

   public static float difficulty(float damage, int difficulty) {
      return switch (difficulty) {
         case 0 -> 0.0F;
         case 1 -> Math.min(damage / 2.0F + 1.0F, damage);
         default -> damage;
         case 3 -> damage * 1.5F;
      };
   }

   public static float armor(float damage, float armor, float toughness) {
      float f = 2.0F + toughness / 4.0F;
      float g = Math.max(armor * 0.2F, Math.min(armor - damage / f, 20.0F));
      return damage * (1.0F - g / 25.0F);
   }

   public static float protection(float damage, float points) {
      float f = Math.max(0.0F, Math.min(points, 20.0F));
      return damage * (1.0F - f / 25.0F);
   }

   public static boolean safe(float damage, float maxSelf, boolean antiSuicide, float healthWithAbsorption) {
      return damage > maxSelf ? false : !antiSuicide || damage * 1.15F + 2.0F < healthWithAbsorption;
   }
}
