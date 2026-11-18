package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.ah.VanillaNames;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.game.ReceiveMessageEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.ItemSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.macro.MacroThread;
import com.jfant.specter.macro.Menus;
import com.jfant.specter.macro.Money;
import com.jfant.specter.macro.Notes;
import com.jfant.specter.macro.Pace;
import com.jfant.specter.macro.Wanted;
import com.jfant.specter.macro.ah.Ah;
import com.jfant.specter.macro.ah.Sniper;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class AhSniper extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSell = this.settings.createGroup("Resell");
   private final Setting<AhSniper.Target> target = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("item"))
                  .description("What to snipe. Shard Axe, Sell Axe and Drill are the server's special tools; Other uses the item below."))
               .defaultValue(AhSniper.Target.ShardAxe))
            .build()
      );
   private final Setting<Item> otherItem = this.sgGeneral
      .add(
         new ItemSetting.Builder()
            .name("other-item")
            .description("The item to snipe when item is set to Other.")
            .defaultValue(Items.DIAMOND_PICKAXE)
            .visible(() -> this.target.get() == AhSniper.Target.Other)
            .build()
      );
   private final Setting<String> maxPrice = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("buy-below")
            .description("Buys a listing only when its price for each piece is at or under this. K, M and B work: 50M, 2.5M. Has to be set.")
            .defaultValue("")
            .build()
      );
   private final Setting<String> maxTotalSpend = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("max-total-spend")
            .description(
               "Stops for good once this much has been spent in all, and skips a listing that would go over it. K, M and B work: 500M, 1.5B. Leave empty for no limit."
            )
            .defaultValue("")
            .build()
      );
   private final Setting<Boolean> onlyWithConfirm = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-buy-with-confirm")
            .description(
               "On DonutSMP a click usually buys at once, with no confirm menu to check first. With this on, a purchase that happens without a confirm menu having been checked stops the sniper, so at most one unchecked buy goes through."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<String> enchants = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("enchantments")
            .description("Enchantments the item must have, like \"protection 4, unbreaking 3, mending\". Leave empty for any.")
            .defaultValue("")
            .build()
      );
   private final Setting<AhSniper.EnchantMatch> enchantMatch = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("enchant-match"))
                     .description("Exactly these: it must have those and no others. At least these: more is fine."))
                  .defaultValue(AhSniper.EnchantMatch.Exactly))
               .visible(() -> !this.enchants.get().isBlank()))
            .build()
      );
   private final Setting<Double> minHours = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-hours-left")
            .description(
               "Skips listings with less time left than this. Only items that show a countdown in their description can be checked; with this above 0, others are skipped."
            )
            .defaultValue(0.0)
            .min(0.0)
            .sliderRange(0.0, 48.0)
            .build()
      );
   private final Setting<Integer> refresh = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("refresh-ms")
            .description(
               "About how often the page is reloaded, in milliseconds. Each wait is a little different, like a hand's, and never shorter than a click can be."
            )
            .defaultValue(300)
            .min(100)
            .sliderRange(100, 2000)
            .build()
      );
   private final Setting<Integer> maxBuys = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("stop-after")
            .description("Switch off after this many purchases. 0 means keep going.")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 100)
            .build()
      );
   private final Setting<Boolean> autoSell = this.sgSell
      .add(
         new BoolSetting.Builder()
            .name("auto-sell")
            .description("List what it bought on the auction house again, at the price below.")
            .defaultValue(false)
            .build()
      );
   private final Setting<String> sellPrice = this.sgSell
      .add(
         new StringSetting.Builder()
            .name("sell-price")
            .description("What to ask for each piece when selling again. Has to be more than buy-below, or it would buy its own listing back.")
            .defaultValue("")
            .visible(this.autoSell::get)
            .build()
      );
   private final MacroThread worker = new MacroThread("specter-ah-sniper");
   private volatile Sniper bot;
   private final Notes notes = new Notes() {
      @Override
      public void info(String text) {
         AhSniper.this.mc.execute(() -> AhSniper.this.info("%s", new Object[]{text}));
      }

      @Override
      public void warn(String text) {
         AhSniper.this.mc.execute(() -> AhSniper.this.warning("%s", new Object[]{text}));
      }
   };

   public AhSniper() {
      super(
         SpecterClientAddon.DONUT,
         "ah-sniper",
         "Refreshes /ah and buys your item the moment it is listed under your price, with enchantment matching and optional resell."
      );
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null && this.mc.getConnection() != null) {
         long max = Money.parse(this.maxPrice.get());
         if (max <= 0L) {
            this.error("Set buy-below to a price like 50M first.", new Object[0]);
            this.toggle();
         } else {
            long limit = 0L;
            if (!this.maxTotalSpend.get().isBlank()) {
               limit = Money.parse(this.maxTotalSpend.get());
               if (limit <= 0L) {
                  this.error("max-total-spend has to be a price like 500M, or empty for no limit.", new Object[0]);
                  this.toggle();
                  return;
               }
            }

            long resell = 0L;
            if (this.autoSell.get()) {
               resell = Money.parse(this.sellPrice.get());
               if (resell <= 0L) {
                  this.error("Set sell-price to a price like 60M, or turn auto-sell off.", new Object[0]);
                  this.toggle();
                  return;
               }

               if (resell <= max) {
                  this.error("sell-price has to be more than buy-below, or the sniper would buy its own listing back.", new Object[0]);
                  this.toggle();
                  return;
               }
            }

            List<Wanted.Enchant> wanted = Wanted.parseEnchants(this.enchants.get());
            boolean exact = this.enchantMatch.get() == AhSniper.EnchantMatch.Exactly;
            AhSniper.Target kind = this.target.get();
            Wanted what = kind == AhSniper.Target.Other
               ? new Wanted(this.otherItem.get(), VanillaNames.search(this.otherItem.get()), VanillaNames.of(this.otherItem.get()), wanted, exact, "", "shard")
               : new Wanted(kind.item, kind.search, kind.label, wanted, exact, kind.nameHas, kind.nameLacks);
            int minutes = (int)Math.round(this.minHours.get() * 60.0);
            Sniper created = new Sniper(
               this.worker,
               new Pace(),
               this.notes,
               new Sniper.Config(what, max, minutes, this.refresh.get().intValue(), this.maxBuys.get(), resell, limit, this.onlyWithConfirm.get()),
               this.mc.getUser().getName()
            );
            this.bot = created;
            this.worker.start(created::run, reason -> this.mc.execute(() -> this.stopped(created, reason)), failure -> {
               SpecterClientAddon.LOG.error("AH Sniper stopped", failure);
               this.mc.execute(() -> this.stopped(created, "something went wrong: " + failure));
            });
         }
      } else {
         this.error("Join a server first.", new Object[0]);
         this.toggle();
      }
   }

   private void stopped(Sniper from, String reason) {
      if (this.bot == from && this.isActive()) {
         this.info("Stopped: %s.", new Object[]{reason});
         this.toggle();
      }
   }

   @Override
   public void onDeactivate() {
      Sniper stopped = this.bot;
      this.bot = null;
      this.worker.stop();
      if (stopped != null) {
         if (this.mc.player != null && Menus.containerOpen()) {
            String title = Menus.title();
            if (Ah.isAuction(title) || Ah.isConfirm(title)) {
               this.mc.player.closeContainer();
            }
         }

         this.info("%s", new Object[]{stopped.summary()});
      }
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      Sniper current = this.bot;
      if (current != null) {
         current.onChat(event.getMessage().getString());
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      if (this.isActive()) {
         this.toggle();
      }
   }

   public static enum EnchantMatch {
      Exactly("Exactly these"),
      AtLeast("At least these");

      private final String label;

      private EnchantMatch(String label) {
         this.label = label;
      }

      @Override
      public String toString() {
         return this.label;
      }
   }

   public static enum Target {
      ShardAxe("Shard Axe", Items.NETHERITE_AXE, "shard axe", "shard", "sell"),
      SellAxe("Sell Axe", Items.NETHERITE_AXE, "sell axe", "sell", ""),
      Drill("Drill", Items.NETHERITE_PICKAXE, "drill", "drill", ""),
      Other("Other item", null, "", "", "");

      final String label;
      final Item item;
      final String search;
      final String nameHas;
      final String nameLacks;

      private Target(String label, Item item, String search, String nameHas, String nameLacks) {
         this.label = label;
         this.item = item;
         this.search = search;
         this.nameHas = nameHas;
         this.nameLacks = nameLacks;
      }

      @Override
      public String toString() {
         return this.label;
      }
   }
}
