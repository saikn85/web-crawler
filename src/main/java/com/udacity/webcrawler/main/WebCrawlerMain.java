package com.udacity.webcrawler.main;

import com.google.inject.Guice;
import com.udacity.webcrawler.WebCrawler;
import com.udacity.webcrawler.WebCrawlerModule;
import com.udacity.webcrawler.json.ConfigurationLoader;
import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.json.CrawlResultWriter;
import com.udacity.webcrawler.json.CrawlerConfiguration;
import com.udacity.webcrawler.profiler.Profiler;
import com.udacity.webcrawler.profiler.ProfilerModule;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Objects;

public final class WebCrawlerMain {

    private final CrawlerConfiguration config;
    @Inject
    private WebCrawler crawler;
    @Inject
    private Profiler profiler;

    private WebCrawlerMain(CrawlerConfiguration config) {
        this.config = Objects.requireNonNull(config);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: WebCrawlerMain [starting-url]");
            return;
        }

        CrawlerConfiguration config = new ConfigurationLoader(Path.of(args[0])).load();
        new WebCrawlerMain(config).run();
    }

    private void run() throws Exception {
        Guice.createInjector(new WebCrawlerModule(config), new ProfilerModule()).injectMembers(this);

        CrawlResult result = crawler.crawl(config.getStartPages());
        CrawlResultWriter resultWriter = new CrawlResultWriter(result);

        String resultPath = config.getResultPath();
        if ((resultPath == null) || resultPath.trim().isEmpty()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out))) {
                resultWriter.write(writer);
            }
        } else {
            resultWriter.write(Path.of(resultPath));
        }

        String profilePath = config.getProfileOutputPath();
        if ((profilePath == null) || profilePath.trim().isEmpty()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out))) {
                profiler.writeData(writer);
            }
        } else {
            profiler.writeData(Path.of(profilePath));
        }
    }
}