package org.example.routes;
import io.vertx.core.Future;
import org.example.Main;
import org.example.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.database.QueryUtility;
import org.example.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.example.Main.*;


public class Provision
{
    private static final Logger logger = LoggerFactory.getLogger(Provision.class);

    private final HashMap<String, Integer> linuxMetrics = new HashMap<>()
    {{
        put("Linux.Device", Constants.DEVICE_POLL_INTERVAL);

        put("Linux.CPU", Constants.CPU_POLL_INTERVAL);

        put("Linux.Process", Constants.PROCESS_POLL_INTERVAL);

        put("Linux.Disk", Constants.DISK_POLL_INTERVAL);
    }};

    private final HashMap<String, Integer> snmpMetrics = new HashMap<>()
    {{
        put("SNMP.Device", Constants.SNMP_POLL_INTERVAL);

        put("SNMP.Interface", Constants.INTERFACE_POLL_INTERVAL);
    }};

    public void route(Router provisionRouter)
    {
        try
        {
            provisionRouter.post("/:id").handler(this::provision);
        }
        catch(Exception exception)
        {
            logger.error("Error in objects routing", exception);
        }
    }

    private void provision(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        if (discoveryID == null || discoveryID.isEmpty())
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put("status.code", 404)
                            .put("message", "Please enter a valid discovery ID").encodePrettily());
            return;
        }
        try
        {
            var id = Long.parseLong(discoveryID);

            var device = discoveries.get(id);

            if(!"Up".equals(device.getString("status")))
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put("status.code", 404)
                                .put("message", "Device status is down so cannot be provisioned").encodePrettily());
                return;
            }

            if(Helper.isProvisioned(device.getString("ip")))
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put("status.code", 404)
                                .put("message", "Device with this IP is already provisioned").encodePrettily());

                return;
            }

            // Proceed to insert the device into the 'objects' table
            QueryUtility.getInstance().insert(Constants.OBJECTS, new JsonObject()
                            .put("credential_profile", device.getLong("credential_profile"))
                            .put("ip", device.getString("ip"))
                            .put("port",device.getInteger("port"))
                            .put("hostname", device.getString("hostname"))
                            .put("device_type",device.getString("device_type")))
                    .map(insertedId ->
                    {
                        Helper.insertInMap(objects,insertedId,new JsonObject()
                                .put("object_id",insertedId)
                                .put("credential_profile", device.getLong("credential_profile"))
                                .put("ip", device.getString("ip"))
                                .put("port",device.getInteger("port"))
                                .put("hostname", device.getString("hostname"))
                                .put("device_type",device.getString("device_type")));

                        device.put("object_id", insertedId);

                        return device;
                    })
                    .compose(discoveryInfo ->
                    {
                        // Attaching metrics for the provisioned object
                        List<Future<Long>> metricFutures = new ArrayList<>();

                        (Objects.equals(discoveryInfo.getString("device_type"), "Linux") ? linuxMetrics : snmpMetrics).forEach((key, value) ->
                        {
                            metricFutures.add(QueryUtility.getInstance().insert(Constants.METRICS, new JsonObject()
                                    .put("metric_group_name", key)
                                    .put("metric_poll_time", value)
                                    .put("metric_object", discoveryInfo.getLong("object_id")))
                                    .map(metricID->
                                    {
                                        Helper.insertInMap(metrics,metricID,new JsonObject()
                                                .put("metric_id",metricID)
                                                .put("metric_group_name", key)
                                                .put("metric_poll_time", value)
                                                .put("metric_object", discoveryInfo.getLong("object_id")));

                                        return metricID;
                            }));
                        });

                        //If attaching any metric fails , no need to proceed further
                        return Future.all(metricFutures).map(discoveryInfo);
                    })
                    .onSuccess(result ->
                    {
                        Main.vertx.eventBus().send(Constants.OBJECT_PROVISION,new JsonObject()
                                .put("object_id",result.getLong("object_id"))
                                .put("credential_profile", result.getLong("credential_profile"))
                                .put("ip",result.getString("ip"))
                                .put("port",result.getInteger("port"))
                                .put("hostname",result.getString("hostname"))
                                .put("device_type",result.getString("device_type")));

                        context.response()
                                .setStatusCode(201)
                                .end(new JsonObject()
                                        .put("status.code",201)
                                        .put("message","Device provisioned successfully")
                                        .put("data",new JsonObject()
                                                .put("object.id", result.getLong("object_id"))).encodePrettily());
                    })
                    .onFailure(error -> context.response()
                            .setStatusCode(400)
                            .end(new JsonObject()
                                    .put("status.code",400)
                                    .put("message","Device cannot be provisioned")
                                    .put("error",error.getMessage()).encodePrettily()));
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code",500)
                            .put("message","Server error in checking if the device is up or not")
                            .put("error",exception.getCause().getMessage()).encodePrettily());
        }
    }
}
