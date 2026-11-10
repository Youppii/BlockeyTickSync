package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleMoveEntity", at = @At("HEAD"))
    private void onEntityUpdate(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        TickSyncMain.INSTANCE.onEntityPacket((ClientPacketListener) (Object) this);
    }
}
