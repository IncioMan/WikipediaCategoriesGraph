package it.alexincerti;

import java.util.ArrayList;
import java.util.Map;

import org.neo4j.driver.internal.InternalPath;
import org.neo4j.driver.v1.types.Node;

public class Utilties {

	public static Iterable<Node> extractPath(Iterable<Map<String, Object>> shortestPath) {
		try {
			Map<String, Object> map = shortestPath.iterator().next();
			InternalPath path = (InternalPath) map.get("p");
			return path.nodes();
		} catch (Exception e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

}
