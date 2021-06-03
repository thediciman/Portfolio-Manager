package dici.portfolio.jobs;

import dici.portfolio.entities.Portfolio;
import dici.portfolio.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class EmailReportJob implements Job {

    private static final Logger logger = LogManager.getLogger(EmailReportJob.class);

    public void execute(final JobExecutionContext context) {
        logger.info("Running email report job.");

        final JobDataMap jobDataMap = context.getMergedJobDataMap();
        final Portfolio portfolio = (Portfolio) jobDataMap.get("portfolio");

        final Map<String, Double> currentTickersPrices;
        try {
            currentTickersPrices = Utils.getActualTickersPrices(new ArrayList<>(portfolio.getOwnedTickers()));
        } catch (final ExecutionException | InterruptedException exception) {
            logger.info(String.format("Encountered exception while fetching the actual ticker prices: %s", exception));
            return;
        }

        Utils.printPortfolioPositionsEvolution(portfolio, currentTickersPrices);
        Utils.sendEmailWithPortfolioReport(portfolio, currentTickersPrices);
    }

}