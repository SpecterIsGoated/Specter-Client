package com.jfant.specter.region;

public final class DonutRegions {
   public static final int SIZE = 9;
   public static final int REGION_BLOCKS = 50000;
   public static final int HALF_EXTENT = 225000;
   private static final int[] IDS = new int[]{
      82,
      100,
      101,
      102,
      103,
      104,
      105,
      106,
      91,
      83,
      44,
      75,
      42,
      41,
      40,
      39,
      38,
      92,
      84,
      45,
      14,
      13,
      12,
      11,
      10,
      37,
      93,
      85,
      46,
      74,
      3,
      2,
      1,
      25,
      36,
      94,
      86,
      47,
      72,
      71,
      5,
      4,
      24,
      35,
      95,
      87,
      51,
      17,
      9,
      8,
      7,
      23,
      34,
      96,
      88,
      54,
      18,
      61,
      62,
      21,
      22,
      33,
      97,
      89,
      26,
      27,
      28,
      29,
      30,
      59,
      32,
      98,
      90,
      107,
      108,
      109,
      110,
      111,
      112,
      113,
      99
   };
   private static final int[] GROUPS = new int[]{
      5,
      3,
      3,
      3,
      2,
      2,
      2,
      2,
      2,
      5,
      3,
      3,
      3,
      2,
      2,
      2,
      2,
      2,
      5,
      3,
      3,
      3,
      2,
      2,
      2,
      2,
      2,
      5,
      5,
      5,
      3,
      2,
      2,
      2,
      2,
      2,
      4,
      4,
      4,
      4,
      2,
      2,
      2,
      2,
      2,
      4,
      1,
      1,
      0,
      0,
      0,
      0,
      0,
      2,
      4,
      1,
      1,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      1,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      1,
      1,
      1,
      1,
      1,
      1,
      1,
      0
   };
   private static final DonutRegions.Group[] GROUP_VALUES = DonutRegions.Group.values();

   private DonutRegions() {
   }

   public static int cell(double coordinate) {
      return (int)Math.floor((coordinate + 225000.0) / 50000.0);
   }

   public static boolean onMap(int column, int row) {
      return column >= 0 && column < 9 && row >= 0 && row < 9;
   }

   public static int id(int column, int row) {
      return IDS[row * 9 + column];
   }

   public static DonutRegions.Group group(int column, int row) {
      return GROUP_VALUES[GROUPS[row * 9 + column]];
   }

   public static DonutRegions.Tag tag(int column, int row) {
      return switch (id(column, row)) {
         case 2, 7, 28, 29, 32, 34, 59, 86, 88, 98, 99, 109, 112, 113 -> DonutRegions.Tag.OTHER_BASES;
         case 4, 5, 8, 30 -> DonutRegions.Tag.MEDIA_BALTOP;
         case 89, 90, 97, 101, 103, 104 -> DonutRegions.Tag.NO_RTP;
         default -> null;
      };
   }

   public static enum Group {
      EU_CENTRAL("EU Central", 10473059),
      EU_WEST("EU West", 42595),
      NA_EAST("NA East", 5221866),
      NA_WEST("NA West", 3108538),
      ASIA("Asia", 16106050),
      OCEANIA("Oceania", 16549891);

      public final String title;
      public final int rgb;
      public final int textRgb;

      private Group(String title, int rgb) {
         this.title = title;
         this.rgb = rgb;
         this.textRgb = lighten(rgb, 0.25F);
      }

      private static int lighten(int rgb, float amount) {
         int r = rgb >> 16 & 0xFF;
         int g = rgb >> 8 & 0xFF;
         int b = rgb & 0xFF;
         r += Math.round((255 - r) * amount);
         g += Math.round((255 - g) * amount);
         b += Math.round((255 - b) * amount);
         return r << 16 | g << 8 | b;
      }
   }

   public static enum Tag {
      NO_RTP("No RTP spots", "No RTP spots", "No RTP", 14701138),
      MEDIA_BALTOP("Media/baltop bases", "Media/baltop", "Media", 16106050),
      OTHER_BASES("Other bases", "Other bases", "Bases", 5221866);

      public final String title;
      public final String shortTitle;
      public final String legendTitle;
      public final int rgb;
      public final int textRgb;

      private Tag(String title, String shortTitle, String legendTitle, int rgb) {
         this.title = title;
         this.shortTitle = shortTitle;
         this.legendTitle = legendTitle;
         this.rgb = rgb;
         this.textRgb = DonutRegions.Group.lighten(rgb, 0.25F);
      }
   }
}
