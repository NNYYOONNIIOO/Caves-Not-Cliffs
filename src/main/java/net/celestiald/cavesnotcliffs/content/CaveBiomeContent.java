package net.celestiald.cavesnotcliffs.content;

import net.celestiald.cavesnotcliffs.CavesNotCliffs;
import net.celestiald.cavesnotcliffs.worldgen.v118.V118Biome;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Canonical public registrations for the two underground biomes added in Java 1.18.
 * They exist so the Y-aware biome resolver can report real identities (F3, biome tints,
 * mod biome queries) instead of aliasing lush caves to forest and dripstone caves to
 * extreme hills. Spawn lists are copied from the previous vanilla projection targets so
 * mob spawning in cave columns keeps its prior behavior.
 */
@Mod.EventBusSubscriber(modid = CavesNotCliffs.MODID)
public final class CaveBiomeContent {
    /** Immutable definition row and its registered 1.12 biome instance. */
    public enum Definition {
        LUSH_CAVES(V118Biome.LUSH_CAVES, "lush_caves", "Lush Caves", 0.5F, 0.5F,
                Biomes.FOREST),
        DRIPSTONE_CAVES(V118Biome.DRIPSTONE_CAVES, "dripstone_caves", "Dripstone Caves",
                0.8F, 0.4F, Biomes.EXTREME_HILLS);

        private final V118Biome virtualBiome;
        private final ResourceLocation registryName;
        private final Biome spawnSource;
        private final CncCaveBiome biome;

        Definition(V118Biome virtualBiome, String path, String displayName,
                float temperature, float downfall, Biome spawnSource) {
            this.virtualBiome = virtualBiome;
            registryName = new ResourceLocation(CavesNotCliffs.MODID, path);
            this.spawnSource = spawnSource;
            biome = new CncCaveBiome(this, displayName, temperature, downfall);
        }

        public V118Biome virtualBiome() {
            return virtualBiome;
        }

        public ResourceLocation registryName() {
            return registryName;
        }

        public Biome biome() {
            return biome;
        }

        private void copySpawnLists() {
            biome.copySpawnListsFrom(spawnSource);
        }
    }

    private static final class CncCaveBiome extends Biome {
        CncCaveBiome(Definition definition, String displayName, float temperature,
                float downfall) {
            super(new Biome.BiomeProperties(displayName)
                    .setTemperature(temperature)
                    .setRainfall(downfall));
            setRegistryName(definition.registryName());
        }

        private void copySpawnListsFrom(Biome source) {
            spawnableMonsterList.addAll(source.getSpawnableList(EnumCreatureType.MONSTER));
            spawnableCreatureList.addAll(source.getSpawnableList(EnumCreatureType.CREATURE));
            spawnableWaterCreatureList.addAll(
                    source.getSpawnableList(EnumCreatureType.WATER_CREATURE));
            spawnableCaveCreatureList.addAll(
                    source.getSpawnableList(EnumCreatureType.AMBIENT));
        }
    }

    private CaveBiomeContent() {
    }

    @SubscribeEvent
    public static void registerBiomes(RegistryEvent.Register<Biome> event) {
        for (Definition definition : Definition.values()) {
            event.getRegistry().register(definition.biome());
            definition.copySpawnLists();
        }
        BiomeDictionary.addTypes(Definition.LUSH_CAVES.biome(), BiomeDictionary.Type.LUSH);
    }

    public static Biome biomeFor(ResourceLocation registryName) {
        for (Definition definition : Definition.values()) {
            if (definition.registryName().equals(registryName)) {
                return definition.biome();
            }
        }
        return null;
    }

    public static boolean isCaveBiome(V118Biome biome) {
        return BY_VIRTUAL_BIOME.containsKey(biome);
    }

    private static final Map<V118Biome, Definition> BY_VIRTUAL_BIOME = indexDefinitions();

    private static Map<V118Biome, Definition> indexDefinitions() {
        EnumMap<V118Biome, Definition> definitions = new EnumMap<V118Biome, Definition>(
            V118Biome.class);
        for (Definition definition : Definition.values()) {
            definitions.put(definition.virtualBiome(), definition);
        }
        return Collections.unmodifiableMap(definitions);
    }
}
