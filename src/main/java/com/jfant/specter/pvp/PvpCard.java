package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.PurpleCard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

public final class PvpCard {
   public static final int GOOD = -9705867;
   public static final int WARN = -22016;
   public static final int ALARM = -43691;
   public static final int ABSORPTION = -865972;
   public static final int BADGE_TEXT = -15069440;
   private static final int HEALTH_BAR = -2078648;
   public static final int TRACK = -1945500652;
   public static final int PAD = 5;
   public static final int LINE = 12;
   public static final int ICON = 8;
   public static final int BAR = 5;
   public static final EquipmentSlot[] ARMOUR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   public static final int LEFT_OF_HOTBAR = 123;
   public static final int RIGHT_OF_HOTBAR = 94;

   public static int startWidth(int width) {
      return (int)Math.round(width * 0.65);
   }

   private PvpCard() {
   }

   public static boolean visible() {
      return SpecterCore.mc.player != null
         && SpecterCore.mc.level != null
         && !SpecterCore.mc.gui.hud.isHidden()
         && !SpecterCore.mc.debugEntries.isOverlayVisible();
   }

   public static void begin(GuiGraphicsExtractor ctx, CardSpot spot, double scale, int width, int innerHeight) {
      int height = innerHeight + 10;
      float sc = (float)scale;
      spot.place(width * sc, height * sc);
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(Math.round(spot.x()), Math.round(spot.y()));
      matrices.scale(sc, sc);
      PurpleCard.card(ctx, 0, 0, width, height);
      matrices.translate(5.0F, 5.0F);
   }

   public static void end(GuiGraphicsExtractor ctx) {
      ctx.pose().popMatrix();
   }

   public static void healthText(GuiGraphicsExtractor ctx, LivingEntity entity, int right, int y) {
      float absorption = entity.getAbsorptionAmount();
      String extra = absorption > 0.0F ? String.format("+%.0f", absorption) : "";
      String health = String.format("%.1f", entity.getHealth());
      int x = right - CardText.width(extra);
      if (!extra.isEmpty()) {
         CardText.draw(ctx, extra, (float)x, (float)y, -865972, true);
      }

      x -= CardText.width(health) + (extra.isEmpty() ? 0 : 3);
      CardText.draw(ctx, health, (float)x, (float)y, healthColor(entity.getHealth()), true);
   }

   public static void healthRow(GuiGraphicsExtractor ctx, LivingEntity entity, int y, int inner) {
      int textRoom = CardText.width("20.0") + CardText.width("+16") + 7;
      bar(ctx, 0, y + 2, inner - textRoom, entity);
      healthText(ctx, entity, inner, y);
   }

   public static void bar(GuiGraphicsExtractor ctx, int x, int y, int width, LivingEntity entity) {
      float max = Math.max(1.0F, entity.getMaxHealth());
      int red = Math.round(width * Math.min(1.0F, entity.getHealth() / max));
      int gold = Math.min(width - red, Math.round(width * entity.getAbsorptionAmount() / max));
      PurpleCard.fillRounded(ctx, x, y, x + width, y + 5, 2, 15, -1945500652, -1945500652);
      if (red > 0) {
         ctx.fill(x, y, x + red, y + 5, -2078648);
      }

      if (gold > 0) {
         ctx.fill(x + red, y, x + red + gold, y + 5, -865972);
      }
   }

   public static int word(GuiGraphicsExtractor ctx, String text, int x, int color) {
      CardText.draw(ctx, text, (float)x, 0.0F, color, true);
      return x + CardText.width(text) + 4;
   }

   public static int badgeWidth(String text) {
      return CardText.width(text) + 6;
   }

   public static void badge(GuiGraphicsExtractor ctx, String text, int right, int y, int fill, int textColor) {
      int left = right - badgeWidth(text);
      PurpleCard.fillRounded(ctx, left, y - 1, right, y + 9, 2, 15, fill, fill);
      CardText.draw(ctx, text, (float)(left + 3), (float)y, textColor, false);
   }

   public static void item(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(x, y);
      matrices.scale(0.5F, 0.5F);
      ctx.item(stack, 0, 0);
      matrices.popMatrix();
   }

   public static int durability(ItemStack stack) {
      return !stack.isDamageableItem() ? -1 : Math.round(100.0F * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
   }

   public static int durabilityBar(int percent) {
      return percent > 50 ? -9705867 : (percent > 20 ? -865972 : -43691);
   }

   public static int durabilityText(int percent) {
      return percent > 50 ? -1 : (percent > 20 ? -22016 : -43691);
   }

   public static int healthColor(float health) {
      return health > 14.0F ? -9705867 : (health > 7.0F ? -22016 : -43691);
   }
}
