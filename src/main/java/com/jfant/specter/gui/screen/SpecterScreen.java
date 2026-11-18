package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Glide;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.anim.Tween;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiKeyEvents;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.friends.Friend;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.jfant.specter.core.systems.modules.Category;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.systems.profiles.Profile;
import com.jfant.specter.core.systems.profiles.Profiles;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import com.jfant.specter.festive.Festive;
import com.jfant.specter.notify.Notifications;
import com.jfant.specter.render.GuiProfile;
import com.jfant.specter.render.PurpleCard;
import com.jfant.specter.render.QuadBatch;
import com.jfant.specter.render.Theme;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

public class SpecterScreen extends Screen {
   private static final int PANEL_W = 112;
   private static final int HEADER = 15;
   private static final int ROW = 13;
   private static final int GAP = 3;
   private static final int BAR = 20;
   private static final int MARGIN = 4;
   private static final int MIN_W = 90;
   private static final int MAX_W = 260;
   private static final int EDGE = 3;
   private static final String[] BUTTONS = new String[]{"Friends", "Profiles", "Client", "HUD Editor", "Specter+"};
   private static final String[] BUTTON_HINTS = new String[]{
      "Your friends list.",
      "Profiles: save your modules and HUD and load them again later.",
      "Client settings (prefix, chat feedback, fonts) and the HUD's own settings.",
      "Opens the HUD editor, where HUD elements are placed.",
      "Specter+: the premium modules. Opens the Specter Discord."
   };
   private static final int PLUS = 4;
   private static final int PLUS_MIN_W = 66;
   private static final boolean PLUS_INSTALLED = FabricLoader.getInstance().isModLoaded("specterplus");
   private static final String DISCORD_INVITE = "https://discord.gg/UVjHt4QJwN";
   private final Button plusSlot = Button.builder(Component.literal("Specter+"), button -> {}).bounds(0, 0, 0, 0).build();
   private final Button plusPanelSlot = Button.builder(Component.literal("Specter+ Panel"), button -> {}).bounds(0, 0, 0, 0).build();
   private static final long NOTICE_MS = 3000L;
   private static Map<String, int[]> places;
   private static final Map<Object, SpecterScreen.Drop> open = new IdentityHashMap<>();
   private final List<SpecterScreen.Panel> panels = new ArrayList<>();
   private final TextField search = new TextField("Search modules and settings");
   private final TextField friendField = new TextField("Add a friend, Enter");
   private final TextField profileField = new TextField("New profile, Enter");
   private String notice = "";
   private long noticeUntil;
   private final SpecterScreen.Panel[] menus;
   private SpecterScreen.Panel menu;
   private final int[][] buttons = new int[BUTTONS.length][];
   private float scale = 1.0F;
   private SpecterScreen.Panel resizing;
   private int vw;
   private int vh;
   private final Tween presence = Tween.progress(Spec.SCREEN_IN, false);
   private boolean opened;
   private boolean closing;
   private String shownHint = "";
   private String shownTip = "";
   private int shownHintColor = -1;
   private static final float HINT_SCALE = 1.25F;
   private static final int HINT_MAX_W = 300;
   private static final int HINT_LINE = 11;
   private static final int HINT_PAD = 6;

   public SpecterScreen() {
      super(Component.literal("Specter"));

      for (Category category : Modules.loopCategories()) {
         this.panels.add(new SpecterScreen.CategoryPanel(category));
      }

      this.menus = new SpecterScreen.Panel[]{new SpecterScreen.FriendsPanel(), new SpecterScreen.ProfilesPanel(), new SpecterScreen.ClientPanel()};

      for (SpecterScreen.Panel panel : this.menus) {
         panel.menu = true;
      }

      this.friendField.onEnter = this::addFriend;
      this.profileField.onEnter = this::addProfile;
   }

   public static void toggle() {
      if (SpecterCore.mc.screen instanceof SpecterScreen screen) {
         if (screen.closing) {
            screen.reopen();
         } else {
            screen.onClose();
         }
      } else if (Utils.canOpenGui()) {
         SpecterCore.mc.setScreen(new SpecterScreen());
      }
   }

   @Override
   protected void init() {
      this.plusSlot.active = false;
      this.addWidget(this.plusSlot);
      if (PLUS_INSTALLED) {
         this.plusPanelSlot.active = false;
         this.addWidget(this.plusPanelSlot);
      }

      GuiKeyEvents.canUseKeys = true;
      Focus.clear();
      Ui.dragging = null;
      if (!this.opened) {
         this.opened = true;
         this.presence.set(0.0).to(1.0, transition() ? Spec.SCREEN_IN : Spec.SCREEN_IN.lasting(0.0));
      }
   }

   private static boolean transition() {
      return Ui.config() == null || Ui.config().openTransition.get();
   }

   @Override
   public void onClose() {
      if (!this.closing) {
         Focus.clear();
         Ui.dragging = null;
         if (transition() && !(Motion.seconds(Spec.SCREEN_OUT) <= 0.0)) {
            this.closing = true;
            this.presence.to(0.0, Spec.SCREEN_OUT);
         } else {
            super.onClose();
         }
      }
   }

   private void reopen() {
      this.closing = false;
      this.presence.to(1.0, Spec.SCREEN_IN);
   }

   @Override
   public void removed() {
      GuiProfile.closed();
      Profiles.get().save();
      Focus.clear();
      Ui.dragging = null;
      GuiKeyEvents.canUseKeys = true;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
      if (this.minecraft.level == null) {
         this.renderPanorama(context, deltaTicks);
      }
   }

   private void fit() {
      int count = this.panels.size() + (PLUS_INSTALLED ? 1 : 0);
      int needed = 8 + count * 112 + (count - 1) * 3;
      this.scale = Math.min(1.0F, (float)this.width / needed);
      this.vw = (int)(this.width / this.scale);
      this.vh = (int)(this.height / this.scale);
   }

   @Override
   public void render(GuiGraphics ctx, int screenX, int screenY, float deltaTicks) {
      GuiProfile.beginFrame();

      try {
         this.renderGui(ctx, screenX, screenY, deltaTicks);
      } finally {
         GuiProfile.endFrame(Theme.clean() ? "Clean" : "Blocky");
      }
   }

   private void renderGui(GuiGraphics ctx, int screenX, int screenY, float deltaTicks) {
      if (this.closing && this.presence.done()) {
         this.closing = false;
         super.onClose();
      } else {
         float arrived = this.presence.getF();
         this.renderBackground(ctx, screenX, screenY, deltaTicks);
         boolean fade = Ui.config() == null || Ui.config().backgroundFade.get();
         if (this.minecraft.level != null) {
            ctx.fill(0, 0, this.width, this.height, Ui.alpha(1073741824, fade ? arrived : 1.0F));
         }

         this.fit();
         int mouseX = this.closing ? -9999 : (int)(screenX / this.scale);
         int mouseY = this.closing ? -9999 : (int)(screenY / this.scale);
         ctx.pose().pushMatrix();
         ctx.pose().scale(this.scale, this.scale);
         Ui.hint = "";
         Ui.tip = "";
         Festive.backdrop(ctx, this.vw, this.vh, arrived);
         if (Ui.dragging != null) {
            if (GLFW.glfwGetMouseButton(SpecterCore.mc.getWindow().handle(), 0) != 1) {
               Ui.dragging.release();
               Ui.dragging = null;
            } else {
               Ui.dragging.drag(mouseX, mouseY);
            }
         }

         this.layoutBar();
         this.place();
         String query = this.query();
         boolean overMenu = this.menu != null && this.menu.contains(mouseX, mouseY);
         boolean overBar = !overMenu && this.inBar(mouseX, mouseY);
         SpecterScreen.Panel top = !overMenu && !overBar ? this.topAt(mouseX, mouseY, query) : null;
         float out = this.closing ? arrived : 1.0F;
         float travel = (float)Motion.amount(Channel.GUI);

         for (SpecterScreen.Panel panel : this.panels) {
            float match = Animator.progress(panel, "match", !panel.hidden(query), Spec.POP_IN, Spec.POP_OUT);
            if (!(match < 0.01F)) {
               float in = panel.entrance(this.panels);
               float alpha = in * out * match;
               if (!(alpha < 0.01F)) {
                  boolean mine = panel == top;
                  ctx.pose().pushMatrix();
                  ctx.pose().translate(0.0F, -(1.0F - in) * 14.0F * travel - (1.0F - out) * 6.0F * travel);
                  QuadBatch.pushAlpha(alpha);

                  try {
                     panel.render(ctx, mine ? mouseX : -9999, mine ? mouseY : -9999, query);
                  } finally {
                     QuadBatch.popAlpha();
                     ctx.pose().popMatrix();
                  }
               }
            }
         }

         ctx.pose().pushMatrix();
         ctx.pose().translate(0.0F, -(1.0F - arrived) * 24.0F * travel);
         QuadBatch.pushAlpha(arrived);

         try {
            this.bar(ctx, overBar ? mouseX : -9999, overBar ? mouseY : -9999);
         } finally {
            QuadBatch.popAlpha();
            ctx.pose().popMatrix();
         }

         for (SpecterScreen.Panel candidate : this.menus) {
            float down = Animator.progress(candidate, "menu", candidate == this.menu, Spec.POP_IN, Spec.POP_OUT);
            if (!(down < 0.01F)) {
               boolean current = candidate == this.menu && overMenu;
               ctx.pose().pushMatrix();
               ctx.pose().translate(0.0F, -(1.0F - down) * 6.0F * travel);
               QuadBatch.pushAlpha(down * arrived);

               try {
                  candidate.render(ctx, current ? mouseX : -9999, current ? mouseY : -9999, "");
               } finally {
                  QuadBatch.popAlpha();
                  ctx.pose().popMatrix();
               }
            }
         }

         boolean noticing = System.currentTimeMillis() < this.noticeUntil;
         String line = noticing ? this.notice : Ui.hint;
         if (!line.isEmpty()) {
            this.shownHint = line;
            this.shownHintColor = noticing ? -37177 : -1;
         }

         float hinting = Animator.progress(this, "hint", !line.isEmpty(), Spec.POP_IN, Spec.POP_OUT.scaled(1.6)) * arrived;
         if (hinting > 0.01F && !this.shownHint.isEmpty()) {
            QuadBatch.pushAlpha(hinting);

            try {
               this.hintBox(ctx, this.shownHint, this.shownHintColor);
            } finally {
               QuadBatch.popAlpha();
            }
         }

         boolean tipping = !Ui.tip.isEmpty() && Ui.dragging == null;
         if (tipping) {
            this.shownTip = Ui.tip;
         }

         float tipped = Animator.progress(this, "tip", tipping, Spec.POP_IN, Spec.POP_OUT) * arrived;
         if (tipped > 0.01F && !this.shownTip.isEmpty() && mouseX > -9000) {
            QuadBatch.pushAlpha(tipped);

            try {
               this.tip(ctx, this.shownTip, mouseX, mouseY);
            } finally {
               QuadBatch.popAlpha();
            }
         }

         ctx.pose().popMatrix();
         Notifications.render(ctx, this.width, this.height);
      }
   }

   private void hintBox(GuiGraphics ctx, String text, int color) {
      int room = (int)Math.min(300.0F, (this.vw - 16) / 1.25F - 12.0F);
      List<String> lines = Ui.wrap(text, room);
      if (lines.size() > 3) {
         lines = new ArrayList<>(lines.subList(0, 3));
         lines.set(2, Ui.fit(lines.get(2) + " ...", room));
      }

      int textW = 0;

      for (String l : lines) {
         textW = Math.max(textW, Ui.width(l));
      }

      int w = textW + 12;
      int h = lines.size() * 11 + 12 - 3;
      int cardW = Math.max(w, Math.round(Animator.follow(this, "hintW", w, 0.12, Channel.GUI)));
      int cardH = Math.max(h, Math.round(Animator.follow(this, "hintH", h, 0.12, Channel.GUI)));
      Matrix3x2fStack m = ctx.pose();
      m.pushMatrix();
      float cx = this.vw / 2.0F;
      float cy = this.vh * 0.75F;
      m.translate(cx - cardW * 1.25F / 2.0F, cy - cardH * 1.25F / 2.0F);
      m.scale(1.25F, 1.25F);
      PurpleCard.bubble(ctx, 0, 0, cardW, cardH);

      for (int i = 0; i < lines.size(); i++) {
         String l = lines.get(i);
         Ui.text(ctx, l, (cardW - Ui.width(l)) / 2, (cardH - h) / 2 + 6 + i * 11, color);
      }

      m.popMatrix();
   }

   private void tip(GuiGraphics ctx, String name, int mouseX, int mouseY) {
      int w = Math.min(this.vw - 8, Ui.width(name) + 10);
      int h = 13;
      int x = mouseX + 9;
      int y = mouseY + 9;
      if (x + w > this.vw - 4) {
         x = mouseX - 4 - w;
      }

      if (y + h > this.vh - 4) {
         y = mouseY - 4 - h;
      }

      x = Math.max(4, x);
      y = Math.max(4, y);
      PurpleCard.bubble(ctx, x, y, x + w, y + h);
      Ui.text(ctx, Ui.fit(name, w - 10), x + 5, Ui.textY(y, h), -1);
   }

   private void layoutBar() {
      int right = this.vw - 4 - 4;

      for (int i = BUTTONS.length - 1; i >= 0; i--) {
         int w = Ui.width(BUTTONS[i]) + 14;
         if (i == 4) {
            w = Math.max(w, 66);
         }

         this.buttons[i] = new int[]{right - w, 7, w, 14};
         right -= w + 3;
      }

      int[] slot = this.buttons[4];
      this.plusSlot
         .setRectangle(Math.round(slot[2] * this.scale), Math.round(slot[3] * this.scale), Math.round(slot[0] * this.scale), Math.round(slot[1] * this.scale));
      int plusX = 4 + this.panels.size() * 115;
      this.plusPanelSlot
         .setRectangle(
            Math.round(112.0F * this.scale), Math.round(15.0F * this.scale), Math.round(plusX * this.scale), Math.round(this.panelTop() * this.scale)
         );

      for (int i = 0; i < this.menus.length; i++) {
         this.menus[i].x = Math.max(4, Math.min(this.buttons[i][0], this.vw - 4 - 112));
         this.menus[i].y = 26;
      }
   }

   private boolean inBar(int mx, int my) {
      return Ui.in(mx, my, 4, 4, this.vw - 8, 20);
   }

   private int panelTop() {
      return 28;
   }

   private void bar(GuiGraphics ctx, int mx, int my) {
      PurpleCard.bubble(ctx, 4, 4, this.vw - 4, 24);
      int textY = Ui.textY(4, 20);
      Ui.text(ctx, "SPECTER", 13, textY, -37177);
      Ui.text(ctx, "SPECTER", 14, textY, -37177);
      int wordEnd = 14 + Ui.width("SPECTER");
      int searchX = wordEnd + 10;
      int room = Math.max(60, Math.min(200, this.buttons[0][0] - 10 - searchX));
      boolean searching = this.search.focused() || !this.search.text.isEmpty();
      float wide = Animator.progress(this.search, "wide", searching, Spec.EXPAND, Spec.COLLAPSE);
      int idle = Math.max(60, Math.min(room, 132));
      int searchW = Math.round(idle + (room - idle) * wide);
      this.search.render(ctx, searchX, 7, searchW, 14);

      for (int i = 0; i < BUTTONS.length; i++) {
         if (i != 4 || !PLUS_INSTALLED) {
            int[] b = this.buttons[i];
            boolean hover = Ui.in(mx, my, b[0], b[1], b[2], b[3]);
            Ui.button(ctx, BUTTONS[i], b[0], b[1], b[2], b[3], hover);
            float lit = i < this.menus.length ? Animator.progress(BUTTONS[i], "picked", this.menu == this.menus[i], Spec.TINT) : 0.0F;
            if (lit > 0.01F) {
               Ui.glow(ctx, b[0], b[1], b[2], b[3], lit);
               Ui.textCentered(ctx, BUTTONS[i], b[0] + b[2] / 2, Ui.textY(b[1], b[3]), Ui.alpha(-1, lit));
            }

            if (hover) {
               Ui.hint = BUTTON_HINTS[i];
            }
         }
      }

      Festive.bar(ctx, this, 4, 4, this.vw - 8, 20, 13, searchX + searchW + 8, this.buttons[0][0] - 8);
   }

   private String query() {
      return this.search.text.trim().toLowerCase(Locale.ROOT);
   }

   private void place() {
      Map<String, int[]> saved = places();
      boolean changed = false;

      for (SpecterScreen.Panel panel : this.panels) {
         int[] spot = saved.get(panel.name);
         if (spot == null) {
            spot = new int[]{4 + this.rowIndex(panel) * 115, this.panelTop(), 0, 112};
            saved.put(panel.name, spot);
            changed = true;
         }

         panel.w = Math.max(90, Math.min(260, spot[3]));
         panel.x = Math.max(0, Math.min(spot[0], this.vw - panel.w));
         panel.y = Math.max(this.panelTop(), Math.min(spot[1], this.vh - 15));
         panel.folded = spot[2] != 0;
      }

      if (changed) {
         savePlaces();
      }
   }

   private int rowIndex(SpecterScreen.Panel panel) {
      return Math.max(0, this.panels.indexOf(panel));
   }

   private static Map<String, int[]> places() {
      if (places == null) {
         places = new HashMap<>();

         try {
            File file = placesFile();
            if (file.exists()) {
               for (String line : Files.readAllLines(file.toPath())) {
                  String[] parts = line.split("\t");
                  if (parts.length == 4 || parts.length == 5) {
                     int width = parts.length == 5 ? Integer.parseInt(parts[4]) : 112;
                     places.put(parts[0], new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), width});
                  }
               }
            }
         } catch (NumberFormatException | IOException var5) {
            SpecterCore.LOG.warn("Couldn't read the GUI panel places", var5);
         }
      }

      return places;
   }

   private static void savePlaces() {
      List<String> lines = new ArrayList<>();

      for (Entry<String, int[]> entry : places().entrySet()) {
         int[] spot = entry.getValue();
         lines.add(entry.getKey() + "\t" + spot[0] + "\t" + spot[1] + "\t" + spot[2] + "\t" + spot[3]);
      }

      Collections.sort(lines);

      try {
         File file = placesFile();
         file.getParentFile().mkdirs();
         Files.write(file.toPath(), lines);
      } catch (IOException var4) {
         SpecterCore.LOG.warn("Couldn't save the GUI panel places", var4);
      }
   }

   private static File placesFile() {
      return new File(new File(SpecterCore.FOLDER, "specter"), "gui-panels-3.txt");
   }

   private SpecterScreen.Panel topAt(int mx, int my, String query) {
      for (int i = this.panels.size() - 1; i >= 0; i--) {
         SpecterScreen.Panel panel = this.panels.get(i);
         if (!panel.hidden(query) && panel.contains(mx, my)) {
            return panel;
         }
      }

      return null;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      int mx = (int)(click.x() / this.scale);
      int my = (int)(click.y() / this.scale);
      int button = click.button();
      Focus.touched = false;
      if (Focus.get() instanceof KeyCard keyCard && keyCard.mouse(button)) {
         return true;
      } else {
         this.dispatch(mx, my, button);
         if (!Focus.touched && Focus.get() != null) {
            Focus.clear();
         }

         return true;
      }
   }

   private void dispatch(int mx, int my, int button) {
      if (!this.closing) {
         if (!this.search.click(mx, my)) {
            for (int i = 0; i < BUTTONS.length; i++) {
               int[] b = this.buttons[i];
               if (Ui.in(mx, my, b[0], b[1], b[2], b[3])) {
                  if (i == 4) {
                     if (!PLUS_INSTALLED) {
                        this.openDiscord();
                     }

                     return;
                  }

                  Ui.press(BUTTONS[i]);
                  if (i < this.menus.length) {
                     this.menu = this.menu == this.menus[i] ? null : this.menus[i];
                  } else {
                     SpecterCore.mc.setScreen(new HudEditorScreen(GuiThemes.get()));
                  }

                  return;
               }
            }

            if (this.menu != null) {
               if (this.menu.contains(mx, my)) {
                  this.menu.click(mx, my, button);
                  return;
               }

               this.menu = null;
            }

            if (!this.inBar(mx, my)) {
               SpecterScreen.Panel panel = this.topAt(mx, my, this.query());
               if (panel != null) {
                  this.panels.remove(panel);
                  this.panels.add(panel);
                  panel.click(mx, my, button);
               }
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (Ui.dragging != null) {
         Ui.dragging.release();
         Ui.dragging = null;
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int mx = (int)(mouseX / this.scale);
      int my = (int)(mouseY / this.scale);
      if (this.menu != null && this.menu.contains(mx, my)) {
         this.menu.scrolled(mx, my, verticalAmount);
         return true;
      } else {
         SpecterScreen.Panel panel = this.topAt(mx, my, this.query());
         if (panel != null) {
            panel.scrolled(mx, my, verticalAmount);
         }

         return true;
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      Focusable focused = Focus.get();
      if (focused != null) {
         focused.key(input.key(), input.modifiers());
         return true;
      } else if (input.key() == 256) {
         for (SpecterScreen.Drop drop : open.values()) {
            if (!drop.closing && drop.settings.editing()) {
               drop.settings.closeEditor();
               return true;
            }
         }

         if (this.menu != null) {
            this.menu = null;
            return true;
         } else {
            this.onClose();
            return true;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      Focusable focused = Focus.get();
      if (focused != null) {
         return focused.chr(input.codepoint());
      } else if (input.codepoint() >= 32) {
         this.search.focus();
         return this.search.chr(input.codepoint());
      } else {
         return false;
      }
   }

   private void addFriend() {
      String name = this.friendField.text.trim();
      if (!name.isEmpty()) {
         Friend friend = new Friend(name);
         if (!Friends.get().add(friend)) {
            this.notice(name.contains(" ") ? "Names have no spaces." : name + " is already a friend.");
         } else {
            MeteorExecutor.execute(friend::updateInfo);
            this.friendField.setText("");
         }
      }
   }

   private void addProfile() {
      String name = this.profileField.text.trim();
      if (!name.isEmpty()) {
         Profile profile = new Profile();
         profile.name.set(name);
         if (profile.getSafeFile() == null) {
            this.notice("That name can't be a folder name.");
         } else if (Profiles.get().get(name) != null) {
            this.notice("There is a profile called " + name + " already.");
         } else {
            Profiles.get().add(profile);
            this.profileField.setText("");
            dropDown(profile, this.profilePage(profile));
         }
      }
   }

   private Page friendPage(Friend friend) {
      return new Page(
         friend, friend.getName(), "", new Settings(), null, null, List.of(new ButtonCard("Remove", "Takes them off your friends list.", true, () -> {
            Friends.get().remove(friend);
            open.remove(friend);
         }))
      );
   }

   private Page profilePage(Profile profile) {
      return new Page(
         profile,
         profile.name.get(),
         "",
         profile.settings,
         null,
         null,
         List.of(new ButtonCard("Save here", "Saves your modules and HUD as they are now into this profile.", () -> {
            profile.save();
            this.notice("Saved into " + profile.name.get() + ".");
         }), new ButtonCard("Load", "Loads this profile's modules and HUD.", () -> {
            profile.load();
            this.notice("Loaded " + profile.name.get() + ".");
         }), new ButtonCard("Delete", "Deletes this profile and its saved files.", true, () -> {
            Profiles.get().remove(profile);
            open.remove(profile);
         }))
      );
   }

   private void openDiscord() {
      Ui.press(BUTTONS[4]);
      if ("https://discord.gg/UVjHt4QJwN".isEmpty()) {
         this.notice("Specter+ is coming soon - ask in the Specter Discord.");
      } else {
         Util.getPlatform().openUri("https://discord.gg/UVjHt4QJwN");
         this.notice("Opened the Specter Discord in your browser.");
      }
   }

   private void notice(String text) {
      this.notice = text;
      this.noticeUntil = System.currentTimeMillis() + 3000L;
   }

   private static void dropDown(Object key, Page page) {
      SettingsPanel settings = new SettingsPanel();
      settings.setPage(page);
      open.put(key, new SpecterScreen.Drop(settings));
   }

   private static boolean matches(Module module, String query) {
      if (!module.title.toLowerCase(Locale.ROOT).contains(query) && !module.name.contains(query)) {
         for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
               if (setting.title.toLowerCase(Locale.ROOT).contains(query)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private final class CategoryPanel extends SpecterScreen.Panel {
      private final Category category;

      CategoryPanel(Category category) {
         super(category.name);
         this.category = category;
      }

      private List<Module> modules(String query) {
         List<Module> list = new ArrayList<>();

         for (Module module : Modules.get().getGroup(this.category)) {
            if (query.isEmpty() || SpecterScreen.matches(module, query)) {
               list.add(module);
            }
         }

         list.sort(Comparator.comparing(modulex -> modulex.title));
         return list;
      }

      @Override
      List<SpecterScreen.Row> rows(String query) {
         List<SpecterScreen.Row> rows = new ArrayList<>();

         for (Module module : this.modules(query)) {
            rows.add(SpecterScreen.ItemRow.of(module));
         }

         return rows;
      }

      @Override
      String count() {
         int on = 0;
         int all = 0;

         for (Module module : Modules.get().getGroup(this.category)) {
            all++;
            if (module.isActive()) {
               on++;
            }
         }

         return on + "/" + all;
      }

      @Override
      boolean countLit() {
         for (Module module : Modules.get().getGroup(this.category)) {
            if (module.isActive()) {
               return true;
            }
         }

         return false;
      }

      @Override
      boolean hidden(String query) {
         return !query.isEmpty() && this.modules(query).isEmpty();
      }
   }

   private final class ClientPanel extends SpecterScreen.Panel {
      ClientPanel() {
         super("Client");
      }

      @Override
      List<SpecterScreen.Row> rows(String query) {
         return List.of(
            new SpecterScreen.ItemRow(
               "Client",
               "The command prefix, chat feedback, the custom font and other client-wide settings.",
               Config.get(),
               () -> new Page(Config.get(), "Client", "", Config.get().settings, null, null, List.of()),
               null,
               null
            ),
            new SpecterScreen.ItemRow(
               "HUD",
               "The whole HUD: its switch, hiding it in menus, its toggle bind, snapping in the editor.",
               Hud.get(),
               () -> new Page(
                  Hud.get(),
                  "HUD",
                  "",
                  Hud.get().settings,
                  null,
                  null,
                  List.of(
                     new ButtonCard(
                        "HUD Editor",
                        "Opens the HUD editor, where HUD elements are placed.",
                        () -> SpecterCore.mc.setScreen(new HudEditorScreen(GuiThemes.get()))
                     )
                  )
               ),
               () -> Hud.get().active,
               () -> Hud.get().active = !Hud.get().active
            )
         );
      }

      @Override
      boolean hidden(String query) {
         return !query.isEmpty();
      }
   }

   private static final class Drop {
      final SettingsPanel settings;
      final Tween shown = Tween.progress(Spec.REFLOW, false);
      final Glide full = new Glide(Spec.REFLOW, 0.5);
      boolean closing;

      Drop(SettingsPanel settings) {
         this.settings = settings;
         this.shown.to(1.0, Spec.REFLOW);
      }

      void close() {
         this.closing = true;
         this.shown.to(0.0, Spec.REFLOW);
      }

      void reopen() {
         this.closing = false;
         this.shown.to(1.0, Spec.REFLOW);
      }

      boolean gone() {
         return this.closing && this.shown.done();
      }
   }

   private static final class DropRow extends SpecterScreen.Row {
      private final SpecterScreen.Drop drop;
      private final SettingsPanel settings;
      private final int fullWidth;

      DropRow(SpecterScreen.Drop drop, int w) {
         this.drop = drop;
         this.settings = drop.settings;
         this.fullWidth = w;
      }

      private int full() {
         return (int)Math.round(Math.max(0.0, this.drop.full.follow(this.settings.inlineHeight(this.fullWidth - 6))));
      }

      @Override
      int height() {
         return Math.round(this.full() * this.drop.shown.getF());
      }

      @Override
      int restHeight() {
         return this.drop.closing ? 0 : this.settings.inlineHeight(this.fullWidth - 6);
      }

      @Override
      void render(GuiGraphics ctx, int mx, int my) {
         float shown = this.drop.shown.getF();
         int height = Math.round(this.full() * shown);
         if (height > 0) {
            Ui.fill(ctx, this.x + 1, this.y, this.x + this.w - 1, this.y + height, 939524096);
            Ui.fill(ctx, this.x + 1, this.y, this.x + 2, this.y + height, -1862308153);
            boolean partly = shown < 0.999F || this.drop.full.moving();
            if (partly) {
               ctx.enableScissor(this.x, this.y, this.x + this.w, this.y + height);
               QuadBatch.pushAlpha(Math.min(1.0F, shown * 1.4F));
            }

            try {
               boolean live = !this.drop.closing;
               this.settings.renderInline(ctx, this.x + 4, this.y, this.w - 6, live ? mx : -9999, live ? my : -9999);
            } finally {
               if (partly) {
                  QuadBatch.popAlpha();
                  ctx.disableScissor();
               }
            }
         }
      }

      @Override
      void click(int mx, int my, int button) {
         if (!this.drop.closing) {
            this.settings.clickInline(mx, my, button);
         }
      }
   }

   private static final class FieldRow extends SpecterScreen.Row {
      private final TextField field;

      FieldRow(TextField field) {
         this.field = field;
      }

      @Override
      int height() {
         return 18;
      }

      @Override
      void render(GuiGraphics ctx, int mx, int my) {
         this.field.render(ctx, this.x + 3, this.y + 2, this.w - 6, 14);
      }

      @Override
      void click(int mx, int my, int button) {
         this.field.click(mx, my);
      }
   }

   private final class FriendsPanel extends SpecterScreen.Panel {
      FriendsPanel() {
         super("Friends");
      }

      @Override
      List<SpecterScreen.Row> rows(String query) {
         List<SpecterScreen.Row> rows = new ArrayList<>();
         rows.add(new SpecterScreen.FieldRow(SpecterScreen.this.friendField));
         List<Friend> friends = new ArrayList<>();

         for (Friend friend : Friends.get()) {
            friends.add(friend);
         }

         friends.sort(null);

         for (Friend friend : friends) {
            rows.add(new SpecterScreen.ItemRow(friend.getName(), "Click to remove them.", friend, () -> SpecterScreen.this.friendPage(friend), null, null));
         }

         if (friends.isEmpty()) {
            rows.add(new SpecterScreen.TextRow("No friends yet"));
         }

         return rows;
      }

      @Override
      String count() {
         return String.valueOf(Friends.get().count());
      }

      @Override
      boolean hidden(String query) {
         return !query.isEmpty();
      }
   }

   private static final class ItemRow extends SpecterScreen.Row {
      private final String label;
      private final String description;
      private final Object key;
      private final Supplier<Page> page;
      private final BooleanSupplier on;
      private final Runnable toggle;

      ItemRow(String label, String description, Object key, Supplier<Page> page, BooleanSupplier on, Runnable toggle) {
         this.label = label;
         this.description = description;
         this.key = key;
         this.page = page;
         this.on = on;
         this.toggle = toggle;
      }

      static SpecterScreen.ItemRow of(Module module) {
         return new SpecterScreen.ItemRow(module.title, module.description, module, () -> Page.of(module), module::isActive, module::toggle);
      }

      @Override
      int height() {
         return 13;
      }

      @Override
      void render(GuiGraphics ctx, int mx, int my) {
         SpecterScreen.Drop drop = SpecterScreen.open.get(this.key);
         boolean dropped = drop != null && !drop.closing;
         boolean hover = Ui.in(mx, my, this.x, this.y, this.w, 13);
         Ui.wash(ctx, this.x + 1, this.y, this.w - 2, 13, Ui.hover(this.key, hover));
         boolean active = this.on != null && this.on.getAsBoolean();
         float flash = this.on != null && toggleFlash() ? Animator.pulse(this.key, "toggled") : 0.0F;
         if (flash > 0.01F) {
            float reach = Math.min(1.0F, (1.0F - flash) * 1.5F + 0.12F);
            int tint = active ? -12724596 : -1750963;
            int strong = Math.round(flash * 88.0F) << 24 | tint & 16777215;
            int weak = Math.round(flash * 38.0F) << 24 | tint & 16777215;
            PurpleCard.fillRounded(ctx, this.x + 1, this.y, this.x + 1 + Math.round((this.w - 2) * reach), this.y + 13, 3, 15, strong, weak);
         }

         float down = Ui.pressed(this.key);
         if (down > 0.01F) {
            Ui.wash(ctx, this.x + 1, this.y, this.w - 2, 13, down * 1.3F);
         }

         float openness = drop == null ? 0.0F : drop.shown.getF();
         if (openness > 0.01F) {
            int half = Math.round(4.5F * openness);
            Ui.fill(ctx, this.x + 1, this.y + 6 - half, this.x + 2, this.y + 6 + half + 1, Ui.alpha(-37177, openness));
         }

         float lit = this.on == null ? 0.0F : Animator.progress(this.key, "lit", active, Spec.TINT.scaled(1.4));
         int color = Ui.mix(Ui.mix(-1911561, -1, lit), -37177, Animator.progress(this.key, "picked", dropped, Spec.TINT));
         int textRight = this.on != null ? this.w - 34 : this.w - 10;
         String shown = Ui.fit(this.label, textRight);
         Ui.text(ctx, shown, this.x + 6, Ui.textY(this.y, 13), color);
         if (this.on != null) {
            Ui.toggle(ctx, this.key, this.x + this.w - 27, this.y + 1, active);
         }

         if (hover) {
            Ui.hint = this.description.isEmpty() ? this.label : this.label + " - " + this.description;
            Ui.tip = this.label;
         }
      }

      private static boolean toggleFlash() {
         return Ui.config() == null || Ui.config().toggleFlash.get();
      }

      @Override
      void click(int mx, int my, int button) {
         if (button != 1) {
            if (this.on != null && mx >= this.x + this.w - 30) {
               this.toggle.run();
            } else {
               Ui.press(this.key);
               SpecterScreen.Drop drop = SpecterScreen.open.get(this.key);
               if (drop == null) {
                  SpecterScreen.dropDown(this.key, this.page.get());
               } else if (drop.closing) {
                  drop.reopen();
               } else {
                  drop.close();
               }
            }
         }
      }
   }

   private abstract class Panel {
      final String name;
      int x;
      int y;
      int w = 112;
      boolean folded;
      boolean menu;
      private final Scroll scroll = new Scroll();
      private List<SpecterScreen.Row> drawn = List.of();
      private int bodyHeight;
      private final Glide body = new Glide(Spec.REFLOW, 0.5);
      private final Tween enter = Tween.progress(Spec.SCREEN_IN, false);
      private boolean entered;

      Panel(String name) {
         this.name = name;
      }

      float entrance(List<SpecterScreen.Panel> all) {
         if (!this.entered) {
            this.entered = true;
            int before = 0;

            for (SpecterScreen.Panel other : all) {
               if (other.x < this.x) {
                  before++;
               }
            }

            Spec spec = SpecterScreen.transition() ? Spec.SCREEN_IN : Spec.SCREEN_IN.lasting(0.0);
            this.enter.set(0.0).to(1.0, spec, SpecterScreen.transition() ? Motion.stagger(before, 26.0, Channel.GUI) : 0.0);
         }

         return this.enter.getF();
      }

      abstract List<SpecterScreen.Row> rows(String var1);

      String count() {
         return "";
      }

      boolean countLit() {
         return false;
      }

      boolean hidden(String query) {
         return false;
      }

      private List<SpecterScreen.Row> withDrops(String query) {
         List<SpecterScreen.Row> list = new ArrayList<>();

         for (SpecterScreen.Row row : this.rows(query)) {
            list.add(row);
            if (row instanceof SpecterScreen.ItemRow item) {
               SpecterScreen.Drop drop = SpecterScreen.open.get(item.key);
               if (drop != null) {
                  if (drop.gone()) {
                     SpecterScreen.open.remove(item.key);
                  } else {
                     list.add(new SpecterScreen.DropRow(drop, this.w - 2));
                  }
               }
            }
         }

         return list;
      }

      int contentHeight(String query) {
         int h = 2;

         for (SpecterScreen.Row row : this.withDrops(query)) {
            h += row.height();
         }

         return h;
      }

      private int head() {
         return this.menu ? 0 : 15;
      }

      private int maxBody() {
         return Math.max(39, SpecterScreen.this.vh - this.y - this.head() - 4 - 14);
      }

      boolean contains(int mx, int my) {
         return Ui.in(mx, my, this.x, this.y, this.w, this.head() + this.bodyHeight + (this.menu ? 2 : 0));
      }

      void render(GuiGraphics ctx, int mx, int my, String query) {
         boolean wanted = !this.folded || !query.isEmpty() || this.menu;
         List<SpecterScreen.Row> rows = !wanted && this.bodyHeight <= 0 ? List.of() : this.withDrops(query);
         int content = 2;
         int rest = 2;

         for (SpecterScreen.Row row : rows) {
            content += row.height();
            rest += row.restHeight();
         }

         int target = wanted && !rows.isEmpty() ? Math.min(rest, this.maxBody()) : 0;
         this.bodyHeight = (int)Math.round(Math.max(0.0, this.body.follow(target)));
         if (this.bodyHeight <= 0) {
            rows = wanted ? rows : List.of();
         }

         this.scroll.size(this.bodyHeight, content);
         int bottom = this.y + this.head() + this.bodyHeight + (this.bodyHeight > 0 ? 2 : 0);
         PurpleCard.bubble(ctx, this.x, this.y, this.x + this.w, bottom);
         if (!this.menu) {
            this.header(ctx, mx, my);
         }

         if (!this.menu) {
            Festive.panel(ctx, this, this.x, this.y, this.w, bottom - this.y);
         }

         int top = this.y + this.head() + (this.menu ? 1 : 0);
         int cursor = top + 1 - this.scroll.offset();
         ctx.enableScissor(this.x, top, this.x + this.w, top + this.bodyHeight);
         boolean over = Ui.in(mx, my, this.x, top, this.w, this.bodyHeight);

         for (SpecterScreen.Row row : rows) {
            row.x = this.x + 1;
            row.y = cursor;
            row.w = this.w - 2;
            int h = row.height();
            if (cursor + h >= top && cursor <= top + this.bodyHeight) {
               long rowStart = System.nanoTime();
               row.render(ctx, over ? mx : -9999, over ? my : -9999);
               GuiProfile.row(System.nanoTime() - rowStart);
            }

            cursor += row.height();
         }

         ctx.disableScissor();
         if (this.bodyHeight > 0) {
            this.scroll.bar(ctx, this.x + this.w - 1, top, this.bodyHeight);
         }

         this.drawn = rows;
         boolean grabbing = !this.menu && (this.onEdge(mx, my) || SpecterScreen.this.resizing == this);
         float edge = Ui.hover(this, "edge", grabbing);
         if (edge > 0.01F) {
            Ui.fill(ctx, this.x + this.w - 1, this.y + 3, this.x + this.w, this.y + this.head() + this.bodyHeight - 1, Ui.alpha(-37177, edge));
         }

         if (grabbing) {
            Ui.hint = "Drag to change how wide " + this.name + " is.";
         }
      }

      boolean onEdge(int mx, int my) {
         return !this.menu
            && mx >= this.x + this.w - 3
            && mx < this.x + this.w + 1
            && my >= this.y
            && my < this.y + this.head() + (this.folded ? 0 : this.bodyHeight) + 2;
      }

      private void startResize(int mx) {
         final int startW = this.w;
         SpecterScreen.this.resizing = this;
         Ui.dragging = new Ui.Draggable() {
            @Override
            public void drag(int mouseX, int mouseY) {
               int[] spot = SpecterScreen.places().get(Panel.this.name);
               spot[3] = Math.max(90, Math.min(260, startW + mouseX - mx));
            }

            @Override
            public void release() {
               SpecterScreen.this.resizing = null;
               SpecterScreen.savePlaces();
            }
         };
      }

      private void header(GuiGraphics ctx, int mx, int my) {
         boolean headHover = Ui.in(mx, my, this.x, this.y, this.w, 15);
         int corners = this.bodyHeight > 0 ? 3 : 15;
         float lit = Ui.hover(this, "head", headHover);
         PurpleCard.fillRounded(
            ctx,
            this.x + 2,
            this.y + 2,
            this.x + this.w - 2,
            this.y + 15 - (this.bodyHeight > 0 ? 1 : 2),
            2,
            corners,
            Ui.mix(1219510496, 1891655920, lit),
            Ui.mix(678440864, 1350580928, lit)
         );
         if (this.bodyHeight > 0) {
            Ui.fill(ctx, this.x + 3, this.y + 15 - 1, this.x + this.w - 3, this.y + 15, -1862308153);
         }

         Ui.text(ctx, Ui.fit(this.name, this.w - 40), this.x + 6, Ui.textY(this.y, 15), Ui.mix(-1065473, -37177, lit));
         String count = this.count();
         if (!count.isEmpty()) {
            float some = Animator.progress(this, "some-on", this.countLit(), Spec.TINT.scaled(1.6));
            Ui.textRight(ctx, count, this.x + this.w - 6, Ui.textY(this.y, 15), Ui.mix(-4149795, -37177, some));
         }

         if (headHover) {
            Ui.hint = this.folded ? "Click to show " + this.name + ", drag to move it." : "Click to fold " + this.name + ", drag to move it.";
         }
      }

      void click(int mx, int my, int button) {
         if (button == 0 && this.onEdge(mx, my)) {
            this.startResize(mx);
         } else if (!this.menu && my < this.y + 15) {
            if (button == 0) {
               this.startDrag(mx, my);
            }
         } else {
            for (SpecterScreen.Row row : this.drawn) {
               if (row.contains(mx, my)) {
                  row.click(mx, my, button);
                  return;
               }
            }
         }
      }

      void scrolled(int mx, int my, double amount) {
         for (SpecterScreen.Row row : this.drawn) {
            if (row instanceof SpecterScreen.DropRow drop && drop.contains(mx, my) && drop.settings.scrolledInline(amount)) {
               return;
            }
         }

         this.scroll.by(amount);
      }

      private void startDrag(int mx, int my) {
         final int startX = this.x;
         final int startY = this.y;
         Ui.dragging = new Ui.Draggable() {
            private boolean moved;

            @Override
            public void drag(int mouseX, int mouseY) {
               if (Math.abs(mouseX - mx) + Math.abs(mouseY - my) > 2) {
                  this.moved = true;
               }

               if (this.moved) {
                  int[] spot = SpecterScreen.places().get(Panel.this.name);
                  spot[0] = Math.max(0, Math.min(startX + mouseX - mx, SpecterScreen.this.vw - Panel.this.w));
                  spot[1] = Math.max(SpecterScreen.this.panelTop(), Math.min(startY + mouseY - my, SpecterScreen.this.vh - 15));
               }
            }

            @Override
            public void release() {
               if (!this.moved) {
                  int[] spot = SpecterScreen.places().get(Panel.this.name);
                  spot[2] = spot[2] == 0 ? 1 : 0;
               }

               SpecterScreen.savePlaces();
            }
         };
      }
   }

   private final class ProfilesPanel extends SpecterScreen.Panel {
      ProfilesPanel() {
         super("Profiles");
      }

      @Override
      List<SpecterScreen.Row> rows(String query) {
         List<SpecterScreen.Row> rows = new ArrayList<>();
         rows.add(new SpecterScreen.FieldRow(SpecterScreen.this.profileField));

         for (Profile profile : Profiles.get()) {
            rows.add(
               new SpecterScreen.ItemRow(
                  profile.name.get(), "Saves your modules and HUD and loads them back.", profile, () -> SpecterScreen.this.profilePage(profile), null, null
               )
            );
         }

         if (Profiles.get().isEmpty()) {
            rows.add(new SpecterScreen.TextRow("No profiles yet"));
         }

         return rows;
      }

      @Override
      boolean hidden(String query) {
         return !query.isEmpty();
      }
   }

   private abstract static class Row {
      int x;
      int y;
      int w;

      abstract int height();

      int restHeight() {
         return this.height();
      }

      abstract void render(GuiGraphics var1, int var2, int var3);

      abstract void click(int var1, int var2, int var3);

      boolean contains(int mx, int my) {
         return Ui.in(mx, my, this.x, this.y, this.w, this.height());
      }
   }

   private static final class TextRow extends SpecterScreen.Row {
      private final String text;

      TextRow(String text) {
         this.text = text;
      }

      @Override
      int height() {
         return 13;
      }

      @Override
      void render(GuiGraphics ctx, int mx, int my) {
         Ui.text(ctx, Ui.fit(this.text, this.w - 12), this.x + 6, Ui.textY(this.y, 13), -4149795);
      }

      @Override
      void click(int mx, int my, int button) {
      }
   }
}
