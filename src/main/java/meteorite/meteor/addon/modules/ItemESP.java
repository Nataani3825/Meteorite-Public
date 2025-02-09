/**
 * ItemESP
 * =======
 *  - Written by Nataani (modified by you)
 *  Highlights dropped items that match a user‐defined list.
 *  Includes csv logging, an in game GUI of positions, and additional waypoint creation functionality.
 */
package meteorite.meteor.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteorite.meteor.addon.Meteorite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ItemESP extends Module {
    // ----------------------------------------------------------------
    // Setting Groups
    // ----------------------------------------------------------------
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgXaeros = settings.createGroup("Xaeros Waypoints");

    private final List<Item> defaultPlayerItems = new ArrayList<>(List.of(
        Items.DIAMOND_HELMET,
        Items.DIAMOND_CHESTPLATE,
        Items.DIAMOND_LEGGINGS,
        Items.DIAMOND_BOOTS,
        Items.NETHERITE_HELMET,
        Items.NETHERITE_CHESTPLATE,
        Items.NETHERITE_LEGGINGS,
        Items.NETHERITE_BOOTS,
        Items.ELYTRA,
        Items.MACE,
        Items.TRIDENT,
        Items.DIAMOND_SWORD,
        Items.DIAMOND_AXE,
        Items.DIAMOND_PICKAXE,
        Items.DIAMOND_SHOVEL,
        Items.DIAMOND_HOE,
        Items.NETHERITE_SWORD,
        Items.NETHERITE_AXE,
        Items.NETHERITE_PICKAXE,
        Items.NETHERITE_SHOVEL,
        Items.NETHERITE_HOE,
        Items.ENCHANTED_GOLDEN_APPLE,
        Items.END_CRYSTAL,
        Items.ENDER_CHEST,
        Items.TOTEM_OF_UNDYING,
        Items.EXPERIENCE_BOTTLE,
        Items.SHULKER_BOX,
        Items.RED_SHULKER_BOX,
        Items.ORANGE_SHULKER_BOX,
        Items.YELLOW_SHULKER_BOX,
        Items.LIME_SHULKER_BOX,
        Items.GREEN_SHULKER_BOX,
        Items.CYAN_SHULKER_BOX,
        Items.LIGHT_BLUE_SHULKER_BOX,
        Items.BLUE_SHULKER_BOX,
        Items.PURPLE_SHULKER_BOX,
        Items.MAGENTA_SHULKER_BOX,
        Items.PINK_SHULKER_BOX,
        Items.WHITE_SHULKER_BOX,
        Items.LIGHT_GRAY_SHULKER_BOX,
        Items.GRAY_SHULKER_BOX,
        Items.BROWN_SHULKER_BOX,
        Items.BLACK_SHULKER_BOX
    ));

    // ----------------------------------------------------------------
    // Settings for item highlighting
    // ----------------------------------------------------------------
    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to highlight.")
        .defaultValue(defaultPlayerItems)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the highlight boxes are rendered: lines, sides, or both.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Double> fillOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("fill-opacity")
        .description("Opacity of the box fill (0 = transparent, 1 = fully opaque).")
        .defaultValue(0.3)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    private final Setting<SettingColor> colorSetting = sgColors.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color of the highlighted items.")
        .defaultValue(new SettingColor(255, 255, 0, 255)) // default: yellow
        .build()
    );

    // ----------------------------------------------------------------
    // Xaeros Waypoint Settings (for logged items)
    // ----------------------------------------------------------------
    private final Setting<Boolean> createXaerosWaypoint = sgXaeros.add(new BoolSetting.Builder()
        .name("create-xaeros-waypoint")
        .description("If true, append a Xaeros waypoint entry when an item is logged. Note that a relog is required to see the waypoints.")
        .defaultValue(false)
        .build()
    );
    private final Setting<String> xaerosWaypointName = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-waypoint-name")
        .description("The name to use in the Xaeros waypoint entry.")
        .defaultValue("Item")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<String> xaerosWaypointLetter = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-waypoint-letter")
        .description("The letter to use in the Xaeros waypoint entry.")
        .defaultValue("I")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<Integer> xaerosColorNumber = sgXaeros.add(new IntSetting.Builder()
        .name("xaeros-color-number")
        .description("The color number to use in the Xaeros waypoint entry.")
        .defaultValue(1)
        .min(0)
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<String> xaerosOverworldWaypointFilePath = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-overworld-waypoint-file-path")
        .description("The file path for Xaeros waypoints in the Overworld.")
        .defaultValue("path/to/overworld/waypoints.txt")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<String> xaerosNetherWaypointFilePath = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-nether-waypoint-file-path")
        .description("The file path for Xaeros waypoints in the Nether.")
        .defaultValue("path/to/nether/waypoints.txt")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<String> xaerosEndWaypointFilePath = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-end-waypoint-file-path")
        .description("The file path for Xaeros waypoints in the End.")
        .defaultValue("path/to/end/waypoints.txt")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<Boolean> createOverworldWaypoints = sgXaeros.add(new BoolSetting.Builder()
        .name("create-overworld-waypoints")
        .description("If true, create Xaeros waypoints in the Overworld.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> createNetherWaypoints = sgXaeros.add(new BoolSetting.Builder()
        .name("create-nether-waypoints")
        .description("If true, create Xaeros waypoints in the Nether.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> createEndWaypoints = sgXaeros.add(new BoolSetting.Builder()
        .name("create-end-waypoints")
        .description("If true, create Xaeros waypoints in the End.")
        .defaultValue(true)
        .build()
    );

    // ----------------------------------------------------------------
    // Internal Color Buffers and Counters
    // ----------------------------------------------------------------
    private final Color lineColor = new Color();
    private final Color sideColor = new Color();

    // Counter for highlighted items (for HUD)
    private int count = 0;

    // ----------------------------------------------------------------
    // CSV Logging and Widget Storage
    // ----------------------------------------------------------------
    // Stores each logged item (logged only once per entity id)
    private final List<LoggedItem> loggedItems = new ArrayList<>();
    private final Set<Integer> loggedEntityIds = new HashSet<>();

    // ----------------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------------
    public ItemESP() {
        super(Meteorite.Main, "item-esp", "Highlights dropped items on the ground that match a given list.");
    }

    // ----------------------------------------------------------------
    // Rendering and Logging
    // ----------------------------------------------------------------
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        count = 0; // Reset highlighted count each render cycle

        // Loop through all entities in the world
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                if (shouldHighlight(itemEntity)) {
                    count++;
                    drawBoundingBox(event, itemEntity);

                    // If this item entity hasn’t been logged yet, log it.
                    if (!loggedEntityIds.contains(itemEntity.getId())) {
                        loggedEntityIds.add(itemEntity.getId());
                        BlockPos pos = itemEntity.getBlockPos();
                        String itemName = itemEntity.getStack().getItem().getName().getString();
                        LoggedItem log = new LoggedItem(pos.getX(), pos.getY(), pos.getZ(), itemName);
                        loggedItems.add(log);
                        saveCsv();  // Save the updated CSV

                        // If enabled, also add a Xaeros waypoint entry for this logged item.
                        if (createXaerosWaypoint.get()) {
                            appendWaypoint(log);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if an ItemEntity's dropped item matches the user-defined highlight list.
     */
    private boolean shouldHighlight(ItemEntity itemEntity) {
        Item droppedItem = itemEntity.getStack().getItem();
        return items.get().contains(droppedItem);
    }

    /**
     * Draws a highlight box around the specified item entity.
     */
    private void drawBoundingBox(Render3DEvent event, ItemEntity entity) {
        // Apply color
        lineColor.set(colorSetting.get().r, colorSetting.get().g, colorSetting.get().b, colorSetting.get().a);
        sideColor.set(lineColor).a((int) (sideColor.a * fillOpacity.get()));

        // Compute interpolated position
        double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
        double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
        double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

        // Get bounding box
        Box box = entity.getBoundingBox();

        // Render the box
        event.renderer.box(
            x + box.minX, y + box.minY, z + box.minZ,
            x + box.maxX, y + box.maxY, z + box.maxZ,
            sideColor, lineColor, shapeMode.get(), 0
        );
    }

    /**
     * Displays the number of highlighted item entities in the HUD info string.
     */
    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }

    // ----------------------------------------------------------------
    // CSV Logging Methods
    // ----------------------------------------------------------------
    /**
     * Writes the logged items to a CSV file.
     */
    private void saveCsv() {
        try {
            File file = getCsvFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            writer.write("X,Y,Z,Item\n");
            for (LoggedItem li : loggedItems) {
                li.write(writer);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the CSV file to log the items.
     */
    private File getCsvFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "itemesp"), Utils.getFileWorldName()), "logged_items.csv");
    }

    // ----------------------------------------------------------------
    // Xaeros Waypoint Methods
    // ----------------------------------------------------------------
    /**
     * Appends a Xaeros waypoint entry for the logged item.
     */
    private void appendWaypoint(LoggedItem li) {
        String filePath;
        Identifier dimId = mc.world.getRegistryKey().getValue();
        String dimStr = dimId.toString();
        if (dimStr.equals("minecraft:overworld")) {
            if (!createOverworldWaypoints.get()) return;
            filePath = xaerosOverworldWaypointFilePath.get();
        } else if (dimStr.equals("minecraft:the_nether")) {
            if (!createNetherWaypoints.get()) return;
            filePath = xaerosNetherWaypointFilePath.get();
        } else if (dimStr.equals("minecraft:the_end")) {
            if (!createEndWaypoints.get()) return;
            filePath = xaerosEndWaypointFilePath.get();
        } else {
            if (!createOverworldWaypoints.get()) return;
            filePath = xaerosOverworldWaypointFilePath.get();
        }
        int x = li.x;
        int y = li.y;
        int z = li.z;
        String entry = String.format("waypoint:%s:%s:%d:%d:%d:%d:false:0:gui.xaero_default:false:0:0:false",
            xaerosWaypointName.get(),
            xaerosWaypointLetter.get(),
            x, y, z,
            xaerosColorNumber.get()
        );
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            FileWriter fw = new FileWriter(file, true);
            fw.write(System.lineSeparator() + entry);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    // In-Game GUI Widget
    // ----------------------------------------------------------------
    /**
     * Returns a widget that displays the list of logged items.
     */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        // Create a vertical list to hold the widget components.
        WVerticalList list = theme.verticalList();
        WButton clear = list.add(theme.button("Clear")).widget();
        WTable table = new WTable();
        if (!loggedItems.isEmpty()) list.add(table);

        // Clear button action clears all logged items.
        clear.action = () -> {
            loggedItems.clear();
            loggedEntityIds.clear();
            table.clear();
            saveCsv();
        };

        // Populate the table with each logged item.
        for (LoggedItem li : new ArrayList<>(loggedItems)) {
            table.add(theme.label("Item: " + li.itemName + " at " + li.x + ", " + li.y + ", " + li.z));
            WButton gotoBtn = table.add(theme.button("Goto")).widget();
            gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(li.x, li.y, li.z), true);
            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                loggedItems.remove(li);
                loggedEntityIds.remove(li.hashCode()); // or update as needed
                table.clear();
                // Rebuild table
                for (LoggedItem li2 : new ArrayList<>(loggedItems)) {
                    table.add(theme.label("Item: " + li2.itemName + " at " + li2.x + ", " + li2.y + ", " + li2.z));
                    WButton gotoBtn2 = table.add(theme.button("Goto")).widget();
                    gotoBtn2.action = () -> PathManagers.get().moveTo(new BlockPos(li2.x, li2.y, li2.z), true);
                    WMinus delete2 = table.add(theme.minus()).widget();
                    delete2.action = () -> {
                        loggedItems.remove(li2);
                        table.clear();
                        saveCsv();
                    };
                    table.row();
                }
                saveCsv();
            };
            table.row();
        }
        return list;
    }

    // ----------------------------------------------------------------
    // Inner Class for Logged Items
    // ----------------------------------------------------------------
    private static class LoggedItem {
        public int x, y, z;
        public String itemName;

        public LoggedItem(int x, int y, int z, String itemName) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.itemName = itemName;
        }

        public void write(Writer writer) throws IOException {
            writer.write(x + "," + y + "," + z + "," + itemName + "\n");
        }
    }
}
