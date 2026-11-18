package com.jfant.specter.macro.ah;

import com.jfant.specter.macro.Dialogs;
import com.jfant.specter.macro.Hand;
import com.jfant.specter.macro.MacroThread;
import com.jfant.specter.macro.Menus;
import com.jfant.specter.macro.Money;
import com.jfant.specter.macro.Notes;
import com.jfant.specter.macro.Wanted;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Orders {
   private static final int YOUR_ORDERS_BUTTON = 51;
   private static final int ORDER_SLOTS = 45;
   private static final int DEFAULT_COLLECT_BUTTON = 13;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long DIALOG_TIMEOUT_MS = 8000L;
   private static final long PICK_TIMEOUT_MS = 5000L;
   private final AhSeller ah;
   private final MacroThread worker;
   private final Hand hand;
   private final Notes say;
   private final Wanted product;
   private final Orders.Spec spec;
   private long nextCheckAt;
   private volatile long orderedAt;
   private volatile long poorAt;

   public Orders(AhSeller ah, Wanted product, Orders.Spec spec, Notes say) {
      this.ah = ah;
      this.worker = ah.worker;
      this.hand = ah.hand;
      this.product = product;
      this.spec = spec;
      this.say = say;
   }

   public void onChat(String line) {
      long now = System.currentTimeMillis();
      if (line.startsWith("you ordered")) {
         this.orderedAt = now;
      }

      if (line.contains("enough money")) {
         this.poorAt = now;
      }
   }

   public boolean run() {
      long now = System.currentTimeMillis();
      if (now < this.nextCheckAt) {
         return false;
      } else {
         this.nextCheckAt = now + this.ah.config.orderCheckMs();
         if (!this.openYourOrders()) {
            return false;
         } else {
            int own = this.worker.call(() -> Menus.find(stack -> stack.is(this.product.item), 45));
            if (own >= 0) {
               return this.collect(own) > 0;
            } else {
               this.hand.closeScreen();
               long price = this.orderPrice();
               if (price <= 0L) {
                  return false;
               } else {
                  this.create(price);
                  return false;
               }
            }
         }
      }
   }

   private static boolean isOrdersTitle(String title) {
      return title.contains("order");
   }

   private static boolean isRootTitle(String title) {
      return title.contains("order") && !title.contains("your orders") && !Ah.isAuction(title);
   }

   private static boolean isYourOrdersTitle(String title) {
      return title.contains("your orders");
   }

   private static boolean isRootNow() {
      return Menus.containerOpen() && isRootTitle(Menus.title());
   }

   private static boolean isYourOrdersNow() {
      return Menus.containerOpen() && (isYourOrdersTitle(Menus.title()) || Menus.find(Orders::isNewOrderPane, 45) >= 0);
   }

   private static boolean isNewOrderPane(ItemStack stack) {
      return stack.is(Items.STAINED_GLASS_PANE.gray()) && Menus.name(stack).contains("new order");
   }

   private boolean openYourOrders() {
      this.hand.closeScreen();
      this.worker.sleep(150L);
      this.hand.command("orders");
      if (!this.worker.waitFor(Orders::isRootNow, 10000L)) {
         this.say.warn("Could not open /orders.");
         this.hand.closeScreen();
         return false;
      } else {
         Menus.Ref root = this.worker.call(() -> Menus.ref(Orders::isRootTitle));
         String before = this.worker.call(Menus::fingerprint);
         if (root != null && this.hand.click(root, 51)) {
            if (this.worker.waitFor(Orders::isYourOrdersNow, 2500L)) {
               return true;
            } else if (this.worker.waitFor(() -> !Menus.fingerprint().equals(before), 1000L)) {
               this.worker.sleep(150L);
               return true;
            } else {
               this.say.warn("Could not open Your Orders.");
               this.hand.closeScreen();
               return false;
            }
         } else {
            this.say.warn("Could not open Your Orders.");
            this.hand.closeScreen();
            return false;
         }
      }
   }

   private int collect(int ownSlot) {
      Menus.Ref yours = this.worker.call(() -> Menus.ref(Orders::isOrdersTitle));
      String before = this.worker.call(Menus::fingerprint);
      if (yours != null && this.hand.click(yours, ownSlot)) {
         boolean opened = this.worker.waitFor(() -> Menus.containerOpen() && !isYourOrdersNow() && !Menus.fingerprint().equals(before), 2000L);
         if (!opened) {
            this.say.warn("Your order did not open.");
            this.hand.closeScreen();
            return 0;
         } else {
            this.worker.sleep(1000L);
            int[] controls = this.worker.call(() -> {
               List<ItemStack> stacksx = Menus.contents();
               int collectAt = -1;

               for (int ix = 0; ix < stacksx.size(); ix++) {
                  ItemStack stackx = stacksx.get(ix);
                  if (!stackx.isEmpty()) {
                     String name = Menus.name(stackx);
                     if (stackx.is(Items.DYED_TERRACOTTA.red()) || name.contains("cancel")) {
                        return new int[]{1, -1};
                     }

                     if (collectAt < 0 && name.contains("collect")) {
                        collectAt = ix;
                     }
                  }
               }

               if (collectAt < 0 && stacksx.size() > 13 && !stacksx.get(13).isEmpty()) {
                  collectAt = 13;
               }

               return new int[]{0, collectAt};
            });
            if (controls[0] == 1) {
               this.say.info("Your " + this.product.displayName + " order is still filling.");
               this.hand.closeScreen();
               return 0;
            } else if (controls[1] < 0) {
               this.say.warn("No collect button in your order.");
               this.hand.closeScreen();
               return 0;
            } else {
               Menus.Ref order = this.worker.call(() -> Menus.ref(Orders::isOrdersTitle));
               String beforeCollect = this.worker.call(Menus::fingerprint);
               if (order != null && this.hand.click(order, controls[1])) {
                  this.worker.sleep(250L);
                  this.worker.waitFor(() -> !Menus.fingerprint().equals(beforeCollect), 2000L);
                  this.worker.sleep(150L);
                  int collected = 0;

                  for (int pass = 0; pass < 6 && this.worker.call(Menus::containerOpen) && !this.ah.inventoryCapReached(this.product); pass++) {
                     boolean clicked = false;
                     List<ItemStack> stacks = this.worker.call(Menus::contents);

                     for (int i = 0; i < stacks.size(); i++) {
                        ItemStack stack = stacks.get(i);
                        if (!stack.isEmpty() && stack.is(this.product.item)) {
                           if (this.ah.inventoryCapReached(this.product)) {
                              break;
                           }

                           Menus.Ref menu = this.worker.call(() -> Menus.ref(Orders::isOrdersTitle));
                           if (menu == null || !this.hand.click(menu, i, 0, ContainerInput.QUICK_MOVE)) {
                              break;
                           }

                           collected += stack.getCount();
                           clicked = true;
                        }
                     }

                     if (!clicked) {
                        break;
                     }

                     this.worker.sleep(60L);
                  }

                  this.hand.closeScreen();
                  if (collected > 0) {
                     this.say.info("Collected " + collected + "x " + this.product.displayName + " from your order.");
                  }

                  return collected;
               } else {
                  this.hand.closeScreen();
                  return 0;
               }
            }
         }
      } else {
         this.hand.closeScreen();
         return 0;
      }
   }

   private long orderPrice() {
      long price;
      if (!this.spec.beatTopOrder()) {
         price = this.spec.fixedPrice();
      } else {
         double top = -1.0;

         for (int attempt = 1; attempt <= 3 && top <= 0.0; attempt++) {
            this.hand.closeScreen();
            this.worker.sleep(attempt == 1 ? 800L : 1400L);
            this.hand.command("order " + this.product.search);
            top = this.waitTopOrder(attempt == 3 ? 7000L : 4500L);
         }

         this.hand.closeScreen();
         if (top <= 0.0) {
            this.say
               .warn(
                  "No order price found for "
                     + this.product.describe()
                     + (this.product.enchants.isEmpty() ? ", trying later." : ". Use a fixed order price for enchanted items.")
               );
            return -1L;
         }

         price = (long)Math.floor(top) + this.spec.bidIncrement();
         this.say.info("Top order is $ " + Money.shown(top) + ", ordering at $ " + Money.shown(price) + " each.");
      }

      if (this.spec.maxPrice() > 0L && price > this.spec.maxPrice()) {
         this.say.info("Capped at your max order price, $ " + Money.shown(this.spec.maxPrice()) + ".");
         price = this.spec.maxPrice();
      }

      return price;
   }

   private double waitTopOrder(long timeoutMs) {
      long end = System.currentTimeMillis() + timeoutMs;

      while (System.currentTimeMillis() < end) {
         double price = this.worker.call(() -> {
            if (Menus.containerOpen() && isRootTitle(Menus.title())) {
               for (ItemStack stack : Menus.contents()) {
                  if (!stack.isEmpty() && stack.is(this.product.item) && (this.product.enchants.isEmpty() || this.product.matches(stack))) {
                     return Money.orderPrice(stack);
                  }
               }

               return -1.0;
            } else {
               return -1.0;
            }
         });
         if (price > 0.0) {
            return price;
         }

         this.worker.sleep(50L);
      }

      return -1.0;
   }

   private static AhSeller.Problem fail(String message) {
      return new AhSeller.Problem(message);
   }

   private void create(long price) {
      if (this.openYourOrders()) {
         int pane = this.worker.call(() -> Menus.find(Orders::isNewOrderPane, 45));
         if (pane < 0) {
            this.say.warn("No free order slot (no New Order button).");
            this.hand.closeScreen();
         } else {
            Menus.Ref yours = this.worker.call(() -> Menus.ref(Orders::isOrdersTitle));
            if (yours != null && this.hand.click(yours, pane)) {
               Dialogs.View view = this.waitDialog(v -> v.titled("item"), 10000L);
               if (view == null) {
                  throw fail("Choose Item did not open");
               } else {
                  if (!view.textInputs().isEmpty()) {
                     view = this.press(view, view.labelled("search"), Map.of(view.textInputs().getFirst(), this.product.search));
                  }

                  Dialogs.Button item = view.find(button -> this.nameMatches(button.label(), false));
                  if (item == null) {
                     item = this.closestLoose(view);
                  }

                  if (item == null) {
                     throw fail(this.product.displayName + " is not in the search results, " + view.describe());
                  } else {
                     view = this.press(view, item, Map.of());
                     this.checkItem(view);
                     if (view.titled("enchant")) {
                        view = this.pickEnchants(view);
                     } else if (!this.product.enchants.isEmpty()) {
                        throw fail("No enchantment menu for " + this.product.displayName + ", " + view.describe());
                     }

                     if (!view.titled("how many") && !view.titled("amount")) {
                        throw fail("Expected the amount menu, got " + view.describe());
                     } else {
                        view = this.press(view, view.labelled("next", "continue"), textFor(view, Integer.toString(this.spec.amount())));
                        if (!view.titled("price")) {
                           throw fail("Expected the price menu, got " + view.describe());
                        } else {
                           view = this.press(view, view.labelled("review order", "review", "next"), textFor(view, Money.exact(price)));
                           if (!view.titled("review")) {
                              throw fail("Expected the review menu, got " + view.describe());
                           } else {
                              this.checkItem(view);
                              final Dialogs.View review = view;
                              String problem = this.checkReview(view, price);
                              if (problem != null) {
                                 Dialogs.Button cancel = view.labelled("cancel");
                                 if (cancel != null) {
                                    this.hand.waitTurn();
                                    this.worker.call(() -> Dialogs.press(review, cancel, Map.of()));
                                    this.hand.rest();
                                 }

                                 throw fail("Order review did not match (" + problem + "), cancelled");
                              } else {
                                 long mark = System.currentTimeMillis();
                                 Dialogs.Button createButton = view.labelled("create order", "create", "confirm");
                                 if (createButton == null) {
                                    throw fail("No Create Order button, " + view.describe());
                                 } else {
                                    this.hand.waitTurn();
                                    if (!this.worker.call(() -> Dialogs.press(review, createButton, Map.of()))) {
                                       throw fail("The review menu closed");
                                    } else {
                                       this.hand.rest();
                                       this.worker.waitFor(() -> {
                                          if (this.orderedAt < mark && this.poorAt < mark) {
                                             Dialogs.View now = Dialogs.current();
                                             return now == null || now.dialog() != review.dialog();
                                          } else {
                                             return true;
                                          }
                                       }, 8000L);
                                       if (this.poorAt >= mark) {
                                          throw new MacroThread.Halt("not enough money for the order");
                                       } else {
                                          this.worker.sleep(300L);
                                          this.hand.closeScreen();
                                          this.say
                                             .info("Ordered " + this.spec.amount() + "x " + this.product.describe() + " at $ " + Money.shown(price) + " each.");
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            } else {
               throw fail("Your Orders closed before New Order");
            }
         }
      }
   }

   private boolean nameMatches(String label, boolean loose) {
      return AhSeller.nameMatches(this.product, label, loose);
   }

   private Dialogs.Button closestLoose(Dialogs.View view) {
      Dialogs.Button best = null;

      for (Dialogs.Button button : view.buttons()) {
         if (this.nameMatches(button.label(), true) && (best == null || button.key().length() < best.key().length())) {
            best = button;
         }
      }

      return best;
   }

   private void checkItem(Dialogs.View view) {
      if (!view.items().isEmpty()) {
         for (ItemStack shown : view.items()) {
            if (shown.is(this.product.item)) {
               return;
            }
         }

         String picked = view.items().getFirst().getHoverName().getString();
         this.worker.run(Dialogs::close);
         throw fail("Picked " + picked + " instead of " + this.product.displayName + ", cancelled");
      }
   }

   private Dialogs.View pickEnchants(Dialogs.View view) {
      if (this.product.enchants.isEmpty()) {
         return this.press(view, view.labelled("skip enchantments", "skip"), Map.of());
      } else {
         for (Wanted.Enchant wanted : this.product.enchants) {
            Dialogs.Button level = levelButton(view, wanted);
            if (level == null) {
               throw fail(wanted.describe() + " is not offered, " + view.describe());
            }

            if (!level.green()) {
               Dialogs.View before = view;
               this.hand.waitTurn();
               if (!this.worker.call(() -> Dialogs.press(before, level, Map.of()))) {
                  throw fail("The menu changed before picking " + wanted.describe());
               }

               this.hand.rest();
               Dialogs.View next = this.waitDialog(v -> v.dialog() != before.dialog(), 5000L);
               if (next == null) {
                  next = this.worker.call(Dialogs::current);
               }

               if (next == null) {
                  throw fail("The enchantment menu closed after picking " + wanted.describe());
               }

               view = next;
               Dialogs.Button again = levelButton(next, wanted);
               if (again != null && !again.green()) {
                  this.say.warn("Could not confirm " + wanted.describe() + " was picked, continuing.");
               }
            }
         }

         return this.press(view, view.labelled("continue", "confirm", "done", "next"), Map.of());
      }
   }

   static Dialogs.Button levelButton(Dialogs.View view, Wanted.Enchant wanted) {
      String header = "";

      for (Dialogs.Button button : view.buttons()) {
         int level = Wanted.roman(button.label());
         if (level > 0) {
            if (header.equals(wanted.key()) && level == wanted.level()) {
               return button;
            }
         } else if (!button.label().isBlank()) {
            header = Menus.key(Wanted.withoutRoman(button.label()));
         }
      }

      return null;
   }

   private String checkReview(Dialogs.View view, long price) {
      for (String line : view.body()) {
         if (line.startsWith("amount")) {
            List<Double> values = Money.amounts(line);
            if (!values.isEmpty() && Math.abs(values.getFirst() - this.spec.amount()) >= Money.displayStep(line)) {
               return line;
            }
         }
      }

      for (String linex : view.body()) {
         if (linex.startsWith("price")) {
            List<Double> values = Money.amounts(linex);
            if (!values.isEmpty() && Math.abs(values.getFirst() - price) > Money.displayStep(linex)) {
               return linex;
            }
         }
      }

      return null;
   }

   private static Map<String, String> textFor(Dialogs.View view, String value) {
      return view.textInputs().isEmpty() ? Map.of() : Map.of(view.textInputs().getFirst(), value);
   }

   private Dialogs.View press(Dialogs.View view, Dialogs.Button button, Map<String, String> text) {
      if (button == null) {
         throw fail("Button missing, " + view.describe());
      } else {
         if (!text.isEmpty()) {
            int length = text.values().stream().mapToInt(String::length).sum();
            this.worker.sleep(this.hand.pace().nextGap(250.0 + 90.0 * length));
         }

         this.hand.waitTurn();
         if (!this.worker.call(() -> Dialogs.press(view, button, text))) {
            throw fail("The menu changed before clicking " + button.label());
         } else {
            this.hand.rest();
            Dialogs.View next = this.waitDialog(v -> v.dialog() != view.dialog(), 8000L);
            if (next == null) {
               throw fail("No answer after clicking '" + button.label() + "'");
            } else {
               return next;
            }
         }
      }
   }

   private Dialogs.View waitDialog(Predicate<Dialogs.View> test, long timeoutMs) {
      long end = System.currentTimeMillis() + timeoutMs;

      while (System.currentTimeMillis() < end) {
         Dialogs.View view = this.worker.call(Dialogs::current);
         if (view != null && test.test(view)) {
            return view;
         }

         this.worker.sleep(50L);
      }

      return null;
   }

   public record Spec(boolean beatTopOrder, long fixedPrice, long bidIncrement, long maxPrice, int amount) {
   }
}
