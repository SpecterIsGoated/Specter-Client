package com.jfant.specter.gui;

import java.util.List;

public interface PageActions {
   List<PageActions.Action> pageActions();

   public record Action(String label, String description, boolean danger, Runnable run) {
      public Action(String label, String description, Runnable run) {
         this(label, description, false, run);
      }
   }
}
