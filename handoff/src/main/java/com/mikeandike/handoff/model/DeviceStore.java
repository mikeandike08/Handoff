package com.mikeandike.handoff.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Properties;

import org.springframework.stereotype.Component;

import com.mikeandike.handoff.config.HandoffConfig;

@Component 
public class DeviceStore {

    private static final String CONFIG_VERSION = "1";

    private Device cachedDevice;

     // Big list of random names, used to generate a random alias
    private static final List<String> ADJECTIVES = List.of(
        "Caffeinated", "Reluctant", "Suspicious", "Damp", "Overclocked",
        "Feral", "Unbothered", "Crispy", "Nocturnal", "Soggy",
        "Majestic", "Cursed", "Smug", "Lukewarm", "Disheveled",
        "Rogue", "Sleepy", "Chaotic", "Polite", "Rusty",
        "Gelatinous", "Loud", "Forgotten", "Spicy", "Anxious",
        "Velvet", "Stubborn", "Haunted", "Glossy", "Confused",
        "Bootleg", "Tactical", "Wobbly", "Pristine", "Grumpy",
        "Infinite", "Discount", "Radioactive", "Formal", "Lopsided",
        "Immortal", "Humble", "Unhinged", "Brisk", "Secondhand",
        "Dizzy", "Aggressive", "Serene", "Crunchy", "Theoretical"
    );

    private static final List<String> NOUNS = List.of(
        "Walrus", "Toaster", "Pigeon", "Blender", "Raccoon",
        "Lighthouse", "Hamster", "Spatula", "Otter", "Submarine",
        "Goose", "Mailbox", "Badger", "Trombone", "Llama",
        "Thermostat", "Penguin", "Accordion", "Moose", "Kettle",
        "Ferret", "Waffle", "Narwhal", "Stapler", "Pelican",
        "Hovercraft", "Gecko", "Doorknob", "Manatee", "Bagel",
        "Lemur", "Microwave", "Yak", "Harpoon", "Puffin",
        "Turnip", "Wombat", "Zeppelin", "Mongoose", "Crouton",
        "Capybara", "Periscope", "Heron", "Sundial", "Marmot",
        "Igloo", "Ostrich", "Tugboat", "Platypus", "Lantern"
    );

    // Used to create an alphanumeric ID
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final Path configPath;

    public DeviceStore(HandoffConfig config){
        this.configPath = config.getConfigPath();
        this.cachedDevice = loadOrCreateDevice();
    }

    public Device getDevice(){
        return cachedDevice;
    }

    /**
     * Generates a device value using helper functions, writes to a config file on local to save alias, id, and number
     * @return Device Object
     */
    private Device loadOrCreateDevice() {
        Device loaded = tryLoad();
        if (loaded != null) {
            return loaded;
        }
        return generateAndSave();
    }

    private Device tryLoad(){
        if (!Files.exists(configPath)){
            return null;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)){
            props.load(in);
        } catch (IOException e){
            return null;
        }

        if (!CONFIG_VERSION.equals(props.getProperty("version"))){
            return null;
        }

        String alias = props.getProperty("alias");
        String id = props.getProperty("id");
        String numberStr = props.getProperty("number");

        if (alias == null || id == null || numberStr == null) {
            return null;
        }

        try {
            return new Device(alias, Integer.parseInt(numberStr), id, null, null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Device generateAndSave() {
        Device device = new Device(generateAlias(), generateNumber(), generateID(), null, null);

        Properties props = new Properties();
        props.setProperty("version", CONFIG_VERSION);
        props.setProperty("alias", device.alias());
        props.setProperty("number", String.valueOf(device.number()));
        props.setProperty("id", device.id());

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "Handoff Device Config");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config file", e);
        }

        return device;
    }

     /**
     * generates a random number between 1, 9999
     * @return Int
     */
    public static int generateNumber() {
        // Generate a random number for the device.
        // This is a temporary solution until we implement a proper device registration process
        // Using SecureRandom to generate a random number for the device
        SecureRandom random = new SecureRandom();
        int randomInt = random.nextInt(5000); // Create a random number between 0 and 4999
        if (randomInt == 0)
            randomInt += 1; // 0 can cause a crash, keep it above 0
        return randomInt;
    }

    /**
     * Used to get our machines local ID value set when we first created the device
     * @return String | ""
     */
    public String getIDFromLocal(){
        return loadOrCreateDevice().id();
    }

    /**
     * Creates a alphanumeric ID with a length of 6
     * @return String
     */
    public static String generateID(){
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++){
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }

        return sb.toString();
    }

    /**
     * Creates an alias by mixing a random Adjective and Noun together
     * @return String alias
     */
    public static String generateAlias() {
        SecureRandom random = new SecureRandom();
        String alias = ADJECTIVES.get(random.nextInt(ADJECTIVES.size())) + " " + NOUNS.get(random.nextInt(NOUNS.size()));
        return alias;
    }
}
