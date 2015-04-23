/*
 * CountryInformation Class
 *
 * Compile country information from geonames.org and present in JSON format.
 * Two specific sources from the site are of interest:
 *
 * Country names and codes:
 * http://download.geonames.org/export/dump/countryInfo.txt
 *
 * Subdivision names and codes
 * http://download.geonames.org/export/dump/admin1CodesASCII.txt
 *
 * Both sources are used to create a single data structure from which clients
 * can quickly access interesting information, e.g the states in the US or
 * the provinces in France.  Of particular interest are the long names for
 * both countries and subdivisions.  Although much more information is
 * available from the 2 sources, e.g. population, the primary reason for
 * compiling this information is to get names and codes.  This implementation
 * is easily extended in the future to include additional interesting info.
 *
 * Country Input:
 *    Countries: (tab-delimited)
 *       <countryShortName>
 *       <countryIso3Name>
 *       <countryCode>
 *       ...
 *       <countryLongName>
 *
 * Subdivision Input:
 *    Subdivisions: (tab-delimited)
 *       <countryShortName>.<subdivisionShortName>
 *       <subdivisionI18nName>
 *       <subdivisionLongName>
 *       <subdivisionCode>
 *
 * JSON Output:
 * {
 *     <countryLongName>: {
 *         short_name: <countryShortName>,
 *         i18n_name: <countryI18nName>,
 *         iso3_name: <countryIso3Name>,
 *         code: <countryCode>
 *         subdivisions: {
 *             <subdivisionLongName>: {
 *                 short_name: <subdivisionShortName>,
 *                 code: <subdivisionCode>
 *             }
 *         }
 *     }
 * }
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;

import javax.json.*;
import javax.json.stream.*;

public class GetCountryInformation {

    /**
     * URL for country data
     */
    private static final String countryDataFilename =
            "http://download.geonames.org/export/dump/countryInfo.txt";

    /**
     * URL for state data
     */
    private static final String subdivisionDataFilename =
            "http://download.geonames.org/export/dump/admin1CodesASCII.txt";

    /**
     * Country data JSON object
     */
    private JsonObject countryData = null;

    /**
     * Country dictionary for quickly looking up desired information
     */
    private JsonObject countryDictionary = null;

    /**
     * Constructor
     * @param countriesFilename the file that contains all the country info
     * @param subdivisionsFilename the file that contains all the subdivision info
     */
    public GetCountryInformation(String countriesFilename,
            String subdivisionsFilename) {
        // Parse raw text and construct a JSON object with country data
        countryData = getJsonData(countriesFilename,subdivisionsFilename);
    }

    /**
     * Read in the data file and setup a scanner
     * @param countriesFilename country information file
     * @param subdivisionsFilename subdivision information file
     * @return JSON data structure with country/subdivision information
     */
    private JsonObject getJsonData(String countriesFilename,
            String subdivisionsFilename) {
        // Utilities scanner used serially for file scanning
        Scanner scanner = null;

        // Parse countries file and create a JSON data structure for later use
        try {
            scanner = new Scanner(new URL(countriesFilename).
                    openStream()).useDelimiter("\t|\\n");
            countryDictionary = parseCountriesData(scanner);
        } catch (IOException e) {
            System.out.println("Unable to read country file - " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }

        // Parse subdivision file and create JSON data structure
        JsonObject subdivisionJsonData = null;
        try {
            scanner = new Scanner(new URL(subdivisionsFilename).
                    openStream()).useDelimiter("\t|\\n");
            subdivisionJsonData = parseSubdivisionsData(scanner);
        } catch (IOException e) {
            System.out.println("Unable to read subdivisions file - " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }

        // Return complete JSON object from countries and subdivisions info
        return subdivisionJsonData;
    }

    /**
     * Parse the country information file and create a JSON file for later use
     * @param scanner the scanner to use to parse the country file
     * @return country dictionary in JSON format
     */
    private JsonObject parseCountriesData(Scanner scanner) {

        // Initialize dictionary and other temp vars
        JsonObjectBuilder dictionary = Json.createObjectBuilder();
        String countryShortName = null;
        String countryIso3Name = null;
        String countryCode = null;
        String countryLongName = null;

        // Parse the country file and stuff in dictionary
        while (scanner.hasNextLine()) {
            // Get data from line in text file
            countryShortName = scanner.next();
            // Process non-comment lines
            if (!countryShortName.startsWith("#")) {
                countryIso3Name = scanner.next();
                countryCode = scanner.next();
                scanner.next(); // Skip
                countryLongName = scanner.next();
                // Add to dictionary collection
                dictionary.add(countryLongName, Json.createObjectBuilder()
                        .add("short_name", countryShortName)
                        .add("iso3_name", countryIso3Name)
                        .add("code", countryCode));
            }
            // Next line
            scanner.nextLine();
        }

        // Close the scanner as we are done with it
        scanner.close();

        // Return findings
        return dictionary.build();
    }

    /**
     * Parse the subdivisions file and create a combined JSON data structure
     * @param scanner general purpose scanner for subdivisions file
     * @return JSON data structure for country/subdivision information
     */
    private JsonObject parseSubdivisionsData(Scanner scanner) {
        // Data for single subdivision
        String countryLongName = null;
        String countryShortName = null;
        String countryIso3Name = null;
        String countryCode = null;
        String subdivisionI18nName = null;
        String subdivisionLongName = null;
        String subdivisionShortName = null;
        String subdivisionCode = null;

        // Initialize top level builders
        JsonObjectBuilder countriesBuilder = Json.createObjectBuilder();
        JsonObjectBuilder countryBuilder = Json.createObjectBuilder();
        JsonObjectBuilder subdivisionsBuilder = Json.createObjectBuilder();


        // Process each line
        String prevCountryLongName = null;
        String prevCountryShortName = null;

        while (scanner.hasNextLine()) {
            // Get data from the line
            String [] idString = scanner.next().split("\\.");
            countryShortName = idString[0];
            countryLongName = getCountryLongName(countryShortName);
            countryIso3Name = getCountryIso3Name(countryLongName);
            countryCode = getCountryCode(countryLongName);

            // Check for new country
            if (!countryShortName.equals(prevCountryShortName)) {
                // Done with previous country
                if (prevCountryLongName != null) {
                    countriesBuilder.add(prevCountryLongName, countryBuilder
                            .add("subdivisions", subdivisionsBuilder));
                }
                // Start New Country
                countryBuilder = Json.createObjectBuilder()
                        .add("short_name", countryShortName)
                        .add("iso3_name", countryIso3Name)
                        .add("code", countryCode);
                // Start collecting new Subdivisions
                subdivisionsBuilder = Json.createObjectBuilder();
            }

            subdivisionShortName = idString[1];
            subdivisionI18nName = scanner.next(); // Non-ASCII Name
            subdivisionLongName = scanner.next(); // ASCII Name
            subdivisionCode = scanner.next();
            scanner.nextLine();

            // Create new subdivision builder and add to subdivisions
            subdivisionsBuilder.add(subdivisionLongName, Json.createObjectBuilder()
                    .add("short_name", subdivisionShortName)
                    .add("i18n_name", subdivisionI18nName)
                    .add("code", subdivisionCode));

            // Save country short name
            prevCountryLongName = countryLongName;
            prevCountryShortName = countryShortName;
        }

        // Capture last country
        countriesBuilder.add(countryLongName, countryBuilder
                 .add("subdivisions", subdivisionsBuilder));

        // Close scanner now that we are done with it
        scanner.close();

        // Return nicely packaged JSON
        return countriesBuilder.build();
    }

    /**
     * Get country long name from the dictionary
     * @param countryShortName the short name for the country
     * @return the long name for the country
     */
    private String getCountryLongName(String countryShortName) {
        // Walk the JSON object looking for a short name match and return
        // the long name when found
        String longName = null;
        String shortName = null;
        JsonObject countryObject = null;
        for (Map.Entry<String, JsonValue> entry : countryDictionary.entrySet()) {
            countryObject = (JsonObject)entry.getValue();
            shortName = countryObject.getString("short_name");
            if (shortName.equals(countryShortName)) {
                longName = entry.getKey();
                break;
            }
        }
        // Return results
        return longName;
    }

    /**
     * Get country ISO3 name from the dictionary
     * @param countryLongName the long name for the country for lookup
     * @return the ISO3 name for the country
     */
    private String getCountryIso3Name (String countryLongName) {
        // Get the country object for the long name
        JsonObject countryObject =
                countryDictionary.getJsonObject(countryLongName);
        // Extract the i18n name
        String iso3Name = null;
        if (countryObject != null) {
            iso3Name = countryObject.getString("iso3_name");
        }
        // Return findings
        return iso3Name;
    }

    /**
     * Get country code from the dictionary
     * @param countryLongName the long name for the country for lookup
     * @return the code for the country
     */
    private String getCountryCode (String countryLongName) {
        // Get the country object for the long name
        JsonObject countryObject =
                countryDictionary.getJsonObject(countryLongName);
        // Extract the code
        String code = null;
        if (countryObject != null) {
            code = countryObject.getString("code");
        }
        // Return findings
        return code;
    }

    /**
     * Get country data
     * @return compiled country/subdivision JSON data structure
     */
    private JsonObject getCountryData() {
        return countryData;
    }

    /**
     * Main entry point for commandline invocation
     * @param args 0, 1, or 2 args for country and subdivision data files
     */
    public static void main(String[] args) {
        // Optional argument to specify data file
        String countriesFilename = countryDataFilename;
        String subdivisionsFilename = subdivisionDataFilename;
        if (args.length > 0) {
            countriesFilename = args[1];
        }
        if (args.length > 1) {
            subdivisionsFilename = args[2];
        }

        // Parse the file and spit out JSON
        GetCountryInformation countryInformation =
                new GetCountryInformation(countriesFilename, subdivisionsFilename);

        // Pretty print
        StringWriter stringWriter = new StringWriter();
        Map<String,Object> properties = new HashMap<String,Object>(1);
        properties.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(properties);
        JsonWriter jsonWriter = writerFactory.createWriter(stringWriter);
        jsonWriter.writeObject(countryInformation.getCountryData());
        jsonWriter.close();

        // Write out to countryInformation.json
        try (BufferedWriter writer =
                new BufferedWriter(new FileWriter("countrydata.json"));) {
            writer.write(stringWriter.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
