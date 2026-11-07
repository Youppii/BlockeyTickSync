package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;

import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntity", at = @At("HEAD"))
    private void onEntityUpdate(EntityS2CPacket packet, CallbackInfo ci) {
        TickSyncMain.INSTANCE.onEntityPacket();
    }
}
