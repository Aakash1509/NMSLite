package org.example.poll;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.example.Main.metrics;
import static org.example.Main.objects;

public class Scheduler extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Scheduler.class);

    private final Map<Long,Long> pollDevices = new HashMap<>(); //This map will contain metric_id and the next polling time

    public void start()
    {
        try
        {
            //If device is provisioned after I have fetched provisioned devices from database
            vertx.eventBus().<JsonObject>localConsumer(Constants.OBJECT_PROVISION, object->
            {
                for(var entry : metrics.entrySet())
                {
                    if(Objects.equals(entry.getValue().getLong("metric_object"), object.body().getLong("object_id")))
                    {
                        pollDevices.put(entry.getKey(), System.currentTimeMillis()+entry.getValue().getInteger("metric_poll_time")*1000L);
                    }
                }
            });

            //Will fetch provisioned devices from database as soon as this verticle deploys
            getDevices()
                    .onComplete(v->
                            vertx.setPeriodic(Constants.PERIODIC_INTERVAL,id-> checkAndPreparePolling()));
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage(),exception);
        }
    }

    //To fetch provisioned devices present in database
    private Future<Void> getDevices()
    {
        var promise = Promise.<Void>promise();

        try
        {
            for(var entry : metrics.entrySet())
            {
                pollDevices.put(entry.getKey(), System.currentTimeMillis()+entry.getValue().getInteger("metric_poll_time")*1000L);
            }
            promise.complete();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in getting devices from database: {}", exception.getMessage());

            promise.fail(exception.getMessage());
        }
        return promise.future();
    }

    private void checkAndPreparePolling()
    {
        if(pollDevices.isEmpty())
        {
            LOGGER.info("No provisioned devices currently");

            return;
        }

        var currentTime = System.currentTimeMillis();

        for(var entry : pollDevices.entrySet())
        {
            if(currentTime >= entry.getValue())
            {
                preparePolling(objects.get(metrics.get(entry.getKey()).getLong("metric_object")),metrics.get(entry.getKey()),currentTime);

                pollDevices.put(entry.getKey(),entry.getValue()+metrics.get(entry.getKey()).getInteger("metric_poll_time")*1000L);
            }
        }
    }

    private void preparePolling(JsonObject objectData, JsonObject metricData, long currentTime)
    {
        try
        {
            vertx.eventBus().send(Constants.OBJECT_POLL, new JsonObject()
                    .put("credential.profile", objectData.getLong("credential_profile"))
                    .put("ip", objectData.getString("ip"))
                    .put("port",objectData.getInteger("port"))
                    .put("device_type", objectData.getString("device_type"))
                    .put("metric.group.name", metricData.getString("metric_group_name"))
                    .put("timestamp", currentTime / 1000));

            LOGGER.info("Polling triggered for {} at {}", objectData.getString("hostname"), objectData.getString("ip"));
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage(), exception);
        }
    }
}
