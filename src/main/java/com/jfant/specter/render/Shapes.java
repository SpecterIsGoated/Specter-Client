package com.jfant.specter.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Public, float-precision access to the anti-aliased rounded shapes ({@link SmoothShapes}) for code outside this
 * package (Donut Phone). Coordinates are in the current pose's space; the pose may translate and scale uniformly.
 */
public final class Shapes {
   private Shapes() {
   }

   /** Rounded rectangle, optional vertical gradient. {@code corners} is a mask of PurpleCard.TOP_LEFT etc. */
   public static void fill(GuiGraphicsExtractor ctx, float left, float top, float right, float bottom, float radius, int corners, int topColor, int bottomColor) {
      SmoothShapes.fill(ctx, left, top, right, bottom, radius, corners, topColor, bottomColor);
   }

   /** Rounded outline {@code thickness} real pixels wide, {@code inset} real pixels inside the edge. */
   public static void ring(GuiGraphicsExtractor ctx, float left, float top, float right, float bottom, float radius, int inset, int thickness, int topColor, int bottomColor) {
      SmoothShapes.ring(ctx, left, top, right, bottom, radius, inset, thickness, topColor, bottomColor);
   }

   /** Paints {@code color} outside the rounded corners of the rectangle (rounds off whatever is drawn underneath). */
   public static void mask(GuiGraphicsExtractor ctx, float left, float top, float right, float bottom, float radius, int color) {
      SmoothShapes.mask(ctx, left, top, right, bottom, radius, color);
   }
}
