package com.jfant.specter.ah;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class Auction {
   public static final int LISTINGS = 45;
   public static final int PREVIOUS_PAGE = 45;
   public static final int NEXT_PAGE = 53;
   private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMBT])?", 2);
   private static final Pattern BOUGHT = Pattern.compile("^You bought (\\d+) (.+?) for \\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMBT])?\\s*$", 2);

   private Auction() {
   }

   public static boolean isListingPage(String title) {
      return title.startsWith("Auction (Page");
   }

   public static boolean isConfirm(String title) {
      return title.toLowerCase(Locale.ROOT).contains("are you sure you want to buy");
   }

   public static double money(String text) {
      Matcher m = MONEY.matcher(text);
      return !m.find() ? -1.0 : scaled(m.group(1), m.group(2));
   }

   public static double price(ItemStack stack) {
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) {
         return -1.0;
      } else {
         for (Component line : lore.lines()) {
            double p = money(line.getString());
            if (p >= 0.0) {
               return p;
            }
         }

         return -1.0;
      }
   }

   public static Auction.Bought bought(String line) {
      Matcher m = BOUGHT.matcher(line.trim());
      return !m.matches() ? null : new Auction.Bought(Integer.parseInt(m.group(1)), m.group(2), scaled(m.group(3), m.group(4)));
   }

   public static boolean alreadyBought(String line) {
      return line.toLowerCase(Locale.ROOT).contains("already bought");
   }

   public static String format(double money) {
      if (money >= 1.0E12) {
         return trim(money / 1.0E12) + "T";
      } else if (money >= 1.0E9) {
         return trim(money / 1.0E9) + "B";
      } else if (money >= 1000000.0) {
         return trim(money / 1000000.0) + "M";
      } else {
         return money >= 1000.0 ? trim(money / 1000.0) + "K" : trim(money);
      }
   }

   public static double parseAmount(String typed) {
      String s = typed.trim().replace(",", "").replace("$", "").trim();
      if (s.isEmpty()) {
         return -1.0;
      } else {
         char last = Character.toUpperCase(s.charAt(s.length() - 1));
         String suffix = Character.isLetter(last) ? String.valueOf(last) : null;
         String number = suffix != null ? s.substring(0, s.length() - 1).trim() : s;

         try {
            return scaled(number, suffix);
         } catch (NumberFormatException var6) {
            return -1.0;
         }
      }
   }

   private static double scaled(String number, String suffix) {
      double v = Double.parseDouble(number.replace(",", ""));
      if (suffix == null) {
         return v;
      } else {
         String var4 = suffix.toUpperCase(Locale.ROOT);

         return switch (var4) {
            case "K" -> v * 1000.0;
            case "M" -> v * 1000000.0;
            case "B" -> v * 1.0E9;
            case "T" -> v * 1.0E12;
            default -> throw new NumberFormatException(suffix);
         };
      }
   }

   private static String trim(double v) {
      String s = String.format(Locale.ROOT, "%.1f", v);
      return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
   }

   public record Bought(int count, String name, double price) {
   }
}
