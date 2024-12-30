package org.example.poll;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

import static org.example.database.LoadDB.metrics;
import static org.example.database.LoadDB.objects;

public class Scheduler extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);

    private final Map<Long,JsonObject> pollDevices = new HashMap<>(); //Will contain provisioned devices with their metrics

    public void start()
    {
        try
        {
            //If device is provisioned after I have fetched provisioned devices from database
            vertx.eventBus().<JsonObject>localConsumer(Constants.OBJECT_PROVISION, object->
            {
                var objectID = object.body().getLong("object_id");

                var metricsArray = fetchMetricData(objectID);

                var deviceMetrics = new JsonObject()
                                    .put("device", object.body())
                                    .put("metrics", metricsArray);

                pollDevices.put(objectID, deviceMetrics);
            });

            //Will fetch provisioned devices from database as soon as this verticle deploys
            getDevices()
                    .onComplete(v->
                            vertx.setPeriodic(Constants.PERIODIC_INTERVAL,id-> checkAndPreparePolling()));
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(),exception);
        }
    }

    //To fetch provisioned devices present in database
    private Future<Void> getDevices()
    {
        var promise = Promise.<Void>promise();

        try
        {
            for(var entry : objects.entrySet())
            {
                var metricsArray = fetchMetricData(entry.getKey());

                var deviceMetrics = new JsonObject()
                        .put("device",entry.getValue()) //Device contains details of object
                        .put("metrics",metricsArray); //Metrics contains data of that particular object

                pollDevices.put(entry.getKey(),deviceMetrics);
            }
            promise.complete();
        }
        catch (Exception exception)
        {
            logger.error("Error in getDevices: {}", exception.getMessage());

            promise.fail(exception.getMessage());
        }

        return promise.future();
    }

    private JsonArray fetchMetricData(Long objectID)
    {
        var metricsArray = new JsonArray();

        // Iterate through the metrics map and collect metrics for the given objectId
        for (var entry : metrics.entrySet())
        {
            var metric = entry.getValue();

            if (metric.getLong("metric_object").equals(objectID))
            {
                metricsArray.add(metric);
            }
        }
        return metricsArray;
    }

    private void checkAndPreparePolling()
    {
        if(pollDevices.isEmpty())
        {
            logger.info("No provisioned devices currently");

            return;
        }
        for (Map.Entry<Long, JsonObject> entry : pollDevices.entrySet())
        {
            var deviceMetrics = entry.getValue();

            var device = deviceMetrics.getJsonObject("device");

            var metrics = deviceMetrics.getJsonArray("metrics");

            for (int i = 0; i < metrics.size(); i++)
            {
                var metricData = metrics.getJsonObject(i);

                preparePolling(device, metricData);
            }
        }
    }

    private final Map<String, Long> activeDevices = new HashMap<>(); //THis map will contain which devices are currently undergoing polling

    private void preparePolling(JsonObject objectData, JsonObject metricData)
    {
        try
        {
            var pollerId = objectData.getString("ip") + "-" + metricData.getString("metric_group_name");

            var pollTime = metricData.getInteger("metric_poll_time") * 1000L; // Convert to milliseconds

            // Check if the poller already exists with the same interval
            if (activeDevices.containsKey(pollerId))
            {
                return;
            }

            // Schedule the polling for each device at regular intervals based on pollTime
            long timerId = vertx.setPeriodic(pollTime, id ->
            {
                vertx.eventBus().send(Constants.OBJECT_POLL, new JsonObject()
                        .put("credential.profile", objectData.getLong("credential_profile"))
                        .put("ip", objectData.getString("ip"))
                        .put("device_type", objectData.getString("device_type"))
                        .put("metric.group.name", metricData.getString("metric_group_name"))
                        .put("timestamp", System.currentTimeMillis() / 1000));

                logger.info("Polling triggered for {} at {}", objectData.getString("hostname"), objectData.getString("ip"));
            });

            // Update the active pollers map
            activeDevices.put(pollerId, timerId);
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }
}
