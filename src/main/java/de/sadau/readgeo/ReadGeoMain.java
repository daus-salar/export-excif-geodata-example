package de.sadau.readgeo;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;

import org.xml.sax.SAXException;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;

import de.sadau.gpx.GpxType;
import de.sadau.gpx.ObjectFactory;
import de.sadau.gpx.WptType;

public class ReadGeoMain {

	static public class GpsCoordinate {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			long temp;
			temp = Double.doubleToLongBits(latitude);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			temp = Double.doubleToLongBits(longitude);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final GpsCoordinate other = (GpsCoordinate) obj;
			if (Double.doubleToLongBits(latitude) != Double
					.doubleToLongBits(other.latitude)) {
				return false;
			}
			if (Double.doubleToLongBits(longitude) != Double
					.doubleToLongBits(other.longitude)) {
				return false;
			}
			return true;
		}

		public GpsCoordinate(final double longitude, final double latitude) {
			super();
			this.longitude = longitude;
			this.latitude = latitude;

		}

		final public double longitude;
		final public double latitude;
	}

	final static Collector<Metadata, ?, List<Metadata>> toListAndRemoveNulls = collectingAndThen(
			toList(), l -> {
				l.removeIf(m -> m == null);
				return l;
			});

	final static Function<File, ? extends Metadata> extractMetadata = (
			final File imageFile) -> {
				try {
					final Metadata metadata = ImageMetadataReader
							.readMetadata(imageFile);
					return metadata;
				} catch (final Exception e) {
					System.out.println("Couldn't read " + imageFile.toString());
					return null;
				}
			};
			static BiConsumer<String, List<Metadata>> print = (s, lm) -> {
				System.out.printf("%s :", s);

				System.out.printf("\n");

			};

			public static void main(final String[] args) throws FileNotFoundException,
			JAXBException, SAXException {
				final String imageDir = "pictures";
				final File picturesDir = Paths.get(imageDir).toFile();
				// processToGpx(picturesDir);
				processToCSV(picturesDir, new PrintStream(new FileOutputStream(
						"out.csv", false)));
			}

			static void processToCSV(final File picturesDir, final PrintStream outStream) {
				final Set<GpsCoordinate> used = new HashSet<>();
				processGpsMetadata(picturesDir, (s, gpsDir) -> {
					final GpsCoordinate coord = new GpsCoordinate(gpsDir
							.getGeoLocation().getLongitude(), gpsDir.getGeoLocation()
							.getLatitude());
					if (!used.contains(coord)) {
						used.add(coord);
						outStream.printf("%s, %s, %s \n", s, coord.latitude,
								coord.longitude);
					}
				});

			}

			static void processToGpx(final File picturesDir) throws JAXBException,
			PropertyException {
				final ObjectFactory objFac = new ObjectFactory();
				final GpxType gpx = objFac.createGpxType();
				processGpsMetadata(picturesDir, (s, gpsDir) -> {
					final WptType wpt = objFac.createWptType();
					wpt.setLat(BigDecimal
							.valueOf(gpsDir.getGeoLocation().getLatitude()));
					wpt.setLon(BigDecimal.valueOf(gpsDir.getGeoLocation()
							.getLongitude()));
					gpx.getWpt().add(wpt);
				});

				final File file = new File("out.gpx");
				final JAXBContext jaxbContext = JAXBContext.newInstance(GpxType.class);
				final Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

				// output pretty printed
				jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

				final JAXBElement<GpxType> jaxbElement = objFac.createGpx(gpx);
				jaxbMarshaller.marshal(jaxbElement, file);
			}

			private static void processGpsMetadata(final File picturesDir,
					final BiConsumer<String, GpsDirectory> gpsDirProcessor) {
				final Map<String, List<Metadata>> allMetaData = extractMetadata(picturesDir);
				allMetaData
				.entrySet()
				.stream()
				.sorted((f1, f2) -> {
					return String.CASE_INSENSITIVE_ORDER.compare(f1.getKey(),
							f2.getKey());
				})
				.forEach(
						e -> {
							for (final Metadata m : e.getValue()) {
								final GpsDirectory gpsDir = m
										.getDirectory(GpsDirectory.class);
								if (gpsDir != null) {
									gpsDirProcessor.accept(e.getKey(), gpsDir);
								}
							}

						});
			}

			private static Map<String, List<Metadata>> extractMetadata(
			final File picturesDir) {
		return Arrays.stream(picturesDir.listFiles()).collect(
				groupingBy(File::toString,
						mapping(extractMetadata, toListAndRemoveNulls)));
	}
}
