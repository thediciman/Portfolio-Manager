package dici.portfolio.tasks;

import dici.portfolio.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.LocalDate;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class TaskFetchPrices implements Callable<Map<String, Double>> {

    private static final Logger logger = LogManager.getLogger(TaskFetchPrices.class);

    private static final String LATEST_PRICE_JSON_PROPERTY = "latestPrice";

    private static final String API_REQUEST_URL_FORMAT = "https://cloud.iexapis.com/stable/stock/%s/quote?token=%s";

    private final List<String> tickers;
    private final String apiKey;

    public TaskFetchPrices(final List<String> tickers, final String apiKey) {
        this.tickers = tickers;
        this.apiKey = apiKey;
    }

    @Override
    public Map<String, Double> call() {
        try {
            logger.info("Started thread...");

            final Map<String, Double> pricesMap = new HashMap<>(tickers.size());
            final LocalDate currentDate = LocalDate.now();

            final String responsesDirectoryPath = String.format("%s/%s", Utils.RESPONSES_DIRECTORY, currentDate);

            synchronized (TaskFetchPrices.class) {
                final File responsesDirectory = new File(responsesDirectoryPath);
                if (!responsesDirectory.exists()) {
                    final boolean directoryWasCreated = responsesDirectory.mkdir();
                    if (directoryWasCreated) {
                        logger.info(String.format("Responses directory (%s) missing, it is now created.", responsesDirectoryPath));
                    }
                }
            }

            for (final String ticker : tickers) {
                logger.info(String.format("Fetching %s", ticker));

                final String requestUrl = String.format(API_REQUEST_URL_FORMAT, ticker, apiKey);

                final HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create(requestUrl))
                    .build();

                logger.info(String.format("Making request at URL: %s", requestUrl));

                final HttpResponse<String> response;
                try {
                    response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                } catch (final Exception exception) {
                    logger.info(String.format("An exception occurred when making request for ticker %s: %s", ticker, exception));
                    continue;
                }

                final String responseBody = response.body();
                logger.info(String.format("Received response: %s", responseBody));

                final JSONObject jsonResponse;
                try {
                    jsonResponse = new JSONObject(responseBody);
                } catch (final Exception exception) {
                    logger.info(String.format("An exception occurred when parsing the JSON for ticker %s: %s", ticker, exception));
                    continue;
                }

                final double price;
                try {
                    price = Double.parseDouble(jsonResponse.get(LATEST_PRICE_JSON_PROPERTY).toString());
                } catch (final Exception exception) {
                    logger.info(String.format("An exception occurred when trying to fetch the price for ticker %s from the JSON: %s", ticker, exception));
                    continue;
                }

                pricesMap.put(ticker, price);

                Files.write(Path.of(String.format("%s/%s.json", responsesDirectoryPath, ticker)), Collections.singleton(response.body()));
            }

            return pricesMap;
        } catch (final Exception exception) {
            logger.info(String.format("An exception occurred: %s", exception));
        }

        return null;
    }

}