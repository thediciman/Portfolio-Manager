package dici.portfolio;

import dici.portfolio.entities.Portfolio;
import dici.portfolio.entities.Position;
import dici.portfolio.jobs.EmailReportJob;
import dici.portfolio.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final String TICKERS_FILE = "tickers.txt";

    private static final double INVESTMENT_VALUE_IN_USD = 100.00;

    public static void main(final String[] args) throws SchedulerException, ExecutionException, InterruptedException, IOException {
        final List<String> tickers = Files
            .lines(Path.of(TICKERS_FILE))
            .filter(not(String::isEmpty))
            .distinct()
            .collect(Collectors.toList());

        logger.info(String.format("%d tickers loaded from the file.", tickers.size()));

        final File responsesDirectory = new File(Utils.RESPONSES_DIRECTORY);
        if (!responsesDirectory.exists()) {
            final boolean directoryWasCreated = responsesDirectory.mkdir();
            if (directoryWasCreated) {
                logger.info(String.format("Responses directory (%s) missing, it is now created.", Utils.RESPONSES_DIRECTORY));
            }
        }

        final Map<String, Double> tickersPrices = Utils.getActualTickersPrices(tickers);

        logger.info(String.format("Investing $%.3f in each ticker.", INVESTMENT_VALUE_IN_USD));

        final Portfolio portfolio = new Portfolio();

        for (final String ticker : tickers) {
            final double currentPrice = tickersPrices.get(ticker);
            final double purchasedQuantity = INVESTMENT_VALUE_IN_USD / currentPrice;
            portfolio.addPosition(new Position(ticker, purchasedQuantity, currentPrice, DateTime.now()));
        }

        logger.info(portfolio);

        final double initialPortfolioValue = portfolio.getInitialValue();
        logger.info(String.format("Initial portfolio value: %.3f (USD)", initialPortfolioValue));

        final SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        final Scheduler scheduler = schedulerFactory.getScheduler();

        final JobDetail emailReportJob = newJob(EmailReportJob.class)
            .withIdentity("emailReportJob", "emailReportGroup")
            .build();

        emailReportJob.getJobDataMap().put("portfolio", portfolio);

        final CronTrigger emailReportTrigger = newTrigger()
            .withIdentity("emailReportTrigger", "emailReportGroup")
//            .withSchedule(cronSchedule("0 * * ? * *"))
//            .withSchedule(cronSchedule("0 0 0 ? * * *"))
            .withSchedule(cronSchedule("0 0 20 ? * MON-FRI *"))
            .build();

        scheduler.scheduleJob(emailReportJob, emailReportTrigger);
        scheduler.start();
    }

}