package io.amrishraje.cblitetester;

import io.amrishraje.cblitetester.sync.SyncRequest;
import io.amrishraje.cblitetester.sync.SyncService;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Command Line Interface for performing some of the
 * CBLiteTester's behavior.
 */
public class MainCli {
    private static final Logger logger = LoggerFactory.getLogger(MainCli.class);
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String CHANNELS = "channels";
    private Options options;

    public static void main(String... args) {
        MainCli mainCli = new MainCli();
        mainCli.initOptions();
        try {
            CommandLine cmd = mainCli.parseCommandLine(args);
            SyncRequest syncRequest = mainCli.createSyncRequest(cmd);
            mainCli.performSync(syncRequest);
        } catch (ParseException e) {
            logger.error("Invalid usage: {}", e.getMessage());
            mainCli.printUsage();
        }
        System.exit(0);
    }

    private void initOptions() {
        options = new Options();
        options.addOption(Option
                        .builder(USERNAME)
                        .hasArg()
                        .desc("SyncGateway username")
                        .required().build());
        options.addOption(Option
                        .builder(PASSWORD)
                        .hasArg()
                        .desc("SyncGateway password")
                        .required().build());
        options.addOption(Option
                .builder(CHANNELS)
                .hasArgs()
                .desc("SyncGateway channels to sync")
                .required().build());

    }

    private void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "cmd", options );
    }

    private CommandLine parseCommandLine(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        return parser.parse( options, args);
    }

    private SyncRequest createSyncRequest(CommandLine cmd) {
        SyncRequest syncRequest = new SyncRequest(
                cmd.getOptionValue(USERNAME),
                cmd.getOptionValue(PASSWORD),
                Arrays.asList(cmd.getOptionValues(CHANNELS)));
        return syncRequest;
    }

    private void performSync(SyncRequest syncRequest) {
        logger.info("Performing sync");
        SyncService syncService = new SyncService();
        syncService.startPullReplicator(syncRequest);
    }
}
