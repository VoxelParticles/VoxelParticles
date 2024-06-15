package niix.dan.voxelparticles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VoxelParticles extends JavaPlugin {

    private List<Location> modelVertices = new ArrayList<>();
    private List<Face> modelFaces = new ArrayList<>();

    private double scaleFactor = 1;
    private int particleDensity = 1;
    private int durationTicks = 20 * 60;
    private double compressionDistance = 0.2;

    @Override
    public void onEnable() {
        getLogger().info("VoxelParticles has been enabled.");
        loadModel("plugins/VoxelParticles/model.obj");

        getCommand("showmodel").setExecutor(this);


        saveDefaultConfig();
        getConfig().options().copyDefaults(true);

        scaleFactor = getConfig().getDouble("scaleFactor", 1);
        particleDensity = getConfig().getInt("particleDensity", 1);
        durationTicks = getConfig().getInt("durationTicks", 20 * 60);
        compressionDistance = getConfig().getDouble("compressionDistance", 0.2);
    }

    @Override
    public void onDisable() {
        getLogger().info("VoxelParticles has been disabled.");
        modelVertices.clear();
        modelFaces.clear();
    }

    private void loadModel(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("v ")) {
                    String[] parts = line.replace("v", "").trim().split("\\s+");
                    double x = Float.parseFloat(parts[0]) * scaleFactor;
                    double y = Float.parseFloat(parts[1]) * scaleFactor;
                    double z = Float.parseFloat(parts[2]) * scaleFactor;
                    modelVertices.add(new Location(null, x, y, z));
                }
                if (line.startsWith("f ")) {
                    String[] parts = line.replace("f", "").trim().split("\\s+");
                    int[] vertexIndices = new int[parts.length - 1];
                    for (int i = 1; i < parts.length; i++) {
                        String[] indices = parts[i].split("/");
                        vertexIndices[i - 1] = Integer.parseInt(indices[0]) - 1;
                    }
                    modelFaces.add(new Face(vertexIndices));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Location playerLocation = player.getLocation();
            showModel(playerLocation);
            return true;
        }
        return false;
    }

    private void showModel(Location origin) {
        List<Location> allParticles = new ArrayList<>();

        for (Face face : modelFaces) {
            int[] vertexIndices = face.getVertexIndices();
            Location[] vertices = new Location[vertexIndices.length];
            for (int i = 0; i < vertexIndices.length; i++) {
                vertices[i] = modelVertices.get(vertexIndices[i]).clone();
                vertices[i].setWorld(origin.getWorld());
                vertices[i].add(origin);
            }

            for (int i = 0; i < vertices.length; i++) {
                Location start = vertices[i];
                Location end = vertices[(i + 1) % vertices.length];
                allParticles.addAll(generateLineParticles(start, end));
            }
        }

        List<Location> compressedParticles = compressParticles(allParticles);

        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Location particleLocation : compressedParticles) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    origin.getWorld().spawnParticle(Particle.FLAME, particleLocation, 0, 0, 0, 0, 0);
                });
            }
        }, 0L, 5L);

        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.getScheduler().cancelTask(task), durationTicks);
    }

    private List<Location> generateLineParticles(Location start, Location end) {
        List<Location> particleLocations = new ArrayList<>();
        double distance = start.distance(end);
        int particleCount = (int) Math.ceil(distance * particleDensity);

        double deltaX = (end.getX() - start.getX()) / particleCount;
        double deltaY = (end.getY() - start.getY()) / particleCount;
        double deltaZ = (end.getZ() - start.getZ()) / particleCount;

        for (int i = 0; i < particleCount; i++) {
            Location particleLocation = start.clone().add(deltaX * i, deltaY * i, deltaZ * i);
            particleLocations.add(particleLocation);
        }
        return particleLocations;
    }

    private List<Location> compressParticles(List<Location> particleLocations) {
        List<Location> compressedParticles = new ArrayList<>();
        Set<Location> visited = new HashSet<>();

        for (Location loc : particleLocations) {
            if (visited.contains(loc)) {
                continue;
            }

            List<Location> nearParticles = getNearParticles(loc, particleLocations, compressionDistance);
            visited.addAll(nearParticles);

            if (nearParticles.size() > 1) {
                Location averageLocation = averageLocation(nearParticles);
                compressedParticles.add(averageLocation);
            } else {
                compressedParticles.add(loc);
            }
        }
        return compressedParticles;
    }

    private List<Location> getNearParticles(Location loc, List<Location> particleLocations, double compressionDistance) {
        List<Location> nearParticles = new ArrayList<>();
        for (Location other : particleLocations) {
            if (loc.distance(other) <= compressionDistance) {
                nearParticles.add(other);
            }
        }
        return nearParticles;
    }

    private Location averageLocation(List<Location> locations) {
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        World world = locations.get(0).getWorld();

        for (Location loc : locations) {
            sumX += loc.getX();
            sumY += loc.getY();
            sumZ += loc.getZ();
        }

        int count = locations.size();
        return new Location(world, sumX / count, sumY / count, sumZ / count);
    }

    private static class Face {
        private final int[] vertexIndices;

        public Face(int[] vertexIndices) {
            this.vertexIndices = vertexIndices;
        }

        public int[] getVertexIndices() {
            return vertexIndices;
        }
    }
}
