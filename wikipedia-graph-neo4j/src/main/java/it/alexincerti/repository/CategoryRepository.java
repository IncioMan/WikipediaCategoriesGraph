package it.alexincerti.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import it.alexincerti.models.Category;

@Repository
@Transactional
public interface CategoryRepository extends Neo4jRepository<Category, Long> {
	//
	@Query("MATCH (n:Category) WHERE n.name={0} RETURN n LIMIT 1;")
	Category findByName(String name);

	@Query("MATCH (n:Category) WHERE n.name = {0} RETURN count(n) > 0;")
	Boolean existsCategoryWithName(String name);

	@Query("MATCH (n:Category) WHERE n.name IN {0} RETURN n.name;")
	Set<String> findByNamesIn(List<String> names);

	@Query("MATCH (n:Category) WHERE n.name IN {0} RETURN n;")
	List<Category> findCategoriesByNamesIn(Set<String> names);

	@Query("MATCH p=shortestPath((a:Category {name: {0}})-[*]->(b:Category {name: {1}})) RETURN p;")
	public Iterable<Map<String, Object>> getShortestPath(String startCategoryName, String endCategoryName);

	@Query("MATCH (n:Category), (m:Category) where n.name IN {1} AND m.name = {0} WITH collect(n) as nodes, m UNWIND nodes as n MATCH path = allShortestPaths( (m)-[*..{2}]-(n) ) RETURN path")
	public Iterable<Map<String, Object>> getShortestPath(String startCategoryName, List<String> endCategoryNames,
			int maxLength);

	@Query("MATCH p=shortestPath((a:Category {name: {0}})-[*]->(b:Category {name: {1}})) RETURN length(p);")
	public Long getShortestPathLength(String startCategoryName, String endCategoryName);
}