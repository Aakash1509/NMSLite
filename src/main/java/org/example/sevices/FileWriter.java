package org.example.sevices;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWriter extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWriter.class);

    @Override
    public void start()
    {
        vertx.eventBus().<JsonObject>localConsumer(Constants.FILE_WRITE, message ->
        {
            vertx.executeBlocking(promise ->
            {
                try
                {
                    var metricData = message.body().getJsonObject("metrics");

                    var timestamp = message.body().getString("timestamp");

                    var context = metricData.getJsonObject("result");

                    var ip = metricData.getString("ip");

                    var filePath = Constants.BASE_DIRECTORY + "/" + String.format("%s.txt", timestamp);

                    writeToFile(filePath,ip,context);

                    promise.complete();
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error during file operation: {}", exception.getMessage(), exception);

                    promise.fail(exception);
                }
            });
        });
    }

    private void writeToFile(String filePath, String ip, JsonObject metrics)
    {
        try
        {
            vertx.fileSystem().open(filePath,new OpenOptions().setAppend(true).setCreate(true))
                    .onSuccess(file->
                    {
                        if(file.sizeBlocking() == 0)
                        {
                            //New file will be created
                            file.write(Buffer.buffer(new JsonObject()
                                            .put("ip",ip)
                                            .put("result",metrics)
                                            .encodePrettily()))
                                    .onComplete(writeResult -> file.close());
                        }
                        else
                        {
                            //Appending in file
                            file.write(Buffer.buffer("---\n" + new JsonObject()
                                            .put("ip", ip)
                                            .put("result", metrics)
                                            .encodePrettily()))
                                    .onComplete(writeResult -> file.close());
                        }
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error during file operation: {}", exception.getMessage(), exception);

            throw exception;
        }
    }
}
