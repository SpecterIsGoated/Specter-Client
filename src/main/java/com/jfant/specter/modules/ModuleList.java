package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.ModuleListSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import com.jfant.specter.render.PurpleCard;
import com.jfant.specter.render.QuadBatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

public class ModuleList extends Module {
   private static final String TITLE_TEXT = "Modules";
   private static final String EMPTY = "Nothing else on";
   private static final int PAD_X = 5;
   private static final int BUBBLE = 15;
   private static final int TEXT_Y = 4;
   private static final int GAP = 2;
   private static final int INFO_GAP = 4;
   private static final int COUNT_GAP = 8;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final Setting<Boolean> info = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("info")
            .description("Show what some modules add after their name, dimmed - a count, a mode or a timer.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> header = this.sgGeneral
      .add(new BoolSetting.Builder().name("header").description("Put a Modules bubble on top, with how many are on.").defaultValue(false).build());
   private final Setting<List<Module>> hidden = this.sgGeneral
      .add(new ModuleListSetting.Builder().name("hidden-modules").description("Modules to leave off the list.").defaultValue().build());
   private final Setting<Boolean> hideWhenEmpty = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-when-empty").description("Hide the list while nothing else is on.").defaultValue(false).build());
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.BottomRight, 4, 4);
   private final Setting<Double> scale = this.sgPosition
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the bubbles. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .build()
      );
   private static final Comparator<ModuleList.Row> ORDER = Comparator.comparingInt(ModuleList.Row::width).reversed().thenComparing(ModuleList.Row::text);
   private final List<ModuleList.Row> rows = new ArrayList<>();

   public ModuleList() {
      super(SpecterClientAddon.HUD, "module-list", "Lists the modules you have on, longest first, each in a purple glass bubble like the other cards.");
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (!this.mc.options.hideGui && !this.mc.debugEntries.isOverlayVisible()) {
         Font tr = this.mc.font;
         this.rows.clear();
         List<Module> skip = this.hidden.get();

         for (Module module : Modules.get().getActive()) {
            if (module != this && !skip.contains(module)) {
               String extra = this.info.get() ? module.getInfoString() : null;
               if (extra != null && extra.isBlank()) {
                  extra = null;
               }

               this.rows.add(row(tr, module.title, -1, extra, -4149795, 4));
            }
         }

         if (!this.rows.isEmpty() || !this.hideWhenEmpty.get()) {
            this.rows.sort(ORDER);
            int active = this.rows.size();
            if (this.rows.isEmpty()) {
               this.rows.add(row(tr, "Nothing else on", -4149795, null, 0, 0));
            }

            if (this.header.get()) {
               this.rows.addFirst(row(tr, "Modules", -1065473, active == 0 ? "None" : active + " on", active == 0 ? -4149795 : -1, 8));
            }

            int listWidth = 0;

            for (ModuleList.Row row : this.rows) {
               listWidth = Math.max(listWidth, row.width() + 10);
            }

            int listHeight = this.rows.size() * 17 - 2;
            float s = this.scale.get().floatValue();
            this.spot.place(listWidth * s, listHeight * s);
            boolean right = this.spot.right();
            GuiGraphics ctx = event.drawContext;
            Matrix3x2fStack matrices = ctx.pose();
            matrices.pushMatrix();
            matrices.translate(Math.round(this.spot.x()), Math.round(this.spot.y()));
            matrices.scale(s, s);
            int top = 0;

            for (ModuleList.Row row : this.rows) {
               int width = row.width() + 10;
               int left = right ? listWidth - width : 0;
               float in = Animator.to(row.text(), "list-in", 1.0, 0.0, Spec.LIST);
               int rowTop = Math.round(Animator.to(row.text(), "list-y", top, Spec.LIST));
               float slide = (1.0F - in) * 14.0F * (float)Motion.amount(Channel.MODULES) * (right ? 1 : -1);
               matrices.pushMatrix();
               matrices.translate(slide, rowTop - top);
               QuadBatch.pushAlpha(in);
               PurpleCard.bubble(ctx, left, top, left + width, top + 15);
               CardText.draw(ctx, row.text(), (float)(left + 5), (float)(top + 4), row.color(), true);
               if (row.extra() != null) {
                  CardText.draw(ctx, row.extra(), (float)(left + 5 + row.width() - CardText.width(row.extra())), (float)(top + 4), row.extraColor(), true);
               }

               QuadBatch.popAlpha();
               matrices.popMatrix();
               top += 17;
            }

            matrices.popMatrix();
         }
      }
   }

   private static ModuleList.Row row(Font tr, String text, int color, String extra, int extraColor, int gap) {
      int width = CardText.width(text) + (extra == null ? 0 : gap + CardText.width(extra));
      return new ModuleList.Row(text, color, extra, extraColor, width);
   }

   private record Row(String text, int color, String extra, int extraColor, int width) {
   }
}
