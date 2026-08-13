package net.celestiald.cavesnotcliffs.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * The vanilla F3 overlay gates its biome/light lines on a hardcoded 0..256 Y window and
 * prints "Outside of world..." below Y=0 even in extended-height worlds. Redirect the two
 * bounds-check reads so the extended range counts as inside; the reported values and all
 * non-extended worlds are untouched.
 */
@Mixin(GuiOverlayDebug.class)
public abstract class GuiOverlayDebugMixin {
    @Shadow @Final private Minecraft mc;

    @Redirect(method = "call()Ljava/util/List;",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/World;isBlockLoaded(Lnet/minecraft/util/math/BlockPos;)Z"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/Chunk;isEmpty()Z")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I"),
            require = 0,
            expect = 2)
    private int cavesnotcliffs$extendedBoundsY(BlockPos pos) {
        int y = pos.getY();
        if (mc.world != null && WorldHeightAPI.usesExtendedHeight(mc.world)) {
            return y >= WorldHeightAPI.getMinY() && y < WorldHeightAPI.getMaxY()
                    ? 64 : -1;
        }
        return y;
    }
}
