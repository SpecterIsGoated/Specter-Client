package com.jfant.specter.macro.ah;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.ah.Auction;
import com.jfant.specter.macro.Hand;
import com.jfant.specter.macro.MacroThread;
import com.jfant.specter.macro.Menus;
import com.jfant.specter.macro.Money;
import com.jfant.specter.macro.Notes;
import com.jfant.specter.macro.Pace;
import com.jfant.specter.macro.Wanted;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class Sniper {
   static final double MONEY_TOLERANCE = 0.02;
   private static final Pattern RATE_LIMIT = Pattern.compile("you need to wait another ([\\d.]+) seconds?");
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long BUY_TIMEOUT_MS = 4000L;
   private static final long SETTLE_MS = 250L;
   private static final double SETTLE_JITTER_MS = 80.0;
   private static final long FAILED_SKIP_MS = 60000L;
   private static final long BOUGHT_SKIP_MS = 15000L;
   private static final long CLOSED_GRACE_MS = 1500L;
   private static final int MAX_FAILURES = 5;
   private static final int RELOAD_TRIES = 3;
   private final MacroThread worker;
   private final Hand hand;
   private final Pace pace;
   private final Notes say;
   private final Sniper.Config config;
   private final AhSeller seller;
   private final AhSeller.Stock stock;
   private final List<AhSeller.Stock> stocks;
   private final Map<String, Long> skip = new ConcurrentHashMap<>();
   private volatile long boughtAt;
   private volatile Auction.Bought lastBought;
   private volatile long goneAt;
   private volatile long poorAt;
   private volatile long ownAt;
   private volatile long holdUntil;
   private volatile long lastBuyAt;
   private int bought;
   private int unsold;
   private volatile long spent;

   public Sniper(MacroThread worker, Pace pace, Notes say, Sniper.Config config, String ign) {
      this.worker = worker;
      this.pace = pace;
      this.hand = new Hand(worker, pace);
      this.say = say;
      this.config = config;
      if (config.sellPrice() > 0L) {
         this.seller = new AhSeller(worker, this.hand, say, new AhSeller.Config(0L, 60000L, 20000L, 1, 1000, 0L, 3.0, 20.0, 36, 15000L), ign);
         this.stock = new AhSeller.Stock(config.target(), config.sellPrice(), true);
         this.stocks = List.of(this.stock);
      } else {
         this.seller = null;
         this.stock = null;
         this.stocks = List.of();
      }
   }

   public void run() {
      this.say
         .info(
            "Sniping "
               + this.config.target().describe()
               + " under $ "
               + Money.shown(this.config.maxPrice())
               + (this.config.minMinutes() > 0 ? " with " + minutes(this.config.minMinutes()) + "+ left" : "")
               + (this.config.maxTotal() > 0L ? ", spending at most $ " + Money.shown(this.config.maxTotal()) : "")
               + (this.stock != null ? ", then selling at $ " + Money.shown(this.stock.minPrice) : "")
               + "."
         );
      int failures = 0;

      while (true) {
         this.worker.check();

         try {
            if (!this.worker.call(() -> Menus.mc().player != null && Menus.mc().getConnection() != null)) {
               this.worker.sleep(2000L);
            } else {
               if (this.stock != null && this.unsold > 0) {
                  this.sellStock();
               }

               this.snipe();
               failures = 0;
            }
         } catch (MacroThread.Halt | MacroThread.Cancelled var4) {
            throw var4;
         } catch (RuntimeException var5) {
            String reason = var5.getMessage() == null ? var5.getClass().getSimpleName() : var5.getMessage();
            if (++failures >= 5) {
               throw new MacroThread.Halt("gave up after " + failures + " tries in a row: " + reason);
            }

            this.say.warn(reason + ", trying again.");
            this.worker.check();
            this.hand.closeScreen();
            this.worker.sleep(2000L);
         }
      }
   }

   private void snipe() {
      this.openAuction();
      this.ensureRecent();
      long nextReload = System.currentTimeMillis() + this.pace.nextGap(this.config.refreshMs());
      int reloadFailures = 0;
      long closedSince = 0L;

      while (true) {
         this.worker.check();
         String title = this.worker.call(Menus::title);
         boolean open = this.worker.call(Menus::containerOpen);
         if (!open) {
            if (System.currentTimeMillis() - this.lastBuyAt < 5000L) {
               return;
            }

            if (this.worker.call(() -> Menus.mc().gui.screen() instanceof ChatScreen)) {
               this.worker.sleep(100L);
            } else {
               if (this.worker.call(() -> Menus.mc().gui.screen() != null)) {
                  throw new MacroThread.Halt("a screen that is not the auction came up");
               }

               if (closedSince == 0L) {
                  closedSince = System.currentTimeMillis();
               }

               if (System.currentTimeMillis() - closedSince > 1500L) {
                  throw new MacroThread.Halt("the auction menu was closed");
               }

               this.worker.sleep(50L);
            }
         } else {
            closedSince = 0L;
            if (!Ah.isListing(title) && !Ah.isConfirm(title)) {
               throw new MacroThread.Halt("an unexpected menu came up (\"" + title + "\"), so it stopped rather than click into it");
            }

            if (!Ah.isListing(title)) {
               this.hand.closeScreen();
            } else {
               Sniper.Hit hit = this.worker.call(this::scan);
               if (hit != null) {
                  this.worker.sleep(this.pace.nextGap());
                  if (this.handleBuy(hit)) {
                     return;
                  }

                  nextReload = System.currentTimeMillis() + this.pace.nextGap(this.config.refreshMs());
               } else {
                  long now = System.currentTimeMillis();
                  if (now >= nextReload) {
                     if (this.reload()) {
                        reloadFailures = 0;
                     } else if (++reloadFailures >= 3) {
                        throw new MacroThread.Halt("the reload button (the anvil in the bottom row) did not refresh the page 3 times in a row");
                     }

                     nextReload = System.currentTimeMillis() + this.pace.nextGap(this.config.refreshMs());
                  }

                  this.worker.sleep(50L);
               }
            }
         }
      }
   }

   private void openAuction() {
      this.hand.closeScreen();
      long wait = this.holdUntil - System.currentTimeMillis();
      if (wait > 0L) {
         this.worker.sleep(wait);
      }

      this.hand.command("ah " + this.config.target().search);
      if (!this.worker.waitFor(() -> Menus.containerOpen() && Ah.isListing(Menus.title()), 10000L)) {
         throw new AhSeller.Problem("the auction house did not open");
      } else {
         this.worker.sleep(300L);
      }
   }

   private void ensureRecent() {
      for (int attempt = 0; attempt < 4; attempt++) {
         int[] state = this.worker.call(() -> {
            List<ItemStack> stacks = Menus.contents();
            int slot = Ah.filterSlot(stacks);
            return new int[]{slot, slot < 0 ? Ah.Sort.UNKNOWN.ordinal() : Ah.selectedSort(stacks.get(slot)).ordinal()};
         });
         if (state[0] < 0) {
            this.say.warn("No filter button found, sniping with the page's own sorting.");
            return;
         }

         Ah.Sort sort = Ah.Sort.values()[state[1]];
         if (sort == Ah.Sort.RECENT) {
            return;
         }

         if (sort == Ah.Sort.UNKNOWN && attempt > 0) {
            this.say.warn("Could not read the filter - make sure it is on Recently Listed.");
            return;
         }

         Menus.Ref menu = this.worker.call(() -> Menus.ref(Ah::isListing));
         if (menu == null) {
            return;
         }

         String before = this.worker.call(Menus::fingerprint);
         if (!this.hand.click(menu, state[0])) {
            return;
         }

         this.worker.waitFor(() -> !Menus.fingerprint().equals(before), 2000L);
         this.worker.sleep(250L);
      }
   }

   private boolean reload() {
      Menus.Ref menu = this.worker.call(() -> Menus.ref(Ah::isListing));
      int slot = this.worker.call(() -> Ah.refreshSlot(Menus.contents()));
      if (menu != null && slot >= 0) {
         String before = this.worker.call(Menus::fingerprint);
         if (!this.hand.click(menu, slot)) {
            return false;
         } else {
            this.worker.waitFor(() -> !Menus.fingerprint().equals(before) || !Menus.containerOpen(), 1500L);
            return this.worker.waitFor(() -> Menus.containerOpen() && Ah.isListing(Menus.title()), 1500L);
         }
      } else {
         return false;
      }
   }

   private Sniper.Hit scan() {
      if (Menus.containerOpen() && Ah.isListing(Menus.title())) {
         if (!Menus.title().contains("(page 1)")) {
            return null;
         } else {
            long now = System.currentTimeMillis();
            this.skip.values().removeIf(until -> until < now);
            List<ItemStack> stacks = Menus.contents();

            for (int i = 0; i < 45 && i < stacks.size(); i++) {
               ItemStack stack = stacks.get(i);
               if (!stack.isEmpty() && this.wanted(stack)) {
                  double total = Money.listingPrice(stack);
                  if (!(total <= 0.0)) {
                     long unit = Math.round(total / Math.max(1, stack.getCount()));
                     if (unit <= this.config.maxPrice() && (this.config.maxTotal() <= 0L || !(this.spent + total > this.config.maxTotal()))) {
                        String fingerprint = fingerprint(stack, total);
                        if (!this.skip.containsKey(fingerprint)) {
                           return new Sniper.Hit(
                              i,
                              unit,
                              total,
                              stack.getCount(),
                              fingerprint,
                              identity(stack),
                              stack.getHoverName().getString(),
                              stack.getItem().getName().getString()
                           );
                        }
                     }
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private boolean wanted(ItemStack stack) {
      if (!this.config.target().matches(stack)) {
         return false;
      } else {
         return this.config.minMinutes() <= 0 ? true : Ah.minutesLeft(stack) >= this.config.minMinutes();
      }
   }

   private static String fingerprint(ItemStack stack, double total) {
      return Menus.path(stack)
         + "|"
         + stack.getHoverName().getString()
         + "|"
         + Math.round(total)
         + "|"
         + stack.getDamageValue()
         + "|"
         + String.join("/", Menus.loreLines(stack));
   }

   static String identity(ItemStack stack) {
      StringBuilder lore = new StringBuilder();

      for (String line : Menus.loreLines(stack)) {
         String clean = Menus.normalize(line);
         if (!line.contains("$") && !clean.contains("price") && !clean.contains("cost")) {
            lore.append(clean).append('/');
         }
      }

      return Menus.path(stack)
         + "|"
         + Menus.name(stack)
         + "|"
         + stack.getCount()
         + "|"
         + stack.getDamageValue()
         + "|"
         + stack.get(DataComponents.ENCHANTMENTS)
         + "|"
         + lore;
   }

   static boolean sameMoney(double a, double b) {
      return Math.abs(a - b) <= Math.max(1.0, Math.max(a, b) * 0.02);
   }

   static boolean sameName(String a, String b) {
      String x = Menus.key(a);
      String y = Menus.key(b);
      if (!x.isEmpty() && !y.isEmpty()) {
         String shorter = x.length() <= y.length() ? x : y;
         String longer = shorter == x ? y : x;
         return longer.startsWith(shorter) && longer.length() - shorter.length() <= 2;
      } else {
         return false;
      }
   }

   static String mismatch(String name, String kind, int count, double total, Auction.Bought got) {
      if (!sameName(name, got.name()) && !sameName(kind, got.name())) {
         return "got " + got.count() + " " + got.name() + " for $ " + Money.shown(got.price()) + ", not " + name;
      } else if (count != got.count()) {
         return "got " + got.count() + " " + got.name() + ", not " + count;
      } else {
         return !sameMoney(total, got.price()) ? got.name() + " cost $ " + Money.shown(got.price()) + ", not $ " + Money.shown(total) : null;
      }
   }

   private boolean handleBuy(Sniper.Hit hit) {
      if (this.stillThere(hit) == null) {
         return false;
      } else {
         this.worker.sleep(250L + this.pace.nextGap(80.0));
         Menus.Ref menu = this.stillThere(hit);
         if (menu == null) {
            return false;
         } else {
            long mark = System.currentTimeMillis();
            this.lastBought = null;
            if (!this.hand.click(menu, hit.slot())) {
               return false;
            } else {
               SpecterClientAddon.LOG
                  .info("AH Sniper: clicked slot {} - {} x{} for $ {}", new Object[]{hit.slot(), hit.name(), hit.count(), Money.shown(hit.total())});
               this.lastBuyAt = System.currentTimeMillis();
               long end = System.currentTimeMillis() + 4000L;
               int pressedSync = -1;

               for (boolean checked = false; System.currentTimeMillis() < end; this.worker.sleep(50L)) {
                  this.worker.check();
                  if (this.boughtAt >= mark) {
                     return this.bought(hit, checked);
                  }

                  if (this.poorAt >= mark) {
                     throw new MacroThread.Halt("not enough money for " + hit.name() + " at $ " + Money.shown(hit.total()));
                  }

                  if (this.goneAt >= mark || this.ownAt >= mark) {
                     this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 60000L);
                     return false;
                  }

                  Menus.Ref confirm = this.worker.call(() -> Menus.ref(Ah::isConfirm));
                  if (confirm != null && confirm.syncId() != pressedSync) {
                     int button = this.worker.call(() -> this.confirmButton(hit));
                     if (button < 0) {
                        SpecterClientAddon.LOG.warn("AH Sniper: cancelled a confirmation menu it could not read:\n{}", this.worker.call(Menus::fingerprint));
                        this.hand.closeScreen();
                        this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 60000L);
                        this.say.warn("A confirmation menu came up that this could not read (see the game log), so it was cancelled.");
                        return true;
                     }

                     if (this.hand.click(confirm, button)) {
                        pressedSync = confirm.syncId();
                        checked = true;
                     }
                  }
               }

               this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 60000L);
               return !this.worker.call(() -> Menus.containerOpen() && Ah.isListing(Menus.title()));
            }
         }
      }
   }

   private Menus.Ref stillThere(Sniper.Hit hit) {
      return this.worker.call(() -> {
         Menus.Ref ref = Menus.ref(Ah::isListing);
         if (ref == null) {
            return null;
         } else {
            ItemStack stack = Menus.slot(hit.slot());
            double total = Money.listingPrice(stack);
            return !stack.isEmpty() && this.wanted(stack) && !(total <= 0.0) && fingerprint(stack, total).equals(hit.fingerprint()) ? ref : null;
         }
      });
   }

   private int confirmButton(Sniper.Hit hit) {
      List<ItemStack> stacks = Menus.contents();
      int yes = -1;
      boolean shown = false;
      boolean itemOk = true;

      for (int i = 0; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         if (!stack.isEmpty()) {
            if (identity(stack).equals(hit.identity())) {
               double total = Money.listingPrice(stack);
               shown = true;
               itemOk &= total < 0.0 || sameMoney(total, hit.total());
            }

            String words = Menus.name(stack) + " " + Menus.normalize(String.join(" ", Menus.loreLines(stack)));
            if (yes < 0 && (Menus.path(stack).contains("lime") || words.contains("confirm") || words.startsWith("yes") || words.contains("click to buy"))) {
               yes = i;
            }
         }
      }

      return shown && itemOk ? yes : -1;
   }

   private boolean bought(Sniper.Hit hit, boolean checked) {
      this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 15000L);
      this.bought++;
      this.unsold++;
      this.spent = this.spent + Math.round(hit.total());
      this.say
         .info(
            "Sniped "
               + (hit.count() > 1 ? hit.count() + " " : "")
               + hit.name()
               + " for $ "
               + Money.shown(hit.total())
               + " ("
               + this.bought
               + (this.bought == 1 ? " buy, $ " : " buys, $ ")
               + Money.shown(this.spent)
               + " spent)."
         );
      Auction.Bought got = this.lastBought;
      String wrong = got == null ? null : mismatch(hit.name(), hit.kind(), hit.count(), hit.total(), got);
      if (wrong != null) {
         throw new MacroThread.Halt("bought the wrong thing: " + wrong);
      } else {
         if (got == null) {
            SpecterClientAddon.LOG.warn("AH Sniper: could not read the purchase line, so what was bought is unchecked");
         }

         if (this.config.onlyWithConfirm() && !checked) {
            throw new MacroThread.Halt("a purchase went through without a confirm menu to check first, and only-buy-with-confirm is on");
         } else if (this.config.maxTotal() > 0L && this.spent >= this.config.maxTotal()) {
            throw new MacroThread.Halt("spent $ " + Money.shown(this.spent) + ", the limit of $ " + Money.shown(this.config.maxTotal()));
         } else if (this.config.maxBuys() > 0 && this.bought >= this.config.maxBuys()) {
            if (this.stock != null) {
               this.worker.sleep(300L);
               this.sellStock();
            }

            throw new MacroThread.Halt("reached " + this.config.maxBuys() + (this.config.maxBuys() == 1 ? " buy" : " buys"));
         } else if (this.stock == null) {
            return !this.worker.call(() -> Menus.containerOpen() && Ah.isListing(Menus.title()));
         } else {
            this.worker.sleep(300L);
            this.sellStock();
            return true;
         }
      }
   }

   private void sellStock() {
      int count = Math.min(this.unsold, this.seller.countInventory(this.stock.product));
      if (count > 0) {
         this.unsold = this.unsold - this.seller.listSome(this.stock, Math.min(count, 9), new int[]{0});
      }
   }

   public void onChat(String raw) {
      String line = Menus.normalize(raw);
      if (!line.isEmpty()) {
         long now = System.currentTimeMillis();
         Matcher wait = RATE_LIMIT.matcher(line);
         if (wait.find()) {
            try {
               this.holdUntil = now + (long)Math.ceil(Double.parseDouble(wait.group(1)) * 1000.0) + 100L;
            } catch (NumberFormatException var7) {
            }
         }

         Auction.Bought purchase = Auction.bought(raw.trim());
         if (purchase != null || line.startsWith("you bought")) {
            this.lastBought = purchase;
            this.boughtAt = now;
         }

         if (line.contains("already bought") || line.contains("no longer available") || line.contains("not available")) {
            this.goneAt = now;
         }

         if (line.contains("enough money")) {
            this.poorAt = now;
         }

         if (line.contains("your own")) {
            this.ownAt = now;
         }

         if (this.seller != null) {
            this.seller.onChat(line, this.stocks);
         }
      }
   }

   public String summary() {
      return "Sniped "
         + this.bought
         + (this.bought == 1 ? " item" : " items")
         + " for $ "
         + Money.shown(this.spent)
         + (this.stock != null ? ". " + AhSeller.summary(this.stocks) : ".");
   }

   private static String minutes(int minutes) {
      return minutes % 60 == 0 ? minutes / 60 + "h" : minutes / 60 + "h " + minutes % 60 + "m";
   }

   public record Config(Wanted target, long maxPrice, int minMinutes, long refreshMs, int maxBuys, long sellPrice, long maxTotal, boolean onlyWithConfirm) {
   }

   private record Hit(int slot, long unitPrice, double total, int count, String fingerprint, String identity, String name, String kind) {
   }
}
