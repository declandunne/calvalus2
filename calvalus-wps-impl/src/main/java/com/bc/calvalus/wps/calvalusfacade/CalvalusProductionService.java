package com.bc.calvalus.wps.calvalusfacade;

import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.production.ProductionService;
import com.bc.calvalus.production.ProductionServiceConfig;
import com.bc.calvalus.production.hadoop.HadoopProductionServiceFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

/**
 * This class handles the production service operations. It has also been extended to include
 * other singleton objects.
 *
 * @author hans
 */
public class CalvalusProductionService {

    private static ProductionService productionService = null;
    private static Timer statusObserver;
    private static Map<String, Integer> userProductionMap;

    private static final String DEFAULT_BEAM_BUNDLE = HadoopProcessingService.DEFAULT_BEAM_BUNDLE;
    private static final String DEFAULT_CALVALUS_BUNDLE = HadoopProcessingService.DEFAULT_CALVALUS_BUNDLE;
    private static final String STAGING_DIRECTORY = "staging";
    private static final String CATALINA_BASE = System.getProperty("catalina.base");
    private static final String CALWPS_ROOT = CATALINA_BASE + "/webapps/bc-wps/";
    private static final String DEFAULT_CONFIG_PATH = new File(getUserAppDataCalWpsDir(), "calvalus.config").getPath();

    private CalvalusProductionService() {
    }

    public synchronized static ProductionService getProductionServiceSingleton() throws IOException, ProductionException {
        if (productionService == null) {
            productionService = createProductionService();
        }
        return productionService;
    }

    public synchronized static Timer getStatusObserverSingleton() {
        if (statusObserver == null) {
            statusObserver = new Timer("StatusObserver" + new Date().toString(), true);
        }
        return statusObserver;
    }

    public synchronized static Map<String, Integer> getUserProductionMap() {
        if (userProductionMap == null) {
            userProductionMap = new HashMap<>();
        }
        return userProductionMap;
    }

    protected static Map<String, String> getDefaultConfig() {
        Map<String, String> defaultConfig = ProductionServiceConfig.getCalvalusDefaultConfig();
        defaultConfig.put("calvalus.calvalus.bundle", DEFAULT_CALVALUS_BUNDLE);
        defaultConfig.put("calvalus.beam.bundle", DEFAULT_BEAM_BUNDLE);
        defaultConfig.put("calvalus.wps.staging.path", STAGING_DIRECTORY);
        return defaultConfig;
    }

    private static ProductionService createProductionService() throws ProductionException, IOException {
        HadoopProductionServiceFactory productionServiceFactory = new HadoopProductionServiceFactory();
        Map<String, String> defaultConfig = getDefaultConfig();
        Map<String, String> config = ProductionServiceConfig.loadConfig(getConfigFile(), defaultConfig);
        return productionServiceFactory
                    .create(config, getUserAppDataCalWpsDir(), new File(CALWPS_ROOT, config.get("calvalus.wps.staging.path")));
    }

    private static File getConfigFile() {
        File configFile;
        try {
            URL calvalusConfigUrl = CalvalusProductionService.class.getClassLoader().getResource("calvalus.config");
            if (calvalusConfigUrl == null) {
                throw new FileNotFoundException("Cannot find calvalus.config file.");
            }
            configFile = new File(calvalusConfigUrl.toURI());
        } catch (URISyntaxException | FileNotFoundException e) {
            configFile = new File(DEFAULT_CONFIG_PATH);
        }
        return configFile;
    }

    public static File getUserAppDataCalWpsDir() {
        String userHome = System.getProperty("user.home");
        return userHome != null ? new File(userHome, ".calwps") : null;
    }
}
