package net.celestiald.cavesnotcliffs.world;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Samples the vanilla GenLayer biome chain of the base world type — including every biome
 * other mods injected through {@code BiomeManager.addBiome} (e.g. Thaumcraft's Magical
 * Forest) or a custom {@code WorldType.getBiomeLayer} — and lets those modded biomes
 * override the 1.18 climate-map projection wherever the vanilla chain places them.
 * Without this overlay a native-profile world would never contain a single modded biome,
 * which breaks biome-driven mod content such as Silverwood trees or biome grass tints.
 *
 * <p>The overlay never crashes the game over another mod's behavior: a failing sampler
 * is reported once and the overlay silently turns itself off.</p>
 */
final class ModdedBiomeOverlay {
    private static final Logger LOGGER = LogManager.getLogger("CavesNotCliffs/ModdedBiomes");
    private static final ModdedBiomeOverlay DISABLED = new ModdedBiomeOverlay(null);

    /** Rectangular biome grid source in the two coordinate scales biome queries use. */
    interface Sampler {
        /** Generation-scale grid: coordinates arrive in quart (1:4) units. */
        Biome[] generationBiomes(int quartX, int quartZ, int width, int height);

        /** Block-scale grid with the vanilla Voronoi zoom applied. */
        Biome[] blockBiomes(int blockX, int blockZ, int width, int length);
    }

    private Sampler sampler;
    private boolean warned;

    private ModdedBiomeOverlay(Sampler sampler) {
        this.sampler = sampler;
    }

    static ModdedBiomeOverlay disabled() {
        return DISABLED;
    }

    static ModdedBiomeOverlay of(Sampler sampler) {
        if (sampler == null) {
            return DISABLED;
        }
        return new ModdedBiomeOverlay(sampler);
    }

    static ModdedBiomeOverlay fromVanillaProvider(BiomeProvider provider) {
        if (provider == null) {
            return DISABLED;
        }
        return of(new Sampler() {
            @Override
            public Biome[] generationBiomes(int quartX, int quartZ, int width, int height) {
                return provider.getBiomesForGeneration(null, quartX, quartZ, width, height);
            }

            @Override
            public Biome[] blockBiomes(int blockX, int blockZ, int width, int length) {
                return provider.getBiomes(null, blockX, blockZ, width, length, true);
            }
        });
    }

    boolean isEnabled() {
        return sampler != null;
    }

    /** Generation-scale grid overlay; {@code base} cells keep the 1.18 projection by default. */
    Biome[] overlayForGeneration(Biome[] base, int quartX, int quartZ,
            int width, int height) {
        Biome[] vanilla = sampleGeneration(quartX, quartZ, width, height);
        if (vanilla == null) {
            return base;
        }
        for (int index = 0; index < base.length && index < vanilla.length; ++index) {
            base[index] = overlay(base[index], vanilla[index]);
        }
        return base;
    }

    /** Block-scale grid overlay; {@code base} cells keep the 1.18 projection by default. */
    Biome[] overlayBlock(Biome[] base, int blockX, int blockZ, int width, int length) {
        Biome[] modded = moddedBlockBiomes(blockX, blockZ, width, length);
        if (modded == null) {
            return base;
        }
        for (int index = 0; index < base.length && index < modded.length; ++index) {
            if (modded[index] != null) {
                base[index] = modded[index];
            }
        }
        return base;
    }

    /**
     * Block-scale grid holding the modded biome for each cell the vanilla chain claims,
     * or null per cell where the 1.18 projection stays. Null array when disabled/failing.
     */
    Biome[] moddedBlockBiomes(int blockX, int blockZ, int width, int length) {
        Biome[] vanilla = sampleBlock(blockX, blockZ, width, length);
        if (vanilla == null) {
            return null;
        }
        for (int index = 0; index < vanilla.length; ++index) {
            if (!isModded(vanilla[index])) {
                vanilla[index] = null;
            }
        }
        return vanilla;
    }

    /**
     * Returns a modded biome only when the complete sampled population region belongs to that
     * same biome. A vanilla biome decorator receives a population region, not a single block;
     * selecting a majority biome for a mixed region lets its decoration cross into neighbors.
     */
    Biome uniformModdedBiome(int blockX, int blockZ, int width, int length) {
        if (width <= 0 || length <= 0) {
            return null;
        }
        long expectedLong = (long) width * (long) length;
        if (expectedLong > Integer.MAX_VALUE) {
            return null;
        }
        int expected = (int) expectedLong;
        Biome[] sampled = moddedBlockBiomes(blockX, blockZ, width, length);
        if (sampled == null || sampled.length < expected || expected == 0) {
            return null;
        }
        Biome candidate = sampled[0];
        if (candidate == null) {
            return null;
        }
        for (int index = 1; index < expected; ++index) {
            if (sampled[index] != candidate) {
                return null;
            }
        }
        return candidate;
    }

    static boolean isModded(Biome biome) {
        return biome != null
                && biome.getRegistryName() != null
                && !"minecraft".equals(biome.getRegistryName().getResourceDomain());
    }

    private static Biome overlay(Biome projected, Biome vanilla) {
        return isModded(vanilla) ? vanilla : projected;
    }

    private Biome[] sampleGeneration(int quartX, int quartZ, int width, int height) {
        if (sampler == null) {
            return null;
        }
        try {
            return sampler.generationBiomes(quartX, quartZ, width, height);
        } catch (RuntimeException failure) {
            return disable(failure);
        }
    }

    private Biome[] sampleBlock(int blockX, int blockZ, int width, int length) {
        if (sampler == null) {
            return null;
        }
        try {
            return sampler.blockBiomes(blockX, blockZ, width, length);
        } catch (RuntimeException failure) {
            return disable(failure);
        }
    }

    private Biome[] disable(RuntimeException failure) {
        if (!warned) {
            LOGGER.warn("Modded-biome overlay sampler failed; modded biomes stay disabled"
                    + " for this world", failure);
            warned = true;
        }
        sampler = null;
        return null;
    }
}
