package niix.dan.voxelparticles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class VoxelParticles extends JavaPlugin {

    private List<Location> modelVertices = new ArrayList<>();
    private List<Face> modelFaces = new ArrayList<>();

    private double scaleFactor = 1;
    private int particleDensity = 1;

    @Override
    public void onEnable() {
        getLogger().info("VoxelParticles has been enabled.");
        loadModel("plugins/VoxelParticles/model.obj");

        getCommand("showmodel").setExecutor(this);
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
        int durationTicks = 20 * 60;

        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Face face : modelFaces) {
                int[] vertexIndices = face.getVertexIndices();
                Location[] vertices = new Location[vertexIndices.length];
                for (int i = 0; i < vertexIndices.length; i++) {
                    vertices[i] = modelVertices.get(vertexIndices[i]).clone();
                    vertices[i].setWorld(origin.getWorld());
                    vertices[i].add(origin);
                }
                Location center = getFaceCenter(vertices);
                for (int i = 0; i < vertices.length; i++) {
                    Location start = vertices[i];
                    Location end = vertices[(i + 1) % vertices.length];
                    drawLineParticles(origin, start, end, center);
                }
            }
        }, 0L, 5L);

        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.getScheduler().cancelTask(task), durationTicks);
    }

    private Location getFaceCenter(Location[] vertices) {
        double centerX = 0;
        double centerY = 0;
        double centerZ = 0;
        for (Location vertex : vertices) {
            centerX += vertex.getX();
            centerY += vertex.getY();
            centerZ += vertex.getZ();
        }
        int numVertices = vertices.length;
        centerX /= numVertices;
        centerY /= numVertices;
        centerZ /= numVertices;
        return new Location(vertices[0].getWorld(), centerX, centerY, centerZ);
    }

    private void drawLineParticles(Location origin, Location start, Location end, Location center) {
        double distance = start.distance(end);
        int particleCount = (int) Math.ceil(distance * particleDensity);

        double deltaX = (end.getX() - start.getX()) / particleCount;
        double deltaY = (end.getY() - start.getY()) / particleCount;
        double deltaZ = (end.getZ() - start.getZ()) / particleCount;

        List<Location> particleLocations = new ArrayList<>();
        for (int i = 0; i < particleCount; i++) {
            Location particleLocation = start.clone().add(deltaX * i, deltaY * i, deltaZ * i);
            particleLocations.add(particleLocation);
        }

        List<Location> compressedParticles = compressParticles(particleLocations);
        
        for (Location particleLocation : compressedParticles) {
            Bukkit.getScheduler().runTask(this, () -> {
                origin.getWorld().spawnParticle(Particle.FLAME, particleLocation, 0, 0, 0, 0, 0);
            });
        }
    }

    private List<Location> compressParticles(List<Location> particleLocations) {
        double compressionDistance = 0.2; // Distância mínima para agrupar partículas
        List<Location> compressedParticles = new ArrayList<>();
        Location lastLocation = null;

        for (Location loc : particleLocations) {
            if (lastLocation == null || loc.distance(lastLocation) > compressionDistance) {
                compressedParticles.add(loc);
                lastLocation = loc;
            }
        }
        return compressedParticles;
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
