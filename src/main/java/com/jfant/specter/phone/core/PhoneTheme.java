package com.jfant.specter.phone.core;

/**
 * The phone's design tokens. Apps draw with these (never hard-coded colours) so every app follows the active theme
 * and future themes restyle all of them at once. Sizes are in phone points.
 */
public record PhoneTheme(
   String id,
   String name,
   boolean dark,
   int background,
   int surface,
   int surfaceHigh,
   int text,
   int textDim,
   int textFaint,
   int accent,
   int onAccent,
   int accentSoft,
   int separator,
   int success,
   int warning,
   int danger,
   String wallpaper
) {
   // ---- spacing / radii (points)
   public static final float GAP = 8.0F;
   public static final float PAD = 12.0F;
   public static final float RADIUS_S = 6.0F;
   public static final float RADIUS_M = 10.0F;
   public static final float RADIUS_L = 16.0F;
   public static final float ROW = 34.0F;

   // ---- type scale (cap height in points)
   public static final float CAPTION = 4.1F;
   public static final float SMALL = 4.6F;
   public static final float BODY = 5.2F;
   public static final float HEADLINE = 6.2F;
   public static final float TITLE = 8.4F;
   public static final float LARGE = 11.0F;
   public static final float DISPLAY = 24.0F;

   public static final PhoneTheme MIDNIGHT = new PhoneTheme(
      "midnight", "Midnight", true,
      0xFF0E0B16, 0xFF1A1526, 0xFF251E36, 0xFFF4F0FF, 0xFFB3A8CC, 0xFF6F6584,
      0xFF9B6BFF, 0xFFFFFFFF, 0x339B6BFF, 0x1FFFFFFF, 0xFF34D399, 0xFFFBBF24, 0xFFF43F5E,
      "aurora"
   );
   public static final PhoneTheme DAYLIGHT = new PhoneTheme(
      "daylight", "Daylight", false,
      0xFFF3F1F8, 0xFFFFFFFF, 0xFFE9E5F2, 0xFF17131F, 0xFF5D5670, 0xFF9C95AD,
      0xFF7C4DFF, 0xFFFFFFFF, 0x267C4DFF, 0x1A000000, 0xFF059669, 0xFFD97706, 0xFFE11D48,
      "ocean"
   );
   public static final PhoneTheme FROSTING = new PhoneTheme(
      "frosting", "Frosting", true,
      0xFF170C14, 0xFF26131F, 0xFF341A2A, 0xFFFFF0F7, 0xFFE2B4C9, 0xFF8E6577,
      0xFFFF5CA8, 0xFFFFFFFF, 0x33FF5CA8, 0x1FFFFFFF, 0xFF34D399, 0xFFFBBF24, 0xFFFF4D6D,
      "donut"
   );
}
