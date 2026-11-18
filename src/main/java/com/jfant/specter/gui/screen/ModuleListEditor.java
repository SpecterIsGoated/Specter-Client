package com.jfant.specter.gui.screen;

import com.jfant.specter.core.settings.ModuleListSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ModuleListEditor extends Editor {
   private static final int ROW = 18;
   private final ModuleListSetting setting;
   private final List<Module> all = new ArrayList<>(Modules.get().getAll());
   private final TextField search = new TextField("Search");
   private List<Module> shown;

   ModuleListEditor(ModuleListSetting setting, Runnable back) {
      super(setting.title, back);
      this.setting = setting;
      this.all.sort(Comparator.comparing(module -> module.title));
      this.shown = this.all;
      this.search.onChange = this::refilter;
      this.search.focus();
   }

   private void refilter() {
      String query = this.search.text.trim().toLowerCase(Locale.ROOT);
      this.shown = new ArrayList<>();

      for (Module module : this.all) {
         if (query.isEmpty() || module.title.toLowerCase(Locale.ROOT).contains(query)) {
            this.shown.add(module);
         }
      }

      this.scroll.reset();
   }

   @Override
   void body(GuiGraphicsExtractor ctx, int mx, int my) {
      this.search.render(ctx, this.x + 6, this.y + 2, this.w - 12, 14);
      int regionTop = this.y + 20;
      int regionHeight = this.h - 20;
      this.scroll.size(regionHeight, this.shown.size() * 20 + 4);
      int origin = regionTop - this.scroll.offset();
      boolean overRegion = Ui.in(mx, my, this.x, regionTop, this.w, regionHeight);
      ctx.enableScissor(this.x, regionTop, this.x + this.w, regionTop + regionHeight);

      for (int i = 0; i < this.shown.size(); i++) {
         int ry = origin + i * 20;
         if (ry + 18 >= regionTop && ry <= regionTop + regionHeight) {
            Module module = this.shown.get(i);
            boolean hover = overRegion && Ui.in(mx, my, this.x + 6, ry, this.w - 16, 18);
            Ui.box(ctx, this.x + 6, ry, this.w - 16, 18, hover);
            Ui.text(ctx, Ui.fit(module.title, this.w - 16 - 40), this.x + 12, Ui.textY(ry, 18), -1);
            Ui.knobSwitch(ctx, this.x + this.w - 10 - 24, ry + 4, this.setting.get().contains(module));
            if (hover) {
               Ui.hint = module.description;
            }
         }
      }

      ctx.disableScissor();
      this.scroll.bar(ctx, this.x + this.w - 1, regionTop, regionHeight);
   }

   @Override
   void bodyClick(int mx, int my, int button) {
      if (!this.search.click(mx, my)) {
         int regionTop = this.y + 20;
         if (my >= regionTop) {
            int row = (my - regionTop + this.scroll.offset()) / 20;
            if (row >= 0 && row < this.shown.size()) {
               Module module = this.shown.get(row);
               if (!this.setting.get().remove(module)) {
                  this.setting.get().add(module);
               }

               this.setting.onChanged();
            }
         }
      }
   }
}
