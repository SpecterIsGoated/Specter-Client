package com.jfant.specter.render;

public final class Facing {
   private static final String[] NAMES = new String[]{"South", "Southwest", "West", "Northwest", "North", "Northeast", "East", "Southeast"};
   private static final String[] AXES = new String[]{"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};

   private Facing() {
   }

   private static int index(float yaw) {
      return Math.floorMod(Math.round(yaw / 45.0F), 8);
   }

   public static String name(float yaw) {
      return NAMES[index(yaw)];
   }

   public static String axes(float yaw) {
      return AXES[index(yaw)];
   }
}
