package com.jfant.specter.gui.screen;

import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.accounts.AccountType;
import com.jfant.specter.core.systems.accounts.Accounts;
import com.jfant.specter.core.systems.accounts.types.SessionAccount;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.PurpleCard;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.util.UndashedUuid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Token Switcher: your saved accounts in one list, the one you are playing as marked ACTIVE, one click to switch,
 * and a field to add an account from a session token.
 *
 * <p>Tokens are never shown: the field displays dots while you paste, accounts are listed by their verified Minecraft
 * name, nothing is logged, and they are saved encrypted ({@code TokenVault}). Checking a token and switching run off
 * the game thread; a rejected or expired token is reported, never crashes, and leaves your current session as it was.
 * Switching changes who you are for the NEXT server you join - the current connection keeps its account.
 */
public final class TokenSwitcherScreen extends Screen {
   private static final int ROW = 22;
   private static final int WIDTH = 320;
   private EditBox tokenField;
   private String status = "";
   private int statusColor = PurpleCard.DIM;
   private boolean busy;
   private int scroll;
   private List<Account<?>> rows = new ArrayList<>();

   public TokenSwitcherScreen() {
      super(Component.literal("Token Switcher"));
   }

   private int left() {
      return (this.width - WIDTH) / 2;
   }

   private int top() {
      return Math.max(10, this.height / 2 - 130);
   }

   private int listTop() {
      return this.top() + 46;
   }

   private int visibleRows() {
      return 7;
   }

   @Override
   protected void init() {
      int l = this.left(), bottom = this.listTop() + this.visibleRows() * ROW + 8;
      this.tokenField = new EditBox(this.font, l + 8, bottom + 14, WIDTH - 86, 18, Component.literal("Session token"));
      this.tokenField.setMaxLength(4096);
      this.tokenField.setHint(Component.literal("Paste a session token").withStyle(s -> s.withColor(0x8C8496)));
      // never show the token: one dot per character, capped
      this.tokenField.addFormatter((text, start) -> FormattedCharSequence.forward("•".repeat(Math.min(text.length(), 40)), Style.EMPTY));
      this.addRenderableWidget(this.tokenField);
      this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> this.addToken()).bounds(l + WIDTH - 72, bottom + 13, 64, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(l + WIDTH / 2 - 40, bottom + 44, 80, 20).build());
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private void say(String text, int color) {
      this.status = text;
      this.statusColor = color;
   }

   private boolean isActive(Account<?> account) {
      String uuid = account.getCache().uuid;
      if (uuid == null || uuid.isEmpty() || this.minecraft.getUser() == null) return false;
      try {
         UUID id = UndashedUuid.fromStringLenient(uuid);
         return id.equals(this.minecraft.getUser().getProfileId());
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   private void addToken() {
      String token = this.tokenField.getValue().trim();
      if (token.isEmpty() || this.busy) return;
      this.tokenField.setValue("");
      this.busy = true;
      this.say("Checking the token...", PurpleCard.DIM);
      SessionAccount account = new SessionAccount(token);
      MeteorExecutor.execute(() -> {
         boolean ok;
         try {
            ok = account.fetchInfo();
         } catch (RuntimeException e) {
            ok = false;
         }
         boolean valid = ok;
         this.minecraft.execute(() -> {
            this.busy = false;
            if (!valid) {
               this.say("That token was rejected (expired or invalid). Nothing was saved.", 0xFFFF6B6B);
               return;
            }
            for (Account<?> existing : Accounts.get()) {
               if (account.getCache().uuid.equals(existing.getCache().uuid) && existing.getType() == AccountType.Session) {
                  Accounts.get().remove(existing);
                  break;
               }
            }
            Accounts.get().add(account);
            this.say("Added " + account.getUsername() + ".", 0xFF7CFF9A);
         });
      });
   }

   private void switchTo(Account<?> account) {
      if (this.busy) return;
      this.busy = true;
      this.say("Switching to " + account.getUsername() + "...", PurpleCard.DIM);
      MeteorExecutor.execute(() -> {
         boolean ok;
         try {
            ok = account.fetchInfo();
         } catch (RuntimeException e) {
            ok = false;
         }
         boolean fetched = ok;
         this.minecraft.execute(() -> {
            this.busy = false;
            if (!fetched) {
               this.say("Could not log in as " + account.getUsername() + " - its token has expired or was revoked.", 0xFFFF6B6B);
               return;
            }
            boolean loggedIn;
            try {
               loggedIn = account.login();
            } catch (RuntimeException e) {
               loggedIn = false;
            }
            if (!loggedIn) {
               this.say("Login failed for " + account.getUsername() + "; your session was not changed.", 0xFFFF6B6B);
               return;
            }
            Accounts.get().save();
            String note = this.minecraft.level != null ? " Rejoin for servers to see it." : "";
            this.say("Now playing as " + account.getUsername() + "." + note, 0xFF7CFF9A);
         });
      });
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      ctx.fill(0, 0, this.width, this.height, 0xB8100818);
      int l = this.left(), t = this.top();
      int bottom = this.listTop() + this.visibleRows() * ROW + 8;
      PurpleCard.card(ctx, l, t, l + WIDTH, bottom + 72);
      CardText.draw(ctx, "Token Switcher", l + 8, t + 7, PurpleCard.TITLE, true);
      String me = this.minecraft.getUser() == null ? "-" : this.minecraft.getUser().getName();
      String playing = "Playing as " + me;
      CardText.draw(ctx, playing, l + WIDTH - 8 - CardText.width(playing), t + 7, PurpleCard.LABEL, true);
      ctx.fill(l + 6, t + 19, l + WIDTH - 6, t + 20, PurpleCard.DIVIDER);
      CardText.draw(ctx, this.busy ? "Working..." : "Click Switch to use an account.", l + 8, t + 26, PurpleCard.DIM, true);

      this.rows = new ArrayList<>();
      for (Account<?> a : Accounts.get()) this.rows.add(a);
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.rows.size() - this.visibleRows())));
      int top = this.listTop();
      if (this.rows.isEmpty()) CardText.draw(ctx, "No saved accounts yet - paste a token below.", l + 10, top + 6, PurpleCard.LABEL, true);
      for (int i = 0; i < this.visibleRows() && i + this.scroll < this.rows.size(); i++) {
         Account<?> a = this.rows.get(i + this.scroll);
         int y = top + i * ROW;
         boolean active = this.isActive(a);
         boolean over = mouseX >= l + 4 && mouseX < l + WIDTH - 4 && mouseY >= y && mouseY < y + ROW - 2;
         PurpleCard.fillRounded(ctx, l + 4, y, l + WIDTH - 4, y + ROW - 2, 3, 15, active ? 0x4060FF90 : (over ? 0x24FFFFFF : 0x12FFFFFF), active ? 0x3060FF90 : (over ? 0x18FFFFFF : 0x0CFFFFFF));
         CardText.draw(ctx, a.getUsername(), l + 10, y + 3, active ? 0xFF8CFFB0 : PurpleCard.VALUE, true);
         CardText.draw(ctx, a.getType().name() + (active ? "  • ACTIVE" : ""), l + 10, y + 12, active ? 0xFF8CFFB0 : PurpleCard.DIM, true);
         this.rowButton(ctx, l + WIDTH - 104, y + 3, "Switch", !active && !this.busy, mouseX, mouseY);
         this.rowButton(ctx, l + WIDTH - 52, y + 3, "Remove", !this.busy, mouseX, mouseY);
      }
      CardText.draw(ctx, "Add an account", l + 8, bottom + 3, PurpleCard.LABEL, true);
      if (!this.status.isEmpty()) {
         String s = CardText.fit(this.status, WIDTH - 16);
         CardText.draw(ctx, s, l + WIDTH / 2.0F - CardText.width(s) / 2.0F, bottom + 36, this.statusColor, true);
      }
      super.extractRenderState(ctx, mouseX, mouseY, delta);
   }

   private void rowButton(GuiGraphicsExtractor ctx, int x, int y, String label, boolean enabled, int mx, int my) {
      boolean over = enabled && mx >= x && mx < x + 46 && my >= y && my < y + 14;
      PurpleCard.fillRounded(ctx, x, y, x + 46, y + 14, 3, 15, enabled ? (over ? 0x70A070FF : 0x50806AD0) : 0x20FFFFFF, enabled ? (over ? 0x60A070FF : 0x40806AD0) : 0x18FFFFFF);
      CardText.draw(ctx, label, x + 23 - CardText.width(label) / 2.0F, y + 3, enabled ? 0xFFFFFFFF : 0x80FFFFFF, true);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && !this.busy) {
         int l = this.left(), top = this.listTop();
         double mx = click.x(), my = click.y();
         int index = (int)((my - top) / ROW);
         if (my >= top && index >= 0 && index < this.visibleRows() && index + this.scroll < this.rows.size()) {
            Account<?> a = this.rows.get(index + this.scroll);
            double y = top + index * ROW + 3;
            if (my >= y && my < y + 14) {
               if (mx >= l + WIDTH - 104 && mx < l + WIDTH - 58 && !this.isActive(a)) {
                  this.switchTo(a);
                  return true;
               }
               if (mx >= l + WIDTH - 52 && mx < l + WIDTH - 6) {
                  Accounts.get().remove(a);
                  this.say("Removed " + a.getUsername() + ".", PurpleCard.DIM);
                  return true;
               }
            }
         }
      }
      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      this.scroll -= (int)Math.signum(vertical);
      return true;
   }
}
