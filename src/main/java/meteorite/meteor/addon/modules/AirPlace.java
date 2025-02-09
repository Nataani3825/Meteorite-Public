/**
 * AirPlace
 * =======
 *  - Written by Nataani
 *  - Part of the Meteorite Module
 *  An airplace module which bypasses grim.
 */

package meteorite.meteor.addon.modules;

import meteorite.meteor.addon.Meteorite;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.*;

public class AirPlace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far away from the player to place the block.")
        .defaultValue(5)
        .sliderRange(1, 5.5)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("The render mode.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Render side color.")
        .defaultValue(new SettingColor(5, 205, 5, 235))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Render line color.")
        .defaultValue(new SettingColor(5, 205, 5, 235))
        .build()
    );

    private HitResult hitResult;

    public AirPlace() {
        super(Meteorite.Main, "air-place", "Places a block where your crosshair is pointing.  Bypasses GrimAC.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        hitResult = mc.getCameraEntity().raycast(distance.get(), 0, false);

        if (!(hitResult instanceof BlockHitResult blockHitResult)) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;
        if (!mc.options.useKey.isPressed()) return;

        int revision = mc.player.currentScreenHandler.getRevision();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        BlockPos swapPos = new BlockPos(0, 0, 0);

        mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, revision, yaw, pitch));
        mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, revision + 1, yaw, pitch));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, swapPos, blockHitResult.getSide()));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, blockHitResult, revision + 2));
        mc.player.swingHand(Hand.OFF_HAND);
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, swapPos, blockHitResult.getSide()));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!(hitResult instanceof BlockHitResult blockHitResult)) return;
        if (!mc.world.getBlockState(blockHitResult.getBlockPos()).isReplaceable()) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;

        event.renderer.box(blockHitResult.getBlockPos(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}
