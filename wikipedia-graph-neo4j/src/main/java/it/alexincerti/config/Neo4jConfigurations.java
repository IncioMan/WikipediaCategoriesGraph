package it.alexincerti.config;

import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.SessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.transaction.Neo4jTransactionManager;

public class Neo4jConfigurations {
	public static final String URL = System.getenv("NEO4J_URL") != null ? System.getenv("NEO4J_URL")
			: "bolt://neo4j:password@localhost:7687";
	public static final String EMBEDDED_FOLDER = "file:C:\\Users\\Alex\\Code\\wikipedia-category-graph\\DummyGraph\\neostore.nodestore.db";

	@Bean
	public org.neo4j.ogm.config.Configuration getConfiguration() {
		Configuration configuration = new Configuration.Builder().uri(URL).build();
		return configuration;
	}

	@Bean
	public SessionFactory getSessionFactory() {
		return new SessionFactory(getConfiguration(), "it.alexincerti.models");
	}

	@Bean
	public Neo4jTransactionManager transactionManager() {
		return new Neo4jTransactionManager(getSessionFactory());
	}
}
