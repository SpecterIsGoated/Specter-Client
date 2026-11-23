package com.jfant.specter.phone.apps;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.game.ReceiveMessageEvent;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import com.jfant.specter.phone.ui.TextInput;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;

/**
 * Chat on the phone: the server's recent chat lines and a box to send messages or /commands, so a quick reply never
 * means putting the phone away. "Full chat" opens the regular chat box.
 */
public final class ChatApp extends PhoneApplication {
   private static final int KEEP = 150;
   private final ArrayDeque<Line> lines = new ArrayDeque<>();
   private final List<String> sent = new ArrayList<>();
   private boolean subscribed;
   private boolean stickToBottom = true;
   private int lastCount;

   private record Line(String text, long time, boolean mine) {
   }

   @Override
   protected void onCreate() {
      SpecterCore.EVENT_BUS.subscribe(this);
      this.subscribed = true;
   }

   @Override
   protected void onDestroy() {
      if (this.subscribed) SpecterCore.EVENT_BUS.unsubscribe(this);
      this.subscribed = false;
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      String text = event.getMessage().getString();
      if (text.isBlank()) return;
      this.lines.add(new Line(text, System.currentTimeMillis(), false));
      while (this.lines.size() > KEEP) this.lines.poll();
   }

   @EventHandler
   private void onLeave(GameLeftEvent event) {
      this.lines.clear();
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      PhoneTheme t = c.theme();
      float top = ui.header("Chat", null, Glyph.CHAT, () -> this.phone().openChat(""));
      float inputH = 26, bottom = c.height - PhoneCanvas.SAFE_BOTTOM - inputH - 10;
      // layout the lines (newest at the bottom)
      float w = c.width - 24;
      List<String[]> wrapped = new ArrayList<>();
      float total = 0;
      for (Line l : this.lines) {
         List<String> parts = c.wrap(l.text, w - 14, PhoneTheme.SMALL, 6);
         wrapped.add(parts.toArray(new String[0]));
         total += parts.size() * PhoneCanvas.line(PhoneTheme.SMALL) + 10;
      }
      ScrollView sv = ui.scroll("lines");
      if (this.lines.size() != this.lastCount) {
         this.lastCount = this.lines.size();
         if (this.stickToBottom) sv.jumpTo(Float.MAX_VALUE / 4);
      }
      float viewH = bottom - top;
      sv.begin(ui, 0, top, c.width, viewH, total + 8);
      float y = top + 4 + Math.max(0, viewH - total - 8);
      int i = 0;
      for (Line l : this.lines) {
         String[] parts = wrapped.get(i++);
         float h = parts.length * PhoneCanvas.line(PhoneTheme.SMALL) + 6;
         float bw = 0;
         for (String p : parts) bw = Math.max(bw, c.width(p, PhoneTheme.SMALL));
         bw += 14;
         float bx = l.mine ? c.width - 12 - bw : 12;
         c.round(bx, y, bw, h, 8, l.mine ? t.accent() : t.surface());
         for (int k = 0; k < parts.length; k++) {
            c.text(parts[k], bx + 7, y + 4 + k * PhoneCanvas.line(PhoneTheme.SMALL), PhoneTheme.SMALL, l.mine ? t.onAccent() : t.text());
         }
         y += h + 4;
      }
      if (this.lines.isEmpty()) {
         ui.empty(Glyph.CHAT, "No messages yet", "Chat from the server shows up here while you play.", top + 60);
      }
      sv.end(ui);
      this.stickToBottom = sv.atBottom(12) || sv.max() <= 0;
      // input
      TextInput field = ui.field("input");
      field.placeholder = "Message or /command";
      field.maxLength = 256;
      field.onSubmit = () -> this.send(field);
      float iy = c.height - PhoneCanvas.SAFE_BOTTOM - inputH - 4;
      ui.textField(field, 10, iy, c.width - 48, inputH);
      ui.iconButton(c.width - 22, iy + inputH / 2, 13, Glyph.SEND, field.isEmpty() ? t.textFaint() : t.accent(), field.isEmpty() ? null : () -> this.send(field));
   }

   private void send(TextInput field) {
      String text = field.get().trim();
      if (text.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() == null) {
         this.phone().toast("Not connected");
         return;
      }
      if (text.startsWith("/")) {
         this.phone().runCommand(text.substring(1));
      } else {
         mc.getConnection().sendChat(text);
      }
      mc.gui.hud.getChat().addRecentChat(text);
      this.sent.add(text);
      field.set("");
      this.stickToBottom = true;
   }
}
