package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.input.WIntEdit;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.utils.misc.Names;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.apache.commons.lang3.Strings;

public class StatusEffectAmplifierMapSettingScreen extends WindowScreen {
   private final Setting<Reference2IntMap<MobEffect>> setting;
   private WTable table;
   private String filterText = "";

   public StatusEffectAmplifierMapSettingScreen(GuiTheme theme, Setting<Reference2IntMap<MobEffect>> setting) {
      super(theme, "Modify Amplifiers");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      WTextBox filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      filter.setFocused(true);
      filter.action = () -> {
         this.filterText = filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   private void initTable() {
      List<MobEffect> statusEffects = new ArrayList<>(this.setting.get().keySet());
      statusEffects.sort(Comparator.comparing(Names::get));

      for (MobEffect statusEffect : statusEffects) {
         String name = Names.get(statusEffect);
         if (Strings.CI.contains(name, this.filterText)) {
            this.table.add(this.theme.itemWithLabel(this.getPotionStack(statusEffect), name)).expandCellX();
            WIntEdit level = this.theme.intEdit(this.setting.get().getInt(statusEffect), 0, 2147483647, true);
            level.action = () -> {
               this.setting.get().put(statusEffect, level.get());
               this.setting.onChanged();
            };
            this.table.add(level).minWidth(50.0);
            this.table.row();
         }
      }
   }

   private ItemStack getPotionStack(MobEffect effect) {
      ItemStack potion = Items.POTION.getDefaultInstance();
      potion.set(
         DataComponents.POTION_CONTENTS,
         new PotionContents(
            potion.get(DataComponents.POTION_CONTENTS).potion(),
            Optional.of(effect.getColor()),
            potion.get(DataComponents.POTION_CONTENTS).customEffects(),
            Optional.empty()
         )
      );
      return potion;
   }
}
