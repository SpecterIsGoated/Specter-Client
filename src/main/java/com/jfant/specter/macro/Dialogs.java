package com.jfant.specter.macro;

import com.jfant.specter.mixin.DialogScreenAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.ButtonListDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.SimpleDialog;
import net.minecraft.server.dialog.action.Action.ValueGetter;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.world.item.ItemStack;

public final class Dialogs {
   private Dialogs() {
   }

   public static Dialogs.View current() {
      if (!(Minecraft.getInstance().gui.screen() instanceof DialogScreen<?> screen)) {
         return null;
      } else {
         Dialog dialog = ((DialogScreenAccessor)screen).specter$dialog();
         if (dialog == null) {
            return null;
         } else {
            List<ActionButton> raw = new ArrayList<>();
            if (dialog instanceof MultiActionDialog multi) {
               raw.addAll(multi.actions());
            }

            if (dialog instanceof SimpleDialog simple) {
               raw.addAll(simple.mainActions());
            }

            if (dialog instanceof ButtonListDialog columns) {
               columns.exitAction().ifPresent(raw::add);
            }

            List<Dialogs.Button> buttons = new ArrayList<>(raw.size());

            for (ActionButton data : raw) {
               Component label = data.button().label();
               String text = visibleText(label);
               buttons.add(new Dialogs.Button(data, Menus.normalize(text), Menus.key(text), label));
            }

            List<String> body = new ArrayList<>();
            List<ItemStack> items = new ArrayList<>();

            for (DialogBody part : dialog.common().body()) {
               if (part instanceof PlainMessage message) {
                  body.add(Menus.normalize(visibleText(message.contents())));
               }

               if (part instanceof ItemBody item) {
                  items.add(item.item());
                  item.description().ifPresent(message -> body.add(Menus.normalize(visibleText(message.contents()))));
               }
            }

            List<String> textInputs = new ArrayList<>();

            for (Input input : dialog.common().inputs()) {
               if (input.control() instanceof TextInput) {
                  textInputs.add(input.key());
               }
            }

            return new Dialogs.View(dialog, Menus.normalize(visibleText(dialog.common().title())), buttons, body, textInputs, items);
         }
      }
   }

   public static boolean press(Dialogs.View view, Dialogs.Button button, Map<String, String> text) {
      if (Minecraft.getInstance().gui.screen() instanceof DialogScreen<?> screen) {
         if (((DialogScreenAccessor)screen).specter$dialog() != view.dialog()) {
            return false;
         } else {
            Map<String, ValueGetter> values = new HashMap<>();

            for (Input input : view.dialog().common().inputs()) {
               if (input.control() instanceof TextInput box) {
                  values.put(input.key(), ValueGetter.of(text.getOrDefault(input.key(), box.initial())));
               } else if (input.control() instanceof BooleanInput toggle) {
                  values.put(input.key(), ValueGetter.of(toggle.initial() ? toggle.onTrue() : toggle.onFalse()));
               }
            }

            Optional<ClickEvent> event = button.raw().action().flatMap(action -> action.createAction(values));
            screen.runAction(event);
            return true;
         }
      } else {
         return false;
      }
   }

   public static void close() {
      if (Minecraft.getInstance().gui.screen() instanceof DialogScreen<?> screen) {
         screen.onClose();
      }
   }

   public static String visibleText(Component text) {
      StringBuilder out = new StringBuilder();
      append(text, out);
      return out.toString();
   }

   private static void append(Component text, StringBuilder out) {
      if (!(text.getContents() instanceof ObjectContents)) {
         text.getContents().visit(piece -> {
            out.append(piece);
            return Optional.empty();
         });
      }

      for (Component sibling : text.getSiblings()) {
         append(sibling, out);
      }
   }

   public record Button(ActionButton raw, String label, String key, Component component) {
      public boolean green() {
         boolean[] found = new boolean[]{false};
         this.component.visit((style, piece) -> {
            TextColor color = style.getColor();
            if (!piece.isBlank() && color != null && isGreen(color.getValue())) {
               found[0] = true;
            }

            return Optional.empty();
         }, Style.EMPTY);
         return found[0];
      }

      private static boolean isGreen(int rgb) {
         int r = rgb >> 16 & 0xFF;
         int g = rgb >> 8 & 0xFF;
         int b = rgb & 0xFF;
         return g >= 192 && r < 144 && b < 144;
      }
   }

   public record View(Dialog dialog, String title, List<Dialogs.Button> buttons, List<String> body, List<String> textInputs, List<ItemStack> items) {
      public Dialogs.Button find(Predicate<Dialogs.Button> test) {
         for (Dialogs.Button button : this.buttons) {
            if (test.test(button)) {
               return button;
            }
         }

         return null;
      }

      public Dialogs.Button labelled(String... words) {
         for (String word : words) {
            String wanted = Menus.key(word);
            Dialogs.Button exact = this.find(button -> button.key().equals(wanted));
            if (exact != null) {
               return exact;
            }
         }

         for (String wordx : words) {
            String wanted = Menus.key(wordx);
            Dialogs.Button partial = this.find(button -> button.key().contains(wanted));
            if (partial != null) {
               return partial;
            }
         }

         return null;
      }

      public boolean titled(String text) {
         return this.title.contains(text);
      }

      public String describe() {
         List<String> labels = new ArrayList<>();

         for (Dialogs.Button button : this.buttons) {
            if (!button.label().isBlank()) {
               labels.add(button.label());
            }

            if (labels.size() >= 12) {
               break;
            }
         }

         return "'" + this.title + "' buttons: " + (labels.isEmpty() ? "(none)" : String.join(", ", labels));
      }
   }
}
