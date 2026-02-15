package net.serverplugins.items.pack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.serverplugins.items.ItemsConfig;
import net.serverplugins.items.models.CustomItem;

public class PackGenerator {

    private final Logger logger;
    private final ItemsConfig config;
    private final File pluginFolder;
    private final ModelGenerator modelGenerator;
    private final AtlasGenerator atlasGenerator;
    private final GeyserMappingGenerator geyserGenerator;

    public PackGenerator(Logger logger, ItemsConfig config, File pluginFolder) {
        this.logger = logger;
        this.config = config;
        this.pluginFolder = pluginFolder;
        this.modelGenerator = new ModelGenerator(logger);
        this.atlasGenerator = new AtlasGenerator();
        this.geyserGenerator = new GeyserMappingGenerator(logger);
    }

    /**
     * Generates resource packs. When multi-version is enabled, produces one zip per pack_format
     * plus a pack-manifest.json. Otherwise falls back to the single-pack behavior.
     */
    public PackManifest generate(Collection<CustomItem> items) throws IOException {
        if (config.isMultiVersion()) {
            return generateMultiVersion(items);
        }
        return generateSinglePack(items);
    }

    /** Original single-pack generation for backward compatibility. */
    private PackManifest generateSinglePack(Collection<CustomItem> items) throws IOException {
        File packDir = new File(pluginFolder, "pack");
        File assetsDir = new File(packDir, "assets");
        File generatedDir = new File(packDir, "generated");
        generatedDir.mkdirs();

        File userTexturesDir = new File(assetsDir, "serverplugins/textures/item");
        File userModelsDir = new File(assetsDir, "serverplugins/models/item");
        userTexturesDir.mkdirs();
        userModelsDir.mkdirs();

        File outputFile = new File(pluginFolder, config.getPackOutput());
        outputFile.getParentFile().mkdirs();

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile))) {
            writePackMcmeta(zip, config.getPackFormat());
            modelGenerator.generateModelOverrides(zip, items);
            modelGenerator.generateAutoModels(zip, items, userModelsDir);
            atlasGenerator.generateAtlas(zip, items);
            copyUserAssets(zip, assetsDir);
            zip.flush();
        }

        File geyserFile = new File(generatedDir, "geyser_mappings.json");
        geyserGenerator.generate(items, geyserFile);

        String hash = PackHasher.sha1(outputFile);
        logger.info(
                "Resource pack generated: "
                        + outputFile.getName()
                        + " (SHA-1: "
                        + hash
                        + ", "
                        + outputFile.length() / 1024
                        + " KB)");

        PackManifest manifest = new PackManifest();
        manifest.setGenerated(Instant.now().toString());
        manifest.setDefault(outputFile.getName(), hash);
        return manifest;
    }

    /** Multi-version generation: one zip per unique pack_format, plus manifest. */
    private PackManifest generateMultiVersion(Collection<CustomItem> items) throws IOException {
        File packDir = new File(pluginFolder, "pack");
        File assetsDir = new File(packDir, "assets");
        File outputDir = new File(pluginFolder, config.getPackOutputDir());
        outputDir.mkdirs();

        File userTexturesDir = new File(assetsDir, "serverplugins/textures/item");
        File userModelsDir = new File(assetsDir, "serverplugins/models/item");
        userTexturesDir.mkdirs();
        userModelsDir.mkdirs();

        // Pre-generate shared content into memory buffers so we only call generators once
        byte[] modelOverrides =
                captureZipContent(zip -> modelGenerator.generateModelOverrides(zip, items));
        byte[] autoModels =
                captureZipContent(
                        zip -> modelGenerator.generateAutoModels(zip, items, userModelsDir));
        byte[] atlasContent = captureZipContent(zip -> atlasGenerator.generateAtlas(zip, items));

        PackVersionRegistry.PackVersion[] versions = PackVersionRegistry.getVersions();

        // Track generated files: label -> (filename, hash)
        Map<String, String[]> generatedPacks = new LinkedHashMap<>();

        for (PackVersionRegistry.PackVersion version : versions) {
            String filename = "serverplugins-" + version.label() + ".zip";
            File outputFile = new File(outputDir, filename);

            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile))) {
                writePackMcmeta(zip, version.packFormat());

                // Replay pre-generated content
                replayZipContent(zip, modelOverrides);
                replayZipContent(zip, autoModels);
                replayZipContent(zip, atlasContent);

                copyUserAssets(zip, assetsDir);
                zip.flush();
            }

            String hash = PackHasher.sha1(outputFile);
            generatedPacks.put(version.label(), new String[] {filename, hash});

            logger.info(
                    "Generated pack: "
                            + filename
                            + " (format "
                            + version.packFormat()
                            + ", SHA-1: "
                            + hash
                            + ", "
                            + outputFile.length() / 1024
                            + " KB)");
        }

        // Generate Geyser mappings (version-independent)
        File geyserFile = new File(outputDir, "geyser_mappings.json");
        geyserGenerator.generate(items, geyserFile);

        // Build and write manifest
        PackManifest manifest = new PackManifest();
        manifest.setGenerated(Instant.now().toString());

        Map<Integer, String> protoMap = PackVersionRegistry.getProtocolToLabelMap();
        for (Map.Entry<Integer, String> entry : protoMap.entrySet()) {
            String[] packInfo = generatedPacks.get(entry.getValue());
            if (packInfo != null) {
                manifest.addPack(entry.getKey(), packInfo[0], packInfo[1]);
            }
        }

        // Default = latest version
        PackVersionRegistry.PackVersion latest = PackVersionRegistry.getLatest();
        String[] latestPack = generatedPacks.get(latest.label());
        if (latestPack != null) {
            manifest.setDefault(latestPack[0], latestPack[1]);
        }

        File manifestFile = new File(outputDir, "pack-manifest.json");
        manifest.writeToFile(manifestFile);

        logger.info(
                "Pack manifest written: "
                        + versions.length
                        + " version packs, "
                        + protoMap.size()
                        + " protocol mappings");

        return manifest;
    }

    /**
     * Captures zip entries written by a generator into a temporary file so we can replay them into
     * multiple output zips. Returns the raw bytes of the temporary zip.
     */
    private byte[] captureZipContent(ZipContentWriter writer) throws IOException {
        File tempFile = File.createTempFile("pack-content-", ".zip");
        try {
            try (ZipOutputStream tempZip = new ZipOutputStream(new FileOutputStream(tempFile))) {
                writer.write(tempZip);
                tempZip.flush();
            }
            return Files.readAllBytes(tempFile.toPath());
        } finally {
            tempFile.delete();
        }
    }

    /** Replays zip entries from a captured temporary zip into the target zip. */
    private void replayZipContent(ZipOutputStream target, byte[] zipBytes) throws IOException {
        File tempFile = File.createTempFile("pack-replay-", ".zip");
        try {
            Files.write(tempFile.toPath(), zipBytes);
            try (java.util.zip.ZipInputStream zis =
                    new java.util.zip.ZipInputStream(new java.io.FileInputStream(tempFile))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    target.putNextEntry(new ZipEntry(entry.getName()));
                    zis.transferTo(target);
                    target.closeEntry();
                }
            }
        } finally {
            tempFile.delete();
        }
    }

    private void writePackMcmeta(ZipOutputStream zip, int packFormat) throws IOException {
        String mcmeta =
                "{\n"
                        + "  \"pack\": {\n"
                        + "    \"pack_format\": "
                        + packFormat
                        + ",\n"
                        + "    \"description\": \""
                        + config.getPackDescription()
                        + "\"\n"
                        + "  }\n"
                        + "}";
        zip.putNextEntry(new ZipEntry("pack.mcmeta"));
        zip.write(mcmeta.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void copyUserAssets(ZipOutputStream zip, File assetsDir) throws IOException {
        if (!assetsDir.exists()) return;

        Path assetsPath = assetsDir.toPath();
        try (Stream<Path> walk = Files.walk(assetsPath)) {
            walk.filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                try {
                                    String entryName =
                                            "assets/" + assetsPath.relativize(file).toString();
                                    entryName = entryName.replace('\\', '/');
                                    zip.putNextEntry(new ZipEntry(entryName));
                                    Files.copy(file, zip);
                                    zip.closeEntry();
                                } catch (IOException e) {
                                    logger.warning(
                                            "Failed to copy asset: "
                                                    + file
                                                    + " - "
                                                    + e.getMessage());
                                }
                            });
        }
    }

    public String getPackHash(File packFile) throws IOException {
        return PackHasher.sha1(packFile);
    }

    /** Functional interface for zip content generation. */
    @FunctionalInterface
    private interface ZipContentWriter {
        void write(ZipOutputStream zip) throws IOException;
    }
}
