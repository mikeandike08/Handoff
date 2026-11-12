package com.mikeandike.handoff.model;

import java.security.SecureRandom;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record Device(
    @Schema(description = "The alias of the device", requiredMode = Schema.RequiredMode.REQUIRED) String alias,
    @Schema(description = "The number of the device", requiredMode = Schema.RequiredMode.REQUIRED) int number,
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

    public Device {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be null or blank");
        }
        if (number == 0) {
            throw new IllegalArgumentException("Number cannot be zero");
        }

        alias = generateAlias();
        number = generateNumber();
    }

    public static int generateNumber() {
        // Generate a random number for the device.
        // This is a temporary solution until we implement a proper device registration process
        // Using SecureRandom to generate a random number for the device
        SecureRandom random = new SecureRandom();
        int randomInt = random.nextInt(5000); // Create a random number between 0 and 4999
        return randomInt;
    }

    public static Device generateDevice() {
        String alias = generateAlias();
        int number = generateNumber();
        return new Device(alias, number, null, null);
    }

    public static String generateAlias() {
        SecureRandom random = new SecureRandom();
        String alias = ADJECTIVES.get(random.nextInt(ADJECTIVES.size())) + " " + NOUNS.get(random.nextInt(NOUNS.size()));
        return alias;
    }
}

