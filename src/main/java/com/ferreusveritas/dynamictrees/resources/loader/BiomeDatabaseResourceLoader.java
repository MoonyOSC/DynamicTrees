package com.ferreusveritas.dynamictrees.resources.loader;

import com.ferreusveritas.dynamictrees.api.event.Hooks;
import com.ferreusveritas.dynamictrees.api.resource.ResourceAccessor;
import com.ferreusveritas.dynamictrees.api.resource.loading.AbstractResourceLoader;
import com.ferreusveritas.dynamictrees.api.resource.loading.ApplierResourceLoader;
import com.ferreusveritas.dynamictrees.api.resource.loading.preparation.MultiJsonResourcePreparer;
import com.ferreusveritas.dynamictrees.api.treepacks.ApplierRegistryEvent;
import com.ferreusveritas.dynamictrees.api.treepacks.PropertyApplierResult;
import com.ferreusveritas.dynamictrees.api.worldgen.BiomePropertySelectors;
import com.ferreusveritas.dynamictrees.api.worldgen.FeatureCanceller;
import com.ferreusveritas.dynamictrees.deserialisation.BiomeListDeserialiser;
import com.ferreusveritas.dynamictrees.deserialisation.DeserialisationException;
import com.ferreusveritas.dynamictrees.deserialisation.JsonDeserialisers;
import com.ferreusveritas.dynamictrees.deserialisation.JsonPropertyAppliers;
import com.ferreusveritas.dynamictrees.deserialisation.result.JsonResult;
import com.ferreusveritas.dynamictrees.deserialisation.result.Result;
import com.ferreusveritas.dynamictrees.init.DTConfigs;
import com.ferreusveritas.dynamictrees.util.CommonCollectors;
import com.ferreusveritas.dynamictrees.util.IgnoreThrowable;
import com.ferreusveritas.dynamictrees.util.JsonMapWrapper;
import com.ferreusveritas.dynamictrees.util.holderset.DTBiomeHolderSet;
import com.ferreusveritas.dynamictrees.util.holderset.DelayedAnyHolderSet;
import com.ferreusveritas.dynamictrees.worldgen.BiomeDatabase;
import com.ferreusveritas.dynamictrees.worldgen.BiomeDatabases;
import com.ferreusveritas.dynamictrees.worldgen.FeatureCancellationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import static com.ferreusveritas.dynamictrees.api.resource.loading.ApplierResourceLoader.postApplierEvent;
import static com.ferreusveritas.dynamictrees.deserialisation.JsonHelper.throwIfShouldNotLoad;

/**
 * @author Harley O'Connor
 */
public final class BiomeDatabaseResourceLoader
        extends AbstractResourceLoader<Iterable<JsonElement>>
        implements ApplierResourceLoader<Iterable<JsonElement>> {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String DEFAULT_POPULATOR = "default";

    public static final String SELECT = "select";
    public static final String APPLY = "apply";
    public static final String WHITE = "white";
    public static final String CANCELLERS = "cancellers";

    private static final String METHOD = "method";

    public static final String ENTRY_APPLIERS = "entries";
    public static final String CANCELLATION_APPLIERS = "cancellations";

    private final JsonPropertyAppliers<BiomeDatabase.Entry> entryAppliers =
            new JsonPropertyAppliers<>(BiomeDatabase.Entry.class);
    private final JsonPropertyAppliers<BiomePropertySelectors.FeatureCancellations> cancellationAppliers =
            new JsonPropertyAppliers<>(BiomePropertySelectors.FeatureCancellations.class);

    public BiomeDatabaseResourceLoader() {
        super(new MultiJsonResourcePreparer("world_gen"));
    }

    @Override
    public void registerAppliers() {
        this.entryAppliers
                .register("species", JsonElement.class, this::applySpecies)
                .register("density", JsonElement.class, this::applyDensity)
                .register("chance", JsonElement.class, this::applyChance)
                .register("multipass", Boolean.class, this::applyMultipass)
                .register("multipass", JsonObject.class, BiomeDatabase.Entry::setCustomMultipass)
                .register("blacklist", Boolean.class, BiomeDatabase.Entry::setBlacklisted)
                .register("forestness", Float.class, BiomeDatabase.Entry::setForestness)
                .register("subterranean", Boolean.class, BiomeDatabase.Entry::setSubterranean)
                .registerIfTrueApplier("reset", BiomeDatabase.Entry::reset);

        this.cancellationAppliers
                .register("namespace", String.class, BiomePropertySelectors.FeatureCancellations::putNamespace)
                .registerArrayApplier("namespaces", String.class,
                        BiomePropertySelectors.FeatureCancellations::putNamespace)
                .register("type", FeatureCanceller.class, BiomePropertySelectors.FeatureCancellations::putCanceller)
                .registerArrayApplier("types", FeatureCanceller.class,
                        BiomePropertySelectors.FeatureCancellations::putCanceller)
                .register("stage", GenerationStep.Decoration.class,
                        BiomePropertySelectors.FeatureCancellations::putStage)
                .registerArrayApplier("stages", GenerationStep.Decoration.class,
                        BiomePropertySelectors.FeatureCancellations::putStage);

        postApplierEvent(new EntryApplierRegistryEvent<>(this.entryAppliers, ENTRY_APPLIERS));
        postApplierEvent(new CancellationApplierRegistryEvent<>(this.cancellationAppliers,
                CANCELLATION_APPLIERS));
    }

    public static final class EntryApplierRegistryEvent<O> extends ApplierRegistryEvent<O, JsonElement> {
        public EntryApplierRegistryEvent(JsonPropertyAppliers<O> appliers, String identifier) {
            super(appliers, identifier);
        }
    }

    public static final class CancellationApplierRegistryEvent<O> extends ApplierRegistryEvent<O, JsonElement> {
        public CancellationApplierRegistryEvent(JsonPropertyAppliers<O> appliers, String identifier) {
            super(appliers, identifier);
        }
    }

    private PropertyApplierResult applySpecies(BiomeDatabase.Entry entry, JsonElement jsonElement) {
        return PropertyApplierResult.from(JsonDeserialisers.SPECIES_SELECTOR.deserialise(jsonElement)
                .ifSuccess(speciesSelector -> entry.getDatabase().setSpeciesSelector(entry, speciesSelector, getOperationOrWarn(jsonElement))));
    }

    private PropertyApplierResult applyDensity(BiomeDatabase.Entry entry, JsonElement jsonElement) {
        return PropertyApplierResult.from(JsonDeserialisers.DENSITY_SELECTOR.deserialise(jsonElement)
                .ifSuccess(densitySelector -> entry.getDatabase().setDensitySelector(entry, densitySelector, getOperationOrWarn(jsonElement))));
    }

    private PropertyApplierResult applyChance(BiomeDatabase.Entry entry, JsonElement jsonElement) {
        return PropertyApplierResult.from(JsonDeserialisers.CHANCE_SELECTOR.deserialise(jsonElement)
                .ifSuccess(chanceSelector -> entry.getDatabase().setChanceSelector(entry, chanceSelector, getOperationOrWarn(jsonElement))));
    }

    private void applyMultipass(BiomeDatabase.Entry entry, Boolean multipass) {
        if (!multipass) {
            return;
        }
        entry.enableDefaultMultipass();
    }

    public static BiomeDatabase.Operation getOperationOrWarn(final JsonElement jsonElement) {
        return getOperation(jsonElement).orElse(BiomeDatabase.Operation.REPLACE, LOGGER::error, LOGGER::warn);
    }

    private static Result<BiomeDatabase.Operation, JsonElement> getOperation(final JsonElement input) {
        return JsonDeserialisers.JSON_OBJECT.deserialise(input)
                .removeError() // Remove error at this point as we don't want to warn when element is not Json object.
                .map(jsonObject -> jsonObject.has(METHOD) ? jsonObject.get(METHOD) : null)
                .map(JsonDeserialisers.OPERATION::deserialise)
                .orElseApply(error -> JsonResult.failure(input, "Error getting operation (defaulting to " +
                                "replace): " + error),
                        JsonResult.success(input, BiomeDatabase.Operation.REPLACE));
    }

    @Override
    public void applyOnSetup(ResourceAccessor<Iterable<JsonElement>> resourceAccessor,
                             ResourceManager resourceManager) {
        BiomeDatabases.reset();
        if (this.isWorldGenDisabled()) {
            return;
        }

        Hooks.onAddFeatureCancellers();
        this.readCancellers(
                resourceAccessor.filtered(this::isDefaultPopulator).map(this::toLinkedList)
        );
    }

    private void readCancellers(ResourceAccessor<Deque<JsonElement>> defaultPopulators) {
        this.readModCancellers(defaultPopulators);
        this.readTreePackCancellers(defaultPopulators);
    }

    private void readModCancellers(ResourceAccessor<Deque<JsonElement>> defaultPopulators) {
        defaultPopulators.getAllResources().forEach(defaultPopulator ->
                this.readCancellers(defaultPopulator.getLocation(), defaultPopulator.getResource().pollFirst())
        );
    }

    private void readTreePackCancellers(ResourceAccessor<Deque<JsonElement>> defaultPopulators) {
        defaultPopulators.getAllResources().forEach(defaultPopulator ->
                defaultPopulator.getResource().forEach(json ->
                        this.readCancellers(defaultPopulator.getLocation(), json)
                )
        );
    }

    private void readCancellers(final ResourceLocation location, final JsonElement json) {
        LOGGER.debug("Reading cancellers from Json biome populator \"{}\".", location);

        try {
            JsonResult.forInput(json)
                    .mapEachIfArray(JsonObject.class, object -> {
                        try {
                            this.readCancellersInSection(location, object);
                        } catch (IgnoreThrowable ignored) {
                        }
                        return PropertyApplierResult.success();
                    }).forEachWarning(warning ->
                            LOGGER.warn("Warning whilst loading cancellers from populator \"{}\": {}",
                                    location, warning)
                    ).orElseThrow();
        } catch (DeserialisationException e) {
            LOGGER.error("Error whilst loading cancellers from populator \"{}\": {}", location,
                    e.getMessage());
        }
    }

    private void readCancellersInSection(final ResourceLocation location, final JsonObject json)
            throws DeserialisationException, IgnoreThrowable {

        final Consumer<String> errorConsumer = error -> LOGGER.error("Error loading populator \"{}\": {}",
                location, error);
        final Consumer<String> warningConsumer = warning -> LOGGER.warn("Warning whilst loading populator " +
                "\"{}\": {}", location, warning);

        throwIfShouldNotLoad(json);

        final DTBiomeHolderSet biomes = this.collectBiomes(json, warningConsumer);

        // Running this now would be too early!
        // if (biomes.isEmpty()) {
        //     warnNoBiomesSelected(json);
        //     return;
        // }

        JsonResult.forInput(json)
                .mapIfContains(CANCELLERS, JsonObject.class, cancellerObject ->
                                this.applyCanceller(location, errorConsumer, warningConsumer,
                                        biomes, cancellerObject),
                        PropertyApplierResult.success()
                )
                .forEachWarning(warningConsumer)
                .orElseThrow();
    }

    private DTBiomeHolderSet collectBiomes(JsonObject json, Consumer<String> warningConsumer) throws DeserialisationException {
        return JsonResult.forInput(json)
                .mapIfContains(SELECT, DTBiomeHolderSet.class, list -> list)
                .forEachWarning(warningConsumer)
                .orElseThrow();
    }

    private PropertyApplierResult applyCanceller(ResourceLocation location,
                                                 Consumer<String> errorConsumer,
                                                 Consumer<String> warningConsumer, DTBiomeHolderSet biomes,
                                                 JsonObject json) {
        final BiomePropertySelectors.FeatureCancellations cancellations =
                new BiomePropertySelectors.FeatureCancellations();
        this.applyCancellationAppliers(location, json, cancellations);

        cancellations.putDefaultStagesIfEmpty();

        final BiomeDatabase.Operation operation = JsonResult.forInput(json)
                .mapIfContains(METHOD, BiomeDatabase.Operation.class, op -> op, BiomeDatabase.Operation.SPLICE_AFTER)
                .forEachWarning(warningConsumer)
                .orElse(BiomeDatabase.Operation.SPLICE_AFTER, errorConsumer, warningConsumer);

        FeatureCancellationRegistry.addCancellations(biomes, operation, cancellations);

        return PropertyApplierResult.success();
    }

    private void applyCancellationAppliers(ResourceLocation location, JsonObject json,
                                           BiomePropertySelectors.FeatureCancellations cancellations) {
        this.cancellationAppliers.applyAll(new JsonMapWrapper(json), cancellations)
                .forEachErrorWarning(
                        error -> LOGGER.error("Error whilst applying feature cancellations " +
                                "in \"{}\" " + "populator: {}", location, error),
                        warning -> LOGGER.warn("Warning whilst applying feature " +
                                "cancellations in \"{}\" populator: {}", location, warning)
                );
    }

    @Override
    public void applyOnReload(ResourceAccessor<Iterable<JsonElement>> resourceAccessor,
                              ResourceManager resourceManager) {
        BiomeDatabases.reset();
        if (this.isWorldGenDisabled()) {
            return;
        }

        this.readPopulators(
                resourceAccessor.filtered(this::isDefaultPopulator).map(this::toLinkedList)
        );
        this.readDimensionalPopulators(
                resourceAccessor.filtered(resource -> !this.isDefaultPopulator(resource)).map(this::toLinkedList)
        );
    }

    private boolean isWorldGenDisabled() {
        return !DTConfigs.WORLD_GEN.get();
    }

    private void readPopulators(ResourceAccessor<Deque<JsonElement>> resourceAccessor) {
        Hooks.onPopulateDefaultDatabase();
        this.readModPopulators(BiomeDatabases.getDefault(), resourceAccessor);
        this.readTreePackPopulators(BiomeDatabases.getDefault(), resourceAccessor);
    }

    private void readModPopulators(BiomeDatabase database, ResourceAccessor<Deque<JsonElement>> resourceAccessor) {
        resourceAccessor.getAllResources().forEach(defaultPopulator ->
                this.readPopulator(database, defaultPopulator.getLocation(), defaultPopulator.getResource().pollFirst())
        );
    }

    private void readTreePackPopulators(BiomeDatabase database, ResourceAccessor<Deque<JsonElement>> resourceAccessor) {
        resourceAccessor.getAllResources().forEach(defaultPopulator ->
                defaultPopulator.getResource().forEach(jsonElement ->
                        this.readPopulator(database, defaultPopulator.getLocation(), jsonElement))
        );
    }

    private void readDimensionalPopulators(ResourceAccessor<Deque<JsonElement>> resourceAccessor) {
        Hooks.onPopulateDimensionalDatabases();
        this.readDimensionalModPopulators(resourceAccessor);
        this.readDimensionalTreePackPopulators(resourceAccessor);
    }

    private void readDimensionalModPopulators(ResourceAccessor<Deque<JsonElement>> resourceAccessor) {
        resourceAccessor.getAllResources().forEach(dimensionalPopulator ->
                this.readDimensionalPopulator(dimensionalPopulator.getLocation(),
                        dimensionalPopulator.getResource().pollFirst())
        );
    }

    private void readDimensionalTreePackPopulators(ResourceAccessor<Deque<JsonElement>> resourceAccessor) {
        resourceAccessor.getAllResources().forEach(dimensionalPopulator ->
                dimensionalPopulator.getResource().forEach(json ->
                        this.readDimensionalPopulator(dimensionalPopulator.getLocation(), json))
        );
    }

    private void readDimensionalPopulator(ResourceLocation dimensionLocation, JsonElement dimensionalPopulator) {
        this.readPopulator(BiomeDatabases.getOrCreateDimensional(dimensionLocation), dimensionLocation,
                dimensionalPopulator);
    }

    private void readPopulator(BiomeDatabase database, ResourceLocation location, JsonElement json) {
        LOGGER.debug("Loading Json biome populator \"{}\".", location);

        try {
            JsonResult.forInput(json)
                    .mapEachIfArray(JsonObject.class, object -> {
                        this.readPopulatorSection(database, location, object);
                        return PropertyApplierResult.success();
                    }).forEachWarning(warning ->
                            LOGGER.warn("Warning whilst loading populator \"{}\": {}", location, warning)
                    ).orElseThrow();
        } catch (DeserialisationException e) {
            LOGGER.error("Error loading populator \"{}\": {}", location, e.getMessage());
        }
    }

    private void readPopulatorSection(BiomeDatabase database, ResourceLocation location, JsonObject json)
            throws DeserialisationException {

        final DTBiomeHolderSet biomes = this.collectBiomes(json, warning ->
                LOGGER.warn("Warning whilst loading populator \"{}\": {}", location, warning));

        // Running this now would be too early!
        // if (biomes.isEmpty()) {
        //     warnNoBiomesSelected(json);
        //     return;
        // }

        JsonResult.forInput(json)
                .mapIfContains(APPLY, JsonObject.class, applyObject -> {
                    BiomeDatabase.Entry entry = database.getJsonEntry(biomes);
                    this.entryAppliers.applyAll(new JsonMapWrapper(applyObject), entry);
                    return PropertyApplierResult.success();
                }, PropertyApplierResult.success())
                .elseMapIfContains(WHITE, String.class, type -> {
                    this.applyWhite(database, location, biomes, type);
                    return PropertyApplierResult.success();
                }, PropertyApplierResult.success())
                .forEachWarning(warning ->
                        LOGGER.warn("Warning whilst loading populator \"{}\": {}", location, warning))
                .orElseThrow();
    }

    // private void warnNoBiomesSelected(JsonObject json) {
    //     if (noBiomesSelectedWarningNotSuppressed(json)) {
    //         LogManager.getLogger().warn("Could not get any biomes from selector:\n" + json.get(SELECT));
    //     }
    // }
    //
    // private boolean noBiomesSelectedWarningNotSuppressed(JsonObject json) {
    //     final JsonElement suppress = json.get("suppress_none_selected");
    //     return suppress == null || !suppress.isJsonPrimitive() || !suppress.getAsJsonPrimitive().isBoolean() ||
    //             !suppress.getAsJsonPrimitive().getAsBoolean();
    // }

    private void applyWhite(BiomeDatabase database, ResourceLocation location, DTBiomeHolderSet biomes, String type)
            throws DeserialisationException {
        if (type.equalsIgnoreCase("all")) {
            DTBiomeHolderSet allBiomes = new DTBiomeHolderSet();
            allBiomes.getIncludeComponents().add(new DelayedAnyHolderSet<>(BiomeListDeserialiser.DELAYED_BIOME_REGISTRY));
            database.getJsonEntry(allBiomes).setBlacklisted(false);
        } else if (type.equalsIgnoreCase("selected")) {
            database.getJsonEntry(biomes).setBlacklisted(false);
        } else {
            throw new DeserialisationException("Unknown type for whitelist in populator \"" +
                    location + "\": \"" + type + "\".");
        }
    }

    private boolean isDefaultPopulator(final ResourceLocation key) {
        return key.getPath().equals(DEFAULT_POPULATOR);
    }

    private LinkedList<JsonElement> toLinkedList(Iterable<JsonElement> elements) {
        return StreamSupport.stream(elements.spliterator(), false)
                .collect(CommonCollectors.toLinkedList());
    }

}
