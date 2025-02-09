/**
 *  PacketSniffer
 *  =======
 * - Written by Nataani
 * Logs incoming packets to a CSV file (append mode).
 * Creates a date-based folder inside "csvPath" on activation.
 * Splits logs into multiple files of 1 million rows each.
 */

package meteorite.meteor.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteorite.meteor.addon.Meteorite;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;


public class PacketSniffer extends Module {
    // --------------------------------------------------
    // Settings / Configuration
    // --------------------------------------------------

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> csvPath = sgGeneral.add(new StringSetting.Builder()
        .name("csv-path")
        .description("Folder where packet logs will be created.")
        .defaultValue("PacketLogs")
        .build()
    );

    private final Setting<String> classesToLog = sgGeneral.add(new StringSetting.Builder()
        .name("classes-to-log")
        .description("Comma-separated list of packet classes (obfuscated or deobf) to log. If empty, logs all/filtered by positions.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> logPositionsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("only-log-positions")
        .description("If checked and classesToLog is empty, only logs position-based packets.")
        .defaultValue(true)
        .visible(() -> classesToLog.get().isEmpty())
        .build()
    );

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private PrintWriter writer;
    private final Set<String> classNameSet = new HashSet<>();
    private int rowCount;
    private int fileSuffix;
    private File sessionFolder;
    private File currentFile;

    // --------------------------------------------------
    // Constructor
    // --------------------------------------------------

    public PacketSniffer() {
        super(
            Meteorite.Main,
            "packet-sniffer",
            "Logs all incoming packets to CSV files, each up to 1 million rows, inside a date-based subfolder."
        );
    }

    // --------------------------------------------------
    // Lifecycle
    // --------------------------------------------------

    @Override
    public void onActivate() {
        rowCount = 0;
        fileSuffix = 1;

        updateClassNameSet();

        openFile();
        if (currentFile != null) {
            ChatUtils.info("PacketSniffer started, logging to: " + currentFile.getAbsolutePath());
        }
    }

    @Override
    public void onDeactivate() {
        closeCurrentWriter();
        ChatUtils.info("PacketSniffer stopped.");
    }

    // --------------------------------------------------
    // Packet Handling
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (writer == null) return;

        if (rowCount >= 1_000_000) {
            rotateFile();
        }

        Packet<?> packet = event.packet;
        String className = packet.getClass().getSimpleName();
        String deobfName = PacketUtils.getName((Class<? extends Packet<?>>) packet.getClass());
        if (deobfName == null) deobfName = className;
        if (!shouldLog(className, deobfName)) return;

        String contents = packet.toString().replace(",", ";").replace("\n", " ");
        String coords = parseCoordinates(packet);

        String timestamp = dateFormat.format(new Date());
        // CSV line: "Timestamp,ClassName,PacketName,Contents,Coordinates"
        String line = String.format("%s,%s,%s,\"%s\",\"%s\"",
            timestamp,
            className,
            deobfName,
            contents,
            coords
        );

        writer.println(line);
        rowCount++;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (writer == null) return;

        if (rowCount >= 1_000_000) {
            rotateFile();
        }

        Packet<?> packet = event.packet;
        String className = packet.getClass().getSimpleName();

        String deobfName = PacketUtils.getName((Class<? extends Packet<?>>) packet.getClass());
        if (deobfName == null) deobfName = className;

        if (!shouldLog(className, deobfName)) return;

        String contents = packet.toString().replace(",", ";").replace("\n", " ");
        String coords = parseCoordinates(packet);

        String timestamp = dateFormat.format(new Date());
        // CSV line: "Timestamp,ClassName,PacketName,Contents,Coordinates"
        String line = String.format("%s,%s,%s,\"%s\",\"%s\"",
            timestamp,
            className,
            deobfName,
            contents,
            coords
        );

        writer.println(line);
        rowCount++;
    }

    // --------------------------------------------------
    // Helper / Utility
    // --------------------------------------------------

    private void updateClassNameSet() {
        classNameSet.clear();
        String raw = classesToLog.get().trim();
        if (!raw.isEmpty()) {
            for (String token : raw.split(",")) {
                classNameSet.add(token.trim());
            }
        }
    }

    /**
     * Opens the date-based folder + creates the first CSV writer.
     */
    private void openFile() {
        try {
            File baseFolder = new File(csvPath.get());
            if (!baseFolder.exists()) {
                baseFolder.mkdirs();
            }

            if (sessionFolder == null) {
                String dateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                sessionFolder = new File(baseFolder, dateString);
                sessionFolder.mkdirs();
            }

            currentFile = new File(sessionFolder, "PacketLog_" + fileSuffix + ".csv");
            boolean fileExists = currentFile.exists();

            writer = new PrintWriter(new BufferedWriter(new FileWriter(currentFile, true)));

            if (!fileExists) {
                writer.println("Timestamp,PacketClass,PacketName,Contents,Coordinates");
            }

        } catch (IOException e) {
            ChatUtils.error("Failed to open CSV file: " + e.getMessage());
            toggle();
        }
    }

    private void closeCurrentWriter() {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }

    private void rotateFile() {
        closeCurrentWriter();
        fileSuffix++;
        rowCount = 0;
        openFile();
    }

    private boolean shouldLog(String obfName, String deobfName) {
        if (classNameSet.isEmpty()) {
            if (logPositionsOnly.get()) {
                // We only log if not in NO_POS_PACKETS
                return !NO_POS_PACKETS.contains(deobfName);
            }
            return true; // log everything if empty & not restricting positions
        } else {
            return classNameSet.contains(obfName) || classNameSet.contains(deobfName);
        }
    }

    public static final Set<String> NO_POS_PACKETS = Set.of(
        "AdvancementUpdateS2CPacket",
        "ChunkSentS2CPacket",
        "CommandTreeS2CPacket",
        "CommonPingS2CPacket",
        "DamageTiltS2CPacket",
        "DeathMessageS2CPacket",
        "DifficultyS2CPacket",
        "EndCombatS2CPacket",
        "EnterCombatS2CPacket",
        "EnterReconfigurationS2CPacket",
        "EntitiesDestroyS2CPacket",
        "EntityAnimationS2CPacket",
        "EntityAttachS2CPacket",
        "EntityAttributesS2CPacket",
        "EntityEquipmentUpdateS2CPacket",
        "EntityPassengersSetS2CPacket",
        "EntitySetHeadYawS2CPacket",
        "EntityStatusS2CPacket",
        "EntityTrackerUpdateS2CPacket",
        "ExperienceBarUpdateS2CPacket",
        "GameMessageS2CPacket",
        "GameStateChangeS2CPacket",
        "HealthUpdateS2CPacket",
        "InventoryS2CPacket",
        "ItemPickupAnimationS2CPacket",
        "KeepAliveS2CPacket",
        "MapUpdateS2CPacket",
        "PlayerAbilitiesS2CPacket",
        "PlayerActionResponseS2CPacket",
        "PlayerListHeaderS2CPacket",
        "PlayerListS2CPacket",
        "PlayerRemoveS2CPacket",
        "PlayerRespawnS2CPacket",
        "OpenScreenS2CPacket",
        "ScreenHandlerSlotUpdateS2CPacket",
        "SimulationDistanceS2CPacket",
        "StartChunkSendS2CPacket",
        "SubtitleS2CPacket",
        "TickStepS2CPacket",
        "UpdateSelectedSlotS2CPacket",
        "UpdateTickRateS2CPacket",
        "WorldTimeUpdateS2CPacket"
    );

    private String parseCoordinates(Packet<?> packet) {

        String className = packet.getClass().getSimpleName();

        if (NO_POS_PACKETS.contains(className)) {
            return "No Position Data Available";
        }

        else switch (packet) {
            case BlockUpdateS2CPacket packet2 -> {
                BlockPos blockPos = packet2.getPos();

                int x = blockPos.getX();
                int y = blockPos.getY();
                int z = blockPos.getZ();
                return String.format("Block Update: %d,%d,%d", x, y, z);
            }
            case ChunkDataS2CPacket packet2 -> {
                int chunkX = packet2.getChunkX() * 16;
                int chunkZ = packet2.getChunkZ() * 16;

                return String.format("Chunk Data: (%d,0,%d)", chunkX, chunkZ);
            }
            case ChunkDeltaUpdateS2CPacket packet2 -> {
                final int[] chunkCoords = new int[3];

                packet2.visitUpdates((blockPos, blockState) -> {
                    int chunkX = blockPos.getX();
                    int chunkY = blockPos.getY();
                    int chunkZ = blockPos.getZ();

                    chunkCoords[0] = chunkX;
                    chunkCoords[1] = chunkY;
                    chunkCoords[2] = chunkZ;
                });

                if (chunkCoords[0] != 0 || chunkCoords[1] != 0 || chunkCoords[2] != 0) {
                    return String.format("Chunk Update: (%d,%d,%d)", chunkCoords[0], chunkCoords[1], chunkCoords[2]);
                }
            }
            case ChunkLoadDistanceS2CPacket packet2 -> {
                int distance = packet2.getDistance();

                return String.format("Chunk Distance: %d", distance);
            }
            case ChunkRenderDistanceCenterS2CPacket packet2 -> {
                int chunkX = packet2.getChunkX();
                int chunkZ = packet2.getChunkZ();

                return String.format("Chunk Render: (%d,0,%d)", chunkX, chunkZ);
            }
            case EntityDamageS2CPacket packet2 -> {
                return packet2.sourcePosition().toString();
            }
            case EntityS2CPacket.MoveRelative packet2 -> {
                int x = (int) Math.round(packet2.getDeltaX());
                int y = (int) Math.round(packet2.getDeltaY());
                int z = (int) Math.round(packet2.getDeltaZ());

                return String.format("Move: %d,%d,%d", x, y, z);
            }
            case EntityS2CPacket.RotateAndMoveRelative packet2 -> {
                int x = (int) Math.round(packet2.getDeltaX());
                int y = (int) Math.round(packet2.getDeltaY());
                int z = (int) Math.round(packet2.getDeltaZ());

                return String.format("Move: %d,%d,%d", x, y, z);
            }
            case EntityS2CPacket.Rotate packet2 -> {
                int x = (int) Math.round(packet2.getDeltaX());
                int y = (int) Math.round(packet2.getDeltaY());
                int z = (int) Math.round(packet2.getDeltaZ());

                return String.format("Move: %d,%d,%d", x, y, z);
            }
            case EntityPositionS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getX());
                int y = (int) Math.round(packet2.getY());
                int z = (int) Math.round(packet2.getZ());

                return String.format("Entity Pos: %d,%d,%d", x, y, z);
            }
            case EntitySpawnS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getX());
                int y = (int) Math.round(packet2.getY());
                int z = (int) Math.round(packet2.getZ());

                return String.format("Entity Spawn: %d,%d,%d", x, y, z);
            }
            case EntityVelocityUpdateS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getVelocityX());
                int y = (int) Math.round(packet2.getVelocityY());
                int z = (int) Math.round(packet2.getVelocityZ());

                return String.format("Velocity: %d,%d,%d", x, y, z);
            }
            case ExperienceOrbSpawnS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getX());
                int y = (int) Math.round(packet2.getY());
                int z = (int) Math.round(packet2.getZ());

                return String.format("Orb Spawn: %d,%d,%d", x, y, z);
            }
            case LightUpdateS2CPacket packet2 -> {
                int chunkX = packet2.getChunkX();
                int chunkZ = packet2.getChunkZ();

                return String.format("Chunk Render: (%d,0,%d)", chunkX, chunkZ);
            }
            case ParticleS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getX());
                int y = (int) Math.round(packet2.getY());
                int z = (int) Math.round(packet2.getZ());

                return String.format("Particle Spawn: %d,%d,%d", x, y, z);
            }

            case PlayerPositionLookS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getX());
                int y = (int) Math.round(packet2.getY());
                int z = (int) Math.round(packet2.getZ());

                return String.format("Player Look: %d,%d,%d", x, y, z);
            }
            case PlayerSpawnPositionS2CPacket packet2 -> {
                BlockPos blockPos = packet2.getPos();

                int x = blockPos.getX();
                int y = blockPos.getY();
                int z = blockPos.getZ();
                return String.format("Player Spawn: %d,%d,%d", x, y, z);
            }
            case PlaySoundS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getX());
                int y = (int) Math.round(packet2.getY());
                int z = (int) Math.round(packet2.getZ());

                return String.format("Play Sound: %d,%d,%d", x, y, z);
            }
            case UnloadChunkS2CPacket packet2 -> {
                ChunkPos chunkPos = packet2.pos();

                int chunkX = chunkPos.getCenterX();
                int chunkZ = chunkPos.getCenterZ();

                return String.format("Chunk Unload: (%d,0,%d)", chunkX, chunkZ);
            }
            case VehicleMoveS2CPacket packet2 -> {
                int x = (int) Math.round(packet2.getX());
                int y = (int) Math.round(packet2.getY());
                int z = (int) Math.round(packet2.getZ());

                return String.format("Play Sound: %d,%d,%d", x, y, z);
            }
            case WorldBorderInitializeS2CPacket packet2 -> {

                int x = (int) Math.round(packet2.getCenterX());
                int z = (int) Math.round(packet2.getCenterZ());

                return String.format("World Border Center: (%d,0,%d)", x, z);
            }
            case WorldEventS2CPacket packet2 -> {
                BlockPos blockPos = packet2.getPos();

                int x = blockPos.getX();
                int y = blockPos.getY();
                int z = blockPos.getZ();

                return String.format("World Event: %d,%d,%d", x, y, z);
            }
            default -> {
            }
        }

        return "";
    }


    @Override
    public String getInfoString() {
        if (currentFile != null) {
            return "Logging to: " + currentFile.getAbsolutePath();
        }
        return "Logging not started";
    }
}
