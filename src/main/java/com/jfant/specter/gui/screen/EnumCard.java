package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.render.QuadBatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class EnumCard extends Card {
   private final EnumSetting<?> setting;
   private int direction = 1;

   EnumCard(EnumSetting<?> setting) {
      super(setting.title, setting.description);
      this.setting = setting;
   }

   @Override
   boolean compact() {
      return true;
   }

   @Override
   int minWidth() {
      int widest = 0;

      for (Object value : this.values()) {
         widest = Math.max(widest, Ui.width(value.toString()));
      }

      return Ui.width(this.label) + widest + 2 * Ui.width("<") + 26;
   }

   @Override
   int height() {
      return 20;
   }

   private Object[] values() {
      Enum<?> current = this.setting.get();
      return current.getDeclaringClass().getEnumConstants();
   }

   private int valueLeft() {
      return this.x + this.w - 6 - Ui.width(this.setting.get().toString()) - 2 * Ui.width("<") - 8;
   }

   @Override
   void content(GuiGraphicsExtractor ctx, int mx, int my, boolean hover) {
      int textY = Ui.textY(this.y, this.h);
      String value = this.setting.get().toString();
      Ui.text(ctx, Ui.fit(this.label, Math.max(20, this.w - 12 - Ui.width(value) - 2 * Ui.width("<") - 12)), this.x + 6, textY, -1911561);
      int right = this.x + this.w - 6;
      int arrows = Ui.mix(-4149795, -37177, Ui.hover(this, "arrows", hover));
      Ui.textRight(ctx, ">", right, textY, arrows);
      float fresh = Animator.pulse(this, "cycle");
      int slide = Math.round(fresh * 7.0F * this.direction * (float)Motion.amount(Channel.GUI));
      if (fresh > 0.01F) {
         QuadBatch.pushAlpha(1.0F - fresh * 0.85F);
      }

      Ui.textRight(ctx, value, right - Ui.width(">") - 4 + slide, textY, -1);
      if (fresh > 0.01F) {
         QuadBatch.popAlpha();
      }

      Ui.text(ctx, "<", right - Ui.width(">") - 4 - Ui.width(value) - 4 - Ui.width("<"), textY, arrows);
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      boolean back = shift || mx < this.valueLeft() + Ui.width("<") + 4;
      this.cycle(back ? -1 : 1);
   }

   private void cycle(int by) {
      this.direction = by;
      Animator.fire(this, "cycle", Spec.EXPAND);
      Object[] all = this.values();
      int next = Math.floorMod(this.setting.get().ordinal() + by, all.length);
      ((EnumSetting)this.setting).set(all[next]);
   }

   @Override
   void reset() {
      this.setting.reset();
   }
}
