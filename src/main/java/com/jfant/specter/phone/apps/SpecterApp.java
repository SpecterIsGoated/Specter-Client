package com.jfant.specter.phone.apps;

import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.HudElement;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.jfant.specter.core.systems.modules.Category;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.gui.screen.SpecterScreen;
import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.gfx.Colors;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import com.jfant.specter.phone.ui.TextInput;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Specter on the phone: every module by category (search, switch on/off, edit settings), HUD elements, and the
 * client's own preferences. It edits the real Specter modules and settings - nothing is copied - so a change here
 * is the same as a change in the click GUI. Setting types the phone can't edit open the full Specter GUI.
 */
public final class SpecterApp extends PhoneApplication {
   private enum Page {
      HOME,
      CATEGORY,
      MODULE,
      HUD,
      CLIENT
   }

   private final Deque<Object[]> stack = new ArrayDeque<>();
   private Page page = Page.HOME;
   private Category category;
   private Module module;
   private float contentH = 300;

   private void go(Page p, Category c, Module m) {
      this.stack.push(new Object[]{this.page, this.category, this.module});
      this.page = p;
      this.category = c;
      this.module = m;
   }

   @Override
   public boolean back() {
      if (this.stack.isEmpty()) return false;
      Object[] s = this.stack.pop();
      this.page = (Page)s[0];
      this.category = (Category)s[1];
      this.module = (Module)s[2];
      return true;
   }

   private void openScreen(Supplier<Screen> screen) {
      PhoneOS.get().close();
      Minecraft.getInstance().gui.setScreen(screen.get());
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      String title = switch (this.page) {
         case HOME -> "Specter";
         case CATEGORY -> this.category.name;
         case MODULE -> this.module.title;
         case HUD -> "HUD";
         case CLIENT -> "Client";
      };
      Runnable back = this.page == Page.HOME ? null : this::back;
      float top = ui.header(title, back, this.page == Page.HOME ? Glyph.GRID : null, this.page == Page.HOME ? () -> this.openScreen(SpecterScreen::new) : null);
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      ScrollView sv = ui.scroll(this.page + ":" + (this.category == null ? "" : this.category.name) + ":" + (this.module == null ? "" : this.module.name));
      sv.begin(ui, 0, top, c.width, bottom - top, this.contentH);
      float y = top + 4;
      float end = switch (this.page) {
         case HOME -> this.home(c, ui, y);
         case CATEGORY -> this.modules(c, ui, y, Modules.get().getGroup(this.category));
         case MODULE -> this.settings(c, ui, y, this.module.settings, this.module);
         case HUD -> this.hud(c, ui, y);
         case CLIENT -> this.settings(c, ui, y, Config.get().settings, null);
      };
      this.contentH = end - top + 16;
      sv.end(ui);
   }

   private float home(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      float x = 10, w = c.width - 20;
      TextInput search = ui.field("search");
      search.placeholder = "Search modules";
      ui.textField(search, x, y, w, 24);
      y += 32;
      String q = search.get().trim().toLowerCase(Locale.ROOT);
      if (!q.isEmpty()) {
         List<Module> hits = new ArrayList<>();
         for (Module m : Modules.get().getAll()) {
            if (m.title.toLowerCase(Locale.ROOT).contains(q) || m.name.contains(q)) hits.add(m);
         }
         return this.modules(c, ui, y, hits);
      }
      int active = Modules.get().getActive().size();
      ui.card(x, y, w, 44);
      c.text(active + " modules on", x + 12, y + 10, PhoneTheme.HEADLINE, t.text());
      c.text(Modules.get().getCount() + " installed", x + 12, y + 26, PhoneTheme.SMALL, t.textDim());
      y += 52;
      ui.section("Categories", x + 4, y);
      y += 10;
      List<Category> cats = new ArrayList<>();
      for (Category cat : Modules.loopCategories()) cats.add(cat);
      ui.card(x, y, w, cats.size() * 30);
      for (int i = 0; i < cats.size(); i++) {
         Category cat = cats.get(i);
         float ry = y + i * 30;
         if (i > 0) ui.divider(x + 10, ry, w - 10);
         long on = Modules.get().getGroup(cat).stream().filter(Module::isActive).count();
         ui.row(x, ry, w, cat.name, null, () -> this.go(Page.CATEGORY, cat, null));
         String count = on + "/" + Modules.get().getGroup(cat).size();
         c.textRight(count, x + w - 22, ry + 12 - PhoneTheme.SMALL / 2 + 2, PhoneTheme.SMALL, t.textFaint());
      }
      y += cats.size() * 30 + 10;
      ui.card(x, y, w, 60);
      ui.row(x, y, w, "HUD elements", null, () -> this.go(Page.HUD, null, null));
      ui.divider(x + 10, y + 30, w - 10);
      ui.row(x, y + 30, w, "Client preferences", null, () -> this.go(Page.CLIENT, null, null));
      return y + 60;
   }

   private float modules(PhoneCanvas c, PhoneUi ui, float y, List<Module> list) {
      PhoneTheme t = c.theme();
      float x = 10, w = c.width - 20, rowH = 36;
      if (list.isEmpty()) {
         c.textCentered("No modules", c.width / 2, y + 20, PhoneTheme.BODY, t.textDim());
         return y + 40;
      }
      ui.card(x, y, w, list.size() * rowH);
      for (int i = 0; i < list.size(); i++) {
         Module m = list.get(i);
         float ry = y + i * rowH;
         if (i > 0) ui.divider(x + 10, ry, w - 10);
         boolean hover = ui.hovered(x, ry, w - 40, rowH);
         if (hover) c.rect(x, ry, w - 40, rowH, Colors.alpha(t.accentSoft(), 0.5F));
         c.text(c.fit(m.title, w - 56, PhoneTheme.BODY), x + 10, ry + 9, PhoneTheme.BODY, t.text());
         c.text(c.fit(m.description, w - 56, PhoneTheme.CAPTION), x + 10, ry + 22, PhoneTheme.CAPTION, t.textDim());
         ui.onClick(x, ry, w - 40, rowH, () -> this.go(Page.MODULE, this.category, m));
         ui.toggle("m:" + m.name, x + w - 34, ry + (rowH - 15) / 2, m.isActive(), v -> {
            if (m.isActive() != v) m.toggle();
         });
      }
      return y + list.size() * rowH;
   }

   private float settings(PhoneCanvas c, PhoneUi ui, float y, Settings settings, Module owner) {
      PhoneTheme t = c.theme();
      float x = 10, w = c.width - 20;
      if (owner != null) {
         y += c.paragraph(owner.description, x + 4, y, w - 8, PhoneTheme.SMALL, t.textDim(), 5) + 6;
         ui.card(x, y, w, 30);
         c.textMid("Enabled", x + 10, y + 15, PhoneTheme.BODY, t.text());
         ui.toggle("on:" + owner.name, x + w - 36, y + 7.5F, owner.isActive(), v -> {
            if (owner.isActive() != v) owner.toggle();
         });
         y += 40;
      }
      boolean unsupported = false;
      for (SettingGroup group : settings) {
         List<Setting<?>> shown = new ArrayList<>();
         for (Setting<?> s : group) if (s.isVisible()) shown.add(s);
         if (shown.isEmpty()) continue;
         ui.section(group.name, x + 4, y);
         y += 10;
         for (Setting<?> s : shown) {
            float h = this.settingHeight(s);
            ui.card(x, y, w, h);
            if (!this.setting(c, ui, s, x, y, w)) unsupported = true;
            y += h + 4;
         }
         y += 6;
      }
      if (unsupported) {
         ui.button(x, y, w, 24, "More settings in Specter", Glyph.GRID, PhoneUi.Style.SECONDARY, () -> this.openScreen(SpecterScreen::new));
         y += 30;
      }
      return y;
   }

   private float settingHeight(Setting<?> s) {
      if (s instanceof IntSetting || s instanceof DoubleSetting) return 40;
      if (s instanceof EnumSetting<?> e && e.get().getClass().getEnumConstants().length <= 3) return 46;
      return 30;
   }

   /** Draws an editor for a setting; false if this type can only be edited in the Specter GUI. */
   @SuppressWarnings({"unchecked", "rawtypes"})
   private boolean setting(PhoneCanvas c, PhoneUi ui, Setting<?> s, float x, float y, float w) {
      PhoneTheme t = c.theme();
      String key = (this.module == null ? "client" : this.module.name) + ":" + s.name;
      if (s instanceof BoolSetting b) {
         c.textMid(c.fit(s.title, w - 50, PhoneTheme.BODY), x + 10, y + 15, PhoneTheme.BODY, t.text());
         ui.toggle(key, x + w - 36, y + 7.5F, b.get(), b::set);
         return true;
      }
      if (s instanceof IntSetting is) {
         c.text(c.fit(s.title, w - 60, PhoneTheme.SMALL), x + 10, y + 8, PhoneTheme.SMALL, t.text());
         c.textRight(String.valueOf(is.get()), x + w - 10, y + 8, PhoneTheme.SMALL, t.textDim());
         int lo = Math.max(is.min, is.sliderMin), hi = Math.min(is.max, is.sliderMax);
         ui.slider(x + 12, y + 22, w - 24, is.get(), lo, hi, v -> is.set((int)Math.round(v)));
         return true;
      }
      if (s instanceof DoubleSetting ds) {
         c.text(c.fit(s.title, w - 60, PhoneTheme.SMALL), x + 10, y + 8, PhoneTheme.SMALL, t.text());
         c.textRight(String.format("%." + Math.max(0, Math.min(3, ds.decimalPlaces)) + "f", ds.get()), x + w - 10, y + 8, PhoneTheme.SMALL, t.textDim());
         double lo = Math.max(ds.min, ds.sliderMin), hi = Math.min(ds.max, ds.sliderMax);
         double step = Math.pow(10, -Math.max(0, Math.min(3, ds.decimalPlaces)));
         ui.slider(x + 12, y + 22, w - 24, ds.get(), lo, hi, v -> ds.set(Math.round(v / step) * step));
         return true;
      }
      if (s instanceof EnumSetting e) {
         Enum<?> value = (Enum<?>)e.get();
         Enum<?>[] all = value.getDeclaringClass().getEnumConstants();
         if (all.length <= 3) {
            c.text(c.fit(s.title, w - 20, PhoneTheme.SMALL), x + 10, y + 8, PhoneTheme.SMALL, t.text());
            String[] names = new String[all.length];
            for (int i = 0; i < all.length; i++) names[i] = all[i].toString();
            ui.segmented(x + 8, y + 22, w - 16, 17, names, value.ordinal(), i -> e.set(all[i]));
         } else {
            c.textMid(c.fit(s.title, w * 0.5F, PhoneTheme.BODY), x + 10, y + 15, PhoneTheme.BODY, t.text());
            String label = c.fit(value.toString(), w * 0.42F, PhoneTheme.SMALL);
            float lw = c.width(label, PhoneTheme.SMALL) + 18;
            c.round(x + w - lw - 8, y + 6, lw, 18, 9, t.accentSoft());
            c.textMid(label, x + w - lw - 1, y + 15, PhoneTheme.SMALL, t.accent());
            c.glyphAt(Glyph.CHEVRON, x + w - 15, y + 15, 7, t.accent());
            ui.onClick(x + w - lw - 8, y + 6, lw, 18, () -> e.set(all[(value.ordinal() + 1) % all.length]));
         }
         return true;
      }
      if (s instanceof StringSetting ss) {
         TextInput field = ui.field("str:" + key);
         if (!ui.typing() && !field.get().equals(ss.get())) field.set(ss.get());
         field.onSubmit = () -> ss.set(field.get());
         field.onChange = () -> ss.set(field.get());
         c.textMid(c.fit(s.title, w * 0.38F, PhoneTheme.SMALL), x + 10, y + 15, PhoneTheme.SMALL, t.text());
         ui.textField(field, x + w * 0.42F, y + 4, w * 0.58F - 6, 22);
         return true;
      }
      // colours, block/item lists, keybinds...: shown, edited in the Specter GUI
      c.textMid(c.fit(s.title, w * 0.55F, PhoneTheme.BODY), x + 10, y + 15, PhoneTheme.BODY, t.text());
      String v = c.fit(String.valueOf(s.get()), w * 0.35F, PhoneTheme.CAPTION);
      c.textRight(v, x + w - 10, y + 15 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, t.textFaint());
      return false;
   }

   private float hud(PhoneCanvas c, PhoneUi ui, float y) {
      PhoneTheme t = c.theme();
      float x = 10, w = c.width - 20;
      Hud hud = Hud.get();
      ui.card(x, y, w, 30);
      c.textMid("Show HUD", x + 10, y + 15, PhoneTheme.BODY, t.text());
      ui.toggle("hud", x + w - 36, y + 7.5F, hud.active, v -> hud.active = v);
      y += 38;
      List<HudElement> elements = new ArrayList<>();
      for (HudElement e : hud) elements.add(e);
      if (!elements.isEmpty()) {
         ui.section("Elements", x + 4, y);
         y += 10;
         ui.card(x, y, w, elements.size() * 30);
         for (int i = 0; i < elements.size(); i++) {
            HudElement e = elements.get(i);
            float ry = y + i * 30;
            if (i > 0) ui.divider(x + 10, ry, w - 10);
            c.textMid(c.fit(e.info.title, w - 50, PhoneTheme.BODY), x + 10, ry + 15, PhoneTheme.BODY, t.text());
            ui.toggle("hud:" + i + e.info.name, x + w - 36, ry + 7.5F, e.isActive(), v -> {
               if (e.isActive() != v) e.toggle();
            });
         }
         y += elements.size() * 30 + 8;
      }
      ui.button(x, y, w, 26, "Open the HUD editor", Glyph.EDIT, PhoneUi.Style.PRIMARY, () -> this.openScreen(() -> new HudEditorScreen(GuiThemes.get())));
      return y + 30;
   }
}
