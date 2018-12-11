package it.alexincerti.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CalculateClosestMainCategory {
	Logger logger = LoggerFactory.getLogger(CalculateClosestMainCategory.class);

	@Autowired
	private ShortestPathCalculator shortestPathCalculator;

	public ShortestPathCalculator getShortestPathCalculator() {
		return shortestPathCalculator;
	}

	public void calculateShortestDistancesToMainCategories(String categoryFile, String mainCategoriesString,
			String outputFile, Integer threadsNumber, Long statementToStartFrom) {
		try {
			final AtomicInteger analizedStatements = new AtomicInteger(0);
			final AtomicInteger mappedCategories = new AtomicInteger(0);
			final AtomicInteger notMappedCategories = new AtomicInteger(0);
			StopWatch stopWatch = new StopWatch();
			List<CategoryPath> paths = new CopyOnWriteArrayList<CategoryPath>();
			ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNumber);

			List<String> mainCategories = Arrays.asList(mainCategoriesString.split("\\|"));
			stopWatch.start();
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true))) {
				// read the file line by line
				try (BufferedReader br = new BufferedReader(new FileReader(categoryFile))) {
					br.lines().parallel().forEach(line -> {
						// while((line=br.readLine())!=null){
						// pick the lines containing inserts, not comments or DDL
						if (!line.startsWith("INSERT INTO ") || line.length() < 2)
							return;

						int i = 0;
						while (executor.getQueue().size() >= threadsNumber * 2) {
							if (i++ % 2 == 0 && mappedCategories.get() > 0) {
								logger.debug("[" + mappedCategories.get() + " mapped categories in "
										+ stopWatch.getTime() / 1000 + " seconds" + "] On average "
										+ stopWatch.getTime() / mappedCategories.get() / 1000 + " s/category...");
							}
							try {
								logger.debug("[" + executor.getPoolSize()
										+ " threads in the queue] Waiting for threads to finish executing...");
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

						Arrays.stream(line.split("[0-9]\\),\\((?=[0-9])")).filter(v -> !v.startsWith("INSERT INTO"))
								.forEach(statement -> {
									if (analizedStatements.getAndIncrement() + 1 < statementToStartFrom) {
										return;
									}
									String categoryName = statement.replaceAll("[0-9]+,'", "").replaceAll("',.+", "");
									if (isInternalCategory(categoryName))
										return;
									//
//									logger.debug(String.format("Analyzing paths for |%s| (statement |%d|)...",
//											categoryName, analizedStatements.get()));
									executor.submit(() -> {
										CategoryPath closestNode = getShortestPathCalculator()
												.getClosestNode(categoryName, mainCategories);
										if (closestNode == null) {
											notMappedCategories.get();
											return;
										}
										mappedCategories.incrementAndGet();
										paths.add(closestNode);
										String summary = String.format("|%s| -> |%s|: %d, (%s)",
												closestNode.getStartCategory(), closestNode.getEndCategory(),
												closestNode.getLength(), String.join(", ", closestNode.getPath()));
										logger.debug(summary);
										if (paths.size() >= threadsNumber) {
											logger.debug("[" + paths.size()
													+ " paths calculated] Writing on to output file...");
											paths.forEach(p -> {
												String str = String.format("|%s| -> |%s|: %d, (%s)",
														p.getStartCategory(), p.getEndCategory(), p.getLength(),
														String.join(", ", p.getPath()));
												try {
													writer.append(str);
													writer.newLine();
													writer.flush();
												} catch (IOException e) {
													logger.error("Unable to write to file");
													e.printStackTrace();
												}
											});
											paths.clear();
										}
									});
									if (analizedStatements.get() % 10000 == 0) {
										logger.debug("Analyzed distances for " + analizedStatements.get()
												+ " categories so far");
									}
									if (notMappedCategories.get() > 0 && notMappedCategories.get() % 100 == 0) {
										logger.debug(notMappedCategories.get()
												+ " categories so far have not been mapped to any macro-category");
									}
								});
					});
				}
			}
		} catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
		}
	}

	private static boolean isInternalCategory(String name) {
		if (name.startsWith("Wikipedia_articles_"))
			return true;
		if (name.startsWith("Suspected_Wikipedia_sockpuppets"))
			return true;
		if (name.startsWith("Articles_with_"))
			return true;
		if (name.startsWith("Redirects_"))
			return true;
		if (name.startsWith("WikiProject_"))
			return true;
		if (name.startsWith("Articles_needing_"))
			return true;
		return name.startsWith("Wikipedians_");
	}
}
