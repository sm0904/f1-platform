package io.f1platform.ingestion;

import io.f1platform.persistence.Migrations;
import java.nio.file.Path;
import java.util.Map;

/**
 * Command-line entry point.
 * Usage: LoadMain <data-root> <jdbc-url> <user> <password>
 * The data root is the directory holding race/, sprint/ and qualifying/ (for example data/raw/jolpica).
 * Migrations are applied first, so the command also works on an empty database.
 */
public final class LoadMain {

    private LoadMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: LoadMain <data-root> <jdbc-url> <user> <password>");
            System.exit(2);
        }
        Path root = Path.of(args[0]);
        String url = args[1];
        String user = args[2];
        String password = args[3];

        Migrations.migrate(url, user, password);
        Map<JolpicaReader.RaceKey, JolpicaReader.RaceBundle> races = JolpicaReader.read(root);
        PostgresLoader.Summary summary = new PostgresLoader(url, user, password).load(races);
        System.out.println("loaded: " + summary);
    }
}
