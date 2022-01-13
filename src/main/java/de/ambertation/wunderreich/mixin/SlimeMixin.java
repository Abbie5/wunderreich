package de.ambertation.wunderreich.mixin;

import de.ambertation.wunderreich.config.WunderreichConfigs;

import net.minecraft.world.entity.monster.Slime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slime.class)
public class SlimeMixin {
    @Inject(method = "shouldDespawnInPeaceful", at = @At("HEAD"), cancellable = true)
    protected void wunder_shouldDespawnInPeaceful(CallbackInfoReturnable<Boolean> cir) {
        if (WunderreichConfigs.MAIN.doNotDespawnWithNameTag.get()) {
            Slime m = (Slime) (Object) this;
            if (m.hasCustomName()) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }
}
