package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;

public class Reach extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> entityExtra = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("entity-reach")
            .description("Blocks added to your 3-block hit range. Any amount over 0 can be flagged by a reach check; the less, the rarer.")
            .defaultValue(0.2)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .decimalPlaces(2)
            .build()
      );
   private final Setting<Double> blockExtra = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("block-reach")
            .description("Blocks added to your 4.5-block reach for breaking and placing.")
            .defaultValue(0.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .decimalPlaces(2)
            .build()
      );
   private static Reach instance;

   public Reach() {
      super(
         SpecterClientAddon.BLATANT,
         "reach",
         "Hit players and mobs (and reach blocks) from further away, up to 2 extra blocks. Any extra can be flagged by a reach check - keep it small."
      );
      instance = this;
   }

   public static double entityRange(double vanilla) {
      Reach reach = instance;
      return reach != null && reach.isActive() ? vanilla + reach.entityExtra.get() : vanilla;
   }

   public static double blockRange(double vanilla) {
      Reach reach = instance;
      return reach != null && reach.isActive() ? vanilla + reach.blockExtra.get() : vanilla;
   }
}
