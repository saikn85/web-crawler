package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
    private final Clock clock;
    private final Duration timeout;
    private final int popularWordCount;
    private final ForkJoinPool pool;
    private final List<Pattern> ignoredUrls;
    private final int maxDepth;
    private final PageParserFactory parserFactory;

    @Inject
    ParallelWebCrawler(
            Clock clock,
            @Timeout Duration timeout,
            @PopularWordCount int popularWordCount,
            @TargetParallelism int threadCount,
            @IgnoredUrls List<Pattern> ignoredUrls,
            @MaxDepth int maxDepth,
            PageParserFactory parserFactory) {
        this.clock = clock;
        this.timeout = timeout;
        this.popularWordCount = popularWordCount;
        this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
        this.ignoredUrls = ignoredUrls;
        this.maxDepth = maxDepth;
        this.parserFactory = parserFactory;
    }

    @Override
    public CrawlResult crawl(List<String> startingUrls) {
        Instant backOff = clock.instant().plus(timeout);
        // Referred - Java Doc
        ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
        // Referred - Java Doc
        ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();

        // From Lesson on ForkJoinTask
        for (String url : startingUrls) {
            pool.invoke(new CrawlInternalAction(url, backOff, maxDepth, counts, visitedUrls));
        }

        // General Logic flow
        if (counts.isEmpty()) {
            return new CrawlResult.Builder()
                    .setWordCounts(counts)
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        }

        return new CrawlResult.Builder()
                .setWordCounts(WordCounts.sort(counts, popularWordCount))
                .setUrlsVisited(visitedUrls.size())
                .build();
    }

    @Override
    public int getMaxParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }

    final class CrawlInternalAction extends RecursiveAction {
        private final String url;
        private final Instant deadline;
        private final int maxDepth;
        private final ConcurrentMap<String, Integer> counts;
        private final ConcurrentSkipListSet<String> visitedUrls;

        public CrawlInternalAction(final String url, final Instant deadline, final int maxDepth,
                                   final ConcurrentMap<String, Integer> counts,
                                   final ConcurrentSkipListSet<String> visitedUrls) {
            this.url = url;
            this.deadline = deadline;
            this.maxDepth = maxDepth;
            this.counts = counts;
            this.visitedUrls = visitedUrls;
        }

        @Override
        protected void compute() {
            var now = clock.instant();
            final List<CrawlInternalAction> subLinks = new ArrayList<>();

            // Code copied from Sequential Web Crawler
            for (Pattern pattern : ignoredUrls) {
                if (pattern.matcher(url).matches()) {
                    return;
                }
            }

            if (maxDepth == 0 || now.isAfter(deadline)) {
                return;
            }

            if (!visitedUrls.add(url)) {
                return;
            }

            PageParser.Result result = parserFactory.get(url).parse();
            // https://stackoverflow.com/questions/54199820/how-to-set-append-options-and-standardcharsets-encoding-to-bufferedwriter-in-jav
            // Made changes to address Atomicity
            for (Map.Entry<String, Integer> x : result.getWordCounts().entrySet()) {
                counts.compute(x.getKey(), (k, v) -> v == null ? x.getValue() : x.getValue() + v);
            }

            result.getLinks().stream()
                    .map(link -> new CrawlInternalAction(
                            link,
                            deadline,
                            maxDepth - 1,
                            counts,
                            visitedUrls)).forEach(subLinks::add);
            invokeAll(subLinks);
        }
    }
}
