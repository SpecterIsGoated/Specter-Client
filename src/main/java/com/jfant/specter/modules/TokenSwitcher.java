package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.gui.screen.TokenSwitcherScreen;

/**
 * Opens the Token Switcher (bind it to a key, or click it in the GUI). Acts like a button: it turns itself back off.
 * The accounts are Specter's saved accounts (the same ones the alt manager uses), saved encrypted.
 */
public class TokenSwitcher extends Module {
   public TokenSwitcher() {
      super(SpecterClientAddon.TOOLS, "token-switcher", "Switch between your saved accounts, or add one from a session token. Tokens are never shown or logged and are saved encrypted.");
   }

   @Override
   public void onActivate() {
      if (this.mc.gui.screen() != null) this.mc.gui.setScreen(new TokenSwitcherScreen());
      else Utils.screenToOpen = new TokenSwitcherScreen();
      this.toggle();
   }
}
