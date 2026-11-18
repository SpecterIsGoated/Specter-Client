package com.jfant.specter.render;

public enum Corner {
   TopLeft("Top Left", false, false, false, false),
   TopRight("Top Right", true, false, false, false),
   BottomLeft("Bottom Left", false, true, false, false),
   BottomRight("Bottom Right", true, true, false, false),
   MiddleLeft("Middle Left", false, false, true, false),
   TopCenter("Top Center", false, false, false, true),
   BottomCenter("Bottom Center", false, true, false, true);

   private final String title;
   public final boolean right;
   public final boolean bottom;
   public final boolean middle;
   public final boolean center;

   private Corner(String title, boolean right, boolean bottom, boolean middle, boolean center) {
      this.title = title;
      this.right = right;
      this.bottom = bottom;
      this.middle = middle;
      this.center = center;
   }

   public static Corner of(boolean right, boolean bottom) {
      if (right) {
         return bottom ? BottomRight : TopRight;
      } else {
         return bottom ? BottomLeft : TopLeft;
      }
   }

   public static Corner centered(boolean bottom) {
      return bottom ? BottomCenter : TopCenter;
   }

   public float x(int screenWidth, float width, int offset) {
      return this.center
         ? onScreen((screenWidth - width) / 2.0F + offset, screenWidth, width)
         : onScreen(this.right ? screenWidth - width - offset : offset, screenWidth, width);
   }

   public float y(int screenHeight, float height, int offset) {
      return this.middle
         ? onScreen((screenHeight - height) / 2.0F + offset, screenHeight, height)
         : onScreen(this.bottom ? screenHeight - height - offset : offset, screenHeight, height);
   }

   private static float onScreen(float position, int screen, float size) {
      return Math.clamp(position, 0.0F, Math.max(0.0F, screen - size));
   }

   @Override
   public String toString() {
      return this.title;
   }
}
