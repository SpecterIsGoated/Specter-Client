package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.ItemTargets;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;

/** Item Nametags: the name (and count) of each dropped item floats over it, e.g. ELYTRA x1, TOTEM OF UNDYING. */
public class ItemNametags extends Module {
   public enum ColorBy {
      Rarity,
      ItemEspColour,
      White
   }

   public enum Which {
      All,
      ItemEspMarked
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Which> which = this.sgGeneral
      .add(
         new EnumSetting.Builder<Which>()
            .name("items")
            .description("All: every dropped item. Item ESP Marked: only what Item ESP marks (needs Item ESP on).")
            .defaultValue(Which.All)
            .build()
      );
   private final Setting<Boolean> count = this.sgGeneral.add(new BoolSetting.Builder().name("count").description("Show the stack size (x64).").defaultValue(true).build());
   private final Setting<Boolean> upperCase = this.sgGeneral.add(new BoolSetting.Builder().name("upper-case").description("Write names in capitals.").defaultValue(true).build());
   private final Setting<ColorBy> colorBy = this.sgGeneral
      .add(new EnumSetting.Builder<ColorBy>().name("colour").description("Name colour.").defaultValue(ColorBy.Rarity).build());
   private final Setting<Double> scale = this.sgGeneral
      .add(new DoubleSetting.Builder().name("text-size").description("Text size.").defaultValue(1.0).range(0.4, 3.0).sliderRange(0.4, 2.5).build());
   private final Setting<Boolean> distanceScaling = this.sgGeneral
      .add(new BoolSetting.Builder().name("distance-scaling").description("Keep tags readable far away (they shrink less with distance).").defaultValue(true).build());
   private final Setting<Boolean> background = this.sgGeneral
      .add(new BoolSetting.Builder().name("background").description("A dark box behind the text.").defaultValue(true).build());
   private final Setting<Integer> range = this.sgGeneral
      .add(new IntSetting.Builder().name("render-distance").description("Only items within this many blocks.").defaultValue(48).range(4, 256).sliderRange(8, 128).build());

   private final Vector3d pos = new Vector3d();

   public ItemNametags() {
      super(SpecterClientAddon.ESP, "item-nametags", "Shows the name and count of dropped items over them, coloured by rarity.");
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.level == null || this.mc.player == null) return;
      ItemEsp esp = ItemEsp.active();
      double maxSq = (double)this.range.get() * this.range.get();
      GuiGraphicsExtractor ctx = event.graphics;
      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (!(entity instanceof ItemEntity item) || entity.distanceToSqr(this.mc.player) > maxSq) continue;
         ItemStack stack = item.getItem();
         int espColor = esp == null ? 0 : esp.colorOf(stack);
         if (this.which.get() == Which.ItemEspMarked && espColor == 0) continue;
         double x = Mth.lerp(event.tickDelta, entity.xo, entity.getX());
         double y = Mth.lerp(event.tickDelta, entity.yo, entity.getY()) + 0.65;
         double z = Mth.lerp(event.tickDelta, entity.zo, entity.getZ());
         this.pos.set(x, y, z);
         if (!NametagUtils.to2D(this.pos, this.scale.get(), this.distanceScaling.get())) continue;
         String text = ItemTargets.name(stack, this.upperCase.get());
         if (this.count.get() && stack.getCount() > 1) text = text + " x" + stack.getCount();
         int color = switch (this.colorBy.get()) {
            case Rarity -> ItemTargets.rarityColor(stack);
            case ItemEspColour -> espColor != 0 ? espColor : ItemTargets.rarityColor(stack);
            case White -> 0xFFFFFFFF;
         };
         NametagUtils.begin(this.pos, ctx);
         int w = CardText.width(text);
         if (this.background.get()) ctx.fill(-w / 2 - 3, -11, w / 2 + 3, 0, 0x90000000);
         CardText.draw(ctx, text, -w / 2.0F, -9.0F, color, true);
         NametagUtils.end(ctx);
      }
   }
}
