package org.example.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class LoadDB extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(LoadDB.class);

    public static final Map<Long, JsonObject> discoveries = new HashMap<>();

    public static final Map<Long, JsonObject> credentials = new HashMap<>();

    public static final Map<Long, JsonObject> objects = new HashMap<>();

    public static final Map<Long, JsonObject> metrics = new HashMap<>();

    public void start(Promise<Void> startPromise)
    {
        loadTables()
                .onSuccess(result ->
                {
                    logger.info("All tables loaded");

                    startPromise.complete();
                })
                .onFailure(error->
                {
                    startPromise.fail("Failed to load databases");
                });
    }

    private Future<Void> loadTables()
    {
        return loadTable(Constants.DISCOVERIES, discoveries, "discovery_id")
                .compose(v -> loadTable(Constants.CREDENTIALS, credentials, "profile_id"))
                .compose(v -> loadTable(Constants.OBJECTS, objects, "object_id"))
                .compose(v-> loadTable(Constants.METRICS,metrics,"metric_id"));
    }

    private Future<Void> loadTable(String tableName, Map<Long, JsonObject> map, String id)
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
