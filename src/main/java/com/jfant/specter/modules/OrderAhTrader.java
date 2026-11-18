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
import com.jfant.specter.macro.ah.AhSeller;
import com.jfant.specter.macro.ah.Orders;
import com.jfant.specter.macro.ah.Trader;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class OrderAhTrader extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRestock = this.settings.createGroup("Restock");
   private final SettingGroup sgTiming = this.settings.createGroup("Timing");
   private final Setting<Item> item = this.sgGeneral
      .add(new ItemSetting.Builder().name("item").description("The item to sell.").defaultValue(Items.BARREL).build());
   private final Setting<String> enchants = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("enchantments")
            .description("Enchantments the item must have, like \"protection 4, unbreaking 3\" (at least these). Leave empty for any.")
            .defaultValue("")
            .build()
      );
   private final Setting<String> minPrice = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("min-price")
            .description("Never lists for less than this each. K, M and B work: 3K, 1.5M.")
            .defaultValue("3K")
            .build()
      );
   private final Setting<String> undercut = this.sgGeneral
      .add(new StringSetting.Builder().name("undercut-by").description("How much under the cheapest other seller to list, each.").defaultValue("100").build());
   private final Setting<Boolean> restock = this.sgRestock
      .add(
         new BoolSetting.Builder()
            .name("restock")
            .description("When there is nothing left to list, order more through /orders and collect what the order fills.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> orderAmount = this.sgRestock
      .add(
         new IntSetting.Builder()
            .name("order-amount")
            .description("How many to order at a time.")
            .defaultValue(64)
            .min(1)
            .sliderRange(1, 2304)
            .visible(this.restock::get)
            .build()
      );
   private final Setting<OrderAhTrader.OrderPrice> orderPrice = this.sgRestock
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("order-price"))
                     .description("Beat top order: pay a little more each than the best order already out there. Fixed: always the price below."))
                  .defaultValue(OrderAhTrader.OrderPrice.BeatTopOrder))
               .visible(this.restock::get))
            .build()
      );
   private final Setting<String> fixedOrderPrice = this.sgRestock
      .add(
         new StringSetting.Builder()
            .name("fixed-order-price")
            .description("What to pay each when order-price is Fixed.")
            .defaultValue("100")
            .visible(() -> this.restock.get() && this.orderPrice.get() == OrderAhTrader.OrderPrice.Fixed)
            .build()
      );
   private final Setting<String> beatBy = this.sgRestock
      .add(
         new StringSetting.Builder()
            .name("beat-by")
            .description("How much more than the best order to pay, each.")
            .defaultValue("1")
            .visible(() -> this.restock.get() && this.orderPrice.get() == OrderAhTrader.OrderPrice.BeatTopOrder)
            .build()
      );
   private final Setting<String> maxOrderPrice = this.sgRestock
      .add(
         new StringSetting.Builder()
            .name("max-order-price")
            .description("Never pays more than this each for an order. Leave empty for no limit.")
            .defaultValue("")
            .visible(this.restock::get)
            .build()
      );
   private final Setting<Integer> priceCheck = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("price-check-s")
            .description("How often, in seconds, to look at what the others ask.")
            .defaultValue(120)
            .min(10)
            .sliderRange(10, 600)
            .build()
      );
   private final Setting<Integer> salesCheck = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("sales-check-s")
            .description("How often, in seconds, to look in Your Items for what has sold.")
            .defaultValue(20)
            .min(5)
            .sliderRange(5, 300)
            .build()
      );
   private final Setting<Integer> perListing = this.sgTiming
      .add(
         new IntSetting.Builder().name("items-per-listing").description("How many items go in one listing.").defaultValue(1).min(1).sliderRange(1, 64).build()
      );
   private final Setting<Integer> maxListings = this.sgTiming
      .add(
         new IntSetting.Builder().name("max-listings").description("The most listings to have up at once.").defaultValue(25).min(1).sliderRange(1, 50).build()
      );
   private final Setting<Double> listingDelay = this.sgTiming
      .add(
         new DoubleSetting.Builder()
            .name("listing-delay-s")
            .description("An extra pause, in seconds, between one listing and the next.")
            .defaultValue(0.0)
            .min(0.0)
            .sliderRange(0.0, 5.0)
            .build()
      );
   private final Setting<Double> relistGap = this.sgTiming
      .add(
         new DoubleSetting.Builder()
            .name("relist-higher-at")
            .description("When the others' price has gone up by more than this many undercuts, take your listings back and list them higher.")
            .defaultValue(3.0)
            .min(1.0)
            .sliderRange(1.0, 20.0)
            .build()
      );
   private final Setting<Double> ignoreDrops = this.sgTiming
      .add(
         new DoubleSetting.Builder()
            .name("ignore-drops-over")
            .description("A rival who drops their price by more than this many undercuts is taken for a troll and not followed.")
            .defaultValue(20.0)
            .min(1.0)
            .sliderRange(1.0, 100.0)
            .build()
      );
   private final Setting<Integer> collectSlots = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("collect-up-to-slots")
            .description("Stops collecting from an order once this many of your inventory slots hold the item.")
            .defaultValue(18)
            .min(1)
            .sliderRange(1, 36)
            .visible(this.restock::get)
            .build()
      );
   private final Setting<Integer> orderCheck = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("order-check-s")
            .description("How often, in seconds, to look at your orders when there is nothing to list.")
            .defaultValue(15)
            .min(5)
            .sliderRange(5, 300)
            .visible(this.restock::get)
            .build()
      );
   private final MacroThread worker = new MacroThread("specter-order-ah-trader");
   private volatile Trader bot;
   private final Notes notes = new Notes() {
      @Override
      public void info(String text) {
         OrderAhTrader.this.mc.execute(() -> OrderAhTrader.this.info("%s", new Object[]{text}));
      }

      @Override
      public void warn(String text) {
         OrderAhTrader.this.mc.execute(() -> OrderAhTrader.this.warning("%s", new Object[]{text}));
      }
   };

   public OrderAhTrader() {
      super(SpecterClientAddon.DONUT, "order-ah-trader", "Sells an item on the auction house a little under the other sellers and restocks it through orders.");
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null && this.mc.getConnection() != null) {
         String problem = this.validate();
         if (problem != null) {
            this.error("%s", new Object[]{problem});
            this.toggle();
         } else {
            Item picked = this.item.get();
            Wanted product = new Wanted(picked, VanillaNames.search(picked), VanillaNames.of(picked), Wanted.parseEnchants(this.enchants.get()), false, "", "");
            long max = this.maxOrderPrice.get().isBlank() ? 0L : Money.parse(this.maxOrderPrice.get());
            Orders.Spec order = new Orders.Spec(
               this.orderPrice.get() == OrderAhTrader.OrderPrice.BeatTopOrder,
               Math.max(1L, Money.parse(this.fixedOrderPrice.get())),
               Math.max(1L, Money.parse(this.beatBy.get())),
               Math.max(0L, max),
               this.orderAmount.get()
            );
            AhSeller.Config ah = new AhSeller.Config(
               Math.max(0L, Money.parse(this.undercut.get())),
               this.priceCheck.get().intValue() * 1000L,
               this.salesCheck.get().intValue() * 1000L,
               this.perListing.get(),
               this.maxListings.get(),
               Math.round(this.listingDelay.get() * 1000.0),
               this.relistGap.get(),
               this.ignoreDrops.get(),
               this.collectSlots.get(),
               this.orderCheck.get().intValue() * 1000L
            );
            Trader created = new Trader(
               this.worker,
               new Pace(),
               this.notes,
               new Trader.Config(product, Money.parse(this.minPrice.get()), this.restock.get(), order, ah),
               this.mc.getUser().getName()
            );
            this.bot = created;
            this.worker.start(created::run, reason -> this.mc.execute(() -> this.stopped(created, reason)), failure -> {
               SpecterClientAddon.LOG.error("Order AH Trader stopped", failure);
               this.mc.execute(() -> this.stopped(created, "something went wrong: " + failure));
            });
         }
      } else {
         this.error("Join a server first.", new Object[0]);
         this.toggle();
      }
   }

   private String validate() {
      if (Money.parse(this.minPrice.get()) <= 0L) {
         return "min-price has to be a price like 3K.";
      } else if (Money.parse(this.undercut.get()) < 0L) {
         return "undercut-by has to be a price like 100.";
      } else if (!this.restock.get()) {
         return null;
      } else if (this.orderPrice.get() == OrderAhTrader.OrderPrice.Fixed && Money.parse(this.fixedOrderPrice.get()) <= 0L) {
         return "fixed-order-price has to be a price like 150K.";
      } else if (this.orderPrice.get() == OrderAhTrader.OrderPrice.BeatTopOrder && Money.parse(this.beatBy.get()) <= 0L) {
         return "beat-by has to be a price like 1.";
      } else {
         return !this.maxOrderPrice.get().isBlank() && Money.parse(this.maxOrderPrice.get()) <= 0L
            ? "max-order-price has to be empty or a price like 200K."
            : null;
      }
   }

   private void stopped(Trader from, String reason) {
      if (this.bot == from && this.isActive()) {
         this.info("Stopped: %s.", new Object[]{reason});
         this.toggle();
      }
   }

   @Override
   public void onDeactivate() {
      Trader stopped = this.bot;
      this.bot = null;
      this.worker.stop();
      if (stopped != null) {
         if (this.mc.player != null && Menus.containerOpen()) {
            String title = Menus.title();
            if (Ah.isAuction(title) || title.contains("order")) {
               this.mc.player.closeContainer();
            }
         }

         this.info("%s", new Object[]{stopped.summary()});
      }
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      Trader current = this.bot;
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

   public static enum OrderPrice {
      BeatTopOrder("Beat top order"),
      Fixed("Fixed");

      private final String label;

      private OrderPrice(String label) {
         this.label = label;
      }

      @Override
      public String toString() {
         return this.label;
      }
   }
}
