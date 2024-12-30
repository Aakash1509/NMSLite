package org.example;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.example.database.QueryUtility;
import org.example.poll.Poller;
import org.example.poll.Scheduler;
import org.example.routes.Server;
import org.example.sevices.FileSender;
import org.example.sevices.FileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Main
{
    public static final Vertx vertx = Vertx.vertx();

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final Map<Long, JsonObject> discoveries = new HashMap<>();

    public static final Map<Long, JsonObject> credentials = new HashMap<>();

    public static final Map<Long, JsonObject> objects = new HashMap<>();

    public static final Map<Long, JsonObject> metrics = new HashMap<>();


    public static void main(String[] args)
    {
            init()

                .compose(result->vertx.deployVerticle(new Server()))

                .compose(result -> vertx.deployVerticle(new Scheduler()))

                .compose(result -> vertx.deployVerticle(new Poller()))

                .compose(result -> vertx.deployVerticle(new FileWriter()))

                .compose(result-> vertx.deployVerticle(new FileSender()))

                .onComplete(result ->
                {
                    if (result.succeeded())
                    {
                        logger.info("All verticles deployed successfully");
                    }
                    else
                    {
                        logger.error("Error deploying verticles", result.cause());
                    }
                });
    }

    private static CompositeFuture init()
    {
        return Future.all(
                        loadTable(Constants.DISCOVERIES, discoveries, "discovery_id"),

                        loadTable(Constants.CREDENTIALS, credentials, "profile_id"),

                        loadTable(Constants.OBJECTS, objects, "object_id"),

                        loadTable(Constants.METRICS, metrics, "metric_id")

                ).onSuccess(v -> logger.info("All tables loaded"))

                .onFailure(error -> logger.error("Database initialization failed", error));
    }

    private static Future<Void> loadTable(String tableName, Map<Long, JsonObject> map, String id)
    {
        var promise = Promise.<Void>promise();

        QueryUtility.getInstance().getAll(tableName).onComplete(result ->
        {
            if (result.succeeded())
            {
                var rows = result.result();

                for (int i = 0; i < rows.size(); i++)
                {
                    var object = rows.getJsonObject(i);

                    map.put(object.getLong(id), object);
                }

                promise.complete();
            }
            else
            {
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

}