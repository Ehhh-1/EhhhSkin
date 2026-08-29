package Ehhh.skin.mixin;

import Ehhh.skin.EhhhSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class MixinAbstractClientPlayerEntity {
    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void replaceSkin(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity)(Object)this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == self && EhhhSkin.isSkinLoaded()) {
            SkinTextures customSkin = new SkinTextures(
                    EhhhSkin.getCurrentSkinTexture(),
                    null,
                    null,
                    null,
                    EhhhSkin.getCurrentModel(),
                    false
            );
            cir.setReturnValue(customSkin);
        }
    }
}