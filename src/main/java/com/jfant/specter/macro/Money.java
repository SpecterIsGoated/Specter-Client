package com.jfant.specter.macro;

import com.jfant.specter.ah.Auction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.item.ItemStack;

public final class Money {
   private static final Pattern ANY = Pattern.compile("\\$?[ \\t]*([0-9][0-9,]*(?:\\.[0-9]+)?)[ \\t]*([kmb])?(?![a-z])");

   private Money() {
   }

   public static long parse(String typed) {
      if (typed == null) {
         return -1L;
      } else {
         double value = Auction.parseAmount(typed);
         return value < 0.0 ? -1L : Math.round(value);
      }
   }

   public static List<Double> amounts(String text) {
      List<Double> out = new ArrayList<>();
      if (text == null) {
         return out;
      } else {
         Matcher m = ANY.matcher(Menus.normalize(text));

         while (m.find()) {
            double value = scaled(m.group(1), m.group(2));
            if (value > 0.0) {
               out.add(value);
            }
         }

         return out;
      }
   }

   public static double displayStep(String text) {
      Matcher m = ANY.matcher(Menus.normalize(text));
      if (!m.find()) {
         return 1.0;
      } else {
         String number = m.group(1);
         int dot = number.indexOf(46);
         int decimals = dot < 0 ? 0 : number.length() - dot - 1;
         return Math.max(1.0, scaled("1", m.group(2)) / Math.pow(10.0, decimals));
      }
   }

   public static double listingPrice(ItemStack stack) {
      double best = -1.0;
      List<String> lore = Menus.loreLines(stack);

      for (String line : lore) {
         String clean = Menus.normalize(line);
         if (clean.contains("price") || clean.contains("cost") || clean.contains("buy")) {
            for (double value : amounts(line)) {
               if (value >= 100.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      if (best > 0.0) {
         return best;
      } else {
         for (String linex : lore) {
            if (linex.contains("$")) {
               for (double valuex : amounts(linex)) {
                  if (valuex >= 100.0 && (best < 0.0 || valuex < best)) {
                     best = valuex;
                  }
               }
            }
         }

         return best;
      }
   }

   public static double orderPrice(ItemStack stack) {
      double best = -1.0;
      List<String> lore = Menus.loreLines(stack);

      for (String line : lore) {
         String clean = Menus.normalize(line);
         if (line.contains("$") && (clean.contains("price") || clean.contains("each") || clean.contains("per item") || clean.contains("pay"))) {
            for (double value : amounts(line)) {
               if (value > 0.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      if (best > 0.0) {
         return best;
      } else {
         for (String linex : lore) {
            if (linex.contains("$")) {
               List<Double> values = amounts(linex);
               if (!values.isEmpty()) {
                  return values.getFirst();
               }
            }
         }

         return -1.0;
      }
   }

   public static String exact(long value) {
      if (value >= 1000000000L && value % 1000000000L == 0L) {
         return value / 1000000000L + "b";
      } else if (value >= 1000000L && value % 1000000L == 0L) {
         return value / 1000000L + "m";
      } else if (value >= 1000000L && value % 100000L == 0L) {
         return value / 1000000L + "." + value % 1000000L / 100000L + "m";
      } else {
         return value >= 1000L && value % 1000L == 0L ? value / 1000L + "k" : Long.toString(value);
      }
   }

   public static String shown(double value) {
      return Auction.format(value);
   }

   private static double scaled(String number, String suffix) {
      double value;
      try {
         value = Double.parseDouble(number.replace(",", ""));
      } catch (NumberFormatException var6) {
         return -1.0;
      }

      if (suffix == null) {
         return value;
      } else {
         String e = suffix.toLowerCase(Locale.ROOT);

         return switch (e) {
            case "k" -> value * 1000.0;
            case "m" -> value * 1000000.0;
            case "b" -> value * 1.0E9;
            default -> value;
         };
      }
   }
}
