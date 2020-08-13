/*
 * Copyright (c) 2020.  amrishraje@gmail.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.amrishraje.cblitetester.sync;

import com.couchbase.lite.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

import static com.couchbase.lite.AbstractReplicator.ActivityLevel.BUSY;
import static com.couchbase.lite.AbstractReplicator.ActivityLevel.STOPPED;

public class SyncService {
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    public static final String CONFIG_XML = "config.xml";
    private static Properties properties;

    public String dbName;
    public String dbPath;
    public String syncGatewayUrl;
    private Database database;
    private Replicator replicator;
    private ReplicatorConfiguration replicatorConfig;
    private volatile boolean replicating;

    public SyncService() {
        loadProperties();
    }

    public Database getDatabase() {
        return database;
    }

    public void createLocalCBLiteFile() {
        // Initialize Couchbase Lite
        CouchbaseLite.init();
        // Get the database (and create it if it doesn't exist).
        DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory(dbPath);
        try {
            database = new Database(dbName, config);
        } catch (CouchbaseLiteException e) {
            logger.info("Unable to create CBLite DB", e);
        }
        logger.debug("CbLite file has been created and database has been initialized");
    }

    public void startPullReplicator(SyncRequest syncRequest) {
        startReplicator(syncRequest.getUser(), syncRequest.getPassword(),
                false, syncRequest.getChannels(), "pull");
    }

    private void startReplicator(String user, String pwd, boolean isContinuous, List<String> channels, String replicationMode) {
        logger.debug("initializing");
        try {
            properties = new Properties();
            properties.loadFromXML(new FileInputStream("config.xml"));
        } catch (IOException e) {
            logger.error("Error in static block while reading config file", e);
        }

        logger.debug("calling startReplicator");
        logger.debug("syncing channels: " + channels);
        if (database == null) createLocalCBLiteFile();
        syncGatewayUrl = properties.getProperty("sgURL");
        Endpoint targetEndpoint = null;
        try {
            targetEndpoint = new URLEndpoint(new URI(syncGatewayUrl));
        } catch (URISyntaxException e) {
            logger.info("Bad Sync URL", e);
        }
        replicatorConfig = new ReplicatorConfiguration(database, targetEndpoint);
        logger.debug("Replication Mode is {}", replicationMode);
        switch (replicationMode) {
            case "Push":
                replicatorConfig.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH);
                break;
            case "Pull and Push":
                replicatorConfig.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL);
                break;
            case "Pull":
            default:
                replicatorConfig.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PULL);
                break;
        }
//        Do not replicate deleted docs!
        replicatorConfig.setPullFilter((document, flags) -> !flags.contains(DocumentFlag.DocumentFlagsDeleted));
        replicatorConfig.setPushFilter((document, flags) -> !flags.contains(DocumentFlag.DocumentFlagsDeleted));

//      Set sync channels
        replicatorConfig.setChannels(channels);
//    Amrish - Cert pinning
        if (!properties.getProperty("sgCert", "none").equals("none")) {
            InputStream is = null;
            try {
                is = new FileInputStream(properties.getProperty("sgCert"));
            } catch (FileNotFoundException ex) {
                logger.error("Sync Gateway Cert not found", ex);
            }
            byte[] cert = null;
            try {
                cert = IOUtils.toByteArray(is);
            } catch (IOException ex) {
                logger.error("Sync Gateway cert error", ex);
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignore) {
                }
            }
            replicatorConfig.setPinnedServerCertificate(cert);
            if (isContinuous) replicatorConfig.setContinuous(true);
            else replicatorConfig.setContinuous(false);
        }
//end cert pinning
        // Add authentication.
        replicatorConfig.setAuthenticator(new BasicAuthenticator(user, pwd));
//        TODO support session based auth in future
//        replicatorConfig.setAuthenticator(new SessionAuthenticator("00ee4a2fca27d65061f509f89c758e00a4ca83cf"));
        replicator = new Replicator(replicatorConfig);
        setReplicating(true);
        //Add Change listener to check for errors
        ListenerToken changeListenerToken =
                replicator.addChangeListener(change -> {
            AbstractReplicator.Status status = change.getStatus();
            if (status.getError() != null) {
                logger.error("Error replicating from Sync GW, error:  " + status.getError().getMessage()
                        + " " + status.getError().getCode());
            }
            logger.info("Replication Status: " + status);
            if (status.getActivityLevel() == STOPPED) {
                logger.info("Sync stopped: {} of {}", status.getProgress().getCompleted(), status.getProgress().getTotal());
                setReplicating(false);
            } else if (status.getActivityLevel() == BUSY) {
                logger.info("Sync progress: {} of {}", status.getProgress().getCompleted(), status.getProgress().getTotal());
            }
//            Platform.runLater(new Runnable() {
//                @Override
//                public void run() {
//                    if (change.getStatus().getError() != null) {
//                        mainController.statusLabel.setText(change.getStatus().getError().getMessage());
//                        isReplicatorStarted = false;
//                    } else {
//                        mainController.statusLabel.setText("Sync Status: " + change.getStatus().getActivityLevel() +
//                                "\n" + "Synced  " + change.getStatus().getProgress().getCompleted() + "  of   " +
//                                change.getStatus().getProgress().getTotal());
//                        if (change.getStatus().getActivityLevel().equals(AbstractReplicator.ActivityLevel.STOPPED) ||
//                                change.getStatus().getActivityLevel().equals(AbstractReplicator.ActivityLevel.IDLE)) {
//                            mainController.populateTable(false);
//                        }
//                    }
//                }
//            });

        });

        // Start replication.
        replicator.start();

        while (true) {
            if (isReplicating()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Unexpected exception", e);
                }
            } else {
                logger.info("Replication complete");
                try {
                    database.close();
                    replicator.removeChangeListener(changeListenerToken);
                } catch (CouchbaseLiteException e) {
                    logger.error("Unexpected exception", e);
                }
                break;
            }
        }
    }

    private synchronized void setReplicating(boolean replicating) {
        this.replicating = replicating;
    }

    private synchronized boolean isReplicating() {
        return replicating;
    }

    private void loadProperties() {
        properties = new Properties();
        try {
            properties.loadFromXML(new FileInputStream(CONFIG_XML));
        } catch (IOException e) {
            throw new RuntimeException("Exception while loading properties from " + CONFIG_XML, e);
        }
        dbName = properties.getProperty("sgDB", "syncdb");
        dbPath = properties.getProperty("cblite-loc");
        syncGatewayUrl = properties.getProperty("sgURL");
    }
}
