package com.jfant.specter.macro.ah;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.macro.Dialogs;
import com.jfant.specter.macro.Hand;
import com.jfant.specter.macro.MacroThread;
import com.jfant.specter.macro.Menus;
import com.jfant.specter.macro.Money;
import com.jfant.specter.macro.Notes;
import com.jfant.specter.macro.Wanted;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class AhSeller {
   private static final int INVENTORY_SLOTS = 36;
   private static final int CHEST_CONFIRM_SLOT = 15;
   private static final int COLLECT_LIMIT = 50;
   private static final int VERIFY_LIMIT = 15;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long NAV_TIMEOUT_MS = 4000L;
   private static final long SELL_TIMEOUT_MS = 15000L;
   final MacroThread worker;
   final Hand hand;
   final Notes say;
   final AhSeller.Config config;
   private final String ignKey;
   private final String ign;
   private volatile long tooManyAt;
   private volatile long alreadyBoughtAt;
   private volatile long listedAt;
   private volatile long errorAt;

   public AhSeller(MacroThread worker, Hand hand, Notes say, AhSeller.Config config, String ign) {
      this.worker = worker;
      this.hand = hand;
      this.say = say;
      this.config = config;
      this.ign = ign;
      this.ignKey = Menus.key(ign);
   }

   public Hand hand() {
      return this.hand;
   }

   public boolean inWorld() {
      return this.worker.call(() -> Menus.mc().player != null && Menus.mc().getConnection() != null);
   }

   public void ensureAlive() {
      if (this.worker.call(() -> Menus.mc().player != null && Menus.mc().player.isDeadOrDying())) {
         throw new MacroThread.Halt("you died");
      }
   }

   public void ensurePrice(AhSeller.Stock stock) {
      if (stock.fixedPrice) {
         stock.price = stock.minPrice;
      } else if (stock.price <= 0L || System.currentTimeMillis() - stock.lastPriceCheck >= this.config.priceCheckMs()) {
         this.updatePrice(stock);
      }
   }

   private void updatePrice(AhSeller.Stock stock) {
      double[] scan = this.scan(stock);
      stock.lastPriceCheck = System.currentTimeMillis();
      boolean confirmed = scan[0] > 0.0;
      boolean matched = scan[1] > 0.0;
      double cheapestUnit = scan[2];
      long undercut = this.config.undercut();
      String name = stock.product.displayName;
      if (cheapestUnit > 0.0) {
         long cheapest = Math.round(cheapestUnit);
         long next = Math.max(cheapest - undercut, stock.minPrice);
         if (stock.price > 0L && cheapest < stock.price) {
            long drop = stock.price - cheapest;
            if (drop > undercut * this.config.ignoreDrops()) {
               this.say.warn(name + ": ignoring a deep undercut to $ " + Money.shown(cheapest) + " (a drop of $ " + Money.shown(drop) + ").");
            } else if (next < stock.price) {
               this.say.info(name + ": undercut to $ " + Money.shown(cheapest) + ", relisting at $ " + Money.shown(next) + ".");
               this.relist(stock, next);
            }
         } else if (stock.price > 0L && next > stock.price + undercut * this.config.relistGap() && stock.inAh > 0) {
            this.say.info(name + ": price went up to $ " + Money.shown(cheapest) + ", relisting at $ " + Money.shown(next) + ".");
            this.relist(stock, next);
         } else {
            if (stock.price <= 0L) {
               this.say.info(name + ": cheapest is $ " + Money.shown(cheapest) + ", selling at $ " + Money.shown(next) + " each.");
            }

            stock.price = next;
         }
      } else if (stock.price <= 0L && confirmed && !matched) {
         stock.price = stock.minPrice * 2L;
         this.say.info(name + ": no other listings, selling at $ " + Money.shown(stock.price) + " each.");
      } else if (stock.price <= 0L) {
         this.say.warn(name + ": could not read the auction prices, waiting.");
      }
   }

   private double[] scan(AhSeller.Stock stock) {
      Wanted product = stock.product;
      this.hand.closeScreen();
      this.worker.sleep(200L);
      this.hand.command("ah " + product.search);
      boolean open = this.worker.waitFor(() -> this.isResults() && this.hasContent(), 10000L);
      if (!open) {
         this.hand.closeScreen();
         return new double[]{0.0, 0.0, -1.0};
      } else {
         this.worker.sleep(600L);
         if (!this.worker.call(this::isResults)) {
            this.hand.closeScreen();
            return new double[]{0.0, 0.0, -1.0};
         } else {
            double[] result = this.worker.call(() -> {
               List<ItemStack> stacks = Menus.contents();
               boolean matched = false;
               double cheapest = -1.0;

               for (int i = 0; i < stacks.size() && i < 45; i++) {
                  ItemStack stack = stacks.get(i);
                  if (product.matches(stack)) {
                     matched = true;
                     double total = Money.listingPrice(stack);
                     if (!(total <= 0.0)) {
                        double unit = total / Math.max(1, stack.getCount());
                        if (!this.ownListing(stock, stack, unit) && (cheapest < 0.0 || unit < cheapest)) {
                           cheapest = unit;
                        }
                     }
                  }
               }

               return new double[]{1.0, matched ? 1.0 : 0.0, cheapest};
            });
            this.hand.closeScreen();
            this.worker.sleep(200L);
            return result;
         }
      }
   }

   private boolean ownListing(AhSeller.Stock stock, ItemStack stack, double unit) {
      for (String line : Menus.loreLines(stack)) {
         if (!this.ignKey.isEmpty() && Menus.key(line).contains(this.ignKey)) {
            return true;
         }
      }

      return stock.inAh > 0 && stock.price > 0L && Math.abs(unit - stock.price) < 1.0;
   }

   private boolean hasContent() {
      List<ItemStack> stacks = Menus.contents();
      if (stacks.size() > 49 && !stacks.get(49).isEmpty()) {
         return true;
      } else {
         for (int i = 0; i <= 10 && i < stacks.size(); i++) {
            if (!stacks.get(i).isEmpty()) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean isResults() {
      return Menus.containerOpen() && Ah.isAuction(Menus.title());
   }

   private boolean isAuctionRoot() {
      return Menus.containerOpen() && Ah.isAuction(Menus.title()) && Ah.yourItemsSlot(Menus.contents()) >= 0;
   }

   private String openYourItems() {
      this.hand.closeScreen();
      this.worker.sleep(200L);
      this.hand.command("ah " + this.ign);
      if (!this.worker.waitFor(this::isAuctionRoot, 10000L)) {
         return null;
      } else {
         for (int attempt = 0; attempt < 2; attempt++) {
            Menus.Ref root = this.worker.call(() -> Menus.ref(Ah::isAuction));
            if (root == null) {
               return null;
            }

            int slot = this.worker.call(() -> Ah.yourItemsSlot(Menus.contents()));
            if (slot < 0) {
               return null;
            }

            String before = this.worker.call(Menus::fingerprint);
            if (this.hand.click(root, slot) && this.worker.waitFor(() -> !Menus.fingerprint().equals(before), 4000L)) {
               this.worker.sleep(200L);
               return this.worker.call(() -> Menus.containerOpen() ? Menus.title() : null);
            }
         }

         return null;
      }
   }

   public boolean refreshListings(List<AhSeller.Stock> stocks) {
      if (this.openYourItems() == null) {
         this.hand.closeScreen();
         return false;
      } else {
         int[] counts = this.worker.call(() -> {
            int[] found = new int[stocks.size()];

            for (ItemStack stack : Menus.contents()) {
               for (int ix = 0; ix < stocks.size(); ix++) {
                  if (stocks.get(ix).product.matches(stack)) {
                     found[ix]++;
                     break;
                  }
               }
            }

            return found;
         });
         this.hand.closeScreen();
         this.worker.sleep(200L);

         for (int i = 0; i < stocks.size(); i++) {
            AhSeller.Stock stock = stocks.get(i);
            int gone = stock.inAh - counts[i];
            if (gone > 0 && stock.chatSales.getAndSet(0) == 0) {
               stock.sold.addAndGet(gone);
               stock.earned.addAndGet(gone * Math.max(0L, stock.price) * this.config.perListing());
               this.say.info("Sold " + gone + "x " + stock.product.displayName + " (" + stock.sold.get() + " in all).");
            }

            stock.chatSales.set(0);
            stock.inAh = counts[i];
         }

         return true;
      }
   }

   private int collect(AhSeller.Stock stock) {
      String title = this.openYourItems();
      if (title == null) {
         this.say.warn("Could not open Your Items.");
         this.hand.closeScreen();
         return 0;
      } else {
         int collected = this.collectPass(stock.product, title, 50);
         if (collected > 0) {
            this.hand.closeScreen();
            this.worker.sleep(500L);
            String again = this.openYourItems();
            if (again != null) {
               collected += this.collectPass(stock.product, again, 15);
            }
         }

         this.hand.closeScreen();
         this.worker.sleep(200L);
         if (collected > 0) {
            this.say.info("Took back " + collected + (collected == 1 ? " listing of " : " listings of ") + stock.product.displayName + ".");
         }

         return collected;
      }
   }

   private int collectPass(Wanted product, String title, int limit) {
      int collected = 0;
      Set<Integer> skipped = new HashSet<>();

      Menus.Ref menu;
      while (collected < limit && (menu = this.worker.call(() -> Menus.ref(title::equals))) != null) {
         int slot = this.worker.call(() -> {
            List<ItemStack> stacks = Menus.contents();

            for (int i = 0; i < stacks.size(); i++) {
               if (!skipped.contains(i) && product.matches(stacks.get(i))) {
                  return i;
               }
            }

            return -1;
         });
         if (slot < 0) {
            break;
         }

         long mark = System.currentTimeMillis();
         if (!this.hand.click(menu, slot)) {
            break;
         }

         this.worker.sleep(150L);
         if (this.alreadyBoughtAt >= mark) {
            skipped.add(slot);
         } else {
            collected++;
         }
      }

      return collected;
   }

   private void relist(AhSeller.Stock stock, long next) {
      this.refreshListings(List.of(stock));
      stock.price = next;
      this.collect(stock);
      stock.inAh = 0;
   }

   public int countInventory(Wanted product) {
      return this.worker.call(() -> {
         LocalPlayer player = Menus.mc().player;
         if (player == null) {
            return 0;
         } else {
            int total = 0;

            for (int i = 0; i < 36; i++) {
               ItemStack stack = player.getInventory().getItem(i);
               if (product.matches(stack)) {
                  total += stack.getCount();
               }
            }

            return total;
         }
      });
   }

   public boolean inventoryCapReached(Wanted product) {
      return this.worker.call(() -> {
         Inventory inventory = Menus.mc().player.getInventory();
         int used = 0;
         int empty = 0;

         for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
               empty++;
            } else if (stack.is(product.item)) {
               used++;
            }
         }

         return used >= this.config.collectSlots() || empty <= 0;
      });
   }

   public int listSome(AhSeller.Stock stock, int wanted, int[] totalInAh) {
      if (wanted <= 0) {
         return 0;
      } else {
         this.hand.closeScreen();
         int previous = this.worker.call(() -> Menus.mc().player.getInventory().getSelectedSlot());
         int done = 0;
         int failures = 0;

         try {
            for (int i = 0; i < wanted; i++) {
               this.ensureAlive();
               int hotbar = this.stage(stock.product);
               if (hotbar < 0) {
                  this.say.warn("Could not get a hotbar slot ready (is the inventory full?).");
                  break;
               }

               AhSeller.Listing result = this.listFrom(stock, hotbar);
               if (result == AhSeller.Listing.FULL) {
                  totalInAh[0] = Math.max(totalInAh[0], this.config.maxListings());
                  this.say.warn("The auction house says you have too many listings.");
                  break;
               }

               if (result == AhSeller.Listing.FAILED) {
                  if (++failures >= 2) {
                     break;
                  }
               } else {
                  done++;
                  stock.inAh++;
                  totalInAh[0]++;
                  stock.listed.incrementAndGet();
                  if (this.config.listingDelayMs() > 0L && i < wanted - 1) {
                     this.worker.sleep(this.config.listingDelayMs());
                  }
               }
            }
         } finally {
            this.worker.run(() -> {
               LocalPlayer player = Menus.mc().player;
               if (player != null) {
                  player.getInventory().setSelectedSlot(previous);
               }
            });
         }

         if (done > 0) {
            this.say
               .info(
                  "Listed "
                     + done
                     + "x "
                     + stock.product.displayName
                     + " at $ "
                     + Money.shown(stock.price * this.config.perListing())
                     + " ("
                     + totalInAh[0]
                     + " up)."
               );
         }

         return done;
      }
   }

   private AhSeller.Listing listFrom(AhSeller.Stock stock, int hotbar) {
      Wanted product = stock.product;
      this.worker.run(() -> Menus.mc().player.getInventory().setSelectedSlot(hotbar));
      this.worker.sleep(120L);
      int before = this.worker.call(() -> Menus.mc().player.getInventory().getItem(hotbar).getCount());
      long mark = System.currentTimeMillis();
      this.hand.command("ah sell " + Money.exact(stock.price * this.config.perListing()));
      long end = mark + 15000L;
      boolean confirmed = false;
      Object pressedDialog = null;
      int pressedChest = -1;

      while (System.currentTimeMillis() < end) {
         if (this.tooManyAt >= mark) {
            this.hand.closeScreen();
            return AhSeller.Listing.FULL;
         }

         if (this.listedAt >= mark) {
            this.worker.sleep(150L);
            this.hand.closeScreen();
            return AhSeller.Listing.LISTED;
         }

         if (this.errorAt >= mark) {
            this.hand.closeScreen();
            return AhSeller.Listing.FAILED;
         }

         long elapsed = System.currentTimeMillis() - mark;
         Dialogs.View view = this.worker.call(Dialogs::current);
         if (view != null) {
            if (view.dialog() != pressedDialog && (view.titled("sure") || view.titled("confirm") || view.titled("sell"))) {
               Dialogs.Button yes = view.labelled("yes", "confirm");
               if (yes != null) {
                  this.hand.waitTurn();
                  if (this.worker.call(() -> Dialogs.press(view, yes, Map.of()))) {
                     this.hand.rest();
                     pressedDialog = view.dialog();
                     confirmed = true;
                     this.worker.sleep(250L);
                     continue;
                  }
               }
            }
         } else if (this.worker.call(Menus::containerOpen)) {
            Menus.Ref menu = this.worker.call(() -> Menus.ref(title -> !Ah.isAuction(title)));
            ItemStack button = this.worker.call(() -> Menus.slot(15));
            if (menu != null && menu.syncId() != pressedChest && !button.isEmpty() && Menus.path(button).contains("lime") && this.hand.click(menu, 15)) {
               pressedChest = menu.syncId();
               confirmed = true;
               this.worker.sleep(250L);
               continue;
            }
         } else {
            ItemStack held = this.worker.call(() -> Menus.mc().player.getInventory().getItem(hotbar).copy());
            boolean gone = !product.matches(held) || held.getCount() < before;
            if (gone && (confirmed || elapsed > 2000L)) {
               this.hand.closeScreen();
               return AhSeller.Listing.LISTED;
            }
         }

         this.worker.sleep(50L);
      }

      this.hand.closeScreen();
      this.say.warn("Listing timed out.");
      return AhSeller.Listing.FAILED;
   }

   private int stage(Wanted product) {
      int n = this.config.perListing();
      boolean opened = false;

      try {
         for (int attempt = 0; attempt < 12; attempt++) {
            AhSeller.Plan plan = this.worker.call(() -> planStage(product, n));
            if (plan.clicks().isEmpty()) {
               return plan.hotbar();
            }

            if (!opened) {
               this.hand.openInventory();
               this.worker.sleep(150L);
               opened = true;
            }

            for (AhSeller.Click click : plan.clicks()) {
               if (!this.hand.clickInventory(click.slot(), click.button(), click.type())) {
                  return -1;
               }
            }
         }

         return -1;
      } finally {
         if (opened) {
            this.hand.closeScreen();
         }
      }
   }

   private static AhSeller.Plan planStage(Wanted product, int n) {
      LocalPlayer player = Menus.mc().player;
      Inventory inventory = player.getInventory();
      AhSeller.Plan none = new AhSeller.Plan(-1, List.of());
      if (!player.inventoryMenu.getCarried().isEmpty()) {
         int empty = emptyMain(inventory);
         return empty < 0 ? none : new AhSeller.Plan(-1, List.of(new AhSeller.Click(menuSlot(empty), 0, ClickType.PICKUP)));
      } else {
         for (int h = 0; h < 9; h++) {
            ItemStack stack = inventory.getItem(h);
            if (product.matches(stack) && stack.getCount() == n) {
               return new AhSeller.Plan(h, List.of());
            }
         }

         int target = -1;

         for (int hx = 0; hx < 9 && target < 0; hx++) {
            ItemStack stack = inventory.getItem(hx);
            if (product.matches(stack) && stack.getCount() < n) {
               target = hx;
            }
         }

         for (int hxx = 0; hxx < 9 && target < 0; hxx++) {
            if (inventory.getItem(hxx).isEmpty()) {
               target = hxx;
            }
         }

         if (target < 0) {
            int victim = -1;

            for (int hxxx = 8; hxxx >= 0 && victim < 0; hxxx--) {
               if (!product.matches(inventory.getItem(hxxx))) {
                  victim = hxxx;
               }
            }

            if (victim < 0) {
               victim = 8;
            }

            return emptyMain(inventory) < 0 ? none : new AhSeller.Plan(-1, List.of(new AhSeller.Click(menuSlot(victim), 0, ClickType.QUICK_MOVE)));
         } else {
            int have = product.matches(inventory.getItem(target)) ? inventory.getItem(target).getCount() : 0;
            int source = -1;
            int best = 0;

            for (int index = 0; index < 36; index++) {
               if (index != target) {
                  ItemStack stack = inventory.getItem(index);
                  if (product.matches(stack) && stack.getCount() > best) {
                     best = stack.getCount();
                     source = index;
                  }
               }
            }

            if (source < 0) {
               return none;
            } else {
               int need = n - have;
               List<AhSeller.Click> clicks = new ArrayList<>();
               clicks.add(new AhSeller.Click(menuSlot(source), 0, ClickType.PICKUP));
               if (best <= need) {
                  clicks.add(new AhSeller.Click(menuSlot(target), 0, ClickType.PICKUP));
               } else {
                  for (int i = 0; i < need; i++) {
                     clicks.add(new AhSeller.Click(menuSlot(target), 1, ClickType.PICKUP));
                  }

                  clicks.add(new AhSeller.Click(menuSlot(source), 0, ClickType.PICKUP));
               }

               return new AhSeller.Plan(-1, clicks);
            }
         }
      }
   }

   private static int menuSlot(int inventoryIndex) {
      return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
   }

   private static int emptyMain(Inventory inventory) {
      for (int i = 9; i < 36; i++) {
         if (inventory.getItem(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   public void waitSalesCheck() {
      long until = System.currentTimeMillis() + this.config.salesCheckMs();

      while (System.currentTimeMillis() < until) {
         this.worker.sleep(Math.min(1000L, until - System.currentTimeMillis()));
      }
   }

   public void onChat(String line, List<AhSeller.Stock> stocks) {
      long now = System.currentTimeMillis();
      if (line.contains("too many listed items")) {
         this.tooManyAt = now;
      }

      if (line.contains("already bought")) {
         this.alreadyBoughtAt = now;
      }

      if (line.startsWith("you listed")) {
         this.listedAt = now;
      }

      if (line.contains("you cannot do this") || line.contains("you need to wait another") || line.contains("don't have enough")) {
         this.errorAt = now;
      }

      int bought = line.indexOf(" bought your ");
      if (bought > 0) {
         String rest = line.substring(bought + " bought your ".length());
         int forAt = rest.lastIndexOf(" for ");
         String itemPart = forAt > 0 ? rest.substring(0, forAt) : rest;
         AhSeller.Stock stock = null;

         for (AhSeller.Stock candidate : stocks) {
            if (nameMatches(candidate.product, itemPart, false)) {
               stock = candidate;
               break;
            }
         }

         if (stock == null) {
            for (AhSeller.Stock candidatex : stocks) {
               if (nameMatches(candidatex.product, itemPart, true)) {
                  stock = candidatex;
                  break;
               }
            }
         }

         if (stock != null) {
            List<Double> money = forAt > 0 ? Money.amounts(rest.substring(forAt)) : List.of();
            long amount = money.isEmpty() ? 0L : Math.round(money.getFirst());
            stock.sold.incrementAndGet();
            stock.chatSales.incrementAndGet();
            stock.earned.addAndGet(amount);
            this.say
               .info(
                  "Sold "
                     + stock.product.displayName
                     + (amount > 0L ? " for $ " + Money.shown(amount) : "")
                     + " ("
                     + stock.sold.get()
                     + " in all, about $ "
                     + Money.shown(stock.earned.get())
                     + ")."
               );
         }
      }
   }

   public static boolean nameMatches(Wanted product, String label, boolean loose) {
      String key = Menus.key(label);
      String byId = Menus.key(BuiltInRegistries.ITEM.getKey(product.item).getPath());
      String byName = Menus.key(product.displayName);
      return !key.equals(byId) && !key.equals(byName) ? loose && (key.endsWith(byId) || !byName.isEmpty() && key.endsWith(byName)) : true;
   }

   public static String summary(List<AhSeller.Stock> stocks) {
      int listed = 0;
      int sold = 0;
      long earned = 0L;

      for (AhSeller.Stock stock : stocks) {
         listed += stock.listed.get();
         sold += stock.sold.get();
         earned += stock.earned.get();
      }

      return "Listed " + listed + ", sold " + sold + ", earned about $ " + Money.shown(earned) + ".";
   }

   static void log(String message) {
      SpecterClientAddon.LOG.info("AH: {}", message);
   }

   private record Click(int slot, int button, ClickType type) {
   }

   public record Config(
      long undercut,
      long priceCheckMs,
      long salesCheckMs,
      int perListing,
      int maxListings,
      long listingDelayMs,
      double relistGap,
      double ignoreDrops,
      int collectSlots,
      long orderCheckMs
   ) {
   }

   private static enum Listing {
      LISTED,
      FULL,
      FAILED;
   }

   private record Plan(int hotbar, List<AhSeller.Click> clicks) {
   }

   public static final class Problem extends RuntimeException {
      public Problem(String message) {
         super(message, null, false, false);
      }
   }

   public static final class Stock {
      public final Wanted product;
      public final long minPrice;
      public final boolean fixedPrice;
      public volatile long price;
      long lastPriceCheck;
      public volatile int inAh;
      public final AtomicInteger listed = new AtomicInteger();
      public final AtomicInteger sold = new AtomicInteger();
      final AtomicInteger chatSales = new AtomicInteger();
      public final AtomicLong earned = new AtomicLong();

      public Stock(Wanted product, long minPrice, boolean fixedPrice) {
         this.product = product;
         this.minPrice = minPrice;
         this.fixedPrice = fixedPrice;
         this.price = fixedPrice ? minPrice : -1L;
      }
   }
}
