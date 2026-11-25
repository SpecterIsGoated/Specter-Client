package com.jfant.specter.mech;

/**
 * Material ids used by the mech geometry, and the paint schemes that colour them.
 *
 * <p>Colours are packed RGB (no alpha). The scheme is resolved once per frame into {@link #rgb}, so the vertex loop
 * only does an array lookup.
 */
public final class MechPalette {
   public static final int ARMOR = 0;
   public static final int ACCENT = 1;
   public static final int SILVER = 2;
   public static final int DARK = 3;
   public static final int PANEL = 4;
   public static final int CANOPY = 5;
   public static final int TRIM = 6;
   /** Glowing: visor and chest core. */
   public static final int GLOW = 7;
   /** Glowing: inside of the engine nozzles (colour follows the engine state). */
   public static final int NOZZLE = 8;
   public static final int COUNT = 9;

   public static boolean glows(int mat) {
      return mat == GLOW || mat == NOZZLE;
   }

   public enum Paint {
      Specter("Specter"),
      Raptor("Raptor Gray"),
      Arctic("Arctic"),
      Midnight("Midnight"),
      Custom("Custom");

      private final String title;

      Paint(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   /** Current colours by material id. */
   public final int[] rgb = new int[COUNT];

   public MechPalette() {
      this.set(Paint.Raptor, 0, 0, 0);
   }

   /**
    * @param customArmor  used only for {@link Paint#Custom}
    * @param customAccent used only for {@link Paint#Custom}
    * @param customGlow   used only for {@link Paint#Custom}
    */
   public void set(Paint paint, int customArmor, int customAccent, int customGlow) {
      switch (paint) {
         // F-22 two-tone grey (the darker blotches are the accent), gold-tinted canopy, pale blue visor
         case Raptor -> this.fill(0x80868E, 0x666C74, 0xA8ADB4, 0x2B2F34, 0x747A82, 0x5E4E2C, 0x8E949C, 0xBFE6FF);
         case Arctic -> this.fill(0xD9DEE4, 0x3C7FD6, 0x9AA2AD, 0x2B3038, 0xF0F2F5, 0x1F2C3E, 0x3C7FD6, 0x9FD8FF);
         case Midnight -> this.fill(0x24262B, 0x3A3F48, 0x8D939C, 0x111214, 0x30333A, 0x161B26, 0x5C626C, 0xFF5A3C);
         case Custom -> this.fill(
               customArmor, customAccent, 0xB8BEC8, 0x22252B, lighten(customArmor, 0.18F), 0x241A33, lighten(customAccent, 0.35F), customGlow
            );
         // the same F-22 greys, with Specter's violet visor and chest light
         default -> this.fill(0x80868E, 0x666C74, 0xA8ADB4, 0x2B2F34, 0x747A82, 0x5E4E2C, 0x8E949C, 0xC9A0FF);
      }
   }

   private void fill(int armor, int accent, int silver, int dark, int panel, int canopy, int trim, int glow) {
      this.rgb[ARMOR] = armor;
      this.rgb[ACCENT] = accent;
      this.rgb[SILVER] = silver;
      this.rgb[DARK] = dark;
      this.rgb[PANEL] = panel;
      this.rgb[CANOPY] = canopy;
      this.rgb[TRIM] = trim;
      this.rgb[GLOW] = glow;
      this.rgb[NOZZLE] = 0xFF8A3C;
   }

   static int lighten(int rgb, float f) {
      int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
      r = Math.min(255, (int)(r + (255 - r) * f));
      g = Math.min(255, (int)(g + (255 - g) * f));
      b = Math.min(255, (int)(b + (255 - b) * f));
      return r << 16 | g << 8 | b;
   }
}
