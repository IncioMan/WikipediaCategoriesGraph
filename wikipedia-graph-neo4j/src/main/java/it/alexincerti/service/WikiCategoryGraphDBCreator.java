package it.alexincerti.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import it.alexincerti.FileParser;
import it.alexincerti.models.Category;
import it.alexincerti.models.SubcategoryOfRelationship;
import it.alexincerti.repository.CategoryRepository;

@Service
@Profile("create-wiki-graph-db")
public class WikiCategoryGraphDBCreator {
	Logger logger = LoggerFactory.getLogger(WikiCategoryGraphDBCreator.class);

	@Autowired
	private CategoryRepository categoryRepository;
	private String baseFolder;
	private String categoryFile;
	private String categoryLinksFile;
	private String pageFile;

	public WikiCategoryGraphDBCreator(@Value("${category-dump-file}") String categoryFile, //
			@Value("${page-dump-file}") String pageFile, @Value("${category-links-dump-file}") String categoryLinksFile,
			@Value("${base-folder}") String baseFolder) {
		this.categoryFile = categoryFile;
		this.pageFile = pageFile;
		this.categoryLinksFile = categoryLinksFile;
		this.baseFolder = baseFolder;
	}

	public String getBaseFolder() {
		return baseFolder;
	}

	public String getCategoryFile() {
		return categoryFile;
	}

	public String getCategoryLinksFile() {
		return categoryLinksFile;
	}

	public String getPageFile() {
		return pageFile;
	}

	@PostConstruct
	private void init() {
		logger.info(String.format("Starting creating graph db..."));
		logger.info(String.format("Category SQL dump file: |%s|", getCategoryFile()));
		logger.info(String.format("Page SQL dump file: |%s|", getPageFile()));
		logger.info(String.format("Categorylinks SQL dump file: |%s|", getCategoryLinksFile()));
		create(getCategoryFile(), getPageFile(), getCategoryLinksFile());
	}

	// "C:\\Users\\Alex\\Documents\\category.sql"
	// "C:\\Users\\Alex\\Documents\\page.sql"
	// "C:\\Users\\Alex\\Documents\\categorylinks.sql"
	public void create(String categoryFile, String pageFile, String categoryLinksFile) {
		try {
			createCategoryNodes(categoryFile);
			createIDCategoryNameFromPageLink(pageFile, "pageIdCategoryFile.txt");
			ConcurrentHashMap<Long, String> pageDictionary = buildPageDictionary("pageIdCategoryFile.txt");
			createRelationships(categoryLinksFile, pageDictionary);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void createCategoryNodes(String categoryFile) throws FileNotFoundException, IOException {
		logger.info("Loading the category file. Starting creating nodes...");
		long lastTime = System.currentTimeMillis();
		final AtomicInteger done = new AtomicInteger(0);
		StopWatch watch = new StopWatch();
		watch.start();
		// read the file line by line
		List<Category> categoriesToSave = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(categoryFile))) {
			br.lines().parallel().forEach(line -> {
				if (!line.startsWith("INSERT INTO ") || line.length() < 2)
					return;

				Arrays.stream(line.split("[0-9]\\),\\((?=[0-9])")).filter(v -> !v.startsWith("INSERT INTO"))
						.forEach(statement -> {
							String categoryName = statement.replaceAll("[0-9]+,'", "").replaceAll("',.+", "");
							if (isInternalCategory(categoryName))
								return;
							Category category = new Category();
							category.setName(categoryName);
							categoriesToSave.add(category);
//
							if (done.incrementAndGet() % 30000 == 0) {
								getCategoryRepository().save(categoriesToSave, 0);
								categoriesToSave.clear();
								watch.stop();
								logger.info("Created " + done.get() + " category nodes" + ". It took: "
										+ watch.getTime() + " ms");
								watch.reset();
								watch.start();
							}
						});
			});
		}
		getCategoryRepository().save(categoriesToSave, 0);
		logger.info("Finished creating category nodes...");
		logger.info("Loaded " + done.get() + " categories in " + (System.currentTimeMillis() - lastTime) / 1000
				+ " seconds");
	}

	private String getPath(String file) {
		return Paths.get(baseFolder, file).toString();
	}

	public void createIDCategoryNameFromPageLink(String pageLinkFile, String outputFile)
			throws FileNotFoundException, IOException {
		logger.info("Loading the page file. Starting creating <pageID, category_title> pairs...");
		List<Pair<String, Integer>> categoriesToCheck = new ArrayList<>();
		// read the file line by line

		final AtomicInteger analyzedPages = new AtomicInteger(0);
		final AtomicInteger foundCategories = new AtomicInteger(0);
		final AtomicInteger notFoundCategories = new AtomicInteger(0);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(getPath(outputFile), false))) {
			try (BufferedWriter errorWriter = new BufferedWriter(new FileWriter(getPath("notfound" + outputFile)))) {
				try (BufferedReader br = new BufferedReader(new FileReader(pageLinkFile))) {
					br.lines().forEach(line -> {
						if (!line.startsWith("INSERT INTO ") || line.length() < 2)
							return;
						String name = null;
						Integer ID = null;
						Integer namespace = null;
						for (String page : FileParser.getPages(line)) {
							name = FileParser.getPageName(page);
							try {
								analyzedPages.incrementAndGet();
								if (analyzedPages.incrementAndGet() % 300000 == 0) {
									logger.info("Analyzed " + analyzedPages.get() + " pages so far");
								}
								if (name == null) {
									continue;
								}
//								name = page.split(",")[2].replaceAll(",'.+", "").replace("'", "");
								if (isInternalCategory(name)) {
									continue;
								}
								ID = Integer.parseInt(page.split(",")[0].replaceAll(",'.+", ""));
								namespace = Integer.parseInt(page.split(",")[1].replaceAll(",'.+", ""));

								if (namespace != 14) {
									continue;
								}

								categoriesToCheck.add(Pair.of(name, ID));
								if (categoriesToCheck.size() % 10000 == 0) {
									checkTheBatchOfCategories(categoriesToCheck, writer, errorWriter,
											notFoundCategories, foundCategories);
								}
							} catch (Exception ex) {
								// System.err.println("Error parsing " + page);
								ex.printStackTrace();
								continue;
							}
							if (analyzedPages.incrementAndGet() % 100000 == 0)
								logger.info("Parsed " + analyzedPages.get() + " pages so far");
						}
					});
					checkTheBatchOfCategories(categoriesToCheck, writer, errorWriter, notFoundCategories,
							foundCategories);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				errorWriter.flush();
			}
			logger.info("Was not able to find " + notFoundCategories.get()
					+ " categories, which were labeld as categories in the page file");
			logger.info("Found " + foundCategories.get() + " categories");
			writer.flush();
		}
	}

	private void checkTheBatchOfCategories(List<Pair<String, Integer>> categoriesToCheck, BufferedWriter writer,
			BufferedWriter errorWriter, AtomicInteger notFoundCategories, AtomicInteger foundCategories)
			throws IOException {
		List<String> names = categoriesToCheck.stream().map(c -> c.getLeft()).collect(Collectors.toList());
		Set<String> exsistingCategories = getCategoryRepository().findByNamesIn(names);
		categoriesToCheck.forEach(c -> {
			if (!exsistingCategories.contains(c.getLeft())) {
				notFoundCategories.getAndIncrement();
				try {
					errorWriter.write(c.getLeft() + ": " + c.getRight());
					errorWriter.newLine();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// System.err.println("Not found node for category: " + name);
				return;
			}

			if (foundCategories.incrementAndGet() % 100 == 0) {
				logger.debug("Found " + foundCategories.getAndIncrement() + " categories in page file so far");
			}
			try {
				writer.write(c.getRight() + ": " + c.getLeft());
				writer.newLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		writer.flush();
		errorWriter.flush();
		categoriesToCheck.clear();
	}

	private void createRelationshipsInBatch(List<Pair<String, String>> relationshipsToCreate,
			AtomicInteger doneCategories) {
		//
		List<Category> categoriesToSave = new ArrayList<>();
		Set<String> names = relationshipsToCreate.stream().map(c -> c.getLeft()).collect(Collectors.toSet());
		names.addAll(relationshipsToCreate.stream().map(c -> c.getRight()).collect(Collectors.toList()));
		List<Category> categories = getCategoryRepository().findCategoriesByNamesIn(names);
		Map<String, Category> nameCategoryMap = categories.stream()
				.collect(Collectors.toMap(Category::getName, Function.identity()));
		//
		relationshipsToCreate.forEach(relationship -> {
			Category startNode = nameCategoryMap.get(relationship.getLeft());
			Category endNode = nameCategoryMap.get(relationship.getRight());
			if (startNode == null || endNode == null) {
				return;
			}
			SubcategoryOfRelationship subcategoryOfRelationship = new SubcategoryOfRelationship();
			subcategoryOfRelationship.setSubCategory(startNode);
			subcategoryOfRelationship.setUpperCategory(endNode);
			startNode.getSubcategoriesOf().add(subcategoryOfRelationship);

			categoriesToSave.add(startNode);
			doneCategories.getAndIncrement();
		});
		getCategoryRepository().save(categoriesToSave, 1);
		relationshipsToCreate.clear();
	}

	public void createRelationships(String categoryLinksFile, ConcurrentHashMap<Long, String> pageDictionary)
			throws FileNotFoundException, IOException {
		final AtomicInteger doneCats = new AtomicInteger(0);
		List<Pair<String, String>> relationshipsToCreate = new ArrayList<>();
		long lastTime = System.currentTimeMillis();

		logger.info("Loading the categorylinks file. Creating relationships between categories...");
		try (BufferedReader br = new BufferedReader(new FileReader(categoryLinksFile))) {
			br.lines().forEach(line -> {
				// pick the lines containing inserts, not comments or DDL
				if (!line.startsWith("INSERT INTO ") || line.length() < 2)
					return;

				Arrays.stream(line.split("'\\),\\((?=[0-9])"))

						.filter(v -> !v.startsWith("INSERT INTO")).forEach(edge -> {
							if (edge.endsWith("','file")) {
								return;
							}
							Long ID = Long.parseLong(edge.split(",")[0]);
							String endCategoryName = edge.replaceAll("[0-9]+,'", "").replaceAll("',.+", "");

							// System.out.println(edge);

							if (edge.endsWith("'subcat")) {
								// if the subcategory was stored, is already indexed
								String startCategoryName = pageDictionary.get(ID);
								if (startCategoryName == null) {
									return;
								}

								relationshipsToCreate.add(Pair.of(startCategoryName, endCategoryName));
								if (relationshipsToCreate.size() % 10000 == 0) {
									createRelationshipsInBatch(relationshipsToCreate, doneCats);
								}

								if (doneCats.incrementAndGet() % 10000 == 0) {
									logger.info("Parsed " + doneCats.get() + " relationships so far");
									return;
								}
							}
						});
			});
		}
		logger.debug("Loaded " + doneCats.get() + " categories) in " + (System.currentTimeMillis() - lastTime) / 1000
				+ " seconds");
	}

	public static ConcurrentHashMap<Long, String> buildPageDictionary(String file) throws IOException {
		ConcurrentHashMap<Long, String> dictionary = new ConcurrentHashMap<Long, String>();
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] ls = line.split(":");
				Long id = Long.parseLong(ls[0].replace(" ", ""));
				String name = ls[1].replace(" ", "");
				dictionary.put(id, name);
			}
		}
		return dictionary;
	}

	public static boolean isInternalCategory(String name) {
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

	public CategoryRepository getCategoryRepository() {
		return categoryRepository;
	}
}