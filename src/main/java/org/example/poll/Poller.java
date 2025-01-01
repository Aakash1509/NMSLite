package org.example.poll;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

import static org.example.Main.credentials;

public class Poller extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(Poller.class);

    private static final WorkerExecutor poll = Main.vertx.createSharedWorkerExecutor("polling",10,60, TimeUnit.SECONDS);

    public void start()
    {
        try
        {
            vertx.eventBus().<JsonObject>localConsumer(Constants.OBJECT_POLL, message ->
            {
                var pollingData = message.body();

                //Fetching credentials of qualified devices
                var credential = credentials.get(pollingData.getLong("credential.profile"));

                //Fetching device details through credential profile ID as that details will also be needed

                pollingData.put("profile.protocol",credential.getString("profile_protocol"))
                        .put("user.name",credential.getString("user_name"))
                        .put("user.password",credential.getString("user_password"))
                        .put("community",credential.getString("community"))
                        .put("version",credential.getString("version"));

                var timestamp = pollingData.getString("timestamp");

                //As I don't require these 2 values in plugin , so I am separately passing the timestamp

                pollingData.remove("credential.profile");

                pollingData.remove("timestamp");

                startPoll(pollingData,timestamp);
            });
        }
        catch (Exception exception)
        {
            logger.error("Exception occurred");
        }
    }

    private void startPoll(JsonObject pollingData, String timestamp)
    {
        logger.info("Started polling of ip: {}",pollingData.getString("ip"));

        poll.executeBlocking(promise ->
        {
            try
            {
                // Start the process
                pollingData.put(Constants.EVENT_TYPE, Constants.POLL);

                Process process = new ProcessBuilder(Constants.PLUGIN_PATH, pollingData.encode())
                        .redirectErrorStream(true).start();

                // Capture output from the Go executable
                var output = new String(process.getInputStream().readAllBytes());

                if (process.waitFor(60, TimeUnit.SECONDS))
                {
                    if (process.exitValue() != 0)
                    {
                        logger.error("Go executable failed with error: {}", output.trim());

                        promise.fail(new RuntimeException("Polling failed"));
                    }
                    else
                    {
                        logger.info("Metrics collected: {}", output.trim());

                        var result = new JsonObject(output.trim());

                        // Send the result over the event bus to File writer verticle
                        vertx.eventBus().send(
                                Constants.FILE_WRITE,
                                new JsonObject()
                                        .put("metrics", result)
                                        .put("timestamp", timestamp));

                        promise.complete(result);
                    }
                }
                else
                {
                    // Timeout occurred
                    process.destroy();
                }
            }
            catch (Exception exception)
            {
                logger.error("Failed to execute Go executable", exception);

                promise.fail(exception);
            }
        }, false, res ->
        {
            if (res.succeeded())
            {
                logger.info("Metrics fetched successfully for ip: {}", pollingData.getString("ip"));
            }
            else
            {
                logger.error("Failed to fetch metrics for ip: {}", pollingData.getString("ip"));
            }
        });
    }

}
