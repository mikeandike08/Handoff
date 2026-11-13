package com.mikeandike.handoff.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.InputStream;
import java.util.Properties;
import java.io.OutputStream;

import java.io.IOException;

public record Device(
    @Schema(description = "The alias of the device", requiredMode = Schema.RequiredMode.REQUIRED) String alias,
    @Schema(description = "The number of the device", requiredMode = Schema.RequiredMode.REQUIRED) int number,
    @Schema(description = "The device ID", requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(description = "The IP address of the device") String ip,
    @Schema(description = "The port of the device") String port
) {
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

    public Device {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be null or blank");
        }
        if (number == 0) {
            throw new IllegalArgumentException("Number cannot be zero");
        }
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
     * Generates a device value using helper functions, writes to a config file on local to save alias, id, and number
     * @return Device Object
     */
    public static Device generateDevice() {
        // Check if we already have a device name in a local config file, if so, use that name. 
        // Otherwise, generate a new name and save it to the config file.
        Path configPath = Path.of(System.getProperty("user.home"), ".handoff", "config.properties");

        String alias;
        String id;
        int number;

        System.out.println("CHECK: " + configPath.toAbsolutePath() + " exists=" + Files.exists(configPath));

        // Write to the path
        if (Files.exists(configPath)){
            System.out.println("Config file exists, loading config...");
            // load config
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configPath)){
                props.load(in);
            } catch (Exception e){
                throw new RuntimeException("Failed to load config file", e);
            }
            alias = props.getProperty("alias");
            number = Integer.parseInt(props.getProperty("number"));
            id = props.getProperty("id");
        } else{
            //generate new config
            alias = generateAlias();
            number = generateNumber();
            id = generateID();

            try {
                Files.createDirectories(configPath.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create config directory", e);
            }
            Properties props = new Properties();
            props.setProperty("alias", alias);
            props.setProperty("number", String.valueOf(number));
            props.setProperty("id", id);
            try (OutputStream out = Files.newOutputStream(configPath)){
                props.store(out, "Handoff Device Config");
            } catch (Exception e){
                throw new RuntimeException("Failed to save config file", e);
            }
        }

        return new Device(alias, number, id, null, null);
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

