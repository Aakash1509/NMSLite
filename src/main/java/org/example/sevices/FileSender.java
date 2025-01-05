package org.example.sevices;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.nio.file.Paths;
import java.util.List;

public class FileSender extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSender.class);

    private ZMQ.Socket socket;

    private ZContext context;

    public void start(Promise<Void> startPromise)
    {
        try
        {
            context = new ZContext();

            socket = context.createSocket(ZMQ.PUSH);

            socket.connect("tcp://" + Constants.IP + ":" + Constants.ZMQ_PORT);

            // Periodically process files
            vertx.setPeriodic(Constants.DATABASE_INTERVAL, id -> processFiles());

            startPromise.complete();

        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to initialize ZMQ context: {}", exception.getMessage(), exception);

            startPromise.fail(exception);
        }
    }

    private void processFiles()
    {
        vertx.<List<String>>executeBlocking(promise ->
        {
            try
            {
                promise.complete(vertx.fileSystem().readDirBlocking(Constants.BASE_DIRECTORY));
            }
            catch (Exception exception)
            {
                promise.fail(exception);
            }
        }).onSuccess(files ->
        {
            files.forEach(this::sendFile);

        }).onFailure(error ->
        {
            LOGGER.error("Error occurred while reading directory: {}", error.getMessage());
        });
    }

    private void sendFile(String filePath)
    {
        vertx.<String>executeBlocking(promise ->
        {
            try
            {
                promise.complete(vertx.fileSystem().readFileBlocking(filePath).toString());
            }
            catch (Exception exception)
            {
                promise.fail(exception);
            }
        }).compose(fileContent ->
        {
            // Extract file name
            var fileName = Paths.get(filePath).getFileName().toString();

            var fileParts = fileContent.split("---\n");

            for (var part : fileParts)
            {
                if (!part.trim().isEmpty())
                {
                    var message = new JsonObject()
                            .put("filename", fileName)
                            .put("content", part);

                    socket.send(message.encode(), 0);
                }
            }

            // Delete file after sending
            return vertx.fileSystem().delete(filePath);
        });
    }

    @Override
    public void stop()
    {
        if (socket != null)
        {
            socket.close();
        }
        if (context != null)
        {
            context.close();
        }
    }
}
