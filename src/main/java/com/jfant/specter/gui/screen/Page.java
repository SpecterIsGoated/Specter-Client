package com.jfant.specter.gui.screen;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.meteor.ModuleBindChangedEvent;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.gui.PageActions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;

final class Page {
   final Object key;
   final String title;
   final String description;
   final Settings settings;
   @Nullable
   final BooleanSupplier on;
   @Nullable
   final Runnable toggle;
   final List<Card> extras;

   Page(Object key, String title, String description, Settings settings, @Nullable BooleanSupplier on, @Nullable Runnable toggle, List<Card> extras) {
      this.key = key;
      this.title = title;
      this.description = description == null ? "" : description;
      this.settings = settings;
      this.on = on;
      this.toggle = toggle;
      this.extras = extras;
   }

   static Page of(Module module) {
      Card bind = new KeyCard(
         "Bind",
         "Press a key or a mouse button (not left or right) to toggle this module. Escape cancels, Delete clears.",
         () -> module.keybind,
         () -> SpecterCore.EVENT_BUS.post(ModuleBindChangedEvent.get(module)),
         () -> {
            module.keybind.set(Keybind.none());
            SpecterCore.EVENT_BUS.post(ModuleBindChangedEvent.get(module));
         }
      );
      Card toggleMsg = new ToggleCard(
         "Toggle msg",
         "Whether switching this module on or off says so in chat.",
         () -> module.chatFeedback,
         value -> module.chatFeedback = value,
         () -> module.chatFeedback = true
      );
      List<Card> extras = new ArrayList<>();
      if (module instanceof PageActions actions) {
         for (PageActions.Action action : actions.pageActions()) {
            extras.add(new ButtonCard(action.label(), action.description(), action.danger(), action.run()));
         }
      }

      extras.add(bind);
      extras.add(toggleMsg);
      return new Page(module, module.title, module.description, module.settings, module::isActive, module::toggle, extras);
   }
}
