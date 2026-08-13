package net.celestiald.cavesnotcliffs.world;

import net.celestiald.cavesnotcliffs.content.MountainBiomeContent;
import net.celestiald.cavesnotcliffs.worldgen.v118.V118NoiseRouterData;
import net.minecraft.init.Biomes;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class V118BiomeProviderTest {
    private static final long SEED = 20240801L;
    // Resolved lazily after Bootstrap: touching Biomes.* during class init dies with
    // "Accessed Biomes before Bootstrap" whenever this class loads first in the test JVM.
    private static List<Biome> villageBiomes;
    private static List<Biome> oceanBiomes;

    @BeforeClass
    public static void bootstrapVanillaRegistries() {
        Bootstrap.register();
        villageBiomes = Arrays.asList(Biomes.PLAINS, Biomes.DESERT, Biomes.SAVANNA,
            Biomes.TAIGA);
        oceanBiomes = Arrays.asList(Biomes.OCEAN, Biomes.DEEP_OCEAN);
    }

    @Test
    public void villageViabilityFollowsThe118ClimateMapInsteadOfVanillaGenLayer() {
        V118BiomeProvider provider = provider(SEED);
        BlockPos oceanAnchor = null;
        BlockPos plainsAnchor = null;
        for (int radius = 0; radius <= 4096 && (oceanAnchor == null || plainsAnchor == null);
                radius += 64) {
            for (int z = -radius; z <= radius; z += 64) {
                for (int x = -radius; x <= radius; x += 64) {
                    BlockPos pos = new BlockPos(x, 64, z);
                    Biome biome = provider.getBiome(pos);
                    if (oceanAnchor == null && oceanBiomes.contains(biome)
                            && provider.areBiomesViable(x, z, 0, oceanBiomes)) {
                        oceanAnchor = pos;
                    }
                    if (plainsAnchor == null && biome == Biomes.PLAINS
                            && provider.areBiomesViable(x, z, 0, villageBiomes)) {
                        plainsAnchor = pos;
                    }
                }
            }
        }
        assertNotNull("fixture seed must expose a 1.18 ocean near the origin", oceanAnchor);
        assertNotNull("fixture seed must expose 1.18 plains near the origin", plainsAnchor);
        // The bug report: villages passed their biome check over the 1.18 ocean because the
        // check sampled the unrelated vanilla GenLayer layout. Both checks must agree now.
        assertFalse(provider.areBiomesViable(oceanAnchor.getX(), oceanAnchor.getZ(), 0,
            villageBiomes));
        assertFalse(provider.areBiomesViable(oceanAnchor.getX(), oceanAnchor.getZ(), 32,
            villageBiomes));
        assertTrue(provider.areBiomesViable(plainsAnchor.getX(), plainsAnchor.getZ(), 0,
            villageBiomes));
    }

    @Test
    public void resolvesIdenticallyAcrossInstancesAndProfiles() {
        for (V118NoiseRouterData.Profile profile : V118NoiseRouterData.Profile.values()) {
            V118BiomeProvider first = provider(SEED + profile.ordinal(), profile);
            V118BiomeProvider second = provider(SEED + profile.ordinal(), profile);
            for (int index = 0; index < 64; ++index) {
                int x = -1024 + index * 31;
                int z = 512 - index * 17;
                assertEquals(first.getBiome(new BlockPos(x, 64, z)),
                    second.getBiome(new BlockPos(x, 64, z)));
                Biome[] generationGrid = first.getBiomesForGeneration(null, x >> 2, z >> 2, 4, 4);
                assertEquals(generationGrid,
                    second.getBiomesForGeneration(null, x >> 2, z >> 2, 4, 4));
            }
        }
    }

    @Test
    public void findBiomePositionLocatesAVillageBiomeInThe118Layout() {
        V118BiomeProvider provider = provider(SEED);
        BlockPos found = provider.findBiomePosition(0, 0, 1024,
            Collections.singletonList(Biomes.PLAINS), new Random(7L));
        assertNotNull("fixture seed must have plains within 1024 blocks of the origin", found);
        assertEquals(Biomes.PLAINS, provider.getBiome(new BlockPos(found.getX(), 64,
            found.getZ())));
    }

    @Test
    public void moddedBiomeOverlayWinsOverTheClimateProjection() {
        Biome magicalForest = new Biome(new Biome.BiomeProperties("Magical Forest")) {
        };
        magicalForest.setRegistryName("thaumcraft", "magical_forest");
        ModdedBiomeOverlay overlay = ModdedBiomeOverlay.of(new ModdedBiomeOverlay.Sampler() {
            @Override
            public Biome[] generationBiomes(int quartX, int quartZ, int width, int height) {
                Biome[] grid = new Biome[width * height];
                Arrays.fill(grid, magicalForest);
                grid[0] = Biomes.FOREST;
                return grid;
            }

            @Override
            public Biome[] blockBiomes(int blockX, int blockZ, int width, int length) {
                Biome[] grid = new Biome[width * length];
                Arrays.fill(grid, magicalForest);
                grid[0] = Biomes.FOREST;
                return grid;
            }
        });
        V118BiomeProvider provider = new V118BiomeProvider(SEED,
            V118NoiseRouterData.Profile.DEFAULT, mapper(), overlay);

        // Vanilla chain cells are never claimed: the first cell keeps the 1.18 projection.
        V118BiomeProvider plain = provider(SEED);
        Biome[] generation = provider.getBiomesForGeneration(null, 0, 0, 4, 4);
        assertEquals(plain.getBiomesForGeneration(null, 0, 0, 4, 4)[0], generation[0]);
        for (int index = 1; index < generation.length; ++index) {
            assertEquals(magicalForest, generation[index]);
        }

        Biome[] blocks = provider.getBiomes(null, 16, 16, 8, 8, true);
        assertEquals(plain.getBiomes(null, 16, 16, 8, 8, true)[0], blocks[0]);
        for (int index = 1; index < blocks.length; ++index) {
            assertEquals(magicalForest, blocks[index]);
        }
    }

    @Test
    public void vanillaOverlaySamplerLeavesTheClimateProjectionAlone() {
        ModdedBiomeOverlay overlay = ModdedBiomeOverlay.of(new ModdedBiomeOverlay.Sampler() {
            @Override
            public Biome[] generationBiomes(int quartX, int quartZ, int width, int height) {
                Biome[] grid = new Biome[width * height];
                Arrays.fill(grid, Biomes.FOREST);
                return grid;
            }

            @Override
            public Biome[] blockBiomes(int blockX, int blockZ, int width, int length) {
                Biome[] grid = new Biome[width * length];
                Arrays.fill(grid, Biomes.FOREST);
                return grid;
            }
        });
        V118BiomeProvider overlaid = new V118BiomeProvider(SEED,
            V118NoiseRouterData.Profile.DEFAULT, mapper(), overlay);
        V118BiomeProvider plain = provider(SEED);
        for (int index = 0; index < 16; ++index) {
            int x = -512 + index * 67;
            int z = 256 - index * 41;
            assertEquals(plain.getBiome(new BlockPos(x, 64, z)),
                overlaid.getBiome(new BlockPos(x, 64, z)));
        }
    }

    @Test
    public void findBiomePositionLocatesModdedOverlayBiomes() {
        final Biome magicalForest = new Biome(new Biome.BiomeProperties("Magical Forest")) {
        };
        magicalForest.setRegistryName("thaumcraft", "magical_forest");
        final int targetQuartX = 20;
        final int targetQuartZ = -12;
        final int targetBlockX = targetQuartX << 2;
        final int targetBlockZ = targetQuartZ << 2;
        ModdedBiomeOverlay overlay = ModdedBiomeOverlay.of(new ModdedBiomeOverlay.Sampler() {
            @Override
            public Biome[] generationBiomes(int quartX, int quartZ, int width, int height) {
                Biome[] grid = new Biome[width * height];
                Arrays.fill(grid, Biomes.FOREST);
                return grid;
            }

            @Override
            public Biome[] blockBiomes(int blockX, int blockZ, int width, int length) {
                Biome[] grid = new Biome[width * length];
                Arrays.fill(grid, Biomes.FOREST);
                if (blockZ <= targetBlockZ && targetBlockZ < blockZ + length
                        && blockX <= targetBlockX && targetBlockX < blockX + width) {
                    grid[(targetBlockZ - blockZ) * width + (targetBlockX - blockX)] =
                        magicalForest;
                }
                return grid;
            }
        });
        V118BiomeProvider provider = new V118BiomeProvider(SEED,
            V118NoiseRouterData.Profile.DEFAULT, mapper(), overlay);

        BlockPos found = provider.findBiomePosition(0, 0, 256,
            Collections.singletonList(magicalForest), new Random(0L));
        assertNotNull("the overlay biome must be locatable by biome search tools", found);
        assertEquals(targetQuartX << 2, found.getX());
        assertEquals(targetQuartZ << 2, found.getZ());

        // Without the overlay the modded biome does not exist in the climate projection.
        assertNull(provider(SEED).findBiomePosition(0, 0, 256,
            Collections.singletonList(magicalForest), new Random(0L)));
    }

    private static V118BiomeProvider provider(long seed) {
        return provider(seed, V118NoiseRouterData.Profile.DEFAULT);
    }

    private static V118BiomeProvider provider(long seed, V118NoiseRouterData.Profile profile) {
        return new V118BiomeProvider(seed, profile, mapper());
    }

    private static V118BiomeMapper mapper() {
        return V118BiomeMapper.fromResolver(location -> {
            Biome nativeBiome = MountainBiomeContent.biomeFor(location);
            if (nativeBiome == null) {
                nativeBiome = net.celestiald.cavesnotcliffs.content.CaveBiomeContent
                    .biomeFor(location);
            }
            return nativeBiome == null ? Biome.REGISTRY.getObject(location) : nativeBiome;
        });
    }
}
