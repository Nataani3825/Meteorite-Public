/**
 * MiddleClickFlight
 * =================
 *  - Adapted by Nataani from the base Meteor MiddleClickExtra module.
 *
 *  When you middle-click, this module swaps to (and uses) a rocket, then triggers
 *  elytra flight if you're not already gliding. Once airborne, it automatically fires
 *  the rocket after a short delay, letting you get an immediate boost from a single click.
 */

package meteorite.meteor.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.FinishUsingItemEvent;
import meteordevelopment.meteorclient.events.entity.player.StoppedUsingItemEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteorite.meteor.addon.Meteorite;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE;

public class MiddleClickFlight extends Module {
    // ----------------------------------------------------------------
    // Setting Group
    // ----------------------------------------------------------------
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ----------------------------------------------------------------
    // Settings
    // ----------------------------------------------------------------
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which item to use for flight when middle-clicked.")
        .defaultValue(Mode.Rocket)
        .build()
    );

    private final Setting<Boolean> quickSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("quick-swap")
        .description("Pull items from your inventory by simulating hotbar key presses. Potentially unsafe on some servers.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Revert to the original item slot once usage is finished.")
        .defaultValue(false)
        .visible(() -> !quickSwap.get())
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Warns you if a rocket is not found in your hotbar.")
        .defaultValue(true)
        .build()
    );

    // ----------------------------------------------------------------
    // Internal State
    // ----------------------------------------------------------------
    private boolean isUsing;
    private boolean wasHeld;
    private int itemSlot;
    private int selectedSlot;

    private boolean shouldTakeOff;
    private boolean jumpedFromGround;
    private int takeoffTimer;

    private boolean shouldUseRocket;
    private int rocketTimer;

    // ----------------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------------
    public MiddleClickFlight() {
        super(Meteorite.Main, "middle-click-flight", "One-click solution: deploy elytra flight, then automatically fire a rocket.");
    }

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------
    @Override
    public void onDeactivate() {
        stopIfUsing(false);
        resetTakeoff();
        resetRocketUsage();
    }

    // ----------------------------------------------------------------
    // Input Handling
    // ----------------------------------------------------------------
    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (event.action != KeyAction.Press || event.button != GLFW_MOUSE_BUTTON_MIDDLE || mc.currentScreen != null) return;

        FindItemResult rocket = InvUtils.find(mode.get().item);
        if (!rocket.found() || (!rocket.isHotbar() && !quickSwap.get())) {
            // If not found, optionally warn
            if (notify.get()) warning("Unable to find the rocket in your hotbar.");
            return;
        }

        selectedSlot = mc.player.getInventory().selectedSlot;
        itemSlot = rocket.slot();
        wasHeld = rocket.isMainHand();

        if (!wasHeld) {
            if (!quickSwap.get()) {
                InvUtils.swap(itemSlot, swapBack.get());
            } else {
                InvUtils.quickSwap().fromId(selectedSlot).to(itemSlot);
            }
        }

        if (mc.player.isFallFlying()) {
            useRocket();
        } else {
            initTakeoff();
        }
    }

    // ----------------------------------------------------------------
    // Tick Logic
    // ----------------------------------------------------------------
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (isUsing) {
            boolean pressed = true;
            if (mc.player.getMainHandStack().getItem() instanceof BowItem) {
                pressed = BowItem.getPullProgress(mc.player.getItemUseTime()) < 1;
            }
            mc.options.useKey.setPressed(pressed);
        }

        if (shouldTakeOff) handleTakeoff();

        if (shouldUseRocket) handlePostFlightRocket();
    }

    // ----------------------------------------------------------------
    // Takeoff Handling
    // ----------------------------------------------------------------
    private void initTakeoff() {
        shouldTakeOff = true;
        takeoffTimer = 0;

        if (mc.player.isOnGround()) {
            jumpedFromGround = true;
            mc.player.jump();
        } else {
            jumpedFromGround = false;
        }
    }

    private void handleTakeoff() {
        if (mc.player.isFallFlying()) {
            resetTakeoff();
            useRocket();
            return;
        }

        takeoffTimer++;

        if (jumpedFromGround && takeoffTimer >= 3) {
            doTakeoff();
        }

        else if (!jumpedFromGround && takeoffTimer >= 1) {
            doTakeoff();
        }
    }

    private void doTakeoff() {
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        shouldUseRocket = true;
        rocketTimer = 0;
        resetTakeoff();
    }

    private void resetTakeoff() {
        shouldTakeOff = false;
        jumpedFromGround = false;
        takeoffTimer = 0;
    }

    // ----------------------------------------------------------------
    // Post-Flight Rocket Usage
    // ----------------------------------------------------------------
    private void handlePostFlightRocket() {
        if (!mc.player.isFallFlying()) return;

        rocketTimer++;
        if (rocketTimer >= 2) {
            useRocket();
            resetRocketUsage();
        }
    }

    private void resetRocketUsage() {
        shouldUseRocket = false;
        rocketTimer = 0;
    }

    private void useRocket() {
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        // Optionally swap back to original slot
        swapBack(false);
    }

    // ----------------------------------------------------------------
    // Cleanup: Stopping usage mid-flight
    // ----------------------------------------------------------------
    @EventHandler
    private void onPacketSendEvent(PacketEvent.Send event) {
        // If hotbar slot changes, consider usage canceled
        if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
            stopIfUsing(true);
        }
    }

    @EventHandler
    private void onStoppedUsingItem(StoppedUsingItemEvent event) {
        stopIfUsing(false);
    }

    @EventHandler
    private void onFinishUsingItem(FinishUsingItemEvent event) {
        stopIfUsing(false);
    }

    private void stopIfUsing(boolean wasCancelled) {
        if (isUsing) {
            swapBack(wasCancelled);
            mc.options.useKey.setPressed(false);
            isUsing = false;
        }
    }

    private void swapBack(boolean wasCancelled) {
        if (wasHeld) return;
        if (quickSwap.get()) {
            InvUtils.quickSwap().fromId(selectedSlot).to(itemSlot);
        } else {
            if (!swapBack.get() || wasCancelled) return;
            InvUtils.swapBack();
        }
    }

    // ----------------------------------------------------------------
    // Enum representing the rocket mode
    // ----------------------------------------------------------------
    public enum Mode {
        Rocket(Items.FIREWORK_ROCKET, true);

        public final Item item;
        public final boolean immediate;

        Mode(Item item, boolean immediate) {
            this.item = item;
            this.immediate = immediate;
        }
    }
}
