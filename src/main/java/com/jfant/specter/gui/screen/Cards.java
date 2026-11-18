package com.jfant.specter.gui.screen;

import com.jfant.specter.core.settings.BlockListSetting;
import com.jfant.specter.core.settings.BlockPosSetting;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.ItemListSetting;
import com.jfant.specter.core.settings.ItemSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.ModuleListSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.StringListSetting;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.utils.misc.Names;
import com.jfant.specter.core.utils.render.color.SettingColor;
import java.util.List;

final class Cards {
   private Cards() {
   }

   static Card create(Setting<?> setting, SettingsPanel panel) {
      if (setting instanceof BoolSetting s) {
         return new ToggleCard(s.title, s.description, () -> s.get(), value -> s.set(value), s::reset);
      } else if (setting instanceof IntSetting s) {
         return new SliderCard(
            s.title,
            s.description,
            () -> s.get().intValue(),
            value -> s.set((int)Math.round(value)),
            s::reset,
            s.min,
            s.max,
            s.sliderMin,
            s.sliderMax,
            0,
            s.noSlider,
            false
         );
      } else if (setting instanceof DoubleSetting s) {
         return new SliderCard(
            s.title,
            s.description,
            () -> s.get(),
            value -> s.set(value),
            s::reset,
            s.min,
            s.max,
            s.sliderMin,
            s.sliderMax,
            s.decimalPlaces,
            s.noSlider,
            s.onSliderRelease
         );
      } else if (setting instanceof EnumSetting<?> s) {
         return new EnumCard(s);
      } else if (setting instanceof StringSetting s) {
         return new TextCard(s);
      } else if (setting instanceof KeybindSetting s) {
         return new KeyCard(s.title, s.description, () -> s.get(), s::onChanged, s::reset);
      } else if (setting instanceof BlockPosSetting s) {
         return new PosCard(s);
      } else if (setting instanceof ColorSetting s) {
         return new OpenCard(
            s.title, s.description, () -> hex(s.get()), () -> Ui.argb(s.get()), null, () -> panel.open(new ColorEditor(s, panel::closeEditor)), s::reset
         );
      } else if (setting instanceof ItemSetting s) {
         return new OpenCard(
            s.title,
            s.description,
            () -> Names.get(s.get()),
            null,
            () -> s.get().getDefaultInstance(),
            () -> panel.open(PickerEditor.forItem(s, panel::closeEditor)),
            s::reset
         );
      } else if (setting instanceof ItemListSetting s) {
         return new OpenCard(
            s.title, s.description, () -> count(s.get()), null, null, () -> panel.open(PickerEditor.forItemList(s, panel::closeEditor)), s::reset
         );
      } else if (setting instanceof BlockListSetting s) {
         return new OpenCard(
            s.title, s.description, () -> count(s.get()), null, null, () -> panel.open(PickerEditor.forBlockList(s, panel::closeEditor)), s::reset
         );
      } else if (setting instanceof StringListSetting s) {
         return new OpenCard(s.title, s.description, () -> count(s.get()), null, null, () -> panel.open(new StringListEditor(s, panel::closeEditor)), s::reset);
      } else {
         return setting instanceof ModuleListSetting s
            ? new OpenCard(s.title, s.description, () -> count(s.get()), null, null, () -> panel.open(new ModuleListEditor(s, panel::closeEditor)), s::reset)
            : new OpenCard(setting.title, setting.description, () -> String.valueOf(setting.get()), null, null, null, setting::reset);
      }
   }

   private static String hex(SettingColor color) {
      return color.rainbow ? "rainbow" : String.format("#%02X%02X%02X", color.r, color.g, color.b);
   }

   private static String count(List<?> list) {
      return list.isEmpty() ? "none" : list.size() + " selected";
   }
}
