package com.jfant.specter.core.modintegration;

import java.util.function.BooleanSupplier;

public class BadOptimizationsHook implements BooleanSupplier {
   @Override
   public boolean getAsBoolean() {
      return false;
   }
}
