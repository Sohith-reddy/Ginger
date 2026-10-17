package dev.ginger.ops;

import dev.ginger.repository.Repository;

import java.io.IOException;
import java.util.Map;

public final class Identity {

    private Identity() {
    }

    public static String author(Repository repo, Map<String, String> env) throws IOException {
        Map<String, String> config = repo.readConfig();
        String name = env.getOrDefault("GINGER_AUTHOR_NAME",
                config.getOrDefault("user.name", System.getProperty("user.name", "unknown")));
        String email = env.getOrDefault("GINGER_AUTHOR_EMAIL", config.get("user.email"));
        return email == null || email.isBlank() ? name : name + " <" + email + ">";
    }
}
