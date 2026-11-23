package com.jfant.specter.gui.screen;

import com.jfant.specter.core.SpecterCore;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Your Minecraft account ID (UUID), hidden until you click it (so it isn't on screen in a stream or screenshot by
 * accident). Click to show it; click again to copy it. It hides itself again after 15 seconds.
 */
final class AccountIdCard extends Card {
   private static final long SHOW_MS = 15_000L;
   private long shownAt;
   private long copiedAt;

   AccountIdCard() {
      super("Account ID (UUID)", "Your Minecraft account's ID. Hidden until you click it; click again to copy. It's an ID, not a password or token.");
   }

   private boolean shown() {
      return this.shownAt != 0L && System.currentTimeMillis() - this.shownAt < SHOW_MS;
   }

   @Override
   int height() {
      return 32;
   }

   @Override
   void content(GuiGraphicsExtractor ctx, int mx, int my, boolean hover) {
      Ui.text(ctx, Ui.fit(this.label, this.w - 12), this.x + 6, this.y + 4, -1911561);
      String line;
      int color;
      if (System.currentTimeMillis() - this.copiedAt < 1500L) {
         line = "Copied";
         color = -7278960;
      } else if (this.shown()) {
         UUID id = SpecterCore.mc.getUser().getProfileId();
         line = id == null ? "No account ID" : id.toString();
         color = -1;
      } else {
         line = "Hidden - click to show";
         color = -4149795;
      }
      Ui.text(ctx, Ui.fit(line, this.w - 12), this.x + 6, this.y + 17, color);
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      Ui.press(this);
      if (this.shown()) {
         UUID id = SpecterCore.mc.getUser().getProfileId();
         if (id != null) SpecterCore.mc.keyboardHandler.setClipboard(id.toString());
         this.copiedAt = System.currentTimeMillis();
      } else {
         this.shownAt = System.currentTimeMillis();
      }
   }
}
