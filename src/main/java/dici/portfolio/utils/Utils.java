package dici.portfolio.utils;

import dici.portfolio.entities.Portfolio;
import dici.portfolio.entities.Position;
import dici.portfolio.tasks.TaskFetchPrices;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Utils {

    public static final String API_TOKEN = "pk_d6ffb449db4d4d2db67f321614aa28dd";

    public static final int DEFAULT_NUMBER_OF_THREADS = 20;

    public static final String RESPONSES_DIRECTORY = "./responses";

    private static final Logger logger = LogManager.getLogger(Utils.class);

    private static final String TARGET_EMAIL_ADDRESS = "dicianuioanalexandru@gmail.com";

    private static final String SENDER_EMAIL_ADDRESS = "djlumov@gmail.com";
    private static final String SENDER_EMAIL_PASSWORD = "pnjrfbimkzsnhktg";

    public static Map<String, Double> getActualTickersPrices(final List<String> tickers) throws ExecutionException, InterruptedException {
        int numberOfThreads = Math.min(DEFAULT_NUMBER_OF_THREADS, tickers.size());

        final List<List<String>> tickersSublists = new ArrayList<>();

        final int tickersPerThread = tickers.size() / numberOfThreads;

        for (int i = 0; i < numberOfThreads - 1; ++i) {
            tickersSublists.add(tickers.subList(i * tickersPerThread, (i + 1) * tickersPerThread));
        }

        tickersSublists.add(tickers.subList((numberOfThreads - 1) * tickersPerThread, tickers.size()));

        final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        try {
            final List<Future<Map<String, Double>>> tickerPriceFutures = new ArrayList<>();
            for (int i = 0; i < numberOfThreads; ++i) {
                tickerPriceFutures.add(
                    executorService.submit(new TaskFetchPrices(tickersSublists.get(i), API_TOKEN))
                );
            }

            final Map<String, Double> tickersPrices = new HashMap<>();

            for (final Future<Map<String, Double>> future : tickerPriceFutures) {
                final Map<String, Double> threadResultMap = future.get();

                if (threadResultMap == null) {
                    logger.info("The thread returned a null map, will ignore it.");
                } else {
                    tickersPrices.putAll(future.get());
                }
            }

            logger.info(String.format("Loaded prices for %d tickers: ", tickersPrices.size()));

            tickersPrices.forEach((ticker, price) -> logger.info(String.format("%s: %f", ticker, price)));

            return tickersPrices;
        } catch (final Exception exception) {
            logger.info(String.format("Encountered exception while fetching the actual ticker prices: %s", exception));
            throw exception;
        } finally {
            executorService.shutdown();
        }
    }

    public static void printPortfolioPositionsEvolution(final Portfolio portfolio, final Map<String, Double> tickersPrices) {
        final List<Position> openPositions = portfolio.getOpenPositions();

        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("Open positions evolution: ").append("\n");

        openPositions
            .stream()
            .sorted((firstPosition, secondPosition) -> {
                double firstPositionCurrentTickerPrice = tickersPrices.get(firstPosition.getTicker());
                double secondPositionCurrentTickerPrice = tickersPrices.get(secondPosition.getTicker());
                double firstPositionRelativePriceChange = (firstPositionCurrentTickerPrice / firstPosition.getPrice() - 1) * 100;
                double secondPositionRelativePriceChange = (secondPositionCurrentTickerPrice / secondPosition.getPrice() - 1) * 100;
                return Double.compare(secondPositionRelativePriceChange, firstPositionRelativePriceChange);
            })
            .forEach(position -> {
                double currentTickerPrice = tickersPrices.get(position.getTicker());
                double absolutePriceChange = currentTickerPrice - position.getPrice();
                double relativePriceChange = (currentTickerPrice / position.getPrice() - 1) * 100;
                stringBuilder.append(String.format("%s, change: %.3f (USD) (%.3f%%)", position, absolutePriceChange, relativePriceChange));
            });

        logger.info(String.format("%s", stringBuilder.toString()));

        double initialPortfolioValue = portfolio.getInitialValue();
        double currentPortfolioValue = portfolio.getCurrentValue(tickersPrices);

        double absolutePortfolioValueChange = currentPortfolioValue - initialPortfolioValue;
        double relativePortfolioValueChange = (currentPortfolioValue / initialPortfolioValue - 1) * 100;

        logger.info(String.format("Current portfolio value: %.3f (USD) (%s), change: %.3f (USD) (%.3f%%)", currentPortfolioValue, DateTime.now(), absolutePortfolioValueChange, relativePortfolioValueChange));
    }

    public static String generateHtmlReportForPortfolio(final Portfolio portfolio, final Map<String, Double> tickersPrices) {
        final StringBuilder stringBuilder = new StringBuilder();

        final LocalDate currentDate = LocalDate.now();

        stringBuilder.append(
            """
                <!DOCTYPE html>
                <html>

                <head>
                                
                """
        );

        stringBuilder.append(String.format("\t<title>EOD %s REPORT</title>", currentDate));

        stringBuilder.append(
            """

                \t<link rel="icon" type="image/png" href="https://i.ibb.co/pybQDCD/HEADER-1-32x32-2.png">

                \t<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.6.0/jquery.min.js"></script>

                \t<style>
                \t\t#main-paragraph {
                \t\t\ttext-align: center;
                \t\t\tmargin-top: 2.5%;
                \t\t\tfont-size: 24px;
                \t\t}
                \t\t\s
                \t\t.bottom-paragraph {
                \t\t\ttext-align: center;
                \t\t\tmargin-top: 1.5%;
                \t\t\tfont-size: 24px;
                \t\t\tfont-weight: 400;
                \t\t}
                \t\t\s
                \t\t#portfolio-table {
                \t\t\tmargin: auto;
                \t\t\tmargin-top: 2.5%;
                \t\t\twidth: 75%;
                \t\t}
                \t\t\s
                \t\t#portfolio-table,
                \t\t#portfolio-table th,
                \t\t#portfolio-table td {
                \t\t\tborder: 1px solid black;
                \t\t\tborder-collapse: collapse;
                \t\t\ttext-align: center;
                \t\t}
                \t\t\s
                \t\t#portfolio-table th {
                \t\t\tuser-select: none;
                \t\t}
                \t\t\s
                \t\t.positive-return {
                \t\t\tbackground-color: greenyellow;
                \t\t}
                \t\t\s
                \t\t.negative-return {
                \t\t\tbackground-color: crimson;
                \t\t}
                \t</style>

                \t<script>
                \t\tlet columnsParsers = [
                \t\t\tticker => [ticker, new String(ticker)],
                \t\t\tdate => [date, new Date(Date.parse(new String(date)))],
                \t\t\tquantity => [quantity, parseFloat(new String(quantity))],
                \t\t\tprice => [price, parseFloat(new String(price).slice(0, -6))],
                \t\t\tprice => [price, parseFloat(new String(price).slice(0, -6))],
                \t\t\tprice => [price, parseFloat(new String(price).slice(0, -6))],
                \t\t\tpercentage => [percentage, parseFloat(new String(percentage).slice(0, -1))]
                \t\t];

                \t\tlet columnComparators = [
                \t\t\t(a, b) => a.localeCompare(b),
                \t\t\t(a, b) => a - b,
                \t\t\t(a, b) => a - b,
                \t\t\t(a, b) => a - b,
                \t\t\t(a, b) => a - b,
                \t\t\t(a, b) => a - b,
                \t\t\t(a, b) => a - b
                \t\t];

                \t\tfunction handleHeaderClick(event) {
                \t\t\tlet headerElement = event.currentTarget;
                \t\t\tlet headerIndex = headerElement.cellIndex;

                \t\t\tlet tableElement = document.getElementById("portfolio-table");

                \t\t\tlet rows = [];

                \t\t\tfor (let rowIndex = 1; rowIndex < tableElement.rows.length; ++rowIndex) {
                \t\t\t\tlet rowCells = [];
                \t\t\t\tfor (let cellIndex = 0; cellIndex < 7; ++cellIndex) {
                \t\t\t\t\tlet cellValue = tableElement.rows[rowIndex].cells[cellIndex].innerHTML;
                \t\t\t\t\trowCells.push(columnsParsers[cellIndex](cellValue));
                \t\t\t\t}
                \t\t\t\trows.push(rowCells);
                \t\t\t}

                \t\t\tif (headerElement.hasBeenClicked) {
                \t\t\t\trows.sort((firstRow, secondRow) => columnComparators[headerIndex](secondRow[headerIndex][1], firstRow[headerIndex][1]));
                \t\t\t\theaderElement.hasBeenClicked = false;
                \t\t\t} else {
                \t\t\t\trows.sort((firstRow, secondRow) => columnComparators[headerIndex](firstRow[headerIndex][1], secondRow[headerIndex][1]));
                \t\t\t\theaderElement.hasBeenClicked = true;
                \t\t\t}

                \t\t\tfor (let rowIndex = 1; rowIndex < tableElement.rows.length; ++rowIndex) {
                \t\t\t\tfor (let cellIndex = 0; cellIndex < 7; ++cellIndex) {
                \t\t\t\t\ttableElement.rows[rowIndex].cells[cellIndex].innerHTML = rows[rowIndex - 1][cellIndex][0];
                \t\t\t\t}
                \t\t\t\tlet relativeChange = rows[rowIndex - 1][6][1];
                \t\t\t\tif (relativeChange > 0) {
                \t\t\t\t\ttableElement.rows[rowIndex].setAttribute("class", "positive-return");
                \t\t\t\t} else if (relativeChange < 0) {
                \t\t\t\t\ttableElement.rows[rowIndex].setAttribute("class", "negative-return");
                \t\t\t\t} else {
                \t\t\t\t\ttableElement.rows[rowIndex].setAttribute("class", "");
                \t\t\t\t}
                \t\t\t}
                \t\t}

                \t\t$(document).ready(() => {
                \t\t\t$("th").click(handleHeaderClick);
                \t\t});
                \t</script>

                </head>

                <body>

                \t<p id='main-paragraph'>Here's how your portfolio looks so far!</p>

                \t<table id='portfolio-table'>
                \t\t<tr>
                \t\t\t<th>Ticker</th>
                \t\t\t<th>Purchase Date</th>
                \t\t\t<th>Quantity</th>
                \t\t\t<th>Purchase Price</th>
                \t\t\t<th>Actual Price</th>
                \t\t\t<th>Absolute Change</th>
                \t\t\t<th>Relative Change</th>
                \t\t</tr>
                """
        );

        portfolio
            .getOpenPositions()
            .stream()
            .sorted((firstPosition, secondPosition) -> {
                double firstPositionCurrentTickerPrice = tickersPrices.get(firstPosition.getTicker());
                double secondPositionCurrentTickerPrice = tickersPrices.get(secondPosition.getTicker());
                double firstPositionRelativePriceChange = (firstPositionCurrentTickerPrice / firstPosition.getPrice() - 1) * 100;
                double secondPositionRelativePriceChange = (secondPositionCurrentTickerPrice / secondPosition.getPrice() - 1) * 100;
                return Double.compare(secondPositionRelativePriceChange, firstPositionRelativePriceChange);
            })
            .forEach(position -> {
                double currentTickerPrice = tickersPrices.get(position.getTicker());
                double absolutePriceChange = currentTickerPrice - position.getPrice();
                double relativePriceChange = (currentTickerPrice / position.getPrice() - 1) * 100;

                if (relativePriceChange > 0) {
                    stringBuilder.append("\t\t<tr class=\"positive-return\">\n");
                } else if (relativePriceChange < 0) {
                    stringBuilder.append("\t\t<tr class=\"negative-return\">\n");
                } else {
                    stringBuilder.append("\t\t<tr>\n");
                }

                stringBuilder.append("\t\t\t<td>");
                stringBuilder.append(position.getTicker());
                stringBuilder.append("</td>\n");

                stringBuilder.append("\t\t\t<td>");
                stringBuilder.append(position.getPurchaseDate());
                stringBuilder.append("</td>\n");

                stringBuilder.append("\t\t\t<td>");
                stringBuilder.append(String.format("%.5f", position.getQuantity()));
                stringBuilder.append("</td>\n");

                stringBuilder.append("\t\t\t<td>");
                stringBuilder.append(String.format("%.3f", position.getPrice()));
                stringBuilder.append(" (USD)");
                stringBuilder.append("</td>\n");

                stringBuilder.append("\t\t\t<td>");
                stringBuilder.append(String.format("%.3f", currentTickerPrice));
                stringBuilder.append(" (USD)");
                stringBuilder.append("</td>\n");

                stringBuilder.append("\t\t\t<td>");
                stringBuilder.append(String.format("%.3f", absolutePriceChange));
                stringBuilder.append(" (USD)");
                stringBuilder.append("</td>\n");

                stringBuilder.append("\t\t\t<td>");
                stringBuilder.append(String.format("%.3f", relativePriceChange));
                stringBuilder.append("%");
                stringBuilder.append("</td>\n");

                stringBuilder.append("\t\t</tr>\n");
            });

        stringBuilder.append("\t</table>\n\n");

        double initialPortfolioValue = portfolio.getInitialValue();
        double currentPortfolioValue = portfolio.getCurrentValue(tickersPrices);

        double absolutePortfolioValueChange = currentPortfolioValue - initialPortfolioValue;
        double relativePortfolioValueChange = (currentPortfolioValue / initialPortfolioValue - 1) * 100;

        stringBuilder.append(String.format("\t<p class=\"bottom-paragraph\">Total portfolio value: %.3f (USD)</p>\n", currentPortfolioValue));
        stringBuilder.append(String.format("\t<p class=\"bottom-paragraph\">Portfolio value absolute change: %.3f (USD)</p>\n", absolutePortfolioValueChange));
        stringBuilder.append(String.format("\t<p class=\"bottom-paragraph\">Portfolio value relative change: %.3f%%</p>\n", relativePortfolioValueChange));

        stringBuilder.append(
            """

                </body>

                </html>"""
        );

        return stringBuilder.toString();
    }

    public static void sendEmailWithPortfolioReport(final Portfolio portfolio, final Map<String, Double> tickersPrices) {
        final String htmlReport = Utils.generateHtmlReportForPortfolio(portfolio, tickersPrices);

        final String host = "smtp.gmail.com";

        final Properties properties = System.getProperties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.auth", "true");

        final Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL_ADDRESS, SENDER_EMAIL_PASSWORD);
            }
        });

        try {
            final MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL_ADDRESS));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(TARGET_EMAIL_ADDRESS));

            final BodyPart textBodyPart = new MimeBodyPart();
            textBodyPart.setContent(htmlReport, "text/html; charset=utf-8");

            final DataSource dataSource = new ByteArrayDataSource(htmlReport, "application/x-any");

            final MimeBodyPart htmlFileBodyPart = new MimeBodyPart();
            htmlFileBodyPart.setFileName("report.html");
            htmlFileBodyPart.setDataHandler(new DataHandler(dataSource));

            final Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textBodyPart);
            multipart.addBodyPart(htmlFileBodyPart);

            message.setContent(multipart);
            message.setSubject(String.format("EOD Portfolio Report - %s", LocalDate.now()));

            logger.info("Sending email.");

            Transport.send(message);

            logger.info("Email sent successfully.");
        } catch (final MessagingException | IOException exception) {
            logger.info(String.format("An exception occurred when sending the email: %s.", exception));
        }
    }

}