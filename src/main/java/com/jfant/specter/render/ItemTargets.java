package com.jfant.specter.render;

import com.jfant.specter.core.renderer.Renderer3D;
import com.jfant.specter.cosmetic.Draw3D;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Shared by Item ESP and Item Nametags: which preset category a dropped item belongs to, the preset colours, rarity
 * colours, and thick screen-space tracers.
 */
public final class ItemTargets {
   public enum Category {
      Netherite(0xFF7A4CFF),
      Elytra(0xFFB9C7FF),
      Totem(0xFFFFD23F),
      Diamond(0xFF4FE3E8),
      Shulker(0xFFD28CFF),
      Armor(0xFF6FA8FF),
      Weapon(0xFFFF5A5A),
      Custom(0xFF7CFF7C),
      Other(0xFFE0E0E0);

      public final int argb;

      Category(int argb) {
         this.argb = argb;
      }
   }

   private ItemTargets() {
   }

   /** The most specific preset an item falls in (a netherite sword is Netherite, not Weapon). */
   public static Category categorize(ItemStack stack) {
      Item item = stack.getItem();
      if (item == Items.ELYTRA) return Category.Elytra;
      if (item == Items.TOTEM_OF_UNDYING) return Category.Totem;
      if (item instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) return Category.Shulker;
      String path = BuiltInRegistries.ITEM.getKey(item).getPath();
      if (path.contains("netherite") || item == Items.ANCIENT_DEBRIS) return Category.Netherite;
      if (path.contains("diamond")) return Category.Diamond;
      if (isArmor(stack)) return Category.Armor;
      if (isWeapon(stack)) return Category.Weapon;
      return Category.Other;
   }

   public static boolean isArmor(ItemStack stack) {
      var equippable = stack.get(DataComponents.EQUIPPABLE);
      return equippable != null && equippable.slot().isArmor() && stack.getItem() != Items.ELYTRA;
   }

   public static boolean isWeapon(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.has(DataComponents.PIERCING_WEAPON) || stack.getItem() == Items.MACE
         || stack.getItem() == Items.TRIDENT || stack.getItem() == Items.BOW || stack.getItem() == Items.CROSSBOW;
   }

   /** Text colour of an item's rarity, like the tooltip. */
   public static int rarityColor(ItemStack stack) {
      Rarity rarity = stack.getRarity();
      TextColor c = TextColor.fromLegacyFormat(rarity.color());
      return c == null ? 0xFFFFFFFF : 0xFF000000 | c.getValue();
   }

   public static String name(ItemStack stack, boolean upper) {
      String name = stack.getHoverName().getString();
      return upper ? name.toUpperCase(Locale.ROOT) : name;
   }

   /**
    * A line drawn as a camera-facing strip, {@code pixels} wide on screen at every distance (so far tracers do not
    * thin out). Use the no-depth renderer for through-wall tracers.
    */
   public static void thickLine(Renderer3D r, double ax, double ay, double az, double bx, double by, double bz, double pixels, int argbA, int argbB) {
      Minecraft mc = Minecraft.getInstance();
      var cam = mc.gameRenderer.mainCamera().position();
      double perPixel = 2.0 * Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0) / Math.max(1, mc.getWindow().getHeight());
      double dx = bx - ax, dy = by - ay, dz = bz - az;
      double wa = side(dx, dy, dz, ax - cam.x, ay - cam.y, az - cam.z, pixels * perPixel * 0.5, SIDE_A);
      double wb = side(dx, dy, dz, bx - cam.x, by - cam.y, bz - cam.z, pixels * perPixel * 0.5, SIDE_B);
      if (wa < 0 || wb < 0) return;
      Draw3D.quad(r, ax - SIDE_A[0], ay - SIDE_A[1], az - SIDE_A[2], ax + SIDE_A[0], ay + SIDE_A[1], az + SIDE_A[2], bx + SIDE_B[0], by + SIDE_B[1], bz + SIDE_B[2],
         bx - SIDE_B[0], by - SIDE_B[1], bz - SIDE_B[2], argbA, argbA, argbB, argbB);
   }

   private static final double[] SIDE_A = new double[3];
   private static final double[] SIDE_B = new double[3];

   /** Perpendicular to the line and to the view ray at a point, scaled to {@code halfPerUnit * distance}. */
   private static double side(double dx, double dy, double dz, double vx, double vy, double vz, double halfPerUnit, double[] out) {
      double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
      double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
      if (len < 1.0E-9) return -1;
      double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
      double w = halfPerUnit * Math.max(0.05, dist);
      out[0] = sx / len * w;
      out[1] = sy / len * w;
      out[2] = sz / len * w;
      return w;
   }
}
