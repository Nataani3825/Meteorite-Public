/**
 * Survey
 * =======
 *  - Written by Nataani
 *  - Part of the Meteorite Module
 *  This module executes various different flight patterns; rectangular, spiral or zigzag.
 */

package meteorite.meteor.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteorite.meteor.addon.Meteorite;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class Survey extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPitch40 = settings.createGroup("Pitch40");
    private final SettingGroup sgSurvey = settings.createGroup("Survey");

    public final Setting<Boolean> pitch40 = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch40")
        .description("Enable Pitch40.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> survey = sgGeneral.add(new BoolSetting.Builder()
        .name("survey")
        .description("Enable Survey.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> replace = sgPitch40.add(new BoolSetting.Builder()
        .name("elytra-replace")
        .description("Replaces your elytra when its durability becomes dangerously low.")
        .defaultValue(false)
        .visible(pitch40::get)
        .build()
    );

    public final Setting<Integer> replaceDurability = sgPitch40.add(new IntSetting.Builder()
        .name("replace-durability")
        .description("Durability threshold at which the elytra will be replaced.")
        .defaultValue(2)
        .range(1, Items.ELYTRA.getComponents().get(DataComponentTypes.MAX_DAMAGE) - 1)
        .sliderRange(1, Items.ELYTRA.getComponents().get(DataComponentTypes.MAX_DAMAGE) - 1)
        .visible(replace::get)
        .build()
    );

    public final Setting<Boolean> autoReplenish = sgPitch40.add(new BoolSetting.Builder()
        .name("replenish-fireworks")
        .description("Automatically moves fireworks to a chosen hotbar slot if it's empty.")
        .defaultValue(false)
        .visible(pitch40::get)
        .build()
    );

    public final Setting<Integer> replenishSlot = sgPitch40.add(new IntSetting.Builder()
        .name("replenish-slot")
        .description("Which hotbar slot to move the fireworks into.")
        .defaultValue(9)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoReplenish::get)
        .build()
    );

    public enum SurveyMode {
        Rectangular,
        Spiral,
        ZigZag
    }

    public final Setting<SurveyMode> surveyMode = sgSurvey.add(new EnumSetting.Builder<SurveyMode>()
        .name("survey-mode")
        .description("The flight pattern to be executed.")
        .defaultValue(SurveyMode.Rectangular)
        .build()
    );

    public final Setting<Integer> surveyDistance = sgSurvey.add(new IntSetting.Builder()
        .name("survey-distance")
        .description("Distance (in blocks) to travel before turning in the rectangular or zigzag flight patterns.")
        .defaultValue(10000)
        .min(1000)
        .sliderMax(100000)
        .visible(() -> surveyMode.get() == SurveyMode.Rectangular || surveyMode.get() == SurveyMode.ZigZag)
        .build()
    );

    public enum CardinalDirections {
        NS,
        EW
    }

    public final Setting<CardinalDirections> cardinalDirections = sgSurvey.add(new EnumSetting.Builder<CardinalDirections>()
        .name("cardinal-directions")
        .description("Chooses whether the survey path is oriented North-South or East-West.")
        .defaultValue(CardinalDirections.NS)
        .visible(() -> surveyMode.get() == SurveyMode.Rectangular || surveyMode.get() == SurveyMode.Spiral)
        .build()
    );

    public enum FirstTurn {
        Right,
        Left
    }

    public final Setting<FirstTurn> firstTurn = sgSurvey.add(new EnumSetting.Builder<FirstTurn>()
        .name("first-turn")
        .description("Chooses whether the first turn of the survey will be right or left.")
        .defaultValue(FirstTurn.Right)
        .build()
    );

    public final Setting<Integer> renderDistance = sgSurvey.add(new IntSetting.Builder()
        .name("render-distance-chunks")
        .description("A chunk-based distance to travel on alternate legs of the rectangular pattern or to space the spiral.")
        .defaultValue(8)
        .min(2)
        .sliderMax(32)
        .visible(() -> surveyMode.get() == SurveyMode.Rectangular || surveyMode.get() == SurveyMode.Spiral)
        .build()
    );

    public final Setting<Integer> firstLegModifier = sgSurvey.add(new IntSetting.Builder()
        .name("first-leg-modifier")
        .description("Adjusts the first leg distance: subtracts for Rectangular, adds for Spiral.")
        .defaultValue(0)
        .min(0)
        .sliderMax(5000)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private int rocketCooldown = 0;

    private int pitch = 40;
    private boolean pitchingDown = true;
    private double pitch40lowerBounds;
    private double pitch40upperBounds;

    private int step;
    private Vec3d startPos;
    private double targetDistance;
    private float desiredYaw;
    private boolean usedFirstLegModifier;

    public Survey() {
        super(Meteorite.Main, "Survey",
            "Executes a survey pattern with optional Pitch40 flight.");
    }

    @Override
    public void onActivate() {
        if (pitch40.get()) {
            pitch = 40;
            pitchingDown = true;
            rocketCooldown = 0;
            pitch40upperBounds = mc.player.getY();
            pitch40lowerBounds = pitch40upperBounds - 30;
        }

        if (survey.get() && mc.player != null) {
            startPos = mc.player.getPos();
            step = 0;
            usedFirstLegModifier = false;

            if (surveyMode.get() == SurveyMode.Rectangular || surveyMode.get() == SurveyMode.ZigZag) {
                targetDistance = surveyDistance.get();
            } else if (surveyMode.get() == SurveyMode.Spiral) {
                targetDistance = renderDistance.get() * 16;
            }
            initSurveyYaw();
        }
    }

    @Override
    public void onDeactivate() {}

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pitch40.get()) {
            handlepitch40();
        }
        if (survey.get()) {
            doSurveyPattern();
        }
        doAutoReplenish();
    }

    private void handlepitch40() {
        if (mc.player == null) return;
        if (!mc.player.isFallFlying()) return;
        handlePitchLogic();
        if (mc.player.getY() < pitch40lowerBounds - 20 && rocketCooldown <= 0) {
            pitch = 40;
            mc.player.setPitch(pitch);
            useRocket();
            rocketCooldown = 80;
        }
    }

    private void handlePitchLogic() {
        if (rocketCooldown > 0) rocketCooldown--;

        if (pitchingDown && mc.player.getY() <= pitch40lowerBounds) {
            pitchingDown = false;
        } else if (!pitchingDown && mc.player.getY() >= pitch40upperBounds) {
            pitchingDown = true;
        }

        if (!pitchingDown && mc.player.getPitch() > -40) {
            pitch -= 4;
            if (pitch < -40) pitch = -40;
        } else if (pitchingDown && mc.player.getPitch() < 40) {
            pitch += 4;
            if (pitch > 40) pitch = 40;
        }
        mc.player.setPitch(pitch);
    }

    private void useRocket() {
        FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!rocket.found()) return;
        if (rocket.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
        } else {
            InvUtils.swap(rocket.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
    }

    private void doSurveyPattern() {
        if (mc.player == null) return;
        double distTraveled = mc.player.getPos().distanceTo(startPos);

        if (!usedFirstLegModifier) {
            if (surveyMode.get() == SurveyMode.Rectangular || surveyMode.get() == SurveyMode.ZigZag) {
                targetDistance = Math.max(1, surveyDistance.get() - firstLegModifier.get());
            } else if (surveyMode.get() == SurveyMode.Spiral) {
                targetDistance = (renderDistance.get() * 16) + firstLegModifier.get();
            }
            usedFirstLegModifier = true;
        }

        if (step == 0 && surveyMode.get() == SurveyMode.ZigZag) {
            rotateAndAdvanceStep();
        }

        if (distTraveled >= targetDistance) {
            rotateAndAdvanceStep();
        }
        mc.player.setYaw(desiredYaw);

    }

    private void rotateAndAdvanceStep() {
        if (surveyMode.get() == SurveyMode.Rectangular) {
            switch (step) {
                case 0 -> {
                    if (firstTurn.get() == FirstTurn.Right) {
                        desiredYaw += 90;
                    } else {
                        desiredYaw -= 90;
                    }
                    step = 1;
                    targetDistance = renderDistance.get() * 16;
                }
                case 1 -> {
                    if (firstTurn.get() == FirstTurn.Right) {
                        desiredYaw += 90;
                    } else {
                        desiredYaw -= 90;
                    }
                    step = 2;
                    targetDistance = surveyDistance.get();
                }
                case 2 -> {
                    if (firstTurn.get() == FirstTurn.Right) {
                        desiredYaw -= 90;
                    } else {
                        desiredYaw += 90;
                    }
                    step = 3;
                    targetDistance = renderDistance.get() * 16;
                }
                case 3 -> {
                    if (firstTurn.get() == FirstTurn.Right) {
                        desiredYaw -= 90;
                    } else {
                        desiredYaw += 90;
                    }
                    step = 0;
                    targetDistance = surveyDistance.get();
                }
            }

            desiredYaw = (desiredYaw + 360) % 360;
            startPos = mc.player.getPos();
        } else if (surveyMode.get() == SurveyMode.Spiral) {
            if (firstTurn.get() == FirstTurn.Right) {
                desiredYaw += 90;
            } else {
                desiredYaw -= 90;
            }

            targetDistance += renderDistance.get() * 16;
            desiredYaw = (desiredYaw + 360) % 360;
            startPos = mc.player.getPos();
        } else if (surveyMode.get() == SurveyMode.ZigZag) {
            switch (step) {
                case 0 -> {
                    targetDistance = (double) surveyDistance.get() / 2;
                    if (firstTurn.get() == FirstTurn.Right) {
                        desiredYaw += 45;
                    } else {
                        desiredYaw -= 45;
                    }
                    step = 1;
                }
                case 1 -> {
                    targetDistance = surveyDistance.get();
                    if (firstTurn.get() == FirstTurn.Right) {
                        desiredYaw -= 90;
                    } else {
                        desiredYaw += 90;
                    }
                    step = 2;
                }
                case 2 -> {
                    if (firstTurn.get() == FirstTurn.Right) {
                        desiredYaw += 90;
                    } else {
                        desiredYaw -= 90;
                    }
                    step = 1;
                }
            }

            desiredYaw = (desiredYaw + 360) % 360;
            startPos = mc.player.getPos();
        }
    }

    private void initSurveyYaw() {
        if (mc.player == null) return;
        float yaw = mc.player.getYaw() % 360;
        if (yaw < 0) yaw += 360;
        if (surveyMode.get() == SurveyMode.Rectangular || surveyMode.get() == SurveyMode.Spiral) {
            if (cardinalDirections.get() == CardinalDirections.NS) {
                float diffSouth = Math.abs(yaw - 0);
                float diffNorth = Math.abs(yaw - 180);
                desiredYaw = (diffNorth < diffSouth) ? 180 : 0;
            } else {
                float diffEast = Math.abs(yaw - 90);
                float diffWest = Math.abs(yaw - 270);
                desiredYaw = (diffEast < diffWest) ? 90 : 270;
            }
        }
        else if (surveyMode.get() == SurveyMode.ZigZag) {
            desiredYaw = yaw;
        }
        mc.player.setYaw(desiredYaw);
    }

    private void doAutoReplenish() {
        if (autoReplenish.get()) {
            FindItemResult fireworks = InvUtils.find(Items.FIREWORK_ROCKET);
            if (fireworks.found() && !fireworks.isHotbar()) {
                InvUtils.move().from(fireworks.slot()).toHotbar(replenishSlot.get() - 1);
            }
        }
        if (replace.get()) {
            ItemStack chestStack = mc.player.getInventory().getArmorStack(2);
            if (chestStack.getItem() == Items.ELYTRA) {
                int damageLeft = chestStack.getMaxDamage() - chestStack.getDamage();
                if (damageLeft <= replaceDurability.get()) {
                    FindItemResult elytra = InvUtils.find(stack ->
                        stack.getItem() == Items.ELYTRA &&
                            (stack.getMaxDamage() - stack.getDamage()) > replaceDurability.get()
                    );
                    if (elytra.found()) {
                        InvUtils.move().from(elytra.slot()).toArmor(2);
                    }
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        return "Survey";
    }
}
