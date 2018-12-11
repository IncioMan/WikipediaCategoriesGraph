package it.alexincerti.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.internal.InternalPath;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.alexincerti.models.Category;
import it.alexincerti.repository.CategoryRepository;

@Service
public class ShortestPathCalculator {
	Logger logger = LoggerFactory.getLogger(ShortestPathCalculator.class);

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private SessionFactory sessionFactory;

	public CategoryPath getClosestNode(String startingNode, List<String> endNodes) {
		if (endNodes.contains(startingNode)) {
			CategoryPath categoryPath = new CategoryPath();
			categoryPath.setEndCategory(startingNode);
			categoryPath.setStartCategory(startingNode);
			categoryPath.setLength((long) 0);
			return categoryPath;
		}

		return getShortestPathInBatch(startingNode, endNodes, 20);
	}

	public CategoryPath getShortestPath(String startingCategoryNode, String endCategoryNode) {
		CategoryPath path = new CategoryPath();

		if (startingCategoryNode == null || endCategoryNode == null) {
			return null;
		}
		try {
			Category startCategory = getCategoryRepository().findByName(startingCategoryNode);
			Category endCategory = getCategoryRepository().findByName(endCategoryNode);
			//
			if (startCategory == null || endCategory == null) {
				return null;
			}
			path.setStartCategory(startingCategoryNode);
			path.setEndCategory(endCategoryNode);

			return path;
		} catch (

		Exception e) {
			e.printStackTrace();
			System.err.println(
					String.format("Something went wrong calculating distances. starting node |%s| and end node |%s|",
							startingCategoryNode, endCategoryNode));
			return null;
		}
	}

	// on all macro categories (25) takes 121s
	public CategoryPath getShortestPathInBatchOld(List<Pair<String, String>> startEndNodes) {
		String query = "MATCH p=shortestPath((a:Category {name: $startNode})-[*]->(b:Category {name: $endNode})) RETURN a.name, b.name, length(p)";
		StringBuilder finalQuery = new StringBuilder();
		Session session = getSessionFactory().openSession();
		final AtomicInteger index = new AtomicInteger(0);
		HashMap<String, Object> parameters = new HashMap<>();
		startEndNodes.forEach(pair -> {
			if (index.getAndIncrement() != 0) {
				finalQuery.append(" UNION ");
			}
			finalQuery.append(query.replace("startNode", "startNode" + index).replace("endNode", "endNode" + index));
			parameters.put("startNode" + index, pair.getLeft());
			parameters.put("endNode" + index, pair.getRight());
		});
		Result result = session.query(finalQuery.toString(), parameters);
		Iterable<Map<String, Object>> iterable = () -> result.iterator();
		Stream<Map<String, Object>> targetStream = StreamSupport.stream(iterable.spliterator(), false);
		Map<String, Object> path = targetStream
				.min((a, b) -> ((Integer) Integer.parseInt(a.get("length(p)").toString()))
						.compareTo((Integer) Integer.parseInt(b.get("length(p)").toString())))
				.orElseGet(() -> null);

		if (path == null) {
			return null;
		}

		CategoryPath categoryPath = new CategoryPath();
		categoryPath.setStartCategory(path.get("a.name").toString());
		categoryPath.setEndCategory(path.get("b.name").toString());
		categoryPath.setLength(Long.parseLong(path.get("length(p)").toString()));
		return categoryPath;
	}

	// https://neo4j.com/developer/kb/all-shortest-paths-between-set-of-nodes/
	// approach
	// on all macro categories (25) takes 4s
	private CategoryPath getShortestPathInBatch(String startNode, List<String> endNodes, Integer maxPathLength) {
		String query = "MATCH (n:Category), (m:Category) where n.name IN {endNodes} AND m.name = $startNode\r\n"
				+ "WITH collect(n) as nodes, m\r\n" + "UNWIND nodes as n\r\n"
				+ "MATCH path = allShortestPaths( (m)-[*..$maxLength]-(n) )\r\n" + "RETURN path";
		StringBuilder finalQuery = new StringBuilder();
		Session session = getSessionFactory().openSession();
		HashMap<String, Object> parameters = new HashMap<>();
		finalQuery.append(query.replace("$maxLength", maxPathLength.toString()));
		parameters.put("startNode", startNode);
		parameters.put("endNodes", endNodes);
		parameters.put("maxLength", maxPathLength);
		Result result = session.query(finalQuery.toString(), parameters);
		Iterable<Map<String, Object>> iterable = () -> result.iterator();
		Stream<Map<String, Object>> targetStream = StreamSupport.stream(iterable.spliterator(), false);
		Map<String, Object> path = targetStream.min((a, b) -> {
			InternalPath path1 = (InternalPath) a.get("path");
			InternalPath path2 = (InternalPath) b.get("path");
			return ((Long) StreamSupport.stream(path1.spliterator(), false).count())
					.compareTo(((Long) StreamSupport.stream(path2.spliterator(), false).count()));
		}).orElseGet(() -> null);

		if (path == null) {
			return null;
		}

		InternalPath shortest = (InternalPath) path.get("path");
		CategoryPath categoryPath = new CategoryPath();
		Long size = 1l;
		Iterator<Node> nodes = shortest.nodes().iterator();
		String startNodeName = nodes.next().get("name").asString();
		categoryPath.setStartCategory(startNode);
		categoryPath.getPath().add(startNodeName);
		Node lastNode = null;
		while (nodes.hasNext()) {
			size++;
			lastNode = nodes.next();
			categoryPath.getPath().add(lastNode.get("name").asString());
		}
		categoryPath.setEndCategory(lastNode.get("name").asString());
		categoryPath.setLength(size);
		return categoryPath;
	}

	public List<CategoryPath> getShortestPathList(String startNode, List<String> endNodes, Integer maxPathLength) {
		String query = "MATCH (n:Category), (m:Category) where n.name IN {endNodes} AND m.name = $startNode\r\n"
				+ "WITH collect(n) as nodes, m\r\n" + "UNWIND nodes as n\r\n"
				+ "MATCH path = allShortestPaths( (m)-[*..$maxLength]-(n) )\r\n" + "RETURN path";
		StringBuilder finalQuery = new StringBuilder();
		Session session = getSessionFactory().openSession();
		HashMap<String, Object> parameters = new HashMap<>();
		finalQuery.append(query.replace("$maxLength", maxPathLength.toString()));
		parameters.put("startNode", startNode);
		parameters.put("endNodes", endNodes);
		parameters.put("maxLength", maxPathLength);
		Result result = session.query(finalQuery.toString(), parameters);
		Iterable<Map<String, Object>> iterable = () -> result.iterator();
		Stream<Map<String, Object>> targetStream = StreamSupport.stream(iterable.spliterator(), false);
		List<CategoryPath> paths = targetStream.map(path -> {
			InternalPath shortest = (InternalPath) path.get("path");
			CategoryPath categoryPath = new CategoryPath();
			Long size = 1l;
			Iterator<Node> nodes = shortest.nodes().iterator();
			String startNodeName = nodes.next().get("name").asString();
			categoryPath.setStartCategory(startNode);
			categoryPath.getPath().add(startNodeName);
			Node lastNode = null;
			while (nodes.hasNext()) {
				size++;
				lastNode = nodes.next();
				categoryPath.getPath().add(lastNode.get("name").asString());
			}
			categoryPath.setEndCategory(lastNode.get("name").asString());
			categoryPath.setLength(size);
			return categoryPath;
		}).collect(Collectors.toList());

		return paths;
	}

	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	public CategoryRepository getCategoryRepository() {
		return categoryRepository;
	}

	public CategoryPath getShortestPath(String startCategory, String endCategory, int maxPathLength) {
		String query = "MATCH p=shortestPath((a:Category {name: $startNode})-[*]->(b:Category {name: $endNode})) RETURN p";
		Session session = getSessionFactory().openSession();
		HashMap<String, Object> parameters = new HashMap<>();
		parameters.put("startNode", startCategory);
		parameters.put("endNode", endCategory);
		Result result = session.query(query, parameters);
		Iterable<Map<String, Object>> iterable = () -> result.iterator();
		Iterator<Map<String, Object>> iterator = iterable.iterator();
		if (iterator.hasNext()) {
			InternalPath shortest = (InternalPath) iterator.next().get("p");
			CategoryPath categoryPath = new CategoryPath();
			Long size = 1l;
			Iterator<Node> nodes = shortest.nodes().iterator();
			String startNodeName = nodes.next().get("name").asString();
			categoryPath.setStartCategory(startNodeName);
			categoryPath.getPath().add(startNodeName);
			Node lastNode = null;
			while (nodes.hasNext()) {
				size++;
				lastNode = nodes.next();
				categoryPath.getPath().add(lastNode.get("name").asString());
			}
			categoryPath.setEndCategory(lastNode.get("name").asString());
			categoryPath.setLength(size);
			return categoryPath;
		}
		logger.debug("Could find shortest path between " + startCategory + " and " + endCategory);
		return null;
	}
}
