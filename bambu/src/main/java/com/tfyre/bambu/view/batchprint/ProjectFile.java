package com.tfyre.bambu.view.batchprint;

import com.tfyre.bambu.printer.FilamentType;
import com.tfyre.schema.Config;
import com.tfyre.schema.Metadata;
import com.vaadin.flow.server.AbstractStreamResource;
import com.vaadin.flow.server.StreamResource;
import io.quarkus.logging.Log;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.transform.stream.StreamSource;
import org.jboss.logging.Logger;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Dependent
public class ProjectFile implements FilamentHelper {

    private static final String PLATE_PNG = "Metadata/plate_%d.png";
    private static final String SLICE_INFO = "Metadata/slice_info.config";

    private final Map<Integer, StreamResource> thumbnails = new HashMap<>();
    private final JAXBContext context;
    private final Unmarshaller unmarshaller;
    private ZipFile zipFile;
    private List<Plate> plates;
    private String filename;
    private File file;

    public ProjectFile() {
        try {
            context = JAXBContext.newInstance(com.tfyre.schema.ObjectFactory.class);
            unmarshaller = context.createUnmarshaller();
        } catch (JAXBException ex) {
            throw new RuntimeException("Cannot create JAXB: %s".formatted(ex.getMessage()), ex);
        }
    }

    private Config getSliceInfo() throws ProjectException {
        final ZipEntry sliceEntry = Optional.ofNullable(zipFile.getEntry(SLICE_INFO))
                .orElseThrow(() -> new ProjectException("[%s] not found".formatted(SLICE_INFO)));
        try {
            return unmarshaller.unmarshal(new StreamSource(zipFile.getInputStream(sliceEntry)), Config.class).getValue();
        } catch (JAXBException | IOException ex) {
            throw new ProjectException("Cannot unmarshal [%s]: %s".formatted(SLICE_INFO, ex.getMessage()), ex);
        }
    }

    private PlateFilament mapFilament(final com.tfyre.schema.Filament filament) {
        return new PlateFilament(filament.getId(),
                FilamentType.getFilamentType(filament.getType()).orElse(FilamentType.UNKNOWN),
                filament.getUsedG(), mapFilamentColor(filament.getColor()));
    }

    private Plate mapPlate(final com.tfyre.schema.Plate plate) {
        final Map<String, String> map = plate.getMetadata().stream()
                .collect(Collectors.toMap(Metadata::getKey, Metadata::getValueAttr));
        final int plateId = parseInt(map.get("index"), -1);
        final int prediction = parseInt(map.get("prediction"), -1);
        final double weight = parseDouble(map.get("weight"), -1);
        return new Plate("Plate %d".formatted(plateId), plateId, Duration.ofSeconds(prediction), weight,
                plate.getFilament().stream().map(this::mapFilament).toList());
    }

    public List<Plate> getPlates() {
        return plates;
    }

    public String getFilename() {
        return filename;
    }

    public long getFileSize() {
        return file.length();
    }

    public ProjectFile setup(final String filename, final File file) throws ProjectException {
        this.filename = filename;
        this.file = file;
        try {
            zipFile = new ZipFile(file);
        } catch (IOException ex) {
            throw new ProjectException("Error opening [%s]: %s".formatted(filename, ex.getMessage()), ex);
        }
        plates = getSliceInfo().getPlate().stream()
                .map(this::mapPlate)
                .sorted(Comparator.comparing(Plate::plateId))
                .toList();
        return this;
    }

    private StreamResource getThumbnail(final int plateId) {
        final String platePng = PLATE_PNG.formatted(plateId);
        final ZipEntry pngEntry = zipFile.getEntry(platePng);
        return new StreamResource("image.jpg", () -> {
            try {
                return zipFile.getInputStream(pngEntry);
            } catch (IOException ex) {
                final String message = "Cannot read [%s]: %s".formatted(platePng, ex.getMessage());
                Log.error(message, ex);
                throw new RuntimeException(message);
            }
        });
    }

    public AbstractStreamResource getThumbnail(final Plate plate) {
        return thumbnails.computeIfAbsent(plate.plateId(), this::getThumbnail);

    }

    @PreDestroy
    public void preDestroy() {
        try {
            zipFile.close();
        } catch (IOException ex) {
            Log.errorf(ex, "Error closing 3mf: %s", ex.getMessage());
        }
    }

    public InputStream getStream() throws FileNotFoundException {
        return new FileInputStream(file);
    }

}
