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
            // 直接构造新皮肤纹理，使用细手臂模型
            SkinTextures customSkin = new SkinTextures(
                    EhhhSkin.CUSTOM_SKIN,          // 自定义皮肤纹理
                    null,                          // 纹理 URL
                    null,                          // 披风纹理
                    null,                          // 披风 URL
                    SkinTextures.Model.SLIM,       // 模型：细手臂
                    false                          // 安全标志
            );
            cir.setReturnValue(customSkin);
        }
    }
}