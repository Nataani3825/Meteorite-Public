/**
 * SpawnerScout
 * =======
 *  - Skidded from Trouser ActivatedSpawnerDetector
 *  - Written by Nataani
 *  - Part of the Meteorite Module
 *  This module is based on an old version of ActivatedSpawnerDetector.
 *  Includes csv logging, an in game GUI of positions, and additional waypoint creation functionality.
 */

package meteorite.meteor.addon.modules;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.world.StashFinder;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteorite.meteor.addon.Meteorite;
import meteorite.meteor.addon.mixin.MobSpawnerLogicMixin;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public class SpawnerScout extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgXaeros = settings.createGroup("Xaeros Waypoints");
    private final Setting<Boolean> displaycoords = sgGeneral.add(new BoolSetting.Builder()
        .name("DisplayCoords")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .defaultValue(true)
        .build()
    );
    private final Setting<StashFinder.Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<StashFinder.Mode>()
        .name("notification-mode")
        .defaultValue(StashFinder.Mode.Both)
        .visible(sendNotifications::get)
        .build()
    );
    public final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("Render-Distance(Chunks)")
        .defaultValue(32)
        .min(6)
        .sliderRange(6, 1024)
        .build()
    );
    private final Setting<Boolean> removerenderdist = sgRender.add(new BoolSetting.Builder()
        .name("RemoveOutsideRenderDistance")
        .defaultValue(true)
        .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> spawnerSideColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-side-color")
        .defaultValue(new SettingColor(251, 5, 5, 70))
        .visible(() -> (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    private final Setting<SettingColor> spawnerLineColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-line-color")
        .defaultValue(new SettingColor(251, 5, 5, 235))
        .visible(() -> (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    public final Setting<Integer> blockDistance = sgRender.add(new IntSetting.Builder()
        .name("Block-Distance")
        .defaultValue(7)
        .min(1)
        .sliderRange(1, 14)
        .build()
    );
    private final Setting<Boolean> rangerendering = sgRender.add(new BoolSetting.Builder()
        .name("spawner-range-rendering")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> rangeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-range-side-color")
        .defaultValue(new SettingColor(5, 178, 251, 30))
        .visible(() -> rangerendering.get() && (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    private final Setting<SettingColor> rangeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-range-line-color")
        .defaultValue(new SettingColor(5, 178, 251, 155))
        .visible(() -> rangerendering.get() && (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
        .build()
    );
    private final Setting<Boolean> createXaerosWaypoint = sgXaeros.add(new BoolSetting.Builder()
        .name("create-xaeros-waypoint")
        .description("If true, append a Xaeros waypoint entry when a spawner is discovered.  Note that a relog is required to see the waypoints.")
        .defaultValue(false)
        .build()
    );
    private final Setting<String> xaerosWaypointName = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-waypoint-name")
        .description("The name to use in the Xaeros waypoint entry.")
        .defaultValue("Spawner")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<String> xaerosWaypointLetter = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-waypoint-letter")
        .description("The letter to use in the Xaeros waypoint entry.")
        .defaultValue("S")
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
        .description("The file path for Xaeros waypoints in the Overworld.  Normally {MinecraftPath}/xaero/minimap/World/dim%0/mw$default_1.txt")
        .defaultValue("path/to/overworld/waypoints.txt")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<String> xaerosNetherWaypointFilePath = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-nether-waypoint-file-path")
        .description("The file path for Xaeros waypoints in the Nether.  Normally {MinecraftPath}/.minecraft/xaero/minimap/World/dim%-1/mw$default_1.txt")
        .defaultValue("path/to/nether/waypoints.txt")
        .visible(createXaerosWaypoint::get)
        .build()
    );
    private final Setting<String> xaerosEndWaypointFilePath = sgXaeros.add(new StringSetting.Builder()
        .name("xaeros-end-waypoint-file-path")
        .description("The file path for Xaeros waypoints in the End.  Normally {MinecraftPath}/xaero/minimap/World/dim%-2/mw$default_1.txt")
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
    private final Set<BlockPos> spawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> foundSpawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final List<Spawner> spawners = new ArrayList<>();
    private boolean chestfound;
    private boolean webfound;

    public SpawnerScout() {
        super(Meteorite.Main, "spawner-scout", "Highlights activated spawners in the world and stores them.");
    }

    @Override
    public void onActivate() {
        spawnerPositions.clear();
        foundSpawnerPositions.clear();
        load();
    }
    @Override
    public void onDeactivate() {
        spawnerPositions.clear();
        spawners.clear();
    }
    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.world == null) return;
        int rd = mc.options.getViewDistance().getValue();
        int dist = blockDistance.get();
        ChunkPos playerChunkPos = new ChunkPos(mc.player.getBlockPos());
        for (int chunkX = playerChunkPos.x - rd; chunkX <= playerChunkPos.x + rd; chunkX++) {
            for (int chunkZ = playerChunkPos.z - rd; chunkZ <= playerChunkPos.z + rd; chunkZ++) {
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
                        BlockPos pos = spawner.getPos();
                        MobSpawnerLogicMixin logic = (MobSpawnerLogicMixin) spawner.getLogic();
                        if (foundSpawnerPositions.contains(pos)) {
                            spawnerPositions.add(pos);
                            continue;
                        }
                        if (logic.getSpawnDelay() != 20) {
                            foundSpawnerPositions.add(pos);
                            spawnerPositions.add(pos);
                            chestfound = false;
                            webfound = false;
                            for (int x = -dist; x < dist; x++) {
                                for (int y = -dist; y < dist; y++) {
                                    for (int z = -dist; z < dist; z++) {
                                        BlockPos bpos = pos.add(x, y, z);
                                        if (mc.world.getBlockState(bpos).getBlock() == Blocks.CHEST) chestfound = true;
                                        if (mc.world.getBlockState(bpos).getBlock() == Blocks.COBWEB) webfound = true;
                                    }
                                }
                            }
                            Spawner newSpawner = new Spawner(pos.getX(), pos.getY(), pos.getZ());
                            spawners.add(newSpawner);
                            if (createXaerosWaypoint.get()) {
                                appendWaypoint(newSpawner);
                            }
                            saveJson();
                            saveCsv();
                            if (sendNotifications.get()) {
                                if (chestfound && !webfound) {
                                    notify("Dungeon Spawner! " + pos);
                                } else if (!chestfound && webfound) {
                                    notify("Mineshaft Spawner! " + pos);
                                } else if (chestfound && webfound) {
                                    notify("Mineshaft Spawner w/ Chests! " + pos);
                                }
                            }
                            if (displaycoords.get()) {
                                if (chestfound && !webfound) {
                                    ChatUtils.sendMsg(Text.of("Dungeon Spawner! " + pos));
                                } else if (!chestfound && webfound) {
                                    ChatUtils.sendMsg(Text.of("Mineshaft Spawner! " + pos));
                                } else if (chestfound && webfound) {
                                    ChatUtils.sendMsg(Text.of("Mineshaft Spawner w/ Chests! " + pos));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (removerenderdist.get()) removeChunksOutsideRenderDistance();
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (spawnerSideColor.get().a <= 5 && spawnerLineColor.get().a <= 5 && rangeSideColor.get().a <= 5 && rangeLineColor.get().a <= 5) return;
        synchronized (spawnerPositions) {
            for (BlockPos pos : spawnerPositions) {
                if (pos != null && mc.getCameraEntity().getBlockPos().isWithinDistance(pos, renderDistance.get() * 16)) {
                    int x = pos.getX();
                    int y = pos.getY();
                    int z = pos.getZ();
                    int d = blockDistance.get();
                    if (rangerendering.get()) {
                        Box box = new Box(
                            x + d, y + d, z + d,
                            x - d, y - d, z - d
                        );
                        render(box, rangeSideColor.get(), rangeLineColor.get(), shapeMode.get(), event);
                    } else {
                        Box box = new Box(
                            x + 1, y + 1, z + 1,
                            x, y, z
                        );
                        render(box, spawnerSideColor.get(), spawnerLineColor.get(), shapeMode.get(), event);
                    }
                }
            }
        }
    }
    private void removeChunksOutsideRenderDistance() {
        BlockPos cameraPos = mc.getCameraEntity().getBlockPos();
        double rdb = renderDistance.get() * 16;
        spawnerPositions.removeIf(p -> !cameraPos.isWithinDistance(p, rdb));
    }
    private void render(Box box, Color sides, Color lines, ShapeMode shapeMode, Render3DEvent event) {
        event.renderer.box(
            Math.min(box.minX, box.maxX),
            Math.min(box.minY, box.maxY),
            Math.min(box.minZ, box.maxZ),
            Math.max(box.minX, box.maxX),
            Math.max(box.minY, box.maxY),
            Math.max(box.minZ, box.maxZ),
            sides, lines, shapeMode, 0
        );
    }
    private void notify(String message) {
        switch (notificationMode.get()) {
            case Chat -> {}
            case Toast -> mc.getToastManager().add(new MeteorToast(Items.SPAWNER, title, message));
            case Both -> {
                ChatUtils.sendMsg(Text.of(message));
                mc.getToastManager().add(new MeteorToast(Items.SPAWNER, title, message));
            }
        }
    }
    @Override
    public WWidget getWidget(GuiTheme theme) {
        spawners.sort(Comparator.comparingInt(a -> a.y));
        WVerticalList list = theme.verticalList();
        WButton clear = list.add(theme.button("Clear")).widget();
        WTable table = new WTable();
        if (!spawners.isEmpty()) list.add(table);
        clear.action = () -> {
            spawners.clear();
            spawnerPositions.clear();
            foundSpawnerPositions.clear();
            table.clear();
            saveJson();
            saveCsv();
        };
        fillTable(theme, table);
        return list;
    }
    private void fillTable(GuiTheme theme, WTable table) {
        for (Spawner s : spawners) {
            table.add(theme.label("Pos: " + s.x + ", " + s.y + ", " + s.z));
            WButton gotoBtn = table.add(theme.button("Goto")).widget();
            gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(s.x, s.y, s.z), true);
            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                spawners.remove(s);
                spawnerPositions.remove(new BlockPos(s.x, s.y, s.z));
                foundSpawnerPositions.remove(new BlockPos(s.x, s.y, s.z));
                table.clear();
                fillTable(theme, table);
                saveJson();
                saveCsv();
            };
            table.row();
        }
    }
    private void load() {
        File file = getJsonFile();
        boolean loaded = false;
        if (file.exists()) {
            try {
                FileReader reader = new FileReader(file);
                List<Spawner> data = GSON.fromJson(reader, new TypeToken<List<Spawner>>() {}.getType());
                reader.close();
                if (data != null) {
                    spawners.addAll(data);
                    for (Spawner s : data) {
                        BlockPos p = new BlockPos(s.x, s.y, s.z);
                        spawnerPositions.add(p);
                        foundSpawnerPositions.add(p);
                    }
                    loaded = true;
                }
            } catch (Exception ignored) {}
        }
        if (!loaded) {
            file = getCsvFile();
            if (file.exists()) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    reader.readLine();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] values = line.split(",");
                        Spawner s = new Spawner(
                            Integer.parseInt(values[0]),
                            Integer.parseInt(values[1]),
                            Integer.parseInt(values[2])
                        );
                        spawners.add(s);
                        BlockPos p = new BlockPos(s.x, s.y, s.z);
                        spawnerPositions.add(p);
                        foundSpawnerPositions.add(p);
                    }
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }
    private void saveCsv() {
        try {
            File file = getCsvFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            writer.write("X,Y,Z\n");
            for (Spawner s : spawners) {
                s.write(writer);
            }
            writer.close();
        } catch (IOException ignored) {}
    }
    private void saveJson() {
        try {
            File file = getJsonFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            GSON.toJson(spawners, writer);
            writer.close();
        } catch (IOException ignored) {}
    }
    private File getJsonFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "spawners"), Utils.getFileWorldName()), "spawners.json");
    }
    private File getCsvFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "spawners"), Utils.getFileWorldName()), "spawners.csv");
    }
    @Override
    public String getInfoString() {
        return String.valueOf(spawners.size());
    }
    private void appendWaypoint(Spawner spawner) {
        String filePath;
        Identifier dimId = mc.world.getRegistryKey().getValue();
        String dimStr = dimId.toString();
        if (dimStr.equals("minecraft:overworld")) {
            if(!createOverworldWaypoints.get()) return;
            filePath = xaerosOverworldWaypointFilePath.get();
        } else if (dimStr.equals("minecraft:the_nether")) {
            if(!createNetherWaypoints.get()) return;
            filePath = xaerosNetherWaypointFilePath.get();
        } else if (dimStr.equals("minecraft:the_end")) {
            if(!createEndWaypoints.get()) return;
            filePath = xaerosEndWaypointFilePath.get();
        } else {
            if(!createOverworldWaypoints.get()) return;
            filePath = xaerosOverworldWaypointFilePath.get();
        }
        int x = spawner.x;
        int y = spawner.y;
        int z = spawner.z;
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
    private static class Spawner {
        private static final StringBuilder sb = new StringBuilder();
        public int x, y, z;
        public Spawner(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public void write(Writer writer) throws IOException {
            sb.setLength(0);
            sb.append(x).append(',').append(y).append(',').append(z).append('\n');
            writer.write(sb.toString());
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Spawner spawner = (Spawner) o;
            return x == spawner.x && y == spawner.y && z == spawner.z;
        }
        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
