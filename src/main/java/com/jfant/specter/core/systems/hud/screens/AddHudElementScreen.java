package com.jfant.specter.core.systems.hud.screens;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.WLabel;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.containers.WSection;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.gui.widgets.pressable.WPlus;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.HudElementInfo;
import com.jfant.specter.core.systems.hud.HudGroup;
import com.jfant.specter.core.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

public class AddHudElementScreen extends WindowScreen {
   private final int x;
   private final int y;
   private final WTextBox searchBar;
   private Object firstObject;

   public AddHudElementScreen(GuiTheme theme, int x, int y) {
      super(theme, "Add Hud element");
      this.x = x;
      this.y = y;
      this.searchBar = theme.textBox("");
      this.searchBar.action = () -> {
         this.clear();
         this.initWidgets();
      };
      this.enterAction = () -> this.runObject(this.firstObject);
   }

   @Override
   public void initWidgets() {
      this.firstObject = null;
      this.add(this.searchBar).expandX();
      this.searchBar.setFocused(true);
      Hud hud = Hud.get();
      Map<HudGroup, List<AddHudElementScreen.Item>> grouped = new HashMap<>();

      for (HudElementInfo<?> info : hud.infos.values()) {
         if (info.hasPresets() && !this.searchBar.get().isEmpty()) {
            for (HudElementInfo<?>.Preset preset : info.presets) {
               String title = info.title + "  -  " + preset.title;
               if (Utils.searchTextDefault(title, this.searchBar.get(), false)) {
                  grouped.computeIfAbsent(info.group, hudGroup -> new ArrayList<>()).add(new AddHudElementScreen.Item(title, info.description, preset));
               }
            }
         } else if (Utils.searchTextDefault(info.title, this.searchBar.get(), false)) {
            grouped.computeIfAbsent(info.group, hudGroup -> new ArrayList<>()).add(new AddHudElementScreen.Item(info.title, info.description, info));
         }
      }

      for (HudGroup group : grouped.keySet()) {
         WSection section = this.add(this.theme.section(group.title())).expandX().widget();

         for (AddHudElementScreen.Item item : grouped.get(group)) {
            WHorizontalList l = section.add(this.theme.horizontalList()).expandX().widget();
            WLabel title = l.add(this.theme.label(item.title)).widget();
            title.tooltip = item.description;
            if (item.object instanceof HudElementInfo<?>.Preset presetx) {
               WPlus add = l.add(this.theme.plus()).expandCellX().right().widget();
               add.action = () -> this.runObject(preset);
               if (this.firstObject == null) {
                  this.firstObject = presetx;
               }
            } else {
               HudElementInfo<?> infox = (HudElementInfo<?>)item.object;
               if (infox.hasPresets()) {
                  WButton open = l.add(this.theme.button(" > ")).expandCellX().right().widget();
                  open.action = () -> this.runObject(info);
               } else {
                  WPlus add = l.add(this.theme.plus()).expandCellX().right().widget();
                  add.action = () -> this.runObject(info);
               }

               if (this.firstObject == null) {
                  this.firstObject = infox;
               }
            }
         }
      }
   }

   private void runObject(Object object) {
      if (object != null) {
         if (object instanceof HudElementInfo<?>.Preset preset) {
            Hud.get().add(preset, this.x, this.y);
            this.onClose();
         } else {
            HudElementInfo<?> info = (HudElementInfo<?>)object;
            if (info.hasPresets()) {
               HudElementPresetsScreen screen = new HudElementPresetsScreen(this.theme, info, this.x, this.y);
               screen.parent = this.parent;
               SpecterCore.mc.setScreen(screen);
            } else {
               Hud.get().add(info, this.x, this.y);
               this.onClose();
            }
         }
      }
   }

   @Override
   protected void onRenderBefore(GuiGraphics drawContext, float delta) {
      HudEditorScreen.renderElements(drawContext);
   }

   private record Item(String title, String description, Object object) {
   }
}
