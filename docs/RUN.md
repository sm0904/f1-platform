# Run the full stack locally

You need Docker with Docker Compose, and about 5 GB of free disk. Java and Node are not needed on your machine.
Everything runs in containers.

When it is running, open **http://localhost:3000**. The dashboard calls the API under `/api`.

## Quickest: start from the database dump

If you do not want to fetch and load the data yourself, use the dump `f1platform.dump` (about 750 KB). It holds
all race, qualifying, and sprint results from 1950 to 2026, the Austin and Mexico City predictions, and the schema.
Run these from the project folder, after step 1 below:

```
docker compose up -d db
docker compose cp f1platform.dump db:/tmp/f1.dump
docker compose exec -T db pg_restore -U f1 -d f1platform --no-owner /tmp/f1.dump
docker compose up -d api web
```

Then open **http://localhost:3000**. The dump is not in git, because it is a binary file that goes stale when the data
changes. Put it in the project folder before the `cp` command.

## Steps

1. **Set a database password.** From the project folder:

   ```
   cp .env.example .env
   ```

   Edit `.env` and replace `change-me` with a password of your choice. `.env` is git-ignored.

2. **Fetch the race data** (about 5 minutes, needs internet access). Run these three commands:

   ```
   bash scripts/fetch_jolpica.sh race 1950 2026
   bash scripts/fetch_jolpica.sh qualifying 1994 2026
   bash scripts/fetch_jolpica.sh sprint 2021 2026
   ```

   The files go to `data/raw/` (git-ignored). Re-running is safe: existing pages are skipped.

3. **Start the database:**

   ```
   docker compose up -d db
   ```

4. **Load the data and store the Austin prediction** (one time, about 2 minutes):

   ```
   docker compose --profile load run --rm load
   ```

   This loads all completed races and qualifying and sprint results, then stores the Austin 2026
   pre-qualifying prediction. Running it again is safe for the data. The prediction step then reports that
   the run already exists, which is expected, because stored runs are never overwritten.

5. **Start the API and the dashboard:**

   ```
   docker compose up -d api web
   ```

6. **Open http://localhost:3000.**

## Useful commands

| What | Command |
|---|---|
| See what is running | `docker compose ps` |
| Read the API logs | `docker compose logs -f api` |
| Stop everything (keeps the data) | `docker compose down` |
| Stop and delete the database | `docker compose down -v` |
| Check the API directly | `curl http://localhost:3000/api/v1/predictions/2026/18` |

The admin health check runs inside the API container on port 8081. It is not published to your machine.

## Behind a corporate proxy

If builds fail with certificate errors, place the proxy's root certificate (a `.crt` file) in `certs/` and build
again. The Dockerfiles import anything in that folder into the Java and Node trust stores. The folder is
git-ignored.

## What is and is not included

- The data is Jolpica-F1 (historical results) and OpenF1 (approved for sprint qualifying). It is used for a
  personal, non-commercial project. See the licensing note in `docs/DESIGN.md`.
- The Austin prediction is pre-qualifying, so the starting grid is not known. See `docs/PREDICTION.md`.
- The API has no authentication. Run it on your own machine, not on a public network.
