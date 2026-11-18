package com.jfant.specter.gui.screen;

import com.jfant.specter.core.settings.BlockListSetting;
import com.jfant.specter.core.settings.ItemListSetting;
import com.jfant.specter.core.settings.ItemSetting;
import com.jfant.specter.core.utils.misc.Names;
import com.jfant.specter.render.PurpleCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class PickerEditor<T> extends Editor {
   private static final int CELL = 18;
   private static final int GAP = 2;
   private static final int SECTION_HEADER = 14;
   private final List<T> all = new ArrayList<>();
   private final List<String> searchKeys = new ArrayList<>();
   private final Function<T, ItemStack> icon;
   private final Function<T, String> name;
   private final Supplier<List<T>> selected;
   private final Supplier<T> current;
   private final Consumer<T> choose;
   private final Runnable changed;
   private final TextField search = new TextField("Search");
   private List<T> shown = new ArrayList<>();
   private int columns = 1;
   private int selectedTop;
   private int allTop;
   private int contentHeight;
   private boolean laidOut;

   private PickerEditor(
      String title,
      Runnable back,
      Iterable<T> entries,
      Function<T, ItemStack> icon,
      Function<T, String> name,
      Supplier<List<T>> selected,
      Supplier<T> current,
      Consumer<T> choose,
      Runnable changed
   ) {
      super(title, back);
      this.icon = icon;
      this.name = name;
      this.selected = selected;
      this.current = current;
      this.choose = choose;
      this.changed = changed;

      for (T entry : entries) {
         this.all.add(entry);
         this.searchKeys.add(name.apply(entry).toLowerCase(Locale.ROOT));
      }

      this.shown.addAll(this.all);
      this.search.onChange = this::refilter;
      this.search.focus();
   }

   static PickerEditor<Item> forItemList(ItemListSetting setting, Runnable back) {
      List<Item> entries = new ArrayList<>();

      for (Item item : BuiltInRegistries.ITEM) {
         if (item != Items.AIR && (setting.filter == null || setting.filter.test(item))) {
            entries.add(item);
         }
      }

      return new PickerEditor<>(
         setting.title,
         back,
         entries,
         itemx -> itemx.getDefaultInstance(),
         itemx -> Names.get(itemx),
         setting::get,
         null,
         itemx -> toggle(setting.get(), itemx),
         setting::onChanged
      );
   }

   static PickerEditor<Block> forBlockList(BlockListSetting setting, Runnable back) {
      List<Block> entries = new ArrayList<>();

      for (Block block : BuiltInRegistries.BLOCK) {
         if (block != Blocks.AIR && (setting.filter == null || setting.filter.test(block))) {
            entries.add(block);
         }
      }

      return new PickerEditor<>(
         setting.title,
         back,
         entries,
         PickerEditor::blockIcon,
         blockx -> Names.get(blockx),
         setting::get,
         null,
         blockx -> toggle(setting.get(), blockx),
         setting::onChanged
      );
   }

   static PickerEditor<Item> forItem(ItemSetting setting, Runnable back) {
      List<Item> entries = new ArrayList<>();

      for (Item item : BuiltInRegistries.ITEM) {
         if (item != Items.AIR && (setting.filter == null || setting.filter.test(item))) {
            entries.add(item);
         }
      }

      return new PickerEditor<>(
         setting.title, back, entries, itemx -> itemx.getDefaultInstance(), itemx -> Names.get(itemx), null, setting::get, setting::set, () -> {}
      );
   }

   private static ItemStack blockIcon(Block block) {
      Item item = block.asItem();
      return (item == Items.AIR ? Items.BARRIER : item).getDefaultInstance();
   }

   private static <E> void toggle(List<E> list, E entry) {
      if (!list.remove(entry)) {
         list.add(entry);
      }
   }

   private void refilter() {
      String query = this.search.text.trim().toLowerCase(Locale.ROOT);
      this.shown = new ArrayList<>();

      for (int i = 0; i < this.all.size(); i++) {
         if (query.isEmpty() || this.searchKeys.get(i).contains(query)) {
            this.shown.add(this.all.get(i));
         }
      }

      this.scroll.reset();
   }

   private int rows(int count) {
      return (count + this.columns - 1) / this.columns;
   }

   @Override
   void body(GuiGraphicsExtractor ctx, int mx, int my) {
      this.search.render(ctx, this.x + 6, this.y + 2, this.w - 12, 14);
      int regionTop = this.y + 20;
      int regionHeight = this.h - 20;
      int innerWidth = this.w - 12 - 4;
      this.columns = Math.max(1, (innerWidth + 2) / 20);
      List<T> chosen = this.selected == null ? List.of() : this.selected.get();
      int cursor = 0;
      this.selectedTop = -1;
      if (this.selected != null) {
         this.selectedTop = cursor;
         cursor += 14 + Math.max(1, this.rows(chosen.size())) * 20;
      }

      this.allTop = cursor;
      cursor += 14 + Math.max(1, this.rows(this.shown.size())) * 20;
      this.contentHeight = cursor + 4;
      this.scroll.size(regionHeight, this.contentHeight);
      this.laidOut = true;
      int origin = regionTop - this.scroll.offset();
      int left = this.x + 6;
      ctx.enableScissor(this.x, regionTop, this.x + this.w, regionTop + regionHeight);
      if (this.selected != null) {
         Ui.text(ctx, "Selected (" + chosen.size() + ")", left, origin + this.selectedTop + 3, -1065473);
         if (chosen.isEmpty()) {
            Ui.text(ctx, "nothing yet", left, origin + this.selectedTop + 14 + 5, -4149795);
         }

         this.grid(ctx, chosen, origin + this.selectedTop + 14, left, regionTop, regionHeight, mx, my, chosen, true);
      }

      Ui.text(ctx, this.selected != null ? "Add (" + this.shown.size() + ")" : "Pick one (" + this.shown.size() + ")", left, origin + this.allTop + 3, -1065473);
      this.grid(ctx, this.shown, origin + this.allTop + 14, left, regionTop, regionHeight, mx, my, chosen, false);
      ctx.disableScissor();
      this.scroll.bar(ctx, this.x + this.w - 1, regionTop, regionHeight);
   }

   private void grid(
      GuiGraphicsExtractor ctx, List<T> entries, int top, int left, int regionTop, int regionHeight, int mx, int my, List<T> chosen, boolean isSelectedGrid
   ) {
      T now = this.current == null ? null : this.current.get();
      int firstRow = Math.max(0, (regionTop - top) / 20);
      int lastRow = Math.min(this.rows(entries.size()) - 1, (regionTop + regionHeight - top) / 20);

      for (int row = firstRow; row <= lastRow; row++) {
         for (int col = 0; col < this.columns; col++) {
            int index = row * this.columns + col;
            if (index >= entries.size()) {
               break;
            }

            T entry = entries.get(index);
            int cx = left + col * 20;
            int cy = top + row * 20;
            boolean hover = Ui.in(mx, my, cx, cy, 18, 18) && Ui.in(mx, my, this.x, regionTop, this.w, regionHeight);
            boolean picked = isSelectedGrid || (this.selected != null ? chosen.contains(entry) : entry == now);
            PurpleCard.fillRounded(ctx, cx, cy, cx + 18, cy + 18, 3, 15, picked ? 2030005959 : -2145973978, picked ? 1090481863 : -2145973978);
            int edge = !hover && !picked ? -1333837902 : -37177;
            PurpleCard.strokeRounded(ctx, cx, cy, cx + 18, cy + 18, 3, edge, edge);
            Ui.item(ctx, this.icon.apply(entry), cx + 1, cy + 1);
            if (hover) {
               Ui.hint = this.name.apply(entry);
            }
         }
      }
   }

   @Override
   void bodyClick(int mx, int my, int button) {
      if (!this.search.click(mx, my)) {
         int regionTop = this.y + 20;
         int regionHeight = this.h - 20;
         if (this.laidOut && Ui.in(mx, my, this.x, regionTop, this.w, regionHeight)) {
            int origin = regionTop - this.scroll.offset();
            int left = this.x + 6;
            if (this.selected != null) {
               T hit = this.cellAt(this.selected.get(), origin + this.selectedTop + 14, left, mx, my);
               if (hit != null) {
                  this.choose.accept(hit);
                  this.changed.run();
                  return;
               }
            }

            T hit = this.cellAt(this.shown, origin + this.allTop + 14, left, mx, my);
            if (hit != null) {
               this.choose.accept(hit);
               this.changed.run();
               if (this.selected == null) {
                  this.close();
               }
            }
         }
      }
   }

   private T cellAt(List<T> entries, int top, int left, int mx, int my) {
      if (mx >= left && my >= top) {
         int col = (mx - left) / 20;
         int row = (my - top) / 20;
         if (col < this.columns && (mx - left) % 20 < 18 && (my - top) % 20 < 18) {
            int index = row * this.columns + col;
            return index >= 0 && index < entries.size() ? entries.get(index) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }
}
