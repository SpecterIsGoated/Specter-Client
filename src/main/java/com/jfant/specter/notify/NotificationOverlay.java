package com.jfant.specter.notify;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.gui.screen.SpecterScreen;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class NotificationOverlay {
   private static boolean subscribed;

   private NotificationOverlay() {
   }

   public static void init() {
      if (!subscribed) {
         subscribed = true;
         SpecterCore.EVENT_BUS.subscribe(NotificationOverlay.class);
      }
   }

   @EventHandler(
      priority = -200
   )
   private static void onRender(Render2DEvent event) {
      if (!(SpecterCore.mc.gui.screen() instanceof SpecterScreen)) {
         GuiGraphicsExtractor ctx = event.graphics;
         Notifications.render(ctx, ctx.guiWidth(), ctx.guiHeight());
      }
   }

   @EventHandler
   private static void onLeave(GameLeftEvent event) {
      Notifications.clear();
   }
}
