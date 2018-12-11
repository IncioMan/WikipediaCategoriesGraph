package it.alexincerti.rest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.alexincerti.service.CategoryPath;
import it.alexincerti.service.ShortestPathCalculator;

@RestController
public class CategoryMappingController {
	Logger logger = LoggerFactory.getLogger(CategoryMappingController.class);

	@Autowired
	private ShortestPathCalculator shortestPathCalculator;

	public ShortestPathCalculator getShortestPathCalculator() {
		return shortestPathCalculator;
	}

	public Map<String, CategoryPath> getCategoryMapping(String startCategory, List<String> endNodes,
			int maxPathLength) {
		if (endNodes.contains(startCategory)) {
			CategoryPath categoryPath = new CategoryPath();
			categoryPath.setEndCategory(startCategory);
			categoryPath.setStartCategory(startCategory);
			categoryPath.setLength((long) 0);
			HashMap<String, CategoryPath> hashMap = new HashMap<>();
			hashMap.put(startCategory, categoryPath);
			return hashMap;
		}
		List<CategoryPath> paths = getShortestPathCalculator().getShortestPathList(startCategory, endNodes,
				maxPathLength);
		Map<String, CategoryPath> categoryCountMap = paths.stream().collect(Collectors.groupingBy(
				CategoryPath::getEndCategory, //
				Collectors.collectingAndThen(Collectors.reducing(
						(CategoryPath d1, CategoryPath d2) -> d1.getPath().size() > d2.getPath().size() ? d1 : d2),
						Optional::get)));

		return categoryCountMap;
	}

	@GetMapping("/shortestPath")
	public CategoryPath getShortestPath(@RequestParam("startCategory") String startCategory,
			@RequestParam("endCategory") String endCategory, @RequestParam("maxPathLength") int maxPathLength) {
		return getShortestPathCalculator().getShortestPath(startCategory, endCategory, maxPathLength);
	}

	public String getMacroCategoryMapping(List<String> startCategories, List<String> endCategories, int maxPathLength) {
		Map<String, Long> mappingCount = new ConcurrentHashMap<String, Long>();
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(startCategories.size());
		startCategories.forEach(startCategory -> {
			executor.execute(() -> {
				Map<String, CategoryPath> paths = getCategoryMapping(startCategory, endCategories, 20);
				paths.forEach((k, v) -> {
					mappingCount.put(k, mappingCount.getOrDefault(k, Long.MAX_VALUE) + v.getLength());
				});
			});
		});
		try {
			executor.shutdown();
			executor.awaitTermination(3, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			logger.error("Error waiting for all threads to execute", e);
			e.printStackTrace();
		}
		String category = mappingCount.keySet().stream()
				.min((k1, k2) -> mappingCount.get(k1).compareTo(mappingCount.get(k2))).orElse("");
//		String macroCategory = categoryCountMap.keySet().stream()
//				.min((k1, k2) -> categoryCountMap.get(k1).compareTo(categoryCountMap.get(k2))).orElse(null);
		return category;
	}

	@GetMapping("/mapCategory")
	public String mappingEntrypoint(@RequestParam("startCategories") String startCategories,
			@RequestParam("endCategories") String endCategories) {
		logger.debug(String.format("Mapping function called staring categories |%s| end categories |%s|",
				startCategories, endCategories));
		List<String> startNodes = Arrays.asList(startCategories.split("::"));
		List<String> endNodes = Arrays.asList(endCategories.split("::"));

		try {
			String macroCategoryMapping = getMacroCategoryMapping(startNodes, endNodes, 20);
			logger.info(String.format("Calculating mapping staring categories |%s| end categories |%s| --> |%s|",
					String.join(", ", startNodes), String.join(", ", endNodes), macroCategoryMapping));
			return macroCategoryMapping;
		} catch (Exception e) {
			logger.error(String.format("Error calculating mapping staring categories |%s| end categories |%s|",
					String.join(", ", startNodes), String.join(", ", endNodes)), e);
			return null;
		}
	}

}