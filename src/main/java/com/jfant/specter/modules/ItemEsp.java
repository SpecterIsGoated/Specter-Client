package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.ItemListSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.cosmetic.Draw3D;
import com.jfant.specter.cosmetic.Fx;
import com.jfant.specter.render.ItemTargets;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Item ESP: highlights dropped items through walls - a box, a soft glow and a tracer from your crosshair - coloured by
 * what they are. Presets cover netherite, elytras, totems, diamond gear, shulker boxes, armour and weapons; your own
 * list adds anything else. Tracers keep the same on-screen thickness at any distance.
 */
public class ItemEsp extends Module {
   public enum Mode {
      Box,
      Glow,
      Both
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPresets = this.settings.createGroup("Presets");
   private final SettingGroup sgColors = this.settings.createGroup("Colours");
   private final Setting<Mode> mode = this.sgGeneral.add(new EnumSetting.Builder<Mode>().name("highlight").description("How items are marked.").defaultValue(Mode.Both).build());
   private final Setting<Boolean> tracers = this.sgGeneral.add(new BoolSetting.Builder().name("tracers").description("A line from your crosshair to each item.").defaultValue(true).build());
   private final Setting<Double> tracerWidth = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("tracer-thickness")
            .description("Tracer thickness in screen pixels (the same at any distance).")
            .defaultValue(2.0)
            .range(0.5, 8.0)
            .sliderRange(0.5, 6.0)
            .visible(this.tracers::get)
            .build()
      );
   private final Setting<Integer> range = this.sgGeneral
      .add(new IntSetting.Builder().name("render-distance").description("Only items within this many blocks.").defaultValue(96).range(8, 512).sliderRange(16, 256).build());
   private final Setting<Boolean> allItems = this.sgGeneral
      .add(new BoolSetting.Builder().name("all-items").description("Also mark every other dropped item (in the Other colour).").defaultValue(false).build());
   private final Setting<Double> glowStrength = this.sgGeneral
      .add(new DoubleSetting.Builder().name("glow").description("Glow strength.").defaultValue(1.0).range(0.1, 2.5).sliderRange(0.1, 2.0).build());

   private final Setting<Boolean> netherite = this.preset("netherite", "Netherite gear, ingots, scrap and ancient debris.");
   private final Setting<Boolean> elytra = this.preset("elytras", "Elytras.");
   private final Setting<Boolean> totems = this.preset("totems", "Totems of undying.");
   private final Setting<Boolean> diamonds = this.preset("diamonds", "Diamonds and diamond gear.");
   private final Setting<Boolean> shulkers = this.preset("shulker-boxes", "Shulker boxes of every colour.");
   private final Setting<Boolean> armor = this.preset("armor", "Any armour piece.");
   private final Setting<Boolean> weapons = this.preset("weapons", "Swords, axes, spears, maces, tridents, bows and crossbows.");
   private final Setting<List<Item>> custom = this.sgPresets
      .add(new ItemListSetting.Builder().name("custom-items").description("Your own items to mark (in the Custom colour).").defaultValue().build());

   private final Setting<SettingColor> cNetherite = this.color("netherite-colour", ItemTargets.Category.Netherite);
   private final Setting<SettingColor> cElytra = this.color("elytra-colour", ItemTargets.Category.Elytra);
   private final Setting<SettingColor> cTotem = this.color("totem-colour", ItemTargets.Category.Totem);
   private final Setting<SettingColor> cDiamond = this.color("diamond-colour", ItemTargets.Category.Diamond);
   private final Setting<SettingColor> cShulker = this.color("shulker-colour", ItemTargets.Category.Shulker);
   private final Setting<SettingColor> cArmor = this.color("armor-colour", ItemTargets.Category.Armor);
   private final Setting<SettingColor> cWeapon = this.color("weapon-colour", ItemTargets.Category.Weapon);
   private final Setting<SettingColor> cCustom = this.color("custom-colour", ItemTargets.Category.Custom);
   private final Setting<SettingColor> cOther = this.color("other-colour", ItemTargets.Category.Other);

   private final Color side = new Color();
   private final Color line = new Color();

   public ItemEsp() {
      super(SpecterClientAddon.ESP, "item-esp", "Highlights valuable dropped items through walls with boxes, glow and tracers, coloured by kind (netherite, elytras, totems, diamonds, shulkers, armour, weapons, your own list).");
   }

   private Setting<Boolean> preset(String name, String description) {
      return this.sgPresets.add(new BoolSetting.Builder().name(name).description(description).defaultValue(true).build());
   }

   private Setting<SettingColor> color(String name, ItemTargets.Category category) {
      int c = category.argb;
      return this.sgColors
         .add(new ColorSetting.Builder().name(name).description("Colour for " + category.name() + ".").defaultValue(new SettingColor(c >> 16 & 255, c >> 8 & 255, c & 255)).build());
   }

   /** The colour an item is marked with, or 0 when it is not marked. Shared with Item Nametags. */
   public int colorOf(ItemStack stack) {
      if (this.custom.get().contains(stack.getItem())) return this.cCustom.get().getPacked() | 0xFF000000;
      ItemTargets.Category category = ItemTargets.categorize(stack);
      Setting<SettingColor> c = switch (category) {
         case Netherite -> this.netherite.get() ? this.cNetherite : null;
         case Elytra -> this.elytra.get() ? this.cElytra : null;
         case Totem -> this.totems.get() ? this.cTotem : null;
         case Diamond -> this.diamonds.get() ? this.cDiamond : null;
         case Shulker -> this.shulkers.get() ? this.cShulker : null;
         case Armor -> this.armor.get() ? this.cArmor : null;
         case Weapon -> this.weapons.get() ? this.cWeapon : null;
         default -> null;
      };
      if (c == null && this.allItems.get()) c = this.cOther;
      return c == null ? 0 : c.get().getPacked() | 0xFF000000;
   }

   public static ItemEsp active() {
      ItemEsp esp = Modules.get().get(ItemEsp.class);
      return esp != null && esp.isActive() ? esp : null;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.level == null || this.mc.player == null) return;
      Fx.camera();
      double maxSq = (double)this.range.get() * this.range.get();
      boolean box = this.mode.get() != Mode.Glow, glow = this.mode.get() != Mode.Box;
      float g = this.glowStrength.get().floatValue();
      Vec3 from = RenderUtils.center;
      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (!(entity instanceof ItemEntity item) || entity.distanceToSqr(this.mc.player) > maxSq) continue;
         int argb = this.colorOf(item.getItem());
         if (argb == 0) continue;
         double x = Mth.lerp(event.tickDelta, entity.xo, entity.getX());
         double y = Mth.lerp(event.tickDelta, entity.yo, entity.getY());
         double z = Mth.lerp(event.tickDelta, entity.zo, entity.getZ());
         if (box) {
            AABB bb = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ()).inflate(0.05);
            this.side.set(argb >> 16 & 255, argb >> 8 & 255, argb & 255, 45);
            this.line.set(argb >> 16 & 255, argb >> 8 & 255, argb & 255, 230);
            event.renderer.box(bb, this.side, this.line, ShapeMode.Both, 0);
         }
         if (glow) {
            double r = 0.45 * (0.7 + g * 0.5);
            double pulse = 1.0 + 0.08 * Math.sin(System.currentTimeMillis() / 250.0 + entity.getId());
            r *= pulse;
            Draw3D.glow(event.renderer, x, y + 0.2, z, Fx.rightX * r, Fx.rightY * r, Fx.rightZ * r, Fx.upX * r, Fx.upY * r, Fx.upZ * r, Fx.alpha(argb, (int)Math.min(255, 110 * g)));
         }
         if (this.tracers.get() && from != null) {
            ItemTargets.thickLine(event.renderer, from.x, from.y, from.z, x, y + 0.15, z, this.tracerWidth.get(), Fx.alpha(argb, 60), Fx.alpha(argb, 230));
         }
      }
   }
}
