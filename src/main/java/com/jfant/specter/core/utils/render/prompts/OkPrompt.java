package com.jfant.specter.core.utils.render.prompts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import net.minecraft.client.gui.screens.Screen;

public class OkPrompt extends Prompt<OkPrompt> {
   private Runnable onOk = () -> {};

   private OkPrompt(GuiTheme theme, Screen parent) {
      super(theme, parent);
   }

   public static OkPrompt create() {
      return new OkPrompt(GuiThemes.get(), SpecterCore.mc.screen);
   }

   public static OkPrompt create(GuiTheme theme, Screen parent) {
      return new OkPrompt(theme, parent);
   }

   public OkPrompt onOk(Runnable action) {
      this.onOk = action;
      return this;
   }

   @Override
   protected void initialiseWidgets(Prompt<OkPrompt>.PromptScreen screen) {
      WButton okButton = screen.list.add(this.theme.button("Ok")).expandX().widget();
      okButton.action = () -> {
         this.dontShowAgain(screen);
         this.onOk.run();
         screen.onClose();
      };
   }
}
