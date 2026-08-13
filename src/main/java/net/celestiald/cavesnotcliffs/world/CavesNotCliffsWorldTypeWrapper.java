package net.celestiald.cavesnotcliffs.world;

import net.celestiald.cavebiomes.api.ExtendedChunkAPI;
import net.celestiald.cavebiomes.api.IWrappedWorldType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.ChunkGeneratorSettings;
import net.minecraft.world.gen.layer.GenLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.function.Supplier;

/** Hidden finite-world type which preserves a selected two-dimensional generator as its base. */
public final class CavesNotCliffsWorldTypeWrapper extends WorldType
        implements CavesNotCliffsFiniteWorldType, IWrappedWorldType {
    private static final Logger LOGGER = LogManager.getLogger("CavesNotCliffs/WorldType");
    private final WorldType baseType;
    private final TerrainProfile terrainProfile;

    CavesNotCliffsWorldTypeWrapper(String name, WorldType baseType, TerrainProfile terrainProfile) {
        super(name);
        this.baseType = baseType;
        this.terrainProfile = terrainProfile;
    }

    public WorldType getBaseType() {
        return baseType;
    }

    @Override
    public WorldType getBaseWorldType() {
        return baseType;
    }

    @Override
    public int getTerrainSchema() {
        return CavesNotCliffsWorldData.CURRENT_SCHEMA;
    }

    @Override
    public TerrainProfile getTerrainProfile() {
        return terrainProfile;
    }

    @Override
    public IChunkGenerator getChunkGenerator(World world, String generatorOptions) {
        CavesNotCliffsWorldData data = CavesNotCliffsWorldData.read(world.getWorldInfo());
        if (data == null) {
            throw new IllegalStateException("Schema-2 Caves Not Cliffs world has no persisted generator data");
        }
        data.validateGeneratorContract(getTerrainSchema(), baseType, terrainProfile);
        String options = data.getGeneratorOptions();
        IChunkGenerator baseGenerator = delegate(world,
                () -> baseType.getChunkGenerator(world, options == null ? "" : options));
        if (world.provider.getDimension() != 0) {
            return baseGenerator;
        }
        ExtendedChunkAPI.requireRange("Caves Not Cliffs",
                CavesNotCliffsWorldType.MIN_HEIGHT, CavesNotCliffsWorldType.MAX_HEIGHT);
        TerrainProfile persistedProfile = data.getTerrainProfile();
        if (V118ChunkGenerator.isNativeProfile(persistedProfile)) {
            return new V118ChunkGenerator(world, persistedProfile, baseGenerator,
                moddedBiomeOverlay(world));
        }
        return DelegatingFiniteChunkGenerator.wrap(baseGenerator);
    }

    @Override
    public boolean canBeCreated() {
        return false;
    }

    @Override
    public boolean isCustomizable() {
        return false;
    }

    @Override
    public BiomeProvider getBiomeProvider(World world) {
        // Native-profile worlds lay biomes down with the 1.18 multi-noise climate map, so
        // anything biome-driven outside chunk generation — structure viability checks above
        // all — must consult that same map instead of the untouched vanilla GenLayer chain.
        if (world.provider.getDimension() == 0) {
            CavesNotCliffsWorldData data = CavesNotCliffsWorldData.read(world.getWorldInfo());
            if (data != null && V118ChunkGenerator.isNativeProfile(data.getTerrainProfile())) {
                return new V118BiomeProvider(world.getSeed(),
                    V118ChunkGenerator.nativeProfileFor(data.getTerrainProfile()),
                    V118BiomeMapper.fromRegisteredBiomes(), moddedBiomeOverlay(world));
            }
        }
        return delegate(world, () -> baseType.getBiomeProvider(world));
    }

    /**
     * Builds the vanilla biome chain of the base world type (with every biome other mods
     * injected through BiomeManager) so modded biomes can overlay the 1.18 projection.
     * Any failure leaves the overlay disabled instead of breaking world creation.
     */
    private ModdedBiomeOverlay moddedBiomeOverlay(World world) {
        try {
            return ModdedBiomeOverlay.fromVanillaProvider(
                delegate(world, () -> baseType.getBiomeProvider(world)));
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not sample the base world type's biome chain; modded biomes"
                    + " stay disabled for this world", failure);
            return ModdedBiomeOverlay.disabled();
        }
    }

    @Override
    public int getMinimumSpawnHeight(World world) {
        return delegate(world, () -> baseType.getMinimumSpawnHeight(world));
    }

    @Override
    public double getHorizon(World world) {
        return delegate(world, () -> baseType.getHorizon(world));
    }

    @Override
    public double voidFadeMagnitude() {
        return baseType.voidFadeMagnitude();
    }

    @Override
    public boolean handleSlimeSpawnReduction(Random random, World world) {
        return delegate(world, () -> baseType.handleSlimeSpawnReduction(random, world));
    }

    @Override
    public int getSpawnFuzz(WorldServer world, MinecraftServer server) {
        return delegate(world, () -> baseType.getSpawnFuzz(world, server));
    }

    @Override
    public float getCloudHeight() {
        return baseType.getCloudHeight();
    }

    @Override
    public GenLayer getBiomeLayer(long seed, GenLayer parent, ChunkGeneratorSettings settings) {
        return baseType.getBiomeLayer(seed, parent, settings);
    }

    private <T> T delegate(World world, Supplier<T> operation) {
        synchronized (world.getWorldInfo()) {
            WorldType selected = world.getWorldInfo().getTerrainType();
            world.getWorldInfo().setTerrainType(baseType);
            try {
                return operation.get();
            } finally {
                world.getWorldInfo().setTerrainType(selected);
            }
        }
    }
}
