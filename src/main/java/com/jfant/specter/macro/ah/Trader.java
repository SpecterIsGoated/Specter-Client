package com.jfant.specter.macro.ah;

import com.jfant.specter.macro.Hand;
import com.jfant.specter.macro.MacroThread;
import com.jfant.specter.macro.Menus;
import com.jfant.specter.macro.Notes;
import com.jfant.specter.macro.Pace;
import com.jfant.specter.macro.Wanted;
import java.util.List;

public final class Trader {
   private static final int MAX_LISTINGS_PER_PASS = 9;
   private static final int MAX_FAILURES = 5;
   private final MacroThread worker;
   private final Hand hand;
   private final Notes say;
   private final Trader.Config config;
   private final AhSeller ah;
   private final AhSeller.Stock stock;
   private final List<AhSeller.Stock> stocks;
   private final Orders orders;
   private final int[] inAh = new int[]{0};
   private boolean warnedFull;

   public Trader(MacroThread worker, Pace pace, Notes say, Trader.Config config, String ign) {
      this.worker = worker;
      this.hand = new Hand(worker, pace);
      this.say = say;
      this.config = config;
      this.ah = new AhSeller(worker, this.hand, say, config.ah(), ign);
      this.stock = new AhSeller.Stock(config.product(), config.minPrice(), false);
      this.stocks = List.of(this.stock);
      this.orders = new Orders(this.ah, config.product(), config.order(), say);
   }

   public void run() {
      this.say.info("Selling " + this.config.product().describe() + (this.config.restock() ? ", restocking with orders." : "."));

      try {
         this.hand.closeScreen();
         if (this.ah.refreshListings(this.stocks)) {
            this.inAh[0] = this.stock.inAh;
         }
      } catch (MacroThread.Halt | MacroThread.Cancelled var4) {
         throw var4;
      } catch (RuntimeException var5) {
         this.hand.closeScreen();
      }

      int failures = 0;

      while (true) {
         this.worker.check();

         try {
            this.cycle();
            failures = 0;
         } catch (MacroThread.Halt | MacroThread.Cancelled var6) {
            throw var6;
         } catch (RuntimeException var7) {
            String reason = var7.getMessage() == null ? var7.getClass().getSimpleName() : var7.getMessage();
            if (++failures >= 5) {
               throw new MacroThread.Halt("gave up after " + failures + " failed tries in a row: " + reason);
            }

            this.say.warn(reason + ", trying again.");
            this.worker.check();
            this.hand.closeScreen();
            this.worker.sleep(3000L);
         }
      }
   }

   private void cycle() {
      if (!this.ah.inWorld()) {
         this.worker.sleep(2000L);
      } else {
         this.ah.ensureAlive();
         this.ah.ensurePrice(this.stock);
         this.inAh[0] = this.stock.inAh;
         if (this.stock.price <= 0L) {
            this.worker.sleep(Math.min(5000L, this.ah.config.priceCheckMs()));
         } else {
            AhSeller.Config c = this.config.ah();
            int count = this.ah.countInventory(this.config.product());
            if (count < c.perListing() && this.config.restock() && this.inAh[0] < c.maxListings() && this.orders.run()) {
               count = this.ah.countInventory(this.config.product());
            }

            if (count >= c.perListing()) {
               int room = c.maxListings() - this.inAh[0];
               if (room <= 0) {
                  if (!this.warnedFull) {
                     this.say.info("The auction house is full (" + this.inAh[0] + "/" + c.maxListings() + "), waiting for sales.");
                  }

                  this.warnedFull = true;
               } else {
                  this.warnedFull = false;
                  int listedNow = this.ah.listSome(this.stock, Math.min(Math.min(count / c.perListing(), room), 9), this.inAh);
                  if (listedNow > 0 && this.ah.countInventory(this.config.product()) >= c.perListing() && this.inAh[0] < c.maxListings()) {
                     this.worker.sleep(500L);
                     return;
                  }
               }
            }

            this.ah.waitSalesCheck();
            if (this.ah.refreshListings(this.stocks)) {
               this.inAh[0] = this.stock.inAh;
            }

            int more = this.ah.countInventory(this.config.product());
            if (more < c.perListing() && this.config.restock() && this.inAh[0] < c.maxListings()) {
               this.orders.run();
            }
         }
      }
   }

   public void onChat(String raw) {
      String line = Menus.normalize(raw);
      if (!line.isEmpty()) {
         this.orders.onChat(line);
         this.ah.onChat(line, this.stocks);
      }
   }

   public String summary() {
      return AhSeller.summary(this.stocks);
   }

   public record Config(Wanted product, long minPrice, boolean restock, Orders.Spec order, AhSeller.Config ah) {
   }
}
