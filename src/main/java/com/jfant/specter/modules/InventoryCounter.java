package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.ItemListSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.PvpCard;
import com.jfant.specter.pvp.Targets;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class InventoryCounter extends Module {
   private static final double TARGET_RANGE = 24.0;
   private static final int WIDTH = 124;
   private static final int ROW = 11;
   private static final int COLUMNS = 3;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final Setting<List<Item>> items = this.sgGeneral
      .add(
         new ItemListSetting.Builder()
            .name("items")
            .description("The items to count, in order. The card grows a row for every three.")
            .defaultValue(Items.END_CRYSTAL, Items.OBSIDIAN, Items.RESPAWN_ANCHOR, Items.GLOWSTONE, Items.ENDER_PEARL, Items.WIND_CHARGE)
            .build()
      );
   private final Setting<Boolean> damage = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("damage-preview")
            .description("What the crystal or anchor you aim at would do to them and to you (needs Crystal PvP or Anchor PvP on).")
            .defaultValue(true)
            .build()
      );
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.BottomCenter, 94 + PvpCard.startWidth(124) / 2, 2);
   private final Setting<Double> scale = this.sgPosition
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .build()
      );

   public InventoryCounter() {
      super(
         SpecterClientAddon.HUD,
         "inventory-counter",
         "Counts the items you pick (crystals, obsidian, anchors and more), with the damage the crystal or anchor you aim at would do."
      );
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (PvpCard.visible()) {
         this.draw(event.drawContext);
      }
   }

   private void draw(GuiGraphics ctx) {
      int inner = 114;
      CrystalPvp.Preview crystal = this.damage.get() ? CrystalPvp.preview() : null;
      CrystalPvp.Preview anchor = this.damage.get() ? AnchorPvp.preview() : null;
      CrystalPvp.Preview preview = crystal != null ? crystal : anchor;
      List<Item> counted = this.items.get();
      if (preview != null || !counted.isEmpty()) {
         int top = preview != null ? 12 : 0;
         int rows = (counted.size() + 3 - 1) / 3;
         PvpCard.begin(ctx, this.spot, this.scale.get(), 124, rows == 0 ? 9 : top + rows * 11 - 2);
         if (preview != null) {
            PvpCard.item(ctx, new ItemStack(crystal != null ? Items.END_CRYSTAL : Items.RESPAWN_ANCHOR), 0, 0);
            int x = 11;
            x = PvpCard.word(ctx, String.format("%.1f", preview.target()), x, preview.target() > 0.0F ? -9705867 : -4149795);
            x = PvpCard.word(ctx, "them", x, -4149795);
            x = PvpCard.word(ctx, String.format("%.1f", preview.self()), x, preview.safe() ? -1911561 : -43691);
            PvpCard.word(ctx, "you", x, -4149795);
            Player target = Targets.current(this.mc.player.position(), 24.0);
            if (target != null && preview.target() >= Targets.health(target)) {
               PvpCard.badge(ctx, "POP", inner, 0, -43691, -1);
            }
         }

         int cell = inner / 3;

         for (int i = 0; i < counted.size(); i++) {
            int x = i % 3 * cell;
            int y = top + i / 3 * 11;
            Item item = counted.get(i);
            int count = Hands.count(item);
            PvpCard.item(ctx, new ItemStack(item), x, y);
            CardText.draw(ctx, String.valueOf(count), (float)(x + 8 + 3), (float)y, count == 0 ? -4149795 : -1, true);
         }

         PvpCard.end(ctx);
      }
   }
}
